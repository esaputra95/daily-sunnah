-- Public mobile feed needs approved channel names for its channel filter.

grant select on public.kajian_sources to anon;

drop policy if exists "Public read active kajian channels" on public.kajian_sources;
drop policy if exists "Public can view active sources" on public.kajian_sources;
create policy "Public can view active sources" on public.kajian_sources
    for select to anon
    using (is_active = true);

create or replace view public.kajian_channels with (security_invoker = true) as
select distinct
    channel_name,
    channel_id
from public.kajian_videos
where review_status = 'approved'
  and channel_name is not null
  and trim(channel_name) <> '';

grant select on public.kajian_channels to anon, authenticated;
