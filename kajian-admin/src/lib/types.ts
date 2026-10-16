export type KajianCategory =
  | 'Akidah'
  | 'Fiqih'
  | 'Muamalah'
  | 'Sirah'
  | 'Tazkiyatun Nufus';

export type KajianContentType = 'kajian' | 'short';

export type ReviewStatus = 'pending' | 'approved' | 'rejected';

export type KajianSourceKind = 'channel' | 'playlist';

export type KajianFeedSortOrder = 'newest' | 'oldest' | 'duration_desc' | 'duration_asc';

export type IngestionScheduleMode = 'interval' | 'daily';

export interface KajianSource {
  id: string;
  source_kind: KajianSourceKind;
  source_identifier: string;
  display_name: string;
  category: KajianCategory;
  content_type: KajianContentType;
  initial_review_status?: ReviewStatus;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface KajianIngestionSettings {
  id: string;
  schedule_mode: IngestionScheduleMode;
  interval_minutes: number | null;
  daily_time: string;
  timezone: string;
  default_review_status?: ReviewStatus;
  last_run_at: string | null;
  next_run_at: string;
  run_requested_at: string | null;
}

export interface KajianIngestionRun {
  id: string;
  trigger_type: 'schedule' | 'manual';
  status: 'running' | 'success' | 'failed';
  started_at: string;
  finished_at: string | null;
  videos_seen: number;
  candidates_written: number;
  removed_count: number;
  error_message: string | null;
}

export interface KajianFeedSettings {
  id: string;
  sort_order: KajianFeedSortOrder;
  content_types: KajianContentType[];
  min_duration_seconds: number | null;
  max_duration_seconds: number | null;
  updated_at: string;
}

export interface KajianVideo {
  id: string | number;
  youtube_video_id: string;
  title: string;
  description: string | null;
  channel_name: string | null;
  channel_id: string | null;
  category: KajianCategory;
  content_type: KajianContentType;
  thumbnail_url: string | null;
  duration_seconds: number | null;
  published_at: string | null;
  review_status: ReviewStatus;
  reviewed_at: string | null;
  created_at: string;
  updated_at: string;
}

export const VALID_CATEGORIES: KajianCategory[] = [
  'Akidah',
  'Fiqih',
  'Muamalah',
  'Sirah',
  'Tazkiyatun Nufus',
];

export const VALID_CONTENT_TYPES: KajianContentType[] = ['kajian', 'short'];

export const VALID_REVIEW_STATUSES: ReviewStatus[] = [
  'pending',
  'approved',
  'rejected',
];

export interface StatusCounts {
  pending: number;
  approved: number;
  rejected: number;
  all: number;
}
