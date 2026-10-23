-- 202610160004_ingestion_auto_approve_setting.sql
-- Menambahkan opsi status kurasi otomatis (auto-approve vs pending) pada pengaturan ingestion dan sumber YouTube.

-- 1. Tambah kolom default_review_status pada tabel kajian_ingestion_settings
ALTER TABLE public.kajian_ingestion_settings
ADD COLUMN IF NOT EXISTS default_review_status text NOT NULL DEFAULT 'pending'
CHECK (default_review_status IN ('pending', 'approved'));

-- 2. Tambah kolom initial_review_status pada tabel kajian_sources (per-sumber)
ALTER TABLE public.kajian_sources
ADD COLUMN IF NOT EXISTS initial_review_status text NOT NULL DEFAULT 'pending'
CHECK (initial_review_status IN ('pending', 'approved'));
