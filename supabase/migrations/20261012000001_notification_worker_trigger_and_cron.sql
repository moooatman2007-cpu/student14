-- ============================================================================
-- Migration: 20261012000001_notification_worker_trigger_and_cron.sql
-- Description: Automated Trigger and Scheduled Worker Execution for WhatsApp
--              Queue Processing in Midar Platform
-- ============================================================================

-- 1. Helper View: Inspect Pending vs Processing vs Sent Notifications Across All 5 Event Types
CREATE OR REPLACE VIEW public.v_notification_queue_summary AS
SELECT 
    'ABSENCE' AS event_type,
    count(*) FILTER (WHERE status = 'PENDING') AS pending_count,
    count(*) FILTER (WHERE status = 'PROCESSING') AS processing_count,
    count(*) FILTER (WHERE status = 'SENT') AS sent_count,
    count(*) FILTER (WHERE status = 'FAILED') AS failed_count,
    max(created_at) AS latest_event_at
FROM public.notification_events
UNION ALL
SELECT 
    'HOMEWORK' AS event_type,
    count(*) FILTER (WHERE status = 'PENDING'),
    count(*) FILTER (WHERE status = 'PROCESSING'),
    count(*) FILTER (WHERE status = 'SENT'),
    count(*) FILTER (WHERE status = 'FAILED'),
    max(created_at)
FROM public.homework_notification_events
UNION ALL
SELECT 
    'RECITATION' AS event_type,
    count(*) FILTER (WHERE status = 'PENDING'),
    count(*) FILTER (WHERE status = 'PROCESSING'),
    count(*) FILTER (WHERE status = 'SENT'),
    count(*) FILTER (WHERE status = 'FAILED'),
    max(created_at)
FROM public.recitation_notification_events
UNION ALL
SELECT 
    'EXAM' AS event_type,
    count(*) FILTER (WHERE status = 'PENDING'),
    count(*) FILTER (WHERE status = 'PROCESSING'),
    count(*) FILTER (WHERE status = 'SENT'),
    count(*) FILTER (WHERE status = 'FAILED'),
    max(created_at)
FROM public.exam_notification_events
UNION ALL
SELECT 
    'MONTHLY_REPORT' AS event_type,
    count(*) FILTER (WHERE status = 'PENDING'),
    count(*) FILTER (WHERE status = 'PROCESSING'),
    count(*) FILTER (WHERE status = 'SENT'),
    count(*) FILTER (WHERE status = 'FAILED'),
    max(created_at)
FROM public.monthly_report_notification_events;

-- Restrict view access to authenticated teachers/admins
GRANT SELECT ON public.v_notification_queue_summary TO authenticated, service_role;

-- 2. Optional pg_net Asynchronous Worker Invocation Helper (If pg_net is enabled on Supabase)
-- This function can be called via database triggers or pg_cron to fire an HTTP POST
-- to the studentmanager-notification-worker Edge Function without blocking client transactions.
CREATE OR REPLACE FUNCTION public.invoke_notification_worker_pg_net(
    p_edge_function_url TEXT DEFAULT NULL,
    p_worker_secret TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_url TEXT := COALESCE(p_edge_function_url, current_setting('app.settings.notification_worker_url', true));
    v_secret TEXT := COALESCE(p_worker_secret, current_setting('app.settings.notification_worker_secret', true));
    v_request_id BIGINT;
BEGIN
    IF v_url IS NULL OR length(trim(v_url)) = 0 THEN
        RETURN jsonb_build_object(
            'ok', false,
            'message', 'Notification worker URL not configured in app.settings.notification_worker_url'
        );
    END IF;

    -- Check if pg_net extension schema exists
    IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'net') THEN
        SELECT net.http_post(
            url := v_url,
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'Authorization', 'Bearer ' || COALESCE(v_secret, ''),
                'x-worker-secret', COALESCE(v_secret, '')
            ),
            body := '{}'::jsonb,
            timeout_milliseconds := 15000
        ) INTO v_request_id;

        RETURN jsonb_build_object(
            'ok', true,
            'request_id', v_request_id,
            'url', v_url
        );
    ELSE
        RETURN jsonb_build_object(
            'ok', false,
            'message', 'pg_net extension is not installed in this Supabase database. Use client trigger or pg_cron.'
        );
    END IF;
EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'ok', false,
        'error', SQLERRM
    );
END;
$$;

-- 3. Documented Cron Job Setup (Run in Supabase SQL Editor if pg_cron is enabled):
-- ----------------------------------------------------------------------------
-- SELECT cron.schedule(
--   'process-pending-notifications-every-minute',
--   '* * * * *',
--   $$
--   SELECT net.http_post(
--       url := 'https://<PROJECT-REF>.supabase.co/functions/v1/studentmanager-notification-worker',
--       headers := jsonb_build_object(
--           'Content-Type', 'application/json',
--           'Authorization', 'Bearer <CRON_SECRET_OR_SERVICE_ROLE_KEY>'
--       ),
--       body := '{}'::jsonb
--   );
--   $$
-- );
