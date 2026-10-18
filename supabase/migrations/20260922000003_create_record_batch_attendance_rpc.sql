-- ============================================================================
-- Migration: 20260922000003_create_record_batch_attendance_rpc.sql
-- Description: Atomic single-transaction bulk upsert RPC for fast attendance recording.
-- Status: PROPOSED FOR REVIEW ONLY — NOT EXECUTED IN DATABASE
-- ============================================================================

-- RATIONALE & ARCHITECTURAL INTENT:
-- 1. Replaces individual per-student SELECT-then-INSERT/UPDATE network requests with
--    a single HTTP POST request for the entire class/group batch.
-- 2. Guarantees strict ACID atomicity: all student attendance rows in the payload
--    succeed together or the entire batch rolls back cleanly on any validation error.
-- 3. Derives teacher_id exclusively from auth.uid(), strictly preserving multi-tenant
--    isolation and preventing any client-side tampering of tenant ownership.
-- 4. Validates that every student in the payload belongs to the authenticated teacher
--    and is not soft-deleted before performing any write.
-- 5. Performs an idempotent bulk upsert utilizing the existing unique constraint:
--    uq_attendance_student_date UNIQUE (student_id, date).
-- 6. Returns a structured JSON summary (total, present_count, absent_count, late_count, excused_count).

CREATE OR REPLACE FUNCTION public.record_batch_attendance(
    p_date DATE,
    p_records JSONB
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_teacher_id UUID;
    v_total INT;
    v_valid_students_count INT;
    v_present INT := 0;
    v_absent INT := 0;
    v_late INT := 0;
    v_excused INT := 0;
BEGIN
    -- 1. Security Check: Authenticate caller via secure server context
    v_teacher_id := auth.uid();
    IF v_teacher_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required: session is invalid or expired.';
    END IF;

    -- 2. Validate Date
    IF p_date IS NULL THEN
        RAISE EXCEPTION 'Attendance date cannot be null.';
    END IF;

    IF p_date < '2020-01-01'::DATE OR p_date > (CURRENT_DATE + INTERVAL '30 days') THEN
        RAISE EXCEPTION 'Attendance date is out of allowable range.';
    END IF;

    -- 3. Validate Records Array
    IF p_records IS NULL OR jsonb_typeof(p_records) <> 'array' THEN
        RAISE EXCEPTION 'Payload must be a JSON array of attendance records.';
    END IF;

    v_total := jsonb_array_length(p_records);
    IF v_total = 0 THEN
        RAISE EXCEPTION 'Payload cannot be empty.';
    END IF;

    IF v_total > 1000 THEN
        RAISE EXCEPTION 'Batch size exceeds maximum limit of 1000 records.';
    END IF;

    -- 4. Check for duplicate student_id entries in payload to prevent ON CONFLICT ambiguity
    IF (SELECT count(*) FROM jsonb_array_elements(p_records) AS r) <> 
       (SELECT count(DISTINCT (r->>'student_id')::UUID) FROM jsonb_array_elements(p_records) AS r) THEN
        RAISE EXCEPTION 'Batch payload contains duplicate student_id entries.';
    END IF;

    -- 5. Validate that all student IDs exist, are active, and belong strictly to the authenticated teacher
    SELECT count(*)
    INTO v_valid_students_count
    FROM public.students s
    WHERE s.teacher_id = v_teacher_id
      AND s.deleted_at IS NULL
      AND s.id IN (
          SELECT (r->>'student_id')::UUID 
          FROM jsonb_array_elements(p_records) AS r
      );

    IF v_valid_students_count <> v_total THEN
        RAISE EXCEPTION 'One or more students do not exist, are deleted, or do not belong to the authenticated teacher.';
    END IF;

    -- 6. Validate status strings
    IF EXISTS (
        SELECT 1 
        FROM jsonb_array_elements(p_records) AS r
        WHERE UPPER(TRIM(r->>'status')) NOT IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')
    ) THEN
        RAISE EXCEPTION 'Invalid attendance status found in records. Allowed values: PRESENT, ABSENT, LATE, EXCUSED.';
    END IF;

    -- 7. Compute counts directly from payload
    SELECT 
        count(*) FILTER (WHERE UPPER(TRIM(r->>'status')) = 'PRESENT'),
        count(*) FILTER (WHERE UPPER(TRIM(r->>'status')) = 'ABSENT'),
        count(*) FILTER (WHERE UPPER(TRIM(r->>'status')) = 'LATE'),
        count(*) FILTER (WHERE UPPER(TRIM(r->>'status')) = 'EXCUSED')
    INTO v_present, v_absent, v_late, v_excused
    FROM jsonb_array_elements(p_records) AS r;

    -- 8. Atomic Bulk Upsert utilizing existing unique constraint (student_id, date)
    INSERT INTO public.attendance (
        teacher_id,
        student_id,
        date,
        status,
        note
    )
    SELECT
        v_teacher_id,
        (r->>'student_id')::UUID,
        p_date,
        UPPER(TRIM(r->>'status')),
        NULLIF(TRIM(r->>'note'), '')
    FROM jsonb_array_elements(p_records) AS r
    ON CONFLICT (student_id, date)
    DO UPDATE SET
        status = EXCLUDED.status,
        note = COALESCE(EXCLUDED.note, public.attendance.note),
        updated_at = now()
    WHERE public.attendance.teacher_id = v_teacher_id;

    -- 9. Return structured atomic result summary
    RETURN jsonb_build_object(
        'total', v_total,
        'present_count', v_present,
        'absent_count', v_absent,
        'late_count', v_late,
        'excused_count', v_excused,
        'date', to_char(p_date, 'YYYY-MM-DD')
    );
END;
$$;

-- Revoke default public execution rights and grant strictly to authenticated users
REVOKE EXECUTE ON FUNCTION public.record_batch_attendance(DATE, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.record_batch_attendance(DATE, JSONB) TO authenticated;
