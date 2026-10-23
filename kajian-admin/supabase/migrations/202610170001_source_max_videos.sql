-- 202610170001_source_max_videos.sql
-- Menambahkan batas maksimal video (max_videos) yang diambil per sumber YouTube (channel / playlist).

ALTER TABLE public.kajian_sources
ADD COLUMN IF NOT EXISTS max_videos integer NOT NULL DEFAULT 10
CHECK (max_videos BETWEEN 1 AND 200);
