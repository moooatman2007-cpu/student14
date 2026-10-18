-- ============================================================================
-- Supabase PostgreSQL Migration: StudentManager Production-Ready Schema
-- Migration ID: 20260918000000_init_student_manager_schema.sql
-- Multi-Tenant, High-Performance, Strict Isolation Architecture
-- ============================================================================

-- Enable pgcrypto for UUID generation if needed
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. Helper Functions (updated_at & Code Generators)
-- ============================================================================

-- Function to automatically set updated_at timestamp on record updates
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- ============================================================================
-- 2. Table: public.teachers (Profile linked to auth.users)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.teachers (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    full_name TEXT NOT NULL DEFAULT '',
    phone_number TEXT NULL,
    avatar_url TEXT NULL,
    theme_mode TEXT NOT NULL DEFAULT 'SYSTEM',
    student_code_counter BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT check_teacher_theme_mode CHECK (theme_mode IN ('SYSTEM', 'LIGHT', 'DARK'))
);

CREATE OR REPLACE TRIGGER trg_teachers_updated_at
BEFORE UPDATE ON public.teachers
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- ============================================================================
-- 3. Table: public.grades
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.grades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_grades_teacher_name UNIQUE (teacher_id, name),
    CONSTRAINT uq_grades_id_teacher UNIQUE (id, teacher_id) -- Enables multi-tenant composite foreign keys
);

CREATE OR REPLACE TRIGGER trg_grades_updated_at
BEFORE UPDATE ON public.grades
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- ============================================================================
-- 4. Table: public.students
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    grade_id UUID NOT NULL,
    student_code TEXT NOT NULL,
    full_name TEXT NOT NULL,
    parent_phone TEXT NOT NULL,
    has_whatsapp BOOLEAN NOT NULL DEFAULT true,
    alternative_phone TEXT NULL,
    deleted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_students_grade_tenant FOREIGN KEY (grade_id, teacher_id) REFERENCES public.grades(id, teacher_id) ON DELETE RESTRICT,
    CONSTRAINT uq_students_teacher_code UNIQUE (teacher_id, student_code),
    CONSTRAINT uq_students_id_teacher UNIQUE (id, teacher_id) -- Enables multi-tenant composite foreign keys
);

CREATE OR REPLACE TRIGGER trg_students_updated_at
BEFORE UPDATE ON public.students
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- ============================================================================
-- 5. Student Code Automation (Race-Condition Safe & Stable)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.generate_student_code()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    next_counter BIGINT;
BEGIN
    -- Atomically lock teacher row and increment counter to prevent race conditions
    UPDATE public.teachers
    SET student_code_counter = student_code_counter + 1
    WHERE id = NEW.teacher_id
    RETURNING student_code_counter INTO next_counter;

    IF next_counter IS NULL THEN
        RAISE EXCEPTION 'Teacher with ID % not found.', NEW.teacher_id;
    END IF;

    -- Format student code e.g. ST-00001
    NEW.student_code := 'ST-' || LPAD(next_counter::TEXT, 5, '0');
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_generate_student_code
BEFORE INSERT ON public.students
FOR EACH ROW
EXECUTE FUNCTION public.generate_student_code();

-- Protect student_code from being modified during updates
CREATE OR REPLACE FUNCTION public.protect_student_code()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.student_code <> OLD.student_code THEN
        NEW.student_code := OLD.student_code;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_protect_student_code
BEFORE UPDATE ON public.students
FOR EACH ROW
EXECUTE FUNCTION public.protect_student_code();

-- ============================================================================
-- 6. Table: public.attendance
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    status TEXT NOT NULL DEFAULT 'PRESENT',
    note TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Multi-tenant composite foreign key prevents attaching student of teacher B to teacher A
    CONSTRAINT fk_attendance_student_tenant FOREIGN KEY (student_id, teacher_id) REFERENCES public.students(id, teacher_id) ON DELETE CASCADE,
    CONSTRAINT uq_attendance_student_date UNIQUE (student_id, date),
    CONSTRAINT check_attendance_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED'))
);

CREATE OR REPLACE TRIGGER trg_attendance_updated_at
BEFORE UPDATE ON public.attendance
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- ============================================================================
-- 7. Table: public.recitations
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.recitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    score NUMERIC(5,2) NOT NULL,
    max_score NUMERIC(5,2) NOT NULL DEFAULT 10.00,
    note TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Multi-tenant composite foreign key
    CONSTRAINT fk_recitations_student_tenant FOREIGN KEY (student_id, teacher_id) REFERENCES public.students(id, teacher_id) ON DELETE CASCADE,
    CONSTRAINT check_recitation_score CHECK (score >= 0 AND max_score > 0 AND score <= max_score)
);

