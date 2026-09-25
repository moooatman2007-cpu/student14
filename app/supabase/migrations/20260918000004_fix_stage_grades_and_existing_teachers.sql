-- Migration: Fix stage grades and backfill for existing teachers safely without data loss or duplicates

-- 1. Normalize any grade names with prefix 'الصف '
UPDATE public.grades SET name = 'الأول الابتدائي' WHERE name = 'الصف الأول الابتدائي';
UPDATE public.grades SET name = 'الثاني الابتدائي' WHERE name = 'الصف الثاني الابتدائي';
UPDATE public.grades SET name = 'الثالث الابتدائي' WHERE name = 'الصف الثالث الابتدائي';
UPDATE public.grades SET name = 'الرابع الابتدائي' WHERE name = 'الصف الرابع الابتدائي';
UPDATE public.grades SET name = 'الخامس الابتدائي' WHERE name = 'الصف الخامس الابتدائي';
UPDATE public.grades SET name = 'السادس الابتدائي' WHERE name = 'الصف السادس الابتدائي';

UPDATE public.grades SET name = 'الأول الثانوي' WHERE name = 'الصف الأول الثانوي';
UPDATE public.grades SET name = 'الثاني الثانوي' WHERE name = 'الصف الثاني الثانوي';
UPDATE public.grades SET name = 'الثالث الثانوي' WHERE name = 'الصف الثالث الثانوي';

-- 2. Backfill missing grades for existing teachers according to their educational_stage
DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT id, educational_stage FROM public.teachers LOOP
        IF t.educational_stage = 'ابتدائي' THEN
            -- Delete old default prep grades IF AND ONLY IF no students are in them
            DELETE FROM public.grades 
            WHERE teacher_id = t.id 
              AND name IN ('الأول الإعدادي', 'الثاني الإعدادي', 'الثالث الإعدادي')
              AND NOT EXISTS (SELECT 1 FROM public.students s WHERE s.grade_id = public.grades.id);

            -- Insert the 6 elementary grades
            INSERT INTO public.grades (teacher_id, name, display_order)
            VALUES
                (t.id, 'الأول الابتدائي', 1),
                (t.id, 'الثاني الابتدائي', 2),
                (t.id, 'الثالث الابتدائي', 3),
                (t.id, 'الرابع الابتدائي', 4),
                (t.id, 'الخامس الابتدائي', 5),
                (t.id, 'السادس الابتدائي', 6)
            ON CONFLICT (teacher_id, name) DO NOTHING;

        ELSIF t.educational_stage = 'ثانوي' THEN
            -- Delete old default prep grades IF AND ONLY IF no students are in them
            DELETE FROM public.grades 
            WHERE teacher_id = t.id 
              AND name IN ('الأول الإعدادي', 'الثاني الإعدادي', 'الثالث الإعدادي')
              AND NOT EXISTS (SELECT 1 FROM public.students s WHERE s.grade_id = public.grades.id);

            -- Insert the 3 secondary grades
            INSERT INTO public.grades (teacher_id, name, display_order)
            VALUES
                (t.id, 'الأول الثانوي', 1),
                (t.id, 'الثاني الثانوي', 2),
                (t.id, 'الثالث الثانوي', 3)
            ON CONFLICT (teacher_id, name) DO NOTHING;

        ELSE
            -- Preparatory or NULL (legacy) -> ensure the 3 preparatory grades exist
            INSERT INTO public.grades (teacher_id, name, display_order)
            VALUES
                (t.id, 'الأول الإعدادي', 1),
                (t.id, 'الثاني الإعدادي', 2),
                (t.id, 'الثالث الإعدادي', 3)
            ON CONFLICT (teacher_id, name) DO NOTHING;
        END IF;
    END LOOP;
END;
$$;

-- 3. Ensure handle_new_user uses the exact grade names
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_full_name TEXT;
    v_stage TEXT;
BEGIN
    v_full_name := COALESCE(
        NEW.raw_user_meta_data->>'full_name',
        NEW.raw_user_meta_data->>'name',
        ''
    );

    v_stage := TRIM(NEW.raw_user_meta_data->>'educational_stage');
    IF v_stage = 'ابتدائي' OR v_stage = 'إبتدائي' OR v_stage LIKE '%ابتدائ%' THEN
        v_stage := 'ابتدائي';
    ELSIF v_stage = 'ثانوي' OR v_stage LIKE '%ثانو%' THEN
        v_stage := 'ثانوي';
    ELSIF v_stage = 'إعدادي' OR v_stage = 'اعدادي' OR v_stage LIKE '%عداد%' THEN
        v_stage := 'إعدادي';
    ELSE
        v_stage := NULL;
    END IF;

    -- 1. Create Teacher Profile
    INSERT INTO public.teachers (id, email, full_name, educational_stage, theme_mode, student_code_counter)
    VALUES (
        NEW.id,
        COALESCE(NEW.email, ''),
        v_full_name,
        v_stage,
        'SYSTEM',
        0
    )
    ON CONFLICT (id) DO UPDATE SET
        educational_stage = EXCLUDED.educational_stage,
        full_name = EXCLUDED.full_name;

    -- 2. Create Default Grades based on Stage
    IF v_stage = 'ابتدائي' THEN
        INSERT INTO public.grades (teacher_id, name, display_order)
        VALUES 
            (NEW.id, 'الأول الابتدائي', 1),
            (NEW.id, 'الثاني الابتدائي', 2),
            (NEW.id, 'الثالث الابتدائي', 3),
            (NEW.id, 'الرابع الابتدائي', 4),
            (NEW.id, 'الخامس الابتدائي', 5),
            (NEW.id, 'السادس الابتدائي', 6)
        ON CONFLICT (teacher_id, name) DO NOTHING;
    ELSIF v_stage = 'ثانوي' THEN
        INSERT INTO public.grades (teacher_id, name, display_order)
        VALUES 
            (NEW.id, 'الأول الثانوي', 1),
            (NEW.id, 'الثاني الثانوي', 2),
            (NEW.id, 'الثالث الثانوي', 3)
        ON CONFLICT (teacher_id, name) DO NOTHING;
    ELSE
        INSERT INTO public.grades (teacher_id, name, display_order)
        VALUES 
            (NEW.id, 'الأول الإعدادي', 1),
            (NEW.id, 'الثاني الإعدادي', 2),
            (NEW.id, 'الثالث الإعدادي', 3)
        ON CONFLICT (teacher_id, name) DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$;
