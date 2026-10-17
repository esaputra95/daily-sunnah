#!/usr/bin/env python3
"""Import configured YouTube sources into Supabase for editorial review."""

from __future__ import annotations

import json
import os
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime, time as day_time, timedelta, timezone
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError


YOUTUBE_API = "https://www.googleapis.com/youtube/v3"
VALID_CATEGORIES = {"Akidah", "Fiqih", "Muamalah", "Sirah", "Tazkiyatun Nufus"}
VALID_CONTENT_TYPES = {"kajian", "short"}
VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
CHANNEL_ID = re.compile(r"^UC[A-Za-z0-9_-]{22}$")
ISO_DURATION = re.compile(
    r"^P(?:(?P<days>\d+)D)?(?:T(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?(?:(?P<seconds>\d+)S)?)?$"
)


@dataclass(frozen=True)
class Source:
    source_kind: str
    source_identifier: str
    display_name: str
    category: str
    content_type: str


def parse_duration(value: str) -> int:
    match = ISO_DURATION.fullmatch(value)
    if not match:
        raise ValueError(f"Invalid YouTube duration: {value}")
    parts = {name: int(match.group(name) or 0) for name in ("days", "hours", "minutes", "seconds")}
    return parts["days"] * 86_400 + parts["hours"] * 3_600 + parts["minutes"] * 60 + parts["seconds"]


def parse_sources(rows: list[dict[str, Any]]) -> list[Source]:
    sources: list[Source] = []
    seen: set[tuple[str, str]] = set()
    for item in rows:
        if not isinstance(item, dict):
            raise ValueError("Every source must be an object")
        kind = str(item.get("source_kind", "")).strip().lower()
        identifier = str(item.get("source_identifier", "")).strip()
        display_name = str(item.get("display_name", "")).strip()
        category = str(item.get("category", "")).strip()
        content_type = str(item.get("content_type", "")).strip().lower()
        if kind not in {"channel", "playlist"}:
            raise ValueError("source_kind must be channel or playlist")
        if not identifier or not display_name:
            raise ValueError("Every source needs a source identifier and display name")
        if kind == "channel" and not (CHANNEL_ID.fullmatch(identifier) or identifier.startswith("@")):
            raise ValueError(f"Invalid YouTube channel identifier: {identifier}")
        if kind == "playlist" and not re.fullmatch(r"[A-Za-z0-9_-]{10,100}", identifier):
            raise ValueError(f"Invalid YouTube playlist identifier: {identifier}")
        if category not in VALID_CATEGORIES:
            raise ValueError(f"Unsupported category for source {display_name}: {category}")
        if content_type not in VALID_CONTENT_TYPES:
            raise ValueError("content_type must be kajian or short")
        key = (kind, identifier)
        if key in seen:
            raise ValueError(f"Duplicate source: {kind} {identifier}")
        seen.add(key)
        sources.append(Source(kind, identifier, display_name, category, content_type))
    return sources


def parse_api_datetime(value: str | None) -> datetime | None:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)


def is_run_due(settings: dict[str, Any], now: datetime, force: bool = False) -> bool:
    if force or settings.get("run_requested_at"):
        return True
    next_run = parse_api_datetime(settings.get("next_run_at"))
    return next_run is None or now.astimezone(timezone.utc) >= next_run


def calculate_next_run(settings: dict[str, Any], now: datetime) -> datetime:
    mode = settings.get("schedule_mode", "daily")
    if mode == "interval":
        minutes = int(settings.get("interval_minutes") or 1440)
        if minutes < 15:
            raise ValueError("Ingestion interval must be at least 15 minutes")
        return now.astimezone(timezone.utc) + timedelta(minutes=minutes)
    if mode != "daily":
        raise ValueError("schedule_mode must be interval or daily")

    timezone_name = settings.get("timezone") or "Asia/Jakarta"
    try:
        local_zone = ZoneInfo(timezone_name)
    except ZoneInfoNotFoundError as error:
        raise ValueError(f"Unsupported schedule timezone: {timezone_name}") from error
    raw_time = str(settings.get("daily_time") or "00:00")[:5]
    target_time = day_time.fromisoformat(raw_time)
    local_now = now.astimezone(local_zone)
    target_local = datetime.combine(local_now.date(), target_time, tzinfo=local_zone)
    if target_local <= local_now:
        target_local += timedelta(days=1)
    return target_local.astimezone(timezone.utc)


