-- ==============================================================================
-- Migration: 202610160002_admin_manual_video_insert.sql
-- Description:
--   Memberikan izin INSERT pada tabel public.kajian_videos kepada authenticated
--   admin (claim role = 'kajian_admin') agar dapat menambah dan menguji video
--   secara manual langsung dari panel admin.
-- ==============================================================================

-- 1. Berikan izin INSERT ke role authenticated
GRANT INSERT ON public.kajian_videos TO authenticated;

-- 2. Buat Kebijakan RLS INSERT untuk Admin
DROP POLICY IF EXISTS "Admins can insert kajian videos" ON public.kajian_videos;

CREATE POLICY "Admins can insert kajian videos"
ON public.kajian_videos
FOR INSERT
TO authenticated
WITH CHECK (
  coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '') = 'kajian_admin'
  OR coalesce(auth.jwt() -> 'user_metadata' ->> 'role', '') = 'kajian_admin'
);
