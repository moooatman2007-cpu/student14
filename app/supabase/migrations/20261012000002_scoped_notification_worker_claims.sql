-- ============================================================================
-- Migration: 20261012000002_scoped_notification_worker_claims.sql
-- Description: Adds teacher_id scoping to atomic claim and recovery functions
--              to prevent cross-teacher queue processing when invoked by teachers.
-- ============================================================================

-- 1. Scoped atomic claim core function
CREATE OR REPLACE FUNCTION public.claim_notification_event_v2_core(
  p_event_type text,
  p_teacher_id uuid DEFAULT NULL
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
        AND ($2::uuid IS NULL OR e.teacher_id = $2::uuid)
      ORDER BY e.created_at, e.id
      LIMIT 1
      FOR UPDATE SKIP LOCKED',
    v_table_name
  )
  INTO v_event
  USING 'PENDING', p_teacher_id;

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
    'provider_idempotency_key', v_provider_key
  );
END;
$$;

-- 2. Scoped wrappers for each event type
CREATE OR REPLACE FUNCTION public.claim_notification_event_v2(p_teacher_id uuid DEFAULT NULL)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('ABSENCE', p_teacher_id); $$;

CREATE OR REPLACE FUNCTION public.claim_homework_notification_event_v2(p_teacher_id uuid DEFAULT NULL)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('HOMEWORK', p_teacher_id); $$;

CREATE OR REPLACE FUNCTION public.claim_recitation_notification_event_v2(p_teacher_id uuid DEFAULT NULL)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('RECITATION', p_teacher_id); $$;

CREATE OR REPLACE FUNCTION public.claim_exam_notification_event_v2(p_teacher_id uuid DEFAULT NULL)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('EXAM', p_teacher_id); $$;

CREATE OR REPLACE FUNCTION public.claim_monthly_report_notification_event_v2(p_teacher_id uuid DEFAULT NULL)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER SET search_path = public, pg_temp
AS $$ SELECT public.claim_notification_event_v2_core('MONTHLY_REPORT', p_teacher_id); $$;

-- 3. Scoped stale recovery core function
CREATE OR REPLACE FUNCTION public.recover_notification_event_v2_core(
  p_event_type text,
  p_timeout_minutes integer,
  p_teacher_id uuid DEFAULT NULL
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
        AND ($3::uuid IS NULL OR e.teacher_id = $3::uuid)
      ORDER BY e.processing_started_at, e.id
      LIMIT 1
      FOR UPDATE SKIP LOCKED',
    v_table_name
  )
  INTO v_event
  USING 'PROCESSING', p_timeout_minutes, p_teacher_id;

  IF v_event IS NULL THEN
    RETURN NULL;
  END IF;

  v_event_id := (v_event->>'id')::uuid;

  SELECT *
    INTO v_attempt
    FROM public.notification_delivery_attempts
   WHERE event_type = upper(trim(p_event_type))
     AND event_id = v_event_id
   ORDER BY attempt_number DESC
   LIMIT 1;

  IF v_attempt.attempt_id IS NOT NULL AND v_attempt.provider_status = 'SENT' THEN
    v_status := 'SENT';
    v_action := 'RECONCILED_SENT';
  ELSIF (v_event->>'attempts')::integer >= 3 THEN
    v_status := 'FAILED';
    v_action := 'EXHAUSTED';
  ELSE
    v_status := 'PENDING';
    v_action := 'REQUEUED';
  END IF;

  EXECUTE format(
    'UPDATE public.%I
        SET status = $1,
            claim_token = NULL,
            processing_started_at = NULL,
            updated_at = now()
      WHERE id = $2',
    v_table_name
  )
  USING v_status, v_event_id;

  RETURN jsonb_build_object(
    'event_id', v_event_id,
    'event_type', upper(trim(p_event_type)),
    'previous_status', 'PROCESSING',
    'new_status', v_status,
    'action', v_action
  );
END;
$$;

-- Permissions: Only service_role can call these claim and recovery functions
REVOKE EXECUTE ON FUNCTION public.claim_notification_event_v2_core(text, uuid) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_notification_event_v2_core(text, uuid) TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_notification_event_v2(uuid) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_notification_event_v2(uuid) TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_homework_notification_event_v2(uuid) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_homework_notification_event_v2(uuid) TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_recitation_notification_event_v2(uuid) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_recitation_notification_event_v2(uuid) TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_exam_notification_event_v2(uuid) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_exam_notification_event_v2(uuid) TO service_role;

REVOKE EXECUTE ON FUNCTION public.claim_monthly_report_notification_event_v2(uuid) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_monthly_report_notification_event_v2(uuid) TO service_role;

REVOKE EXECUTE ON FUNCTION public.recover_notification_event_v2_core(text, integer, uuid) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.recover_notification_event_v2_core(text, integer, uuid) TO service_role;