CREATE OR REPLACE TRIGGER trg_recitations_updated_at
BEFORE UPDATE ON public.recitations
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- ============================================================================
-- 8. Table: public.exams
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.exams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    exam_name TEXT NOT NULL,
    subject TEXT NULL,
    score NUMERIC(5,2) NOT NULL,
    max_score NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    note TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Multi-tenant composite foreign key
    CONSTRAINT fk_exams_student_tenant FOREIGN KEY (student_id, teacher_id) REFERENCES public.students(id, teacher_id) ON DELETE CASCADE,
    CONSTRAINT check_exam_score CHECK (score >= 0 AND max_score > 0 AND score <= max_score)
);

CREATE OR REPLACE TRIGGER trg_exams_updated_at
BEFORE UPDATE ON public.exams
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- ============================================================================
-- 9. Table: public.monthly_reports (Teacher notes & remarks only)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.monthly_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    month INTEGER NOT NULL,
    year INTEGER NOT NULL,
    teacher_note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Multi-tenant composite foreign key
    CONSTRAINT fk_monthly_reports_student_tenant FOREIGN KEY (student_id, teacher_id) REFERENCES public.students(id, teacher_id) ON DELETE CASCADE,
    CONSTRAINT uq_monthly_reports_student_period UNIQUE (student_id, year, month),
    CONSTRAINT check_monthly_reports_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT check_monthly_reports_year CHECK (year >= 2020)
);

CREATE OR REPLACE TRIGGER trg_monthly_reports_updated_at
BEFORE UPDATE ON public.monthly_reports
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- ============================================================================
-- 10. Auth User Trigger (Automatic Teacher & Default Grades Provisioning)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_full_name TEXT;
BEGIN
    -- Extract full name from auth user metadata if present
    v_full_name := COALESCE(
        NEW.raw_user_meta_data->>'full_name',
        NEW.raw_user_meta_data->>'name',
        ''
    );

    -- 1. Create Teacher Profile
    INSERT INTO public.teachers (id, email, full_name, theme_mode, student_code_counter)
    VALUES (
        NEW.id,
        COALESCE(NEW.email, ''),
        v_full_name,
        'SYSTEM',
        0
    )
    ON CONFLICT (id) DO NOTHING;

    -- 2. Create Default Grades
    INSERT INTO public.grades (teacher_id, name, display_order)
    VALUES 
        (NEW.id, 'الأول الإعدادي', 1),
        (NEW.id, 'الثاني الإعدادي', 2),
        (NEW.id, 'الثالث الإعدادي', 3)
    ON CONFLICT (teacher_id, name) DO NOTHING;

    RETURN NEW;
END;
$$;

-- Attach trigger to auth.users safely
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_user();

-- ============================================================================
-- 11. Performance Indexes
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_grades_teacher_order ON public.grades (teacher_id, display_order);