class YouTubeDataClient:
    def __init__(self, api_key: str):
        self.api_key = api_key

    def get(self, method: str, **params: str) -> dict[str, Any]:
        query = urlencode({**params, "key": self.api_key})
        request = Request(f"{YOUTUBE_API}/{method}?{query}", headers={"Accept": "application/json"})
        return request_json(request, "YouTube")

    def channel_uploads_playlist(self, identifier: str) -> str:
        params = {"part": "contentDetails", "maxResults": "1"}
        if CHANNEL_ID.fullmatch(identifier):
            params["id"] = identifier
        else:
            params["forHandle"] = identifier
        result = self.get("channels", **params)
        items = result.get("items", [])
        if not items:
            raise ValueError(f"YouTube channel not found: {identifier}")
        uploads = items[0].get("contentDetails", {}).get("relatedPlaylists", {}).get("uploads", "")
        if not uploads:
            raise ValueError(f"YouTube channel has no uploads playlist: {identifier}")
        return uploads

    def playlist_video_ids(self, playlist_id: str) -> list[str]:
        found: list[str] = []
        page_token: str | None = None
        while True:
            params = {"part": "contentDetails", "playlistId": playlist_id, "maxResults": "50"}
            if page_token:
                params["pageToken"] = page_token
            result = self.get("playlistItems", **params)
            for item in result.get("items", []):
                video_id = item.get("contentDetails", {}).get("videoId", "")
                if VIDEO_ID.fullmatch(video_id):
                    found.append(video_id)
            page_token = result.get("nextPageToken")
            if not page_token:
                return found

    def source_video_ids(self, source: Source) -> list[str]:
        playlist_id = (
            self.channel_uploads_playlist(source.source_identifier)
            if source.source_kind == "channel"
            else source.source_identifier
        )
        return self.playlist_video_ids(playlist_id)

    def videos(self, video_ids: list[str]) -> dict[str, dict[str, Any]]:
        result: dict[str, dict[str, Any]] = {}
        for start in range(0, len(video_ids), 50):
            batch = video_ids[start : start + 50]
            response = self.get("videos", part="snippet,contentDetails,status", id=",".join(batch), maxResults="50")
            for item in response.get("items", []):
                result[item["id"]] = item
        return result


def request_json(request: Request, service_name: str) -> Any:
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            with urlopen(request, timeout=25) as response:
                payload = response.read()
                return json.loads(payload.decode("utf-8")) if payload else None
        except HTTPError as error:
            last_error = error
            if error.code not in {408, 429} and error.code < 500:
                raise RuntimeError(f"{service_name} request failed (HTTP {error.code})") from None
        except (URLError, TimeoutError) as error:
            last_error = error
        if attempt < 2:
            time.sleep(2**attempt)
    if isinstance(last_error, HTTPError):
        raise RuntimeError(f"{service_name} request failed after retries (HTTP {last_error.code})") from None
    raise RuntimeError(f"{service_name} network request failed after retries") from None


