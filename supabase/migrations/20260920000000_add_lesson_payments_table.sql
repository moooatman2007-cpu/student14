-- ============================================================================
-- Supabase Migration: Add Lesson Payments Table for StudentManager
-- Migration ID: 20260920000000_add_lesson_payments_table.sql
-- Multi-Tenant, High-Performance, Isolated Monthly Lesson Payments
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.lesson_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    is_paid BOOLEAN NOT NULL DEFAULT false,
    paid_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Multi-tenant composite foreign key prevents attaching student of teacher B to teacher A
    CONSTRAINT fk_lesson_payments_student_tenant FOREIGN KEY (student_id, teacher_id) REFERENCES public.students(id, teacher_id) ON DELETE CASCADE,
    CONSTRAINT uq_lesson_payments_student_period UNIQUE (student_id, year, month),
    CONSTRAINT check_lesson_payments_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT check_lesson_payments_year CHECK (year >= 2020)
);

CREATE OR REPLACE TRIGGER trg_lesson_payments_updated_at
BEFORE UPDATE ON public.lesson_payments
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- Performance Indexes for fast single-query monthly fetching & student lookups
CREATE INDEX IF NOT EXISTS idx_lesson_payments_teacher_period ON public.lesson_payments (teacher_id, year, month);
CREATE INDEX IF NOT EXISTS idx_lesson_payments_student ON public.lesson_payments (student_id, year, month);

-- Enable Row Level Security (RLS)
ALTER TABLE public.lesson_payments ENABLE ROW LEVEL SECURITY;

-- 1. Select Policy
DROP POLICY IF EXISTS "lesson_payments_select_own" ON public.lesson_payments;
CREATE POLICY "lesson_payments_select_own" ON public.lesson_payments
FOR SELECT TO authenticated
USING (teacher_id = (SELECT auth.uid()));

-- 2. Insert Policy
DROP POLICY IF EXISTS "lesson_payments_insert_own" ON public.lesson_payments;
CREATE POLICY "lesson_payments_insert_own" ON public.lesson_payments
FOR INSERT TO authenticated
WITH CHECK (teacher_id = (SELECT auth.uid()));

-- 3. Update Policy
DROP POLICY IF EXISTS "lesson_payments_update_own" ON public.lesson_payments;
CREATE POLICY "lesson_payments_update_own" ON public.lesson_payments
FOR UPDATE TO authenticated
USING (teacher_id = (SELECT auth.uid()))
WITH CHECK (teacher_id = (SELECT auth.uid()));

-- 4. Delete Policy
DROP POLICY IF EXISTS "lesson_payments_delete_own" ON public.lesson_payments;
CREATE POLICY "lesson_payments_delete_own" ON public.lesson_payments
FOR DELETE TO authenticated
USING (teacher_id = (SELECT auth.uid()));
