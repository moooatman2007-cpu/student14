-- Migration: Add educational_stage to public.teachers table
ALTER TABLE public.teachers
ADD COLUMN IF NOT EXISTS educational_stage TEXT CHECK (educational_stage IN ('ابتدائي', 'إعدادي', 'ثانوي') OR educational_stage IS NULL);

COMMENT ON COLUMN public.teachers.educational_stage IS 'Educational stage for the teacher: ابتدائي, إعدادي, or ثانوي';

-- Update handle_new_user function to populate educational_stage from auth user metadata
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

    v_stage := NEW.raw_user_meta_data->>'educational_stage';
    IF v_stage NOT IN ('ابتدائي', 'إعدادي', 'ثانوي') THEN
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
