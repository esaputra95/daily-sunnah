-- Durable, per-source YouTube archive backfill state. The existing daily schedule is unchanged.
ALTER TABLE public.kajian_sources
    ADD COLUMN IF NOT EXISTS backfill_status text NOT NULL DEFAULT 'idle',
    ADD COLUMN IF NOT EXISTS backfill_page_token text,
    ADD COLUMN IF NOT EXISTS backfill_pages_processed integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backfill_videos_processed integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backfill_last_video_id text,
    ADD COLUMN IF NOT EXISTS backfill_error text,
    ADD COLUMN IF NOT EXISTS backfill_requested_at timestamptz,
    ADD COLUMN IF NOT EXISTS backfill_updated_at timestamptz,
    ADD COLUMN IF NOT EXISTS backfill_completed_at timestamptz;

ALTER TABLE public.kajian_sources
    DROP CONSTRAINT IF EXISTS kajian_sources_backfill_status_check,
    ADD CONSTRAINT kajian_sources_backfill_status_check
        CHECK (backfill_status IN ('idle', 'queued', 'running', 'paused', 'completed', 'failed'));

ALTER TABLE public.kajian_sources
    DROP CONSTRAINT IF EXISTS kajian_sources_backfill_pages_processed_check,
    ADD CONSTRAINT kajian_sources_backfill_pages_processed_check CHECK (backfill_pages_processed >= 0);

ALTER TABLE public.kajian_sources
    DROP CONSTRAINT IF EXISTS kajian_sources_backfill_videos_processed_check,
    ADD CONSTRAINT kajian_sources_backfill_videos_processed_check CHECK (backfill_videos_processed >= 0);

ALTER TABLE public.kajian_ingestion_runs
    DROP CONSTRAINT IF EXISTS kajian_ingestion_runs_trigger_type_check,
    ADD CONSTRAINT kajian_ingestion_runs_trigger_type_check
        CHECK (trigger_type IN ('schedule', 'manual', 'backfill'));
