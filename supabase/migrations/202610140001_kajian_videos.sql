create table if not exists public.kajian_videos (
    id uuid primary key default gen_random_uuid(),
    youtube_video_id text not null unique
        check (youtube_video_id ~ '^[A-Za-z0-9_-]{11}$'),
    title text not null check (length(trim(title)) > 0),
    description text not null default '',
    channel_name text not null,
    channel_id text not null,
    category text not null check (category in (
        'Akidah', 'Fiqih', 'Muamalah', 'Sirah', 'Tazkiyatun Nufus'
    )),
    content_type text not null check (content_type in ('kajian', 'short')),
    thumbnail_url text,
    duration_seconds integer not null default 0 check (duration_seconds >= 0),
    published_at timestamptz,
    review_status text not null default 'pending'
        check (review_status in ('pending', 'approved', 'rejected')),
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (thumbnail_url is null or thumbnail_url like 'https://i.ytimg.com/%' or thumbnail_url like 'https://img.youtube.com/%')
);

create index if not exists kajian_videos_approved_feed_idx
    on public.kajian_videos (published_at desc)
    where review_status = 'approved';

create or replace function public.set_kajian_video_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists kajian_videos_updated_at on public.kajian_videos;
create trigger kajian_videos_updated_at
    before update on public.kajian_videos
    for each row execute function public.set_kajian_video_updated_at();

alter table public.kajian_videos enable row level security;
revoke all on public.kajian_videos from anon, authenticated;
grant select on public.kajian_videos to anon, authenticated;
grant select, insert, update, delete on public.kajian_videos to service_role;

drop policy if exists "public read approved kajian videos" on public.kajian_videos;
create policy "public read approved kajian videos"
    on public.kajian_videos
    for select
    to anon, authenticated
    using (review_status = 'approved');
