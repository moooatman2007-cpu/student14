-- ============================================================================
-- Supabase Migration: Add Subject, Center Name to Teachers & Storage Bucket
-- Migration ID: 20260918000001_add_teacher_subject_center_and_storage.sql
-- ============================================================================

-- 1. Add subject and center_name columns to public.teachers
ALTER TABLE public.teachers ADD COLUMN IF NOT EXISTS subject TEXT NULL;
ALTER TABLE public.teachers ADD COLUMN IF NOT EXISTS center_name TEXT NULL;

-- 2. Create Storage Bucket for teacher-avatars
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'teacher-avatars',
    'teacher-avatars',
    true,
    5242880, -- 5MB
    ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
    public = true,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

-- 3. Storage Security Policies for teacher-avatars Bucket (Strict Multi-Tenant Isolation)
DROP POLICY IF EXISTS "Avatar Select Policy" ON storage.objects;
DROP POLICY IF EXISTS "Avatar Public Select Policy" ON storage.objects;
DROP POLICY IF EXISTS "Avatar Insert Policy" ON storage.objects;
DROP POLICY IF EXISTS "Avatar Update Policy" ON storage.objects;
DROP POLICY IF EXISTS "Avatar Delete Policy" ON storage.objects;

-- SELECT policy: Public read for teacher avatars so avatar images can be loaded via publicUrl
CREATE POLICY "Avatar Public Select Policy"
ON storage.objects FOR SELECT TO public
USING (bucket_id = 'teacher-avatars');

-- INSERT policy: Only authenticated teacher can upload to their own folder {auth.uid()}/*
CREATE POLICY "Avatar Insert Policy"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (
    bucket_id = 'teacher-avatars' 
    AND split_part(name, '/', 1) = (auth.uid())::text
);

-- UPDATE policy: Only authenticated teacher can update files in their own folder {auth.uid()}/*
CREATE POLICY "Avatar Update Policy"
ON storage.objects FOR UPDATE TO authenticated
USING (
    bucket_id = 'teacher-avatars' 
    AND split_part(name, '/', 1) = (auth.uid())::text
)
WITH CHECK (
    bucket_id = 'teacher-avatars' 
    AND split_part(name, '/', 1) = (auth.uid())::text
);

-- DELETE policy: Only authenticated teacher can delete files in their own folder {auth.uid()}/*
CREATE POLICY "Avatar Delete Policy"
ON storage.objects FOR DELETE TO authenticated
USING (
    bucket_id = 'teacher-avatars' 
    AND split_part(name, '/', 1) = (auth.uid())::text
);
