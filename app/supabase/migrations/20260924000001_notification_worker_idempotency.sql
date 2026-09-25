-- ============================================================================
-- Migration: 20260924000001_notification_worker_idempotency.sql
-- Description: Production-safe Idempotency, WAHA Message ID tracking, and Atomic Finalize
-- ============================================================================

-- 1. Ensure waha_message_id column and index exist in public.notifications
ALTER TABLE public.notifications 
ADD COLUMN IF NOT EXISTS waha_message_id TEXT;

CREATE INDEX IF NOT EXISTS idx_notifications_waha_message_id 
ON public.notifications(teacher_id, waha_message_id) 
WHERE waha_message_id IS NOT NULL;

-- 2. Helper Function: Check If Notification Was Already Sent (Pre-Send Idempotency)
CREATE OR REPLACE FUNCTION public.check_notification_already_sent(
    p_teacher_id UUID,
    p_student_id UUID,
    p_source_id UUID,
    p_notification_type TEXT,
    p_channel TEXT DEFAULT 'WHATSAPP'
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM public.notifications
        WHERE teacher_id = p_teacher_id
          AND student_id = p_student_id
          AND source_id = p_source_id
          AND notification_type = p_notification_type
          AND channel = p_channel
          AND status = 'SENT'
    );
END;
$$;

