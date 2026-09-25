-- ============================================================================
-- Supabase Migration: Security Hardening for Teacher WhatsApp Sessions RLS
-- Migration ID: 20260922000001_harden_teacher_whatsapp_sessions_rls.sql
-- ============================================================================

-- 1. Drop client write policies (INSERT, UPDATE, DELETE) for authenticated users
DROP POLICY IF EXISTS "Teachers can insert own whatsapp session" ON public.teacher_whatsapp_sessions;
DROP POLICY IF EXISTS "Teachers can update own whatsapp session" ON public.teacher_whatsapp_sessions;
DROP POLICY IF EXISTS "Teachers can delete own whatsapp session" ON public.teacher_whatsapp_sessions;

-- 2. Ensure SELECT policy exists and remains strictly isolated
DROP POLICY IF EXISTS "Teachers can select own whatsapp session" ON public.teacher_whatsapp_sessions;

CREATE POLICY "Teachers can select own whatsapp session"
ON public.teacher_whatsapp_sessions FOR SELECT TO authenticated
USING (teacher_id = auth.uid());