class SupabaseClient:
    def __init__(self, base_url: str, secret_key: str):
        self.base_url = base_url.rstrip("/")
        self.rest_url = f"{self.base_url}/rest/v1"
        self.secret_key = secret_key

    def request(
        self,
        method: str,
        table: str,
        query: dict[str, str] | None = None,
        body: Any | None = None,
        prefer: str | None = None,
    ) -> Any:
        url = f"{self.rest_url}/{table}"
        if query:
            url += "?" + urlencode(query, safe=",()[]")
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers = {"apikey": self.secret_key, "Authorization": f"Bearer {self.secret_key}", "Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if prefer:
            headers["Prefer"] = prefer
        return request_json(Request(url, data=data, headers=headers, method=method), "Supabase")

    def load_settings(self) -> dict[str, Any]:
        rows = self.request("GET", "kajian_ingestion_settings", {"id": "eq.default", "select": "*", "limit": "1"})
        if not rows:
            raise ValueError("kajian_ingestion_settings default row is missing; apply the Supabase migration first")
        return rows[0]

    def load_sources(self) -> list[Source]:
        rows = self.request(
            "GET",
            "kajian_sources",
            {"select": "source_kind,source_identifier,display_name,category,content_type", "is_active": "eq.true", "order": "created_at.asc"},
        )
        return parse_sources(rows or [])

    def all_video_records(self) -> dict[str, dict[str, Any]]:
        records: list[dict[str, Any]] = []
        offset = 0
        while True:
            page = self.request(
                "GET",
                "kajian_videos",
                {
                    "select": "youtube_video_id,category,content_type,review_status,reviewed_at",
                    "order": "created_at.asc",
                    "limit": "1000",
                    "offset": str(offset),
                },
            )
            records.extend(page or [])
            if len(page or []) < 1000:
                break
            offset += len(page)
        return {record["youtube_video_id"]: record for record in records}

    def mark_run_started(self, settings: dict[str, Any], trigger_type: str, now: datetime) -> str:
        run = self.request(
            "POST",
            "kajian_ingestion_runs",
            body={"trigger_type": trigger_type, "status": "running", "started_at": now.isoformat()},
            prefer="return=representation",
        )
        run_id = run[0]["id"]
        next_run = calculate_next_run(settings, now)
        update_query = {"id": "eq.default"}
        update_body: dict[str, Any] = {
            "last_run_at": now.isoformat(),
            "next_run_at": next_run.isoformat(),
        }
        requested_at = settings.get("run_requested_at")
        if requested_at:
            update_query["run_requested_at"] = f"eq.{requested_at}"
            update_body["run_requested_at"] = None
            update_body["run_requested_by"] = None
        self.request(
            "PATCH",
            "kajian_ingestion_settings",
            update_query,
            update_body,
            prefer="return=minimal",
        )
        return run_id

    def finish_run(
        self,
        run_id: str,
        status: str,
        videos_seen: int = 0,
        candidates_written: int = 0,
        removed_count: int = 0,
        error_message: str | None = None,
    ) -> None:
        self.request(
            "PATCH",
            "kajian_ingestion_runs",
            {"id": f"eq.{run_id}"},
            {
                "status": status,
                "finished_at": datetime.now(timezone.utc).isoformat(),
                "videos_seen": videos_seen,
                "candidates_written": candidates_written,
                "removed_count": removed_count,
                "error_message": error_message[:500] if error_message else None,
            },
            prefer="return=minimal",
        )

    def upsert_videos(self, rows: list[dict[str, Any]]) -> None:
        url_query = {"on_conflict": "youtube_video_id"}
        for start in range(0, len(rows), 100):
            self.request(
                "POST",
                "kajian_videos",
                url_query,
                rows[start : start + 100],
                prefer="resolution=merge-duplicates,return=minimal",
            )

    def delete_video(self, video_id: str) -> None:
        self.request("DELETE", "kajian_videos", {"youtube_video_id": f"eq.{video_id}"}, prefer="return=minimal")


def ingest_rows(
    videos: dict[str, dict[str, Any]],
    source_by_video: dict[str, Source],
    existing_records: dict[str, dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[str]]:
    rows: list[dict[str, Any]] = []
    unavailable: list[str] = []
    for video_id, item in videos.items():
        snippet = item.get("snippet", {})
        status = item.get("status", {})
        if status.get("privacyStatus") != "public" or status.get("embeddable") is not True:
            unavailable.append(video_id)
            continue
        source = source_by_video.get(video_id)
        thumbnails = snippet.get("thumbnails", {})
        title = snippet.get("title", "")
        if not title.strip():
            unavailable.append(video_id)
            continue
        thumbnail = next(
            (thumbnails[name]["url"] for name in ("maxres", "standard", "high", "medium", "default") if name in thumbnails),
            None,
        )
        row: dict[str, Any] = {
            "youtube_video_id": video_id,
            "title": title,
            "description": snippet.get("description", ""),
            "channel_name": snippet.get("channelTitle", "YouTube"),
            "channel_id": snippet.get("channelId", ""),
            "thumbnail_url": thumbnail,
            "duration_seconds": parse_duration(item.get("contentDetails", {}).get("duration", "PT0S")),
            "published_at": snippet.get("publishedAt"),
        }
        existing = existing_records.get(video_id)
        if existing:
            row.update({
                "category": existing["category"],
                "content_type": existing["content_type"],
                "review_status": existing["review_status"],
                "reviewed_at": existing["reviewed_at"],
            })
        elif source:
            row["category"] = source.category
            row["content_type"] = source.content_type
            row["review_status"] = "pending"
            row["reviewed_at"] = None
        else:
            # Existing metadata can be refreshed; an unseen video must belong to a source.
            continue
        rows.append(row)
    return rows, unavailable


def collect_source_map(youtube: YouTubeDataClient, sources: list[Source]) -> dict[str, Source]:
    source_by_video: dict[str, Source] = {}
    source_rank: dict[str, int] = {}
    for source in sources:
        for video_id in youtube.source_video_ids(source):
            existing = source_by_video.get(video_id)
            if existing and (existing.category, existing.content_type) != (source.category, source.content_type):
                raise ValueError(f"Video {video_id} appears in sources with conflicting default classification")
            rank = 2 if source.source_kind == "playlist" else 1
            if not existing or rank > source_rank[video_id]:
                source_by_video[video_id] = source
                source_rank[video_id] = rank
    return source_by_video


def run(force: bool = False) -> int:
    youtube_key = os.environ.get("YOUTUBE_DATA_API_KEY", "")
    supabase_url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    secret_key = os.environ.get("SUPABASE_SECRET_KEY", "")
    if not youtube_key or not supabase_url or not secret_key:
        raise ValueError("Set YOUTUBE_DATA_API_KEY, SUPABASE_URL, and SUPABASE_SECRET_KEY in CI secrets")
    if not supabase_url.startswith("https://"):
        raise ValueError("SUPABASE_URL must use HTTPS")

    now = datetime.now(timezone.utc)
    supabase = SupabaseClient(supabase_url, secret_key)
    settings = supabase.load_settings()
    manual_request = bool(settings.get("run_requested_at"))
    if not is_run_due(settings, now, force=force):
        print(f"Ingestion not due; next run at {settings.get('next_run_at')}.")
        return 0

    trigger_type = "manual" if force or manual_request else "schedule"
    run_id = supabase.mark_run_started(settings, trigger_type, now)
    videos_seen = candidates_written = removed_count = 0
    try:
        sources = supabase.load_sources()
        if not sources:
            print("No active YouTube sources; refreshing existing catalog metadata only.")

        youtube = YouTubeDataClient(youtube_key)
        source_by_video = collect_source_map(youtube, sources)
        existing_records = supabase.all_video_records()
        all_ids = sorted(set(existing_records) | set(source_by_video))
        if not all_ids:
            print("No active sources or existing videos; ingestion completed with an empty catalog.")
        else:
            metadata = youtube.videos(all_ids)
            videos_seen = len(metadata)
            rows, unavailable = ingest_rows(metadata, source_by_video, existing_records)
            missing = set(all_ids) - set(metadata)
            unavailable.extend(sorted(missing))
            supabase.upsert_videos(rows)
            for video_id in sorted(set(unavailable)):
                supabase.delete_video(video_id)
            candidates_written = len(rows)
            removed_count = len(set(unavailable))
        supabase.finish_run(run_id, "success", videos_seen, candidates_written, removed_count)
        print(f"Refreshed {candidates_written} videos; removed {removed_count} unavailable or non-embeddable rows.")
        print("New videos stay pending until an editor approves them.")
        return 0
    except Exception as error:
        safe_message = str(error)
        # Never persist a request URL, since YouTube API keys are query parameters.
        safe_message = re.sub(r"https?://\S+", "external API URL", safe_message)[:500]
        try:
            supabase.finish_run(run_id, "failed", videos_seen, candidates_written, removed_count, safe_message)
        except Exception:
            print("Could not update ingestion run status in Supabase.", file=sys.stderr)
        print(f"Catalog ingestion failed: {safe_message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    try:
        raise SystemExit(run(force=os.environ.get("INGEST_FORCE", "false").lower() == "true"))
    except (HTTPError, URLError, TimeoutError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"Catalog ingestion failed: {getattr(error, 'reason', error)}", file=sys.stderr)
        raise SystemExit(1)
