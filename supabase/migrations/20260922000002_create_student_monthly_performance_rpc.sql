-- ============================================================================
-- Migration: 20260922000002_create_student_monthly_performance_rpc.sql
-- Description: Parameterized RPC for student monthly performance aggregation.
-- Status: PROPOSED FOR REVIEW ONLY — NOT EXECUTED IN DATABASE
-- ============================================================================

-- RATIONALE & ARCHITECTURAL INTENT:
-- This function is designed to replace direct high-overhead querying of
-- `public.v_student_monthly_performance` by:
-- 1. Accepting target year and month as explicit parameters (p_year, p_month).
-- 2. Calculating date boundaries (v_start_date, v_next_month_start) once per invocation.
-- 3. Enabling standard B-tree index range scans on (student_id, date) instead of
--    row-by-row CPU evaluation of EXTRACT(YEAR/MONTH FROM date).
-- 4. Preserving active enrolled students in reports even when no attendance/recitation/
--    exam records exist for a historical month (returning 0.00% instead of dropping them).
-- 5. Using SECURITY DEFINER to encapsulate aggregation queries inside a controlled
--    security boundary while strictly binding tenant isolation to auth.uid().
--    NOTE: Actual performance comparison against live RLS has not been benchmarked
--    on the production instance and remains a theoretical architectural improvement.

