-- ============================================================================
-- Supabase Migration: Grade Stage Validation Function and Trigger
-- Migration ID: 20260921000001_validate_grade_stage.sql
-- Enforces DB-Level Stage Isolation for Grades without breaking Teacher Flexibility
-- ============================================================================

-- 1. Pre-validation check query (Run as a safe check before trigger activation)
-- This block ensures no invalid grades exist for existing teachers.
DO $$
DECLARE
    v_invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_invalid_count
    FROM public.grades g
    JOIN public.teachers t ON g.teacher_id = t.id
    WHERE 
        (t.educational_stage = 'ابتدائي' AND g.name NOT IN ('الأول الابتدائي', 'الثاني الابتدائي', 'الثالث الابتدائي', 'الرابع الابتدائي', 'الخامس الابتدائي', 'السادس الابتدائي'))
        OR
        (t.educational_stage = 'إعدادي' AND g.name NOT IN ('الأول الإعدادي', 'الثاني الإعدادي', 'الثالث الإعدادي'))
        OR
        (t.educational_stage = 'ثانوي' AND g.name NOT IN ('الأول الثانوي', 'الثاني الثانوي', 'الثالث الثانوي'))
        OR
        (t.educational_stage IS NULL);

    IF v_invalid_count > 0 THEN
        RAISE NOTICE 'Found % mismatched existing grade record(s). Existing records are preserved.', v_invalid_count;
    END IF;
END;
$$;

-- 2. Function: public.validate_grade_stage()
-- Enforces that newly inserted or updated grades strictly match the teacher's current educational stage.
CREATE OR REPLACE FUNCTION public.validate_grade_stage()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_stage TEXT;
    v_is_valid BOOLEAN := FALSE;
BEGIN
    -- Retrieve the current teacher's educational stage
    SELECT educational_stage INTO v_stage
    FROM public.teachers
    WHERE id = NEW.teacher_id;

    IF v_stage IS NULL THEN
        RAISE EXCEPTION 'Cannot add or modify grade: Teacher (ID: %) does not have an active educational stage configured.', NEW.teacher_id
            USING ERRCODE = 'check_violation';
    END IF;

    -- Validate grade name against the teacher's stage
    IF v_stage = 'ابتدائي' THEN
        IF NEW.name IN (
            'الأول الابتدائي',
            'الثاني الابتدائي',
            'الثالث الابتدائي',
            'الرابع الابتدائي',
            'الخامس الابتدائي',
            'السادس الابتدائي'
        ) THEN
            v_is_valid := TRUE;
        END IF;
    ELSIF v_stage = 'إعدادي' THEN
        IF NEW.name IN (
            'الأول الإعدادي',
            'الثاني الإعدادي',
            'الثالث الإعدادي'
        ) THEN
            v_is_valid := TRUE;
        END IF;
    ELSIF v_stage = 'ثانوي' THEN
        IF NEW.name IN (
            'الأول الثانوي',
            'الثاني الثانوي',
            'الثالث الثانوي'
        ) THEN
            v_is_valid := TRUE;
        END IF;
    END IF;

    IF NOT v_is_valid THEN
        RAISE EXCEPTION 'Invalid grade "%" for educational stage "%". Grade name does not match the teacher stage curriculum.', NEW.name, v_stage
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

-- 3. Restrict Direct Execution Permissions
REVOKE ALL ON FUNCTION public.validate_grade_stage() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.validate_grade_stage() FROM anon;
REVOKE ALL ON FUNCTION public.validate_grade_stage() FROM authenticated;
GRANT EXECUTE ON FUNCTION public.validate_grade_stage() TO service_role;

-- 4. Create Trigger on public.grades
DROP TRIGGER IF EXISTS trg_validate_grade_stage ON public.grades;

CREATE TRIGGER trg_validate_grade_stage
BEFORE INSERT OR UPDATE OF teacher_id, name ON public.grades
FOR EACH ROW
EXECUTE FUNCTION public.validate_grade_stage();

COMMENT ON FUNCTION public.validate_grade_stage() IS 'Validates that any grade name inserted or updated strictly conforms to the teacher educational stage.';
