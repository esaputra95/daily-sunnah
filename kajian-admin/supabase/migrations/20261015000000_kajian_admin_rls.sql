-- ==============================================================================
-- Migration: 20261015000000_kajian_admin_rls.sql
-- Project: Daily Sunnah - Katalog Video Kajian Admin Panel
-- Description:
--   1. Mempertahankan kebijakan publik: publik (anon & authenticated) hanya
--      dapat membaca baris kajian dengan review_status = 'approved'.
--   2. Memberikan admin (auth claim app_metadata.role = 'kajian_admin')
--      akses BACA (SELECT) ke SEMUA status (pending, approved, rejected).
--   3. Membatasi hak UPDATE admin HANYA pada 4 kolom editorial:
--      category, content_type, review_status, dan reviewed_at.
--   4. Mencegah akses INSERT dan DELETE melalui panel atau client umum.
-- ==============================================================================

-- 1. Pastikan Row Level Security (RLS) diaktifkan
ALTER TABLE public.kajian_videos ENABLE ROW LEVEL SECURITY;

-- 2. Atur Hak Akses (Table Grants) di PostgreSQL
-- Cabut semua izin modifikasi penuh terlebih dahulu untuk menghindari celah keamanan
REVOKE ALL ON public.kajian_videos FROM anon, authenticated;

-- Berikan izin SELECT kepada publik (anonim & user login)
GRANT SELECT ON public.kajian_videos TO anon, authenticated;

-- Berikan izin UPDATE HANYA untuk 4 kolom editorial ke authenticated user
-- (Eksekusi update tetap disaring oleh RLS policy di bawah)
GRANT UPDATE (category, content_type, review_status, reviewed_at) ON public.kajian_videos TO authenticated;

-- CATATAN: INSERT dan DELETE sengaja TIDAK di-grant ke anon atau authenticated.
-- Katalog video disinkronkan melalui ingestor backend / pipeline terpisah.


-- 3. Kebijakan RLS (Policies)

-- Kebijakan A: Akses Baca Publik (Anon & User Biasa)
-- Publik dan aplikasi Android hanya dapat membaca video yang berstatus 'approved'
DROP POLICY IF EXISTS "Public can view approved kajian videos" ON public.kajian_videos;
DROP POLICY IF EXISTS "Allow public read access for approved videos" ON public.kajian_videos;
CREATE POLICY "Public can view approved kajian videos"
ON public.kajian_videos
FOR SELECT
TO anon, authenticated
USING (
  review_status = 'approved'
);

-- Kebijakan B: Akses Baca Admin
-- User dengan claim terpercaya app_metadata.role = 'kajian_admin' dapat melihat semua status
DROP POLICY IF EXISTS "Admins can view all kajian videos" ON public.kajian_videos;
CREATE POLICY "Admins can view all kajian videos"
ON public.kajian_videos
FOR SELECT
TO authenticated
USING (
  (auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin'
);

-- Kebijakan C: Akses Update Editorial Admin
-- Hanya user dengan role 'kajian_admin' yang dapat melakukan update
DROP POLICY IF EXISTS "Admins can update editorial fields" ON public.kajian_videos;
CREATE POLICY "Admins can update editorial fields"
ON public.kajian_videos
FOR UPDATE
TO authenticated
USING (
  (auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin'
)
WITH CHECK (
  (auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin'
  AND category IN ('Akidah', 'Fiqih', 'Muamalah', 'Sirah', 'Tazkiyatun Nufus')
  AND content_type IN ('kajian', 'short')
  AND review_status IN ('pending', 'approved', 'rejected')
);

-- ==============================================================================
-- CARA PEMBERIAN ROLE ADMIN (Oleh Pemilik Project di Supabase Dashboard / SQL Editor)
--
-- Jalankan perintah berikut dengan mengganti email admin:
--
-- UPDATE auth.users
-- SET raw_app_meta_data = raw_app_meta_data || jsonb_build_object('role', 'kajian_admin')
-- WHERE email = 'admin@example.com';
--
-- Setelah perintah di atas dijalankan, user admin harus LOGOUT dan LOGIN KEMBALI
-- agar Supabase Auth menerbitkan token JWT baru yang memuat claim role tersebut.
-- ==============================================================================