CREATE INDEX IF NOT EXISTS idx_students_teacher_grade ON public.students (teacher_id, grade_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_students_teacher_code ON public.students (teacher_id, student_code);
CREATE INDEX IF NOT EXISTS idx_students_active ON public.students (teacher_id) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_student_date ON public.attendance (student_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_attendance_teacher_date ON public.attendance (teacher_id, date);

CREATE INDEX IF NOT EXISTS idx_recitations_student_date ON public.recitations (student_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_recitations_teacher_student ON public.recitations (teacher_id, student_id);

CREATE INDEX IF NOT EXISTS idx_exams_student_date ON public.exams (student_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_exams_teacher_student ON public.exams (teacher_id, student_id);

CREATE INDEX IF NOT EXISTS idx_monthly_reports_lookup ON public.monthly_reports (student_id, year, month);
CREATE INDEX IF NOT EXISTS idx_monthly_reports_teacher_period ON public.monthly_reports (teacher_id, year, month);

-- ============================================================================
-- 12. Row Level Security (RLS) Policies
-- ============================================================================

-- Enable RLS on all tables
ALTER TABLE public.teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.grades ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.monthly_reports ENABLE ROW LEVEL SECURITY;

-- 12.1 Teachers Policies
DROP POLICY IF EXISTS "teachers_select_own" ON public.teachers;
CREATE POLICY "teachers_select_own" ON public.teachers
FOR SELECT TO authenticated
USING (id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "teachers_update_own" ON public.teachers;
CREATE POLICY "teachers_update_own" ON public.teachers
FOR UPDATE TO authenticated
USING (id = (SELECT auth.uid()))
WITH CHECK (id = (SELECT auth.uid()));

-- 12.2 Grades Policies
DROP POLICY IF EXISTS "grades_select_own" ON public.grades;
CREATE POLICY "grades_select_own" ON public.grades
FOR SELECT TO authenticated
USING (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "grades_insert_own" ON public.grades;
CREATE POLICY "grades_insert_own" ON public.grades
FOR INSERT TO authenticated
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "grades_update_own" ON public.grades;
CREATE POLICY "grades_update_own" ON public.grades
FOR UPDATE TO authenticated
USING (teacher_id = (SELECT auth.uid()))
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "grades_delete_own" ON public.grades;
CREATE POLICY "grades_delete_own" ON public.grades
FOR DELETE TO authenticated
USING (teacher_id = (SELECT auth.uid()));

-- 12.3 Students Policies
DROP POLICY IF EXISTS "students_select_own" ON public.students;
CREATE POLICY "students_select_own" ON public.students
FOR SELECT TO authenticated
USING (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "students_insert_own" ON public.students;
CREATE POLICY "students_insert_own" ON public.students
FOR INSERT TO authenticated
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "students_update_own" ON public.students;
CREATE POLICY "students_update_own" ON public.students
FOR UPDATE TO authenticated
USING (teacher_id = (SELECT auth.uid()))
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "students_delete_own" ON public.students;
CREATE POLICY "students_delete_own" ON public.students
FOR DELETE TO authenticated
USING (teacher_id = (SELECT auth.uid()));

-- 12.4 Attendance Policies
DROP POLICY IF EXISTS "attendance_select_own" ON public.attendance;
CREATE POLICY "attendance_select_own" ON public.attendance
FOR SELECT TO authenticated
USING (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "attendance_insert_own" ON public.attendance;
CREATE POLICY "attendance_insert_own" ON public.attendance
FOR INSERT TO authenticated
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "attendance_update_own" ON public.attendance;
CREATE POLICY "attendance_update_own" ON public.attendance
FOR UPDATE TO authenticated
USING (teacher_id = (SELECT auth.uid()))
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "attendance_delete_own" ON public.attendance;
CREATE POLICY "attendance_delete_own" ON public.attendance
FOR DELETE TO authenticated
USING (teacher_id = (SELECT auth.uid()));

-- 12.5 Recitations Policies
DROP POLICY IF EXISTS "recitations_select_own" ON public.recitations;
CREATE POLICY "recitations_select_own" ON public.recitations
FOR SELECT TO authenticated
USING (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "recitations_insert_own" ON public.recitations;
CREATE POLICY "recitations_insert_own" ON public.recitations
FOR INSERT TO authenticated
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "recitations_update_own" ON public.recitations;
CREATE POLICY "recitations_update_own" ON public.recitations
FOR UPDATE TO authenticated
USING (teacher_id = (SELECT auth.uid()))
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "recitations_delete_own" ON public.recitations;
CREATE POLICY "recitations_delete_own" ON public.recitations
FOR DELETE TO authenticated
USING (teacher_id = (SELECT auth.uid()));

-- 12.6 Exams Policies
DROP POLICY IF EXISTS "exams_select_own" ON public.exams;
CREATE POLICY "exams_select_own" ON public.exams
FOR SELECT TO authenticated
USING (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "exams_insert_own" ON public.exams;
CREATE POLICY "exams_insert_own" ON public.exams
FOR INSERT TO authenticated
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "exams_update_own" ON public.exams;
CREATE POLICY "exams_update_own" ON public.exams
FOR UPDATE TO authenticated
USING (teacher_id = (SELECT auth.uid()))
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "exams_delete_own" ON public.exams;
CREATE POLICY "exams_delete_own" ON public.exams
FOR DELETE TO authenticated
USING (teacher_id = (SELECT auth.uid()));

-- 12.7 Monthly Reports Policies
DROP POLICY IF EXISTS "monthly_reports_select_own" ON public.monthly_reports;
CREATE POLICY "monthly_reports_select_own" ON public.monthly_reports
FOR SELECT TO authenticated
USING (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "monthly_reports_insert_own" ON public.monthly_reports;
CREATE POLICY "monthly_reports_insert_own" ON public.monthly_reports
FOR INSERT TO authenticated
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "monthly_reports_update_own" ON public.monthly_reports;
CREATE POLICY "monthly_reports_update_own" ON public.monthly_reports
FOR UPDATE TO authenticated
USING (teacher_id = (SELECT auth.uid()))
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "monthly_reports_delete_own" ON public.monthly_reports;
CREATE POLICY "monthly_reports_delete_own" ON public.monthly_reports
FOR DELETE TO authenticated
USING (teacher_id = (SELECT auth.uid()));

-- ============================================================================
-- 13. Dynamic Monthly Performance View (v_student_monthly_performance)
-- ============================================================================

CREATE OR REPLACE VIEW public.v_student_monthly_performance
WITH (security_invoker = true)
AS
SELECT
    s.id AS student_id,
    s.teacher_id,
    s.full_name AS student_name,
    s.student_code,
    s.grade_id,
    g.name AS grade_name,
    dates.year,
    dates.month,
    
    -- Attendance aggregates
    COALESCE(att.present_count, 0) AS attendance_present,
    COALESCE(att.absent_count, 0) AS attendance_absent,
    COALESCE(att.late_count, 0) AS attendance_late,
    COALESCE(att.excused_count, 0) AS attendance_excused,
    COALESCE(att.total_days, 0) AS attendance_total,
    CASE 
        WHEN COALESCE(att.total_days, 0) > 0 
        THEN ROUND((COALESCE(att.present_count, 0)::NUMERIC / att.total_days::NUMERIC) * 100.0, 2)
        ELSE 0.00
    END AS attendance_percentage,

    -- Recitation aggregates
    COALESCE(rec.rec_count, 0) AS recitation_count,
    COALESCE(rec.avg_percentage, 0.00) AS recitation_average_percentage,

    -- Exam aggregates
    COALESCE(ex.exam_count, 0) AS exam_count,
    COALESCE(ex.avg_percentage, 0.00) AS exam_average_percentage,

    -- Teacher monthly note
    COALESCE(mr.teacher_note, '') AS teacher_note

FROM public.students s
JOIN public.grades g ON g.id = s.grade_id
-- Cross join with distinct active months/years present in the system for this student or current period
CROSS JOIN LATERAL (
    SELECT DISTINCT 
        EXTRACT(YEAR FROM d.date_val)::INTEGER AS year,
        EXTRACT(MONTH FROM d.date_val)::INTEGER AS month
    FROM (
        SELECT a.date AS date_val FROM public.attendance a WHERE a.student_id = s.id
        UNION
        SELECT r.date AS date_val FROM public.recitations r WHERE r.student_id = s.id
        UNION
        SELECT e.date AS date_val FROM public.exams e WHERE e.student_id = s.id
        UNION
        SELECT MAKE_DATE(m.year, m.month, 1) AS date_val FROM public.monthly_reports m WHERE m.student_id = s.id
        UNION
        SELECT CURRENT_DATE AS date_val
    ) d
) dates

-- Attendance aggregate subquery
LEFT JOIN LATERAL (
    SELECT 
        COUNT(*)::INTEGER AS total_days,
        COUNT(*) FILTER (WHERE a.status = 'PRESENT')::INTEGER AS present_count,
        COUNT(*) FILTER (WHERE a.status = 'ABSENT')::INTEGER AS absent_count,
        COUNT(*) FILTER (WHERE a.status = 'LATE')::INTEGER AS late_count,
        COUNT(*) FILTER (WHERE a.status = 'EXCUSED')::INTEGER AS excused_count
    FROM public.attendance a
    WHERE a.student_id = s.id
      AND EXTRACT(YEAR FROM a.date) = dates.year
      AND EXTRACT(MONTH FROM a.date) = dates.month
) att ON true

-- Recitation aggregate subquery
LEFT JOIN LATERAL (
    SELECT 
        COUNT(*)::INTEGER AS rec_count,
        ROUND(AVG((r.score / r.max_score) * 100.0), 2) AS avg_percentage
    FROM public.recitations r
    WHERE r.student_id = s.id
      AND EXTRACT(YEAR FROM r.date) = dates.year
      AND EXTRACT(MONTH FROM r.date) = dates.month
) rec ON true

-- Exam aggregate subquery
LEFT JOIN LATERAL (
    SELECT 
        COUNT(*)::INTEGER AS exam_count,
        ROUND(AVG((e.score / e.max_score) * 100.0), 2) AS avg_percentage
    FROM public.exams e
    WHERE e.student_id = s.id
      AND EXTRACT(YEAR FROM e.date) = dates.year
      AND EXTRACT(MONTH FROM e.date) = dates.month
) ex ON true

-- Monthly report note
LEFT JOIN public.monthly_reports mr 
    ON mr.student_id = s.id 
   AND mr.year = dates.year 
   AND mr.month = dates.month

WHERE s.deleted_at IS NULL;