-- 3. Atomic Finalize Function: Strongly validates event, marks SENT, records waha_message_id
CREATE OR REPLACE FUNCTION public.finalize_notification_sent(
    p_event_id UUID,
    p_event_type TEXT,
    p_teacher_id UUID,
    p_student_id UUID,
    p_source_id UUID,
    p_channel TEXT DEFAULT 'WHATSAPP',
    p_waha_message_id TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_norm_type TEXT;
    v_rows_updated INT := 0;
BEGIN
    -- A. Validate inputs
    IF p_event_id IS NULL OR p_teacher_id IS NULL OR p_student_id IS NULL OR p_source_id IS NULL THEN
        RAISE EXCEPTION 'Missing required parameters for finalize_notification_sent';
    END IF;

    -- Normalize notification_type
    v_norm_type := CASE 
        WHEN UPPER(p_event_type) = 'ABSENCE' THEN 'ATTENDANCE_ABSENT'
        ELSE UPPER(p_event_type)
    END;

    -- B. Tenant verification: ensure student belongs to teacher
    IF NOT EXISTS (
        SELECT 1 FROM public.students 
        WHERE id = p_student_id AND teacher_id = p_teacher_id
    ) THEN
        RAISE EXCEPTION 'Tenant security violation: student does not belong to teacher.';
    END IF;

    -- C. Strong Event Validation: Ensure event matches teacher_id, student_id, and source_id
    CASE UPPER(p_event_type)
        WHEN 'ABSENCE' THEN
            UPDATE public.notification_events
            SET status = 'SENT',
                sent_at = now(),
                last_error = NULL,
                updated_at = now()
            WHERE id = p_event_id 
              AND teacher_id = p_teacher_id
              AND student_id = p_student_id
              AND attendance_id = p_source_id;
            GET DIAGNOSTICS v_rows_updated = ROW_COUNT;
            IF v_rows_updated = 0 THEN
                RAISE EXCEPTION 'Event validation failed: event % does not match student %, source %, or teacher % in notification_events', 
                    p_event_id, p_student_id, p_source_id, p_teacher_id;
            END IF;

        WHEN 'HOMEWORK' THEN
            UPDATE public.homework_notification_events
            SET status = 'SENT',
                sent_at = now(),
                last_error = NULL,
                updated_at = now()
            WHERE id = p_event_id 
              AND teacher_id = p_teacher_id
              AND student_id = p_student_id
              AND homework_id = p_source_id;
            GET DIAGNOSTICS v_rows_updated = ROW_COUNT;
            IF v_rows_updated = 0 THEN
                RAISE EXCEPTION 'Event validation failed: event % does not match student %, source %, or teacher % in homework_notification_events', 
                    p_event_id, p_student_id, p_source_id, p_teacher_id;
            END IF;

        WHEN 'RECITATION' THEN
            UPDATE public.recitation_notification_events
            SET status = 'SENT',
                sent_at = now(),
                last_error = NULL,
                updated_at = now()
            WHERE id = p_event_id 
              AND teacher_id = p_teacher_id
              AND student_id = p_student_id
              AND recitation_id = p_source_id;
            GET DIAGNOSTICS v_rows_updated = ROW_COUNT;
            IF v_rows_updated = 0 THEN
                RAISE EXCEPTION 'Event validation failed: event % does not match student %, source %, or teacher % in recitation_notification_events', 
                    p_event_id, p_student_id, p_source_id, p_teacher_id;
            END IF;

        WHEN 'EXAM' THEN
            UPDATE public.exam_notification_events
            SET status = 'SENT',
                sent_at = now(),
                last_error = NULL,
                updated_at = now()
            WHERE id = p_event_id 
              AND teacher_id = p_teacher_id
              AND student_id = p_student_id
              AND exam_id = p_source_id;
            GET DIAGNOSTICS v_rows_updated = ROW_COUNT;
            IF v_rows_updated = 0 THEN
                RAISE EXCEPTION 'Event validation failed: event % does not match student %, source %, or teacher % in exam_notification_events', 
                    p_event_id, p_student_id, p_source_id, p_teacher_id;
            END IF;

        WHEN 'MONTHLY_REPORT' THEN
            UPDATE public.monthly_report_notification_events
            SET status = 'SENT',
                sent_at = now(),
                last_error = NULL,
                updated_at = now()
            WHERE id = p_event_id 
              AND teacher_id = p_teacher_id
              AND student_id = p_student_id
              AND monthly_report_id = p_source_id;
            GET DIAGNOSTICS v_rows_updated = ROW_COUNT;
            IF v_rows_updated = 0 THEN
                RAISE EXCEPTION 'Event validation failed: event % does not match student %, source %, or teacher % in monthly_report_notification_events', 
                    p_event_id, p_student_id, p_source_id, p_teacher_id;
            END IF;

        ELSE
            RAISE EXCEPTION 'Unsupported event type: %', p_event_type;
    END CASE;

    -- D. Atomically insert or update history record in public.notifications
    -- Utilizes existing UNIQUE constraint uq_notifications_source_idempotency
    INSERT INTO public.notifications (
        id,
        teacher_id,
        student_id,
        attendance_id,
        notification_type,
        channel,
        status,
        failure_reason,
        created_at,
        sent_at,
        source_id,
        source_type,
        waha_message_id
    ) VALUES (
        gen_random_uuid(),
        p_teacher_id,
        p_student_id,
        CASE WHEN UPPER(p_event_type) = 'ABSENCE' THEN p_source_id ELSE NULL END,
        v_norm_type,
        p_channel,
        'SENT',
        NULL,
        now(),
        now(),
        p_source_id,
        v_norm_type,
        p_waha_message_id
    )
    ON CONFLICT (teacher_id, student_id, source_id, notification_type, channel)
    DO UPDATE SET 
        status = 'SENT',
        sent_at = now(),
        failure_reason = NULL,
        waha_message_id = COALESCE(EXCLUDED.waha_message_id, notifications.waha_message_id);

    RETURN jsonb_build_object(
        'success', true,
        'event_id', p_event_id,
        'event_type', p_event_type,
        'status', 'SENT',
        'rows_updated', v_rows_updated,
        'waha_message_id', p_waha_message_id
    );
END;
$$;

-- Grant permissions to authenticated service role
GRANT EXECUTE ON FUNCTION public.check_notification_already_sent TO service_role;
GRANT EXECUTE ON FUNCTION public.finalize_notification_sent TO service_role;
