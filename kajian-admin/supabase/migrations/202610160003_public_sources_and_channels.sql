-- 202610160003_public_sources_and_channels.sql
-- Memberikan akses baca publik (anon) ke sumber aktif (kajian_sources)
-- dan membuat view public.kajian_channels untuk daftar channel unik yang sudah disetujui (approved).

-- 1. Berikan hak akses SELECT ke role anon untuk kajian_sources
GRANT SELECT ON public.kajian_sources TO anon;

-- 2. Kebijakan RLS agar pengguna publik (anon) hanya dapat membaca sumber yang aktif (is_active = true)
DROP POLICY IF EXISTS "Public can view active sources" ON public.kajian_sources;
CREATE POLICY "Public can view active sources"
ON public.kajian_sources
FOR SELECT
TO anon
USING (is_active = true);

-- 3. View untuk daftar channel unik dari video yang sudah disetujui (approved)
-- View ini menggunakan security_invoker = true agar mematuhi RLS kajian_videos
CREATE OR REPLACE VIEW public.kajian_channels WITH (security_invoker = true) AS
SELECT DISTINCT
    channel_name,
    channel_id
FROM public.kajian_videos
WHERE review_status = 'approved'
  AND channel_name IS NOT NULL
  AND trim(channel_name) <> '';

GRANT SELECT ON public.kajian_channels TO anon, authenticated;
