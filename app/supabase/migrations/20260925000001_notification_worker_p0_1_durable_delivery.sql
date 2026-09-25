BEGIN;

-- P0-1 durable notification delivery, lease fencing, and reconciliation.
-- This migration is intentionally forward-only and does not backfill old attempts.

-- ---------------------------------------------------------------------------
-- Durable logical notifications and delivery attempts
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.notification_delivery_logicals (
  logical_notification_key text PRIMARY KEY,
  event_type text NOT NULL,
  event_id uuid NOT NULL,
  teacher_id uuid NOT NULL REFERENCES public.teachers(id),
  student_id uuid NOT NULL REFERENCES public.students(id),
  provider_name text NOT NULL DEFAULT 'WAHA',
  provider_idempotency_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_notification_delivery_logical_event
    UNIQUE (event_type, event_id),
  CONSTRAINT uq_notification_delivery_provider_key
    UNIQUE (provider_name, provider_idempotency_key),
  CONSTRAINT uq_notification_delivery_logical_provider_key
    UNIQUE (logical_notification_key, provider_idempotency_key)
);

CREATE TABLE IF NOT EXISTS public.notification_delivery_attempts (
  attempt_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  logical_notification_key text NOT NULL,
  event_type text NOT NULL,
  event_id uuid NOT NULL,
  teacher_id uuid NOT NULL REFERENCES public.teachers(id),
  student_id uuid NOT NULL REFERENCES public.students(id),
  attempt_number integer NOT NULL CHECK (attempt_number > 0),
  provider_name text NOT NULL DEFAULT 'WAHA',
  provider_idempotency_key text NOT NULL,
  claim_token uuid NOT NULL,
  request_fingerprint text,
  provider_message_id text,
  provider_status text NOT NULL DEFAULT 'NOT_STARTED'
    CHECK (provider_status IN (
      'NOT_STARTED', 'SENDING', 'ACCEPTED', 'SENT',
      'FAILED', 'UNKNOWN', 'CANCELLED'
    )),
  provider_response jsonb,
  provider_error text,
  started_at timestamptz,
  completed_at timestamptz,
  reconciliation_status text NOT NULL DEFAULT 'NOT_REQUIRED'
    CHECK (reconciliation_status IN (
      'NOT_REQUIRED', 'REQUIRED', 'IN_PROGRESS',
      'CONFIRMED_SENT', 'CONFIRMED_NOT_SENT', 'UNRESOLVED'
    )),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_notification_delivery_attempt_logical
    FOREIGN KEY (logical_notification_key, provider_idempotency_key)
    REFERENCES public.notification_delivery_logicals(
      logical_notification_key, provider_idempotency_key
    ),
  CONSTRAINT uq_notification_delivery_attempt_number
    UNIQUE (logical_notification_key, attempt_number),
  CONSTRAINT uq_notification_delivery_attempt_claim
    UNIQUE (logical_notification_key, claim_token)
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_attempt_event
  ON public.notification_delivery_attempts(event_type, event_id, attempt_number);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_attempt_reconciliation
  ON public.notification_delivery_attempts(reconciliation_status, provider_status);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_attempt_provider_message
  ON public.notification_delivery_attempts(provider_name, provider_message_id)
  WHERE provider_message_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Lease columns on the five existing event tables
-- ---------------------------------------------------------------------------

ALTER TABLE public.notification_events
  ADD COLUMN IF NOT EXISTS claim_token uuid;

ALTER TABLE public.homework_notification_events
  ADD COLUMN IF NOT EXISTS claim_token uuid;

ALTER TABLE public.recitation_notification_events
  ADD COLUMN IF NOT EXISTS claim_token uuid;

ALTER TABLE public.exam_notification_events
  ADD COLUMN IF NOT EXISTS claim_token uuid;

ALTER TABLE public.monthly_report_notification_events
  ADD COLUMN IF NOT EXISTS claim_token uuid;

-- ---------------------------------------------------------------------------
-- Deterministic metadata and keys
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.notification_logical_key_v2(
  p_event_type text,
  p_event_id uuid
)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT lower(trim(p_event_type)) || ':' || p_event_id::text;
$$;

CREATE OR REPLACE FUNCTION public.notification_provider_idempotency_key_v2(
  p_logical_notification_key text
)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT 'studentmanager:waha:' || p_logical_notification_key;
$$;

CREATE OR REPLACE FUNCTION public.notification_event_metadata_v2(
  p_event_type text
)
RETURNS jsonb
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT CASE upper(trim(p_event_type))
    WHEN 'ABSENCE' THEN jsonb_build_object(
      'table_name', 'notification_events',
      'source_column', 'attendance_id',
      'notification_type', 'ATTENDANCE_ABSENT'
    )
    WHEN 'HOMEWORK' THEN jsonb_build_object(
      'table_name', 'homework_notification_events',
      'source_column', 'homework_id',
      'notification_type', 'HOMEWORK'
    )
    WHEN 'RECITATION' THEN jsonb_build_object(
      'table_name', 'recitation_notification_events',
      'source_column', 'recitation_id',
      'notification_type', 'RECITATION'
    )
    WHEN 'EXAM' THEN jsonb_build_object(
      'table_name', 'exam_notification_events',
      'source_column', 'exam_id',
      'notification_type', 'EXAM'
    )
    WHEN 'MONTHLY_REPORT' THEN jsonb_build_object(
      'table_name', 'monthly_report_notification_events',
      'source_column', 'monthly_report_id',
      'notification_type', 'MONTHLY_REPORT'
    )
    ELSE NULL
  END;
$$;

-- ---------------------------------------------------------------------------
-- Atomic v2 claim implementation and wrappers
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.claim_notification_event_v2_core(
  p_event_type text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_meta jsonb := public.notification_event_metadata_v2(p_event_type);
  v_table_name text;
  v_event jsonb;
  v_claimed_event jsonb;
  v_event_id uuid;
  v_teacher_id uuid;
  v_student_id uuid;
  v_attempt_number integer;
  v_claim_token uuid := gen_random_uuid();
  v_logical_key text;
  v_provider_key text;
  v_attempt_id uuid;
BEGIN
  IF v_meta IS NULL THEN
    RAISE EXCEPTION 'Unsupported notification event type: %', p_event_type;
  END IF;

  v_table_name := v_meta->>'table_name';

  EXECUTE format(
    'SELECT to_jsonb(e)
       FROM public.%I AS e
      WHERE e.status = $1
        AND e.attempts < 3
      ORDER BY e.created_at, e.id
      LIMIT 1
      FOR UPDATE SKIP LOCKED',
    v_table_name
  )
  INTO v_event
  USING 'PENDING';

  IF v_event IS NULL THEN
    RETURN NULL;
  END IF;

  v_event_id := (v_event->>'id')::uuid;
  v_teacher_id := (v_event->>'teacher_id')::uuid;
  v_student_id := (v_event->>'student_id')::uuid;
  v_logical_key := public.notification_logical_key_v2(
    upper(trim(p_event_type)), v_event_id
  );
  v_provider_key := public.notification_provider_idempotency_key_v2(
    v_logical_key
  );

  EXECUTE format(
    'UPDATE public.%I AS e
        SET status = $1,
            attempts = e.attempts + 1,
            claim_token = $2,
            processing_started_at = now(),
            updated_at = now()
      WHERE e.id = $3
        AND e.status = $4
      RETURNING to_jsonb(e)',
    v_table_name
  )
  INTO v_claimed_event
  USING 'PROCESSING', v_claim_token, v_event_id, 'PENDING';

  IF v_claimed_event IS NULL THEN
    RAISE EXCEPTION 'Event changed before claim: %', v_event_id;
  END IF;

  v_attempt_number := (v_claimed_event->>'attempts')::integer;

  INSERT INTO public.notification_delivery_logicals (
    logical_notification_key,
    event_type,
    event_id,
    teacher_id,
    student_id,
    provider_name,
    provider_idempotency_key
  )
  VALUES (
    v_logical_key,
    upper(trim(p_event_type)),
    v_event_id,
    v_teacher_id,
    v_student_id,
    'WAHA',
    v_provider_key
  )
  ON CONFLICT (event_type, event_id)
  DO UPDATE SET updated_at = now();

  INSERT INTO public.notification_delivery_attempts (
    logical_notification_key,
    event_type,
    event_id,
    teacher_id,
    student_id,
    attempt_number,
    provider_name,
    provider_idempotency_key,
    claim_token,
    provider_status,
    reconciliation_status
  )
  VALUES (
    v_logical_key,
    upper(trim(p_event_type)),
    v_event_id,
    v_teacher_id,
    v_student_id,
    v_attempt_number,
    'WAHA',
    v_provider_key,
    v_claim_token,
    'NOT_STARTED',
    'NOT_REQUIRED'
  )
  RETURNING attempt_id INTO v_attempt_id;

  RETURN jsonb_build_object(
    'event', v_claimed_event,
    'attempt_id', v_attempt_id,
    'claim_token', v_claim_token,
    'logical_notification_key', v_logical_key,
    'provider_idempotency_key', v_provider_key,
    'provider_idempotency_support', 'UNSUPPORTED_UNCONFIRMED'
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.claim_notification_event_v2()
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('ABSENCE'); $$;

CREATE OR REPLACE FUNCTION public.claim_homework_notification_event_v2()
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('HOMEWORK'); $$;

CREATE OR REPLACE FUNCTION public.claim_recitation_notification_event_v2()
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('RECITATION'); $$;

CREATE OR REPLACE FUNCTION public.claim_exam_notification_event_v2()
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('EXAM'); $$;

CREATE OR REPLACE FUNCTION public.claim_monthly_report_notification_event_v2()
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('MONTHLY_REPORT'); $$;

-- ---------------------------------------------------------------------------
-- Strict attempt state machine
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.record_notification_delivery_attempt_v2(
  p_attempt_id uuid,
  p_claim_token uuid,
  p_provider_status text,
  p_provider_message_id text DEFAULT NULL,
  p_provider_response jsonb DEFAULT NULL,
  p_provider_error text DEFAULT NULL
)
RETURNS public.notification_delivery_attempts
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_attempt public.notification_delivery_attempts;
  v_old_status text;
BEGIN
  SELECT * INTO v_attempt
  FROM public.notification_delivery_attempts
  WHERE attempt_id = p_attempt_id
    AND claim_token = p_claim_token
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Attempt not found or claim token is stale';
  END IF;

  v_old_status := v_attempt.provider_status;

  IF NOT (
       (v_old_status = 'NOT_STARTED' AND p_provider_status IN ('SENDING', 'FAILED'))
    OR (v_old_status = 'SENDING' AND p_provider_status IN ('ACCEPTED', 'SENT', 'FAILED', 'UNKNOWN'))
    OR (v_old_status = 'ACCEPTED' AND p_provider_status IN ('SENT', 'FAILED', 'UNKNOWN'))
    OR (v_old_status = 'SENT' AND p_provider_status = 'SENT')
    OR (v_old_status = 'FAILED' AND p_provider_status = 'FAILED')
    OR (v_old_status = 'UNKNOWN' AND p_provider_status = 'UNKNOWN')
    OR (v_old_status = 'CANCELLED' AND p_provider_status = 'CANCELLED')
  ) THEN
    RAISE EXCEPTION 'Invalid attempt transition: % -> %', v_old_status, p_provider_status;
  END IF;

  UPDATE public.notification_delivery_attempts
  SET provider_status = p_provider_status,
      provider_message_id = COALESCE(p_provider_message_id, provider_message_id),
      provider_response = COALESCE(p_provider_response, provider_response),
      provider_error = COALESCE(p_provider_error, provider_error),
      started_at = CASE
        WHEN p_provider_status IN ('SENDING', 'ACCEPTED', 'SENT')
        THEN COALESCE(started_at, now()) ELSE started_at END,
      completed_at = CASE
        WHEN p_provider_status IN ('SENT', 'FAILED', 'CANCELLED')
        THEN COALESCE(completed_at, now()) ELSE completed_at END,
      reconciliation_status = CASE
        WHEN p_provider_status IN ('SENT', 'UNKNOWN') THEN 'REQUIRED'
        WHEN p_provider_status = 'FAILED' THEN 'CONFIRMED_NOT_SENT'
        ELSE reconciliation_status
      END,
      updated_at = now()
  WHERE attempt_id = p_attempt_id
    AND claim_token = p_claim_token
  RETURNING * INTO v_attempt;

  RETURN v_attempt;
END;
$$;

-- ---------------------------------------------------------------------------
-- Explicit reconciliation; UNKNOWN is never automatically resent
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.reconcile_notification_delivery_attempt_v2(
  p_attempt_id uuid,
  p_claim_token uuid,
  p_resolution text,
  p_provider_message_id text DEFAULT NULL,
  p_provider_response jsonb DEFAULT NULL,
  p_provider_error text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_attempt public.notification_delivery_attempts;
  v_meta jsonb;
  v_table_name text;
  v_event jsonb;
  v_event_status text;
  v_action text;
BEGIN
  IF p_resolution NOT IN ('CONFIRMED_SENT', 'CONFIRMED_NOT_SENT') THEN
    RAISE EXCEPTION 'Invalid reconciliation resolution: %', p_resolution;
  END IF;

  SELECT * INTO v_attempt
  FROM public.notification_delivery_attempts
  WHERE attempt_id = p_attempt_id
    AND claim_token = p_claim_token
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Attempt not found or claim token is stale';
  END IF;

  IF v_attempt.reconciliation_status <> 'REQUIRED'
     AND v_attempt.provider_status NOT IN ('UNKNOWN', 'SENDING', 'ACCEPTED') THEN
    RAISE EXCEPTION 'Attempt is not awaiting reconciliation';
  END IF;

  IF p_resolution = 'CONFIRMED_NOT_SENT'
     AND v_attempt.provider_status = 'SENT' THEN
    RAISE EXCEPTION 'A SENT provider result cannot be reconciled as NOT_SENT';
  END IF;

  v_meta := public.notification_event_metadata_v2(v_attempt.event_type);
  IF v_meta IS NULL THEN
    RAISE EXCEPTION 'Unsupported event type: %', v_attempt.event_type;
  END IF;

  v_table_name := v_meta->>'table_name';

  EXECUTE format(
    'SELECT to_jsonb(e)
       FROM public.%I AS e
      WHERE e.id = $1
        AND e.claim_token = $2
      FOR UPDATE',
    v_table_name
  )
  INTO v_event
  USING v_attempt.event_id, p_claim_token;

  IF v_event IS NULL THEN
    RAISE EXCEPTION 'Event does not match the current lease';
  END IF;

  IF p_resolution = 'CONFIRMED_SENT' THEN
    IF p_provider_message_id IS NULL OR length(trim(p_provider_message_id)) = 0 THEN
      RAISE EXCEPTION 'CONFIRMED_SENT requires provider_message_id';
    END IF;

    UPDATE public.notification_delivery_attempts
    SET provider_status = 'SENT',
        provider_message_id = p_provider_message_id,
        provider_response = COALESCE(p_provider_response, provider_response),
        reconciliation_status = 'CONFIRMED_SENT',
        completed_at = COALESCE(completed_at, now()),
        updated_at = now()
    WHERE attempt_id = p_attempt_id
      AND claim_token = p_claim_token;

    v_event_status := 'FAILED';
    v_action := 'CONFIRMED_SENT_REQUIRES_FINALIZATION';
  ELSE
    UPDATE public.notification_delivery_attempts
    SET provider_status = 'FAILED',
        provider_response = COALESCE(p_provider_response, provider_response),
        provider_error = COALESCE(p_provider_error, provider_error),
        reconciliation_status = 'CONFIRMED_NOT_SENT',
        completed_at = COALESCE(completed_at, now()),
        updated_at = now()
    WHERE attempt_id = p_attempt_id
      AND claim_token = p_claim_token;

    IF (v_event->>'attempts')::integer < 3 THEN
      v_event_status := 'PENDING';
      v_action := 'CONFIRMED_NOT_SENT_REQUEUED';
    ELSE
      v_event_status := 'FAILED';
      v_action := 'CONFIRMED_NOT_SENT_TERMINAL';
    END IF;
  END IF;

  EXECUTE format(
    'UPDATE public.%I
        SET status = $1,
            processing_started_at = NULL,
            last_error = $2,
            updated_at = now()
      WHERE id = $3
        AND claim_token = $4',
    v_table_name
  )
  USING v_event_status, v_action, v_attempt.event_id, p_claim_token;

  RETURN jsonb_build_object(
    'action', v_action,
    'event_id', v_attempt.event_id,
    'attempt_id', p_attempt_id,
    'claim_token', p_claim_token,
    'event_status', v_event_status
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- Lease-aware stale recovery
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.recover_notification_event_v2_core(
  p_event_type text,
  p_timeout_minutes integer
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_meta jsonb := public.notification_event_metadata_v2(p_event_type);
  v_table_name text;
  v_event jsonb;
  v_event_id uuid;
  v_attempt public.notification_delivery_attempts;
  v_status text;
  v_action text;
BEGIN
  IF v_meta IS NULL THEN
    RAISE EXCEPTION 'Unsupported event type: %', p_event_type;
  END IF;
  IF p_timeout_minutes <= 0 THEN
    RAISE EXCEPTION 'Timeout must be positive';
  END IF;

  v_table_name := v_meta->>'table_name';

  EXECUTE format(
    'SELECT to_jsonb(e)
       FROM public.%I AS e
      WHERE e.status = $1
        AND e.processing_started_at IS NOT NULL
        AND e.processing_started_at < now() - make_interval(mins => $2)
      ORDER BY e.processing_started_at, e.id
      LIMIT 1
      FOR UPDATE SKIP LOCKED',
    v_table_name
  )
  INTO v_event
  USING 'PROCESSING', p_timeout_minutes;

  IF v_event IS NULL THEN
    RETURN NULL;
  END IF;

  v_event_id := (v_event->>'id')::uuid;

  SELECT * INTO v_attempt
  FROM public.notification_delivery_attempts
  WHERE event_type = upper(trim(p_event_type))
    AND event_id = v_event_id
    AND attempt_number = (v_event->>'attempts')::integer
  ORDER BY created_at DESC
  LIMIT 1
  FOR UPDATE;

  IF NOT FOUND THEN
    EXECUTE format(
      'UPDATE public.%I
          SET status = $1,
              processing_started_at = NULL,
              last_error = $2,
              updated_at = now()
        WHERE id = $3',
      v_table_name
    )
    USING 'FAILED', 'Missing durable delivery attempt; reconciliation required', v_event_id;

    RETURN jsonb_build_object(
      'action', 'QUARANTINED_MISSING_ATTEMPT',
      'event_id', v_event_id
    );
  END IF;

  IF v_attempt.provider_status IN ('NOT_STARTED', 'FAILED')
     AND v_attempt.started_at IS NULL THEN
    UPDATE public.notification_delivery_attempts
    SET reconciliation_status = 'CONFIRMED_NOT_SENT',
        completed_at = COALESCE(completed_at, now()),
        updated_at = now()
    WHERE attempt_id = v_attempt.attempt_id;

    IF (v_event->>'attempts')::integer < 3 THEN
      v_status := 'PENDING';
      v_action := 'REQUEUED_CONFIRMED_NOT_SENT';
    ELSE
      v_status := 'FAILED';
      v_action := 'FAILED_TERMINAL_MAX_ATTEMPTS';
    END IF;
  ELSE
    UPDATE public.notification_delivery_attempts
    SET provider_status = CASE
          WHEN provider_status = 'NOT_STARTED' THEN 'UNKNOWN'
          ELSE provider_status
        END,
        reconciliation_status = 'REQUIRED',
        provider_error = COALESCE(
          provider_error,
          'Provider result uncertain; reconciliation required'
        ),
        updated_at = now()
    WHERE attempt_id = v_attempt.attempt_id;

    v_status := 'FAILED';
    v_action := 'QUARANTINED_PROVIDER_RESULT_UNKNOWN';
  END IF;

  EXECUTE format(
    'UPDATE public.%I
        SET status = $1,
            processing_started_at = NULL,
            last_error = $2,
            updated_at = now()
      WHERE id = $3
        AND claim_token = $4',
    v_table_name
  )
  USING v_status, v_action, v_event_id, v_attempt.claim_token;

  RETURN jsonb_build_object(
    'action', v_action,
    'event_id', v_event_id,
    'attempt_id', v_attempt.attempt_id,
    'event_status', v_status
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.recover_notification_events_v2(integer)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.recover_notification_event_v2_core('ABSENCE', $1); $$;

CREATE OR REPLACE FUNCTION public.recover_homework_notification_events_v2(integer)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.recover_notification_event_v2_core('HOMEWORK', $1); $$;

CREATE OR REPLACE FUNCTION public.recover_recitation_notification_events_v2(integer)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.recover_notification_event_v2_core('RECITATION', $1); $$;

CREATE OR REPLACE FUNCTION public.recover_exam_notification_events_v2(integer)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.recover_notification_event_v2_core('EXAM', $1); $$;

CREATE OR REPLACE FUNCTION public.recover_monthly_report_notification_events_v2(integer)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$ SELECT public.recover_notification_event_v2_core('MONTHLY_REPORT', $1); $$;

-- ---------------------------------------------------------------------------
-- Fenced finalization; no payload column is referenced because production does
-- not contain public.notifications.payload.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.finalize_notification_sent_v2(
  p_attempt_id uuid,
  p_claim_token uuid,
  p_event_id uuid,
  p_event_type text,
  p_teacher_id uuid,
  p_student_id uuid,
  p_source_id uuid,
  p_channel text DEFAULT 'WHATSAPP',
  p_waha_message_id text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_meta jsonb := public.notification_event_metadata_v2(p_event_type);
  v_table_name text;
  v_source_column text;
  v_notification_type text;
  v_attempt public.notification_delivery_attempts;
  v_event jsonb;
  v_actual_source_id uuid;
  v_message_id text;
  v_rows integer;
BEGIN
  IF v_meta IS NULL THEN
    RAISE EXCEPTION 'Unsupported event type: %', p_event_type;
  END IF;

  v_table_name := v_meta->>'table_name';
  v_source_column := v_meta->>'source_column';
  v_notification_type := v_meta->>'notification_type';

  IF v_table_name IS NULL
     OR v_source_column IS NULL
     OR v_notification_type IS NULL THEN
    RAISE EXCEPTION 'Invalid notification event metadata';
  END IF;

  SELECT * INTO v_attempt
  FROM public.notification_delivery_attempts
  WHERE attempt_id = p_attempt_id
    AND claim_token = p_claim_token
    AND event_id = p_event_id
    AND event_type = upper(trim(p_event_type))
    AND teacher_id = p_teacher_id
    AND student_id = p_student_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Attempt, lease, event identity, or tenant is invalid';
  END IF;

  IF v_attempt.provider_status <> 'SENT'
     OR v_attempt.reconciliation_status <> 'CONFIRMED_SENT' THEN
    RAISE EXCEPTION 'Only explicitly reconciled SENT attempts may be finalized';
  END IF;

  v_message_id := COALESCE(p_waha_message_id, v_attempt.provider_message_id);
  IF v_message_id IS NULL OR length(trim(v_message_id)) = 0 THEN
    RAISE EXCEPTION 'CONFIRMED_SENT requires provider_message_id';
  END IF;

  EXECUTE format(
    'SELECT to_jsonb(e)
       FROM public.%I AS e
      WHERE e.id = $1
        AND e.teacher_id = $2
        AND e.student_id = $3
        AND e.claim_token = $4
        AND e.status IN ($5, $6)
      FOR UPDATE',
    v_table_name
  )
  INTO v_event
  USING p_event_id, p_teacher_id, p_student_id,
        p_claim_token, 'PROCESSING', 'FAILED';

  IF v_event IS NULL THEN
    RAISE EXCEPTION 'Event does not match current lease or tenant';
  END IF;

  v_actual_source_id := (v_event->>v_source_column)::uuid;
  IF v_actual_source_id <> p_source_id THEN
    RAISE EXCEPTION 'Source ID does not belong to the event';
  END IF;

  EXECUTE format(
    'UPDATE public.%I
        SET status = $1,
            sent_at = COALESCE(sent_at, now()),
            processing_started_at = NULL,
            last_error = NULL,
            updated_at = now()
      WHERE id = $2
        AND claim_token = $3
        AND status IN ($4, $5)',
    v_table_name
  )
  USING 'SENT', p_event_id, p_claim_token, 'PROCESSING', 'FAILED';

  GET DIAGNOSTICS v_rows = ROW_COUNT;
  IF v_rows <> 1 THEN
    RAISE EXCEPTION 'Event finalization rejected';
  END IF;

  INSERT INTO public.notifications (
    teacher_id,
    student_id,
    attendance_id,
    source_id,
    notification_type,
    channel,
    status,
    sent_at,
    waha_message_id
  )
  VALUES (
    p_teacher_id,
    p_student_id,
    CASE WHEN upper(trim(p_event_type)) = 'ABSENCE'
         THEN p_source_id ELSE NULL END,
    p_source_id,
    v_notification_type,
    p_channel,
    'SENT',
    now(),
    v_message_id
  )
  ON CONFLICT (
    teacher_id, student_id, source_id, notification_type, channel
  )
  DO UPDATE SET
    status = 'SENT',
    sent_at = COALESCE(public.notifications.sent_at, EXCLUDED.sent_at),
    attendance_id = COALESCE(
      EXCLUDED.attendance_id,
      public.notifications.attendance_id
    ),
    waha_message_id = COALESCE(
      EXCLUDED.waha_message_id,
      public.notifications.waha_message_id
    );

  UPDATE public.notification_delivery_attempts
  SET reconciliation_status = 'CONFIRMED_SENT',
      provider_message_id = v_message_id,
      completed_at = COALESCE(completed_at, now()),
      updated_at = now()
  WHERE attempt_id = p_attempt_id
    AND claim_token = p_claim_token;

  RETURN jsonb_build_object(
    'status', 'SENT',
    'event_id', p_event_id,
    'attempt_id', p_attempt_id,
    'resent', false
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- Old RPCs remain for rollback but are service_role-only.
-- ---------------------------------------------------------------------------

REVOKE EXECUTE ON FUNCTION public.claim_notification_event()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_notification_event() TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_homework_notification_event()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_homework_notification_event() TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_recitation_notification_event()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_recitation_notification_event() TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_exam_notification_event()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_exam_notification_event() TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_monthly_report_notification_event()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_monthly_report_notification_event() TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_stale_notification_events(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_stale_notification_events(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_stale_homework_notification_events(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_stale_homework_notification_events(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_stale_recitation_notification_events(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_stale_recitation_notification_events(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_stale_exam_notification_events(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_stale_exam_notification_events(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_stale_monthly_report_notification_events(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_stale_monthly_report_notification_events(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.mark_notification_failed(uuid, text)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_notification_failed(uuid, text) TO service_role;

REVOKE EXECUTE ON FUNCTION public.mark_homework_notification_failed(uuid, text)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_homework_notification_failed(uuid, text) TO service_role;

REVOKE EXECUTE ON FUNCTION public.mark_recitation_notification_failed(uuid, text)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_recitation_notification_failed(uuid, text) TO service_role;

REVOKE EXECUTE ON FUNCTION public.mark_exam_notification_failed(uuid, text)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_exam_notification_failed(uuid, text) TO service_role;

REVOKE EXECUTE ON FUNCTION public.mark_monthly_report_notification_failed(uuid, text)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_monthly_report_notification_failed(uuid, text) TO service_role;

REVOKE EXECUTE ON FUNCTION public.check_notification_already_sent(
  uuid, uuid, uuid, text, text
)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_notification_already_sent(
  uuid, uuid, uuid, text, text
) TO service_role;

REVOKE EXECUTE ON FUNCTION public.finalize_notification_sent(
  uuid, text, uuid, uuid, uuid, text, text
)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.finalize_notification_sent(
  uuid, text, uuid, uuid, uuid, text, text
) TO service_role;

REVOKE EXECUTE ON FUNCTION public.monitor_failed_notification_events()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.monitor_failed_notification_events() TO service_role;

-- v2 RPCs are service_role-only.

REVOKE EXECUTE ON FUNCTION public.claim_notification_event_v2_core(text)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_notification_event_v2_core(text) TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_notification_event_v2()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_notification_event_v2() TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_homework_notification_event_v2()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_homework_notification_event_v2() TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_recitation_notification_event_v2()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_recitation_notification_event_v2() TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_exam_notification_event_v2()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_exam_notification_event_v2() TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_monthly_report_notification_event_v2()
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_monthly_report_notification_event_v2() TO service_role;

REVOKE EXECUTE ON FUNCTION public.record_notification_delivery_attempt_v2(
  uuid, uuid, text, text, jsonb, text
)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.record_notification_delivery_attempt_v2(
  uuid, uuid, text, text, jsonb, text
) TO service_role;

REVOKE EXECUTE ON FUNCTION public.reconcile_notification_delivery_attempt_v2(
  uuid, uuid, text, text, jsonb, text
)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.reconcile_notification_delivery_attempt_v2(
  uuid, uuid, text, text, jsonb, text
) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_notification_event_v2_core(text, integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_notification_event_v2_core(text, integer)
  TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_notification_events_v2(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_notification_events_v2(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_homework_notification_events_v2(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_homework_notification_events_v2(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_recitation_notification_events_v2(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_recitation_notification_events_v2(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_exam_notification_events_v2(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_exam_notification_events_v2(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_monthly_report_notification_events_v2(integer)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_monthly_report_notification_events_v2(integer) TO service_role;

REVOKE EXECUTE ON FUNCTION public.finalize_notification_sent_v2(
  uuid, uuid, uuid, text, uuid, uuid, uuid, text, text
)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.finalize_notification_sent_v2(
  uuid, uuid, uuid, text, uuid, uuid, uuid, text, text
) TO service_role;

COMMIT;