CREATE OR REPLACE FUNCTION public.get_student_monthly_performance(
    p_year INT,
    p_month INT,
    p_grade_id UUID DEFAULT NULL,
    p_student_id UUID DEFAULT NULL
)
RETURNS TABLE (
    student_id UUID,
    teacher_id UUID,
    student_name TEXT,
    student_code TEXT,
    grade_id UUID,
    grade_name TEXT,
    year INT,
    month INT,
    attendance_present INT,
    attendance_absent INT,
    attendance_late INT,
    attendance_excused INT,
    attendance_total INT,
    attendance_percentage NUMERIC(5,2),
    recitation_count INT,
    recitation_average_percentage NUMERIC(5,2),
    exam_count INT,
    exam_average_percentage NUMERIC(5,2),
    teacher_note TEXT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_teacher_id UUID;
    v_start_date DATE;
    v_next_month_start DATE;
BEGIN
    -- 1. Security Check: Authenticate caller via secure server context
    -- auth.uid() is the SOLE source of tenant identity; no external teacher_id parameter exists.
    v_teacher_id := auth.uid();
    IF v_teacher_id IS NULL THEN
        RETURN;
    END IF;

    -- 2. Parameter Validation
    IF p_year IS NULL OR p_month IS NULL 
       OR p_month < 1 OR p_month > 12 
       OR p_year < 2020 OR p_year > 2100 THEN
        RETURN;
    END IF;

    -- 3. Calculate date range boundaries for half-open interval: [v_start_date, v_next_month_start)
    v_start_date := MAKE_DATE(p_year, p_month, 1);
    v_next_month_start := (v_start_date + INTERVAL '1 month')::DATE;

    -- 4. Main Query: Drive from active students and left-join indexed monthly metrics
    RETURN QUERY
    SELECT
        s.id AS student_id,
        s.teacher_id,
        s.full_name AS student_name,
        s.student_code,
        s.grade_id,
        g.name AS grade_name,
        p_year AS year,
        p_month AS month,

        -- Attendance metrics
        COALESCE(att.present_count, 0)::INT AS attendance_present,
        COALESCE(att.absent_count, 0)::INT AS attendance_absent,
        COALESCE(att.late_count, 0)::INT AS attendance_late,
        COALESCE(att.excused_count, 0)::INT AS attendance_excused,
        COALESCE(att.total_days, 0)::INT AS attendance_total,
        CASE 
            WHEN COALESCE(att.total_days, 0) > 0 
            THEN ROUND((COALESCE(att.present_count, 0)::NUMERIC / att.total_days::NUMERIC) * 100.0, 2)::NUMERIC(5,2)
            ELSE 0.00::NUMERIC(5,2)
        END AS attendance_percentage,

        -- Recitation metrics
        COALESCE(rec.rec_count, 0)::INT AS recitation_count,
        COALESCE(rec.avg_percentage, 0.00)::NUMERIC(5,2) AS recitation_average_percentage,

        -- Exam metrics
        COALESCE(ex.exam_count, 0)::INT AS exam_count,
        COALESCE(ex.avg_percentage, 0.00)::NUMERIC(5,2) AS exam_average_percentage,

        -- Teacher note for month (safely deduplicated via LIMIT 1)
        COALESCE(mr.teacher_note, '')::TEXT AS teacher_note

    FROM public.students s
    JOIN public.grades g 
        ON g.id = s.grade_id 
       AND g.teacher_id = v_teacher_id

    -- Attendance aggregation using index range scan on (student_id, date)
    LEFT JOIN LATERAL (
        SELECT 
            COUNT(*)::INTEGER AS total_days,
            COUNT(*) FILTER (WHERE a.status = 'PRESENT')::INTEGER AS present_count,
            COUNT(*) FILTER (WHERE a.status = 'ABSENT')::INTEGER AS absent_count,
            COUNT(*) FILTER (WHERE a.status = 'LATE')::INTEGER AS late_count,
            COUNT(*) FILTER (WHERE a.status = 'EXCUSED')::INTEGER AS excused_count
        FROM public.attendance a
        WHERE a.student_id = s.id
          AND a.teacher_id = v_teacher_id
          AND a.date >= v_start_date
          AND a.date < v_next_month_start
    ) att ON true

    -- Recitations aggregation using index range scan on (student_id, date)
    -- Defensively guards against max_score <= 0 or NULL scores
    LEFT JOIN LATERAL (
        SELECT 
            COUNT(*)::INTEGER AS rec_count,
            ROUND(
                COALESCE(
                    AVG(
                        CASE 
                            WHEN r.max_score > 0 THEN (r.score / r.max_score) * 100.0 
                            ELSE 0.00 
                        END
                    ), 
                    0.00
                )::NUMERIC, 
                2
            )::NUMERIC(5,2) AS avg_percentage
        FROM public.recitations r
        WHERE r.student_id = s.id
          AND r.teacher_id = v_teacher_id
          AND r.date >= v_start_date
          AND r.date < v_next_month_start
    ) rec ON true

    -- Exams aggregation using index range scan on (student_id, date)
    -- Defensively guards against max_score <= 0 or NULL scores
    LEFT JOIN LATERAL (
        SELECT 
            COUNT(*)::INTEGER AS exam_count,
            ROUND(
                COALESCE(
                    AVG(
                        CASE 
                            WHEN e.max_score > 0 THEN (e.score / e.max_score) * 100.0 
                            ELSE 0.00 
                        END
                    ), 
                    0.00
                )::NUMERIC, 
                2
            )::NUMERIC(5,2) AS avg_percentage
        FROM public.exams e
        WHERE e.student_id = s.id
          AND e.teacher_id = v_teacher_id
          AND e.date >= v_start_date
          AND e.date < v_next_month_start
    ) ex ON true

    -- Monthly report note lookup:
    -- While the schema defines CONSTRAINT uq_monthly_reports_student_period (student_id, year, month),
    -- we defensively use LIMIT 1 ORDER BY updated_at DESC to guarantee that a student is never
    -- duplicated even if multiple notes exist in legacy/unconstrained data.
    LEFT JOIN LATERAL (
        SELECT mr_sub.teacher_note
        FROM public.monthly_reports mr_sub
        WHERE mr_sub.student_id = s.id
          AND mr_sub.teacher_id = v_teacher_id
          AND mr_sub.year = p_year
          AND mr_sub.month = p_month
        ORDER BY mr_sub.updated_at DESC NULLS LAST, mr_sub.id DESC
        LIMIT 1
    ) mr ON true

    WHERE s.teacher_id = v_teacher_id
      AND s.deleted_at IS NULL
      AND (p_student_id IS NULL OR s.id = p_student_id)
      AND (p_grade_id IS NULL OR s.grade_id = p_grade_id)
    ORDER BY s.full_name ASC;
END;
$$;

-- Security hardening: revoke public execution, allow authenticated role only
REVOKE ALL ON FUNCTION public.get_student_monthly_performance(INT, INT, UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_student_monthly_performance(INT, INT, UUID, UUID) TO authenticated;

COMMENT ON FUNCTION public.get_student_monthly_performance(INT, INT, UUID, UUID) IS
'Aggregates monthly student performance (attendance, recitations, exams, note) for a specified year/month with direct index range scans.';
