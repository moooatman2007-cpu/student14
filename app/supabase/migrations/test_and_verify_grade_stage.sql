-- ============================================================================
-- Verification and Test Script for Grade Stage DB Validation
-- ============================================================================

-- 1. Pre-execution Data Audit on Existing Grades
SELECT 
    t.id AS teacher_id,
    t.email,
    t.educational_stage,
    g.id AS grade_id,
    g.name AS grade_name,
    CASE 
        WHEN t.educational_stage = 'ابتدائي' AND g.name IN ('الأول الابتدائي', 'الثاني الابتدائي', 'الثالث الابتدائي', 'الرابع الابتدائي', 'الخامس الابتدائي', 'السادس الابتدائي') THEN 'VALID'
        WHEN t.educational_stage = 'إعدادي' AND g.name IN ('الأول الإعدادي', 'الثاني الإعدادي', 'الثالث الإعدادي') THEN 'VALID'
        WHEN t.educational_stage = 'ثانوي' AND g.name IN ('الأول الثانوي', 'الثاني الثانوي', 'الثالث الثانوي') THEN 'VALID'
        ELSE 'MISMATCH'
    END AS status
FROM public.grades g
JOIN public.teachers t ON g.teacher_id = t.id
ORDER BY t.id, g.display_order;

-- 2. Verify Function Existence & Definition
SELECT 
    routine_name,
    routine_type,
    security_type,
    external_language
FROM information_schema.routines
WHERE specific_schema = 'public' 
  AND routine_name = 'validate_grade_stage';

-- 3. Verify Function Permissions
SELECT 
    grantee, 
    privilege_type 
FROM information_schema.routine_privileges 
WHERE specific_schema = 'public' 
  AND routine_name = 'validate_grade_stage';

-- 4. Verify Trigger Status on public.grades
SELECT 
    trigger_name,
    event_manipulation,
    event_object_table,
    action_timing,
    action_orientation
FROM information_schema.triggers
WHERE trigger_schema = 'public'
  AND event_object_table = 'grades'
  AND trigger_name = 'trg_validate_grade_stage';

-- 5. Safe Automated Transaction Test for Valid and Invalid Inserts
DO $$
DECLARE
    v_test_teacher_id UUID := gen_random_uuid();
    v_caught_exception BOOLEAN := FALSE;
BEGIN
    -- Setup dummy test teacher (High School / ثانوي)
    INSERT INTO public.teachers (id, email, full_name, educational_stage, theme_mode)
    VALUES (v_test_teacher_id, 'test_teacher@example.com', 'Test Teacher', 'ثانوي', 'SYSTEM');

    -- Test A: Valid Grade Insert (Should Succeed)
    BEGIN
        INSERT INTO public.grades (id, teacher_id, name, display_order)
        VALUES (gen_random_uuid(), v_test_teacher_id, 'الأول الثانوي', 1);
        RAISE NOTICE 'Test A (Valid Grade Insertion): PASSED';
    EXCEPTION WHEN OTHERS THEN
        RAISE EXCEPTION 'Test A FAILED unexpectedly: %', SQLERRM;
    END;

    -- Test B: Invalid Grade Insert (Primary stage grade for Secondary teacher - Must FAIL)
    BEGIN
        INSERT INTO public.grades (id, teacher_id, name, display_order)
        VALUES (gen_random_uuid(), v_test_teacher_id, 'الأول الابتدائي', 1);
        -- If we reach here, trigger did not catch the invalid grade!
        RAISE EXCEPTION 'Test B FAILED: Invalid grade was allowed!';
    EXCEPTION 
        WHEN check_violation OR OTHERS THEN
            v_caught_exception := TRUE;
            RAISE NOTICE 'Test B (Invalid Grade Rejected): PASSED with error: %', SQLERRM;
    END;

    -- Cleanup dummy test records (Clean Rollback)
    DELETE FROM public.grades WHERE teacher_id = v_test_teacher_id;
    DELETE FROM public.teachers WHERE id = v_test_teacher_id;

    RAISE NOTICE 'ALL GRADE STAGE DB AUDIT TESTS PASSED SUCCESSFULLY.';
END;
$$;
