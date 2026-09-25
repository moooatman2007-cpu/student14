-- ============================================================================
-- Supabase Schema Verification Script for StudentManager
-- Migration ID: verify_schema.sql
-- ============================================================================

-- 1. Verify All Tables Exist
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_name IN ('teachers', 'grades', 'students', 'attendance', 'recitations', 'exams', 'monthly_reports')
ORDER BY table_name;

-- 2. Verify RLS is Enabled on All Tables
SELECT relname AS table_name, relrowsecurity AS rls_enabled
FROM pg_class
WHERE relname IN ('teachers', 'grades', 'students', 'attendance', 'recitations', 'exams', 'monthly_reports')
  AND relnamespace = 'public'::regnamespace;

-- 3. Verify Composite Foreign Keys for Strict Multi-Tenant Isolation
SELECT
    tc.table_name, 
    tc.constraint_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name 
FROM information_schema.table_constraints AS tc 
JOIN information_schema.key_column_usage AS kcu
  ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
  ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public'
ORDER BY tc.table_name, tc.constraint_name;

-- 4. Verify Triggers and Automated Functions
SELECT 
    event_object_table AS table_name, 
    trigger_name, 
    action_timing, 
    event_manipulation
FROM information_schema.triggers
WHERE trigger_schema = 'public' OR event_object_schema = 'auth'
ORDER BY event_object_table, trigger_name;

-- 5. Verify View Definition
SELECT table_name, view_definition 
FROM information_schema.views 
WHERE table_schema = 'public' 
  AND table_name = 'v_student_monthly_performance';
