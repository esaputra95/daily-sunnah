-- Admin-managed YouTube ingestion, schedule, run history, and public feed settings.

create table if not exists public.kajian_sources (
    id uuid primary key default gen_random_uuid(),
    source_kind text not null check (source_kind in ('channel', 'playlist')),
    source_identifier text not null check (length(trim(source_identifier)) > 0),
    display_name text not null check (length(trim(display_name)) > 0),
    category text not null check (category in (
        'Akidah', 'Fiqih', 'Muamalah', 'Sirah', 'Tazkiyatun Nufus'
    )),
    content_type text not null check (content_type in ('kajian', 'short')),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (source_kind, source_identifier)
);

create table if not exists public.kajian_ingestion_settings (
    id text primary key default 'default' check (id = 'default'),
    schedule_mode text not null default 'daily' check (schedule_mode in ('interval', 'daily')),
    interval_minutes integer check (interval_minutes between 15 and 43200),
    daily_time time not null default time '00:00',
    timezone text not null default 'Asia/Jakarta',
    last_run_at timestamptz,
    next_run_at timestamptz not null default now(),
    run_requested_at timestamptz,
    run_requested_by uuid references auth.users(id) on delete set null,
    updated_at timestamptz not null default now(),
    check (
        (schedule_mode = 'interval' and interval_minutes is not null)
        or (schedule_mode = 'daily' and interval_minutes is null)
    )
);

create table if not exists public.kajian_ingestion_runs (
    id uuid primary key default gen_random_uuid(),
    trigger_type text not null check (trigger_type in ('schedule', 'manual')),
    status text not null check (status in ('running', 'success', 'failed')),
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    videos_seen integer not null default 0 check (videos_seen >= 0),
    candidates_written integer not null default 0 check (candidates_written >= 0),
    removed_count integer not null default 0 check (removed_count >= 0),
    error_message text
);

create index if not exists kajian_ingestion_runs_started_idx
    on public.kajian_ingestion_runs (started_at desc);

create table if not exists public.kajian_feed_settings (
    id text primary key default 'default' check (id = 'default'),
    sort_order text not null default 'newest'
        check (sort_order in ('newest', 'oldest', 'duration_desc', 'duration_asc')),
    content_types text[] not null default array['kajian', 'short']::text[],
    min_duration_seconds integer check (min_duration_seconds is null or min_duration_seconds >= 0),
    max_duration_seconds integer check (max_duration_seconds is null or max_duration_seconds >= 0),
    updated_at timestamptz not null default now(),
    check (cardinality(content_types) > 0),
    check (content_types <@ array['kajian', 'short']::text[]),
    check (
        min_duration_seconds is null or max_duration_seconds is null
        or min_duration_seconds <= max_duration_seconds
    )
);

create or replace function public.set_kajian_config_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists kajian_sources_updated_at on public.kajian_sources;
create trigger kajian_sources_updated_at
    before update on public.kajian_sources
    for each row execute function public.set_kajian_config_updated_at();

drop trigger if exists kajian_ingestion_settings_updated_at on public.kajian_ingestion_settings;
create trigger kajian_ingestion_settings_updated_at
    before update on public.kajian_ingestion_settings
    for each row execute function public.set_kajian_config_updated_at();

drop trigger if exists kajian_feed_settings_updated_at on public.kajian_feed_settings;
create trigger kajian_feed_settings_updated_at
    before update on public.kajian_feed_settings
    for each row execute function public.set_kajian_config_updated_at();

insert into public.kajian_sources (source_kind, source_identifier, display_name, category, content_type)
values
    ('playlist', 'PLJn69VMQAr8oGkr6jEQqMTE1G5JsuA-od', 'Playlist Kajian — Tazkiyatun Nufus', 'Tazkiyatun Nufus', 'kajian'),
    ('playlist', 'PLJn69VMQAr8qJVVvT_NbxCMdMxUF2atxP', 'Playlist Shorts — Tazkiyatun Nufus', 'Tazkiyatun Nufus', 'short')
on conflict (source_kind, source_identifier) do nothing;

insert into public.kajian_ingestion_settings (id, schedule_mode, interval_minutes, daily_time, timezone)
values ('default', 'daily', null, time '00:00', 'Asia/Jakarta')
on conflict (id) do nothing;

insert into public.kajian_feed_settings (id, sort_order, content_types)
values ('default', 'newest', array['kajian', 'short']::text[])
on conflict (id) do nothing;

alter table public.kajian_sources enable row level security;
alter table public.kajian_ingestion_settings enable row level security;
alter table public.kajian_ingestion_runs enable row level security;
alter table public.kajian_feed_settings enable row level security;

revoke all on public.kajian_sources from anon, authenticated;
revoke all on public.kajian_ingestion_settings from anon, authenticated;
revoke all on public.kajian_ingestion_runs from anon, authenticated;
revoke all on public.kajian_feed_settings from anon, authenticated;

grant select, insert, update, delete on public.kajian_sources to authenticated;
grant select, update on public.kajian_ingestion_settings to authenticated;
grant select on public.kajian_ingestion_runs to authenticated;
grant select on public.kajian_feed_settings to anon, authenticated;
grant update on public.kajian_feed_settings to authenticated;

grant all on public.kajian_sources to service_role;
grant all on public.kajian_ingestion_settings to service_role;
grant all on public.kajian_ingestion_runs to service_role;
grant all on public.kajian_feed_settings to service_role;

drop policy if exists "Kajian admins manage sources" on public.kajian_sources;
create policy "Kajian admins manage sources" on public.kajian_sources
    for all to authenticated
    using ((auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin')
    with check ((auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin');

drop policy if exists "Kajian admins manage ingestion settings" on public.kajian_ingestion_settings;
create policy "Kajian admins manage ingestion settings" on public.kajian_ingestion_settings
    for all to authenticated
    using ((auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin')
    with check ((auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin');

drop policy if exists "Kajian admins read ingestion runs" on public.kajian_ingestion_runs;
create policy "Kajian admins read ingestion runs" on public.kajian_ingestion_runs
    for select to authenticated
    using ((auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin');

drop policy if exists "Public read kajian feed settings" on public.kajian_feed_settings;
create policy "Public read kajian feed settings" on public.kajian_feed_settings
    for select to anon, authenticated using (true);

drop policy if exists "Kajian admins update feed settings" on public.kajian_feed_settings;
create policy "Kajian admins update feed settings" on public.kajian_feed_settings
    for update to authenticated
    using ((auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin')
    with check ((auth.jwt() -> 'app_metadata' ->> 'role') = 'kajian_admin');
