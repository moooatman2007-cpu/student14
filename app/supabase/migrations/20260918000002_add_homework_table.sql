-- ============================================================================
-- Supabase Migration: Add Homework Table for StudentManager
-- Migration ID: 20260918000002_add_homework_table.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.homework (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    title TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    note TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_homework_student_tenant FOREIGN KEY (student_id, teacher_id) REFERENCES public.students(id, teacher_id) ON DELETE CASCADE,
    CONSTRAINT check_homework_status CHECK (status IN ('PENDING', 'COMPLETED', 'NOT_COMPLETED'))
);

CREATE OR REPLACE TRIGGER trg_homework_updated_at
BEFORE UPDATE ON public.homework
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

CREATE INDEX IF NOT EXISTS idx_homework_teacher_id ON public.homework(teacher_id);
CREATE INDEX IF NOT EXISTS idx_homework_student_id ON public.homework(student_id);
CREATE INDEX IF NOT EXISTS idx_homework_date ON public.homework(date);

ALTER TABLE public.homework ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "homework_select_own" ON public.homework;
CREATE POLICY "homework_select_own" ON public.homework
FOR SELECT TO authenticated
USING (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "homework_insert_own" ON public.homework;
CREATE POLICY "homework_insert_own" ON public.homework
FOR INSERT TO authenticated
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "homework_update_own" ON public.homework;
CREATE POLICY "homework_update_own" ON public.homework
FOR UPDATE TO authenticated
USING (teacher_id = (SELECT auth.uid()))
WITH CHECK (teacher_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS "homework_delete_own" ON public.homework;
CREATE POLICY "homework_delete_own" ON public.homework
FOR DELETE TO authenticated
USING (teacher_id = (SELECT auth.uid()));
