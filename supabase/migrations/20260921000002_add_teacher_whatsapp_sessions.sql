-- ============================================================================
-- Supabase Migration: Add Teacher WhatsApp Sessions Table
-- Migration ID: 20260921000002_add_teacher_whatsapp_sessions.sql
-- ============================================================================

-- 1. إنشاء جدول جلسات واتساب المدرسين بدون تخزين أسرار أو بيانات QR
CREATE TABLE IF NOT EXISTS public.teacher_whatsapp_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id UUID NOT NULL UNIQUE REFERENCES public.teachers(id) ON DELETE CASCADE,
    session_name TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'DISCONNECTED',
    connected_phone TEXT NULL,
    last_connected_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT check_waha_session_status CHECK (status IN ('DISCONNECTED', 'SCAN_QR_CODE', 'CONNECTED', 'FAILED'))
);

-- 2. إيقاد التريجر التلقائي لتحديث updated_at
CREATE OR REPLACE TRIGGER trg_teacher_whatsapp_sessions_updated_at
BEFORE UPDATE ON public.teacher_whatsapp_sessions
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- 3. تفعيل الـ Row Level Security (RLS)
ALTER TABLE public.teacher_whatsapp_sessions ENABLE ROW LEVEL SECURITY;

-- 4. سياسات الأمان والعزل التام للمدرسين (Multi-Tenant Isolation)
CREATE POLICY "Teachers can select own whatsapp session"
ON public.teacher_whatsapp_sessions FOR SELECT TO authenticated
USING (teacher_id = auth.uid());

CREATE POLICY "Teachers can insert own whatsapp session"
ON public.teacher_whatsapp_sessions FOR INSERT TO authenticated
WITH CHECK (teacher_id = auth.uid());

CREATE POLICY "Teachers can update own whatsapp session"
ON public.teacher_whatsapp_sessions FOR UPDATE TO authenticated
USING (teacher_id = auth.uid())
WITH CHECK (teacher_id = auth.uid());

CREATE POLICY "Teachers can delete own whatsapp session"
ON public.teacher_whatsapp_sessions FOR DELETE TO authenticated
USING (teacher_id = auth.uid());
