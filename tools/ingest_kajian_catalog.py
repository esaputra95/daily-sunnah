#!/usr/bin/env python3
"""Import YouTube playlist and channel metadata into Supabase as pending editorial items."""

from __future__ import annotations

import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
SOURCE_FILE = ROOT / "config" / "kajian-sources.json"
YOUTUBE_API = "https://www.googleapis.com/youtube/v3"
VALID_CATEGORIES = {"Akidah", "Fiqih", "Muamalah", "Sirah", "Tazkiyatun Nufus"}
VALID_CONTENT_TYPES = {"kajian", "short"}
VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")
ISO_DURATION = re.compile(
    r"^P(?:(?P<days>\d+)D)?(?:T(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?(?:(?P<seconds>\d+)S)?)?$"
)


class Source:
    def __init__(
        self,
        source_identifier: str,
        category: str,
        content_type: str,
        source_kind: str = "playlist",
        display_name: str = "",
        is_active: bool = True,
        initial_review_status: str = "pending",
        max_videos: int = 10,
    ):
        self.source_identifier = source_identifier.strip()
        self.category = category.strip()
        self.content_type = content_type.strip().lower()
        self.source_kind = source_kind.strip().lower()
        self.display_name = display_name.strip() or self.source_identifier
        self.is_active = is_active
        self.initial_review_status = initial_review_status.strip().lower() or "pending"
        try:
            val = int(max_videos)
            self.max_videos = max(1, min(val, 200))
        except (ValueError, TypeError):
            self.max_videos = 10

    @property
    def playlist_id(self) -> str:
        return self.source_identifier

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Source):
            return False
        return (
            self.source_identifier == other.source_identifier
            and self.category == other.category
            and self.content_type == other.content_type
            and self.source_kind == other.source_kind
            and self.is_active == other.is_active
            and self.initial_review_status == other.initial_review_status
            and self.max_videos == other.max_videos
        )

    def __hash__(self) -> int:
        return hash((self.source_kind, self.source_identifier, self.category, self.content_type, self.is_active, self.initial_review_status, self.max_videos))

    def __repr__(self) -> str:
        return f"Source({self.source_kind}:{self.source_identifier}, category={self.category}, type={self.content_type}, status={self.initial_review_status}, max={self.max_videos})"


def parse_duration(value: str) -> int:
    match = ISO_DURATION.fullmatch(value)
    if not match:
        raise ValueError(f"Invalid YouTube duration: {value}")
    parts = {name: int(match.group(name) or 0) for name in ("days", "hours", "minutes", "seconds")}
    return parts["days"] * 86_400 + parts["hours"] * 3_600 + parts["minutes"] * 60 + parts["seconds"]


def load_sources(path: Path = SOURCE_FILE) -> list[Source]:
    if not path.is_file():
        return []
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError("kajian-sources.json must contain a JSON array")
    sources: list[Source] = []
    seen: set[tuple[str, str]] = set()
    for item in raw:
        if not isinstance(item, dict):
            raise ValueError("Every source must be an object")
        kind = str(item.get("source_kind", "playlist")).strip().lower()
        playlist_id = str(item.get("playlist_id", item.get("source_identifier", ""))).strip()
        category = str(item.get("category", "")).strip()
        content_type = str(item.get("content_type", "")).strip().lower()
        display_name = str(item.get("display_name", "")).strip()
        is_active = bool(item.get("is_active", True))
        initial_review_status = str(item.get("initial_review_status", "pending")).strip().lower()
        max_videos = int(item.get("max_videos", 10))
        if not playlist_id:
            raise ValueError("Every source needs playlist_id or source_identifier")
        if category not in VALID_CATEGORIES:
            raise ValueError(f"Unsupported category for source {playlist_id}: {category}")
        if content_type not in VALID_CONTENT_TYPES:
            raise ValueError(f"content_type must be one of {sorted(VALID_CONTENT_TYPES)}")
        key = (kind, playlist_id)
        if key in seen:
            raise ValueError(f"Duplicate source: {playlist_id}")
        seen.add(key)
        sources.append(Source(
            source_identifier=playlist_id,
            category=category,
            content_type=content_type,
            source_kind=kind,
            display_name=display_name or playlist_id,
            is_active=is_active,
            initial_review_status=initial_review_status,
            max_videos=max_videos,
        ))
    return sources


def calculate_next_run(settings: dict[str, Any] | None, current_time: datetime | None = None) -> str:
    now_utc = current_time or datetime.now(timezone.utc)
    if not settings:
        return (now_utc + timedelta(days=1)).isoformat()

    mode = settings.get("schedule_mode", "daily")
    if mode == "interval":
        interval = int(settings.get("interval_minutes") or 1440)
        return (now_utc + timedelta(minutes=interval)).isoformat()

    # Daily mode in Asia/Jakarta (UTC+7)
    daily_time_str = str(settings.get("daily_time", "00:00"))[:5]
    try:
        hour, minute = map(int, daily_time_str.split(":"))
    except Exception:
        hour, minute = 0, 0

    jakarta_offset = timedelta(hours=7)
    jakarta_now = now_utc + jakarta_offset
    target_jakarta = jakarta_now.replace(hour=hour, minute=minute, second=0, microsecond=0)
    if target_jakarta <= jakarta_now:
        target_jakarta += timedelta(days=1)

    target_utc = target_jakarta - jakarta_offset
    return target_utc.isoformat()


def should_run(settings: dict[str, Any] | None, force: bool = False) -> tuple[bool, str]:
    if force:
        return True, "manual"
    if not settings:
        return True, "schedule"

    # 1. Manual run requested via panel ("Cek sekarang")
    if settings.get("run_requested_at"):
        return True, "manual"

    # 2. Check next scheduled time
    next_run_str = settings.get("next_run_at")
    if not next_run_str:
        return True, "schedule"

    try:
        next_run = datetime.fromisoformat(next_run_str.replace("Z", "+00:00"))
        now_utc = datetime.now(timezone.utc)
        if now_utc >= next_run:
            return True, "schedule"
    except Exception:
        return True, "schedule"

    return False, "none"


class YouTubeDataClient:
    def __init__(self, api_key: str):
        self.api_key = api_key

    def get(self, method: str, **params: str) -> dict[str, Any]:
        query = urlencode({**params, "key": self.api_key})
        request = Request(f"{YOUTUBE_API}/{method}?{query}", headers={"Accept": "application/json"})
        with urlopen(request, timeout=25) as response:
            return json.loads(response.read().decode("utf-8"))

    def resolve_channel_uploads_playlist(self, identifier: str) -> str:
        clean_id = identifier.strip()
        if clean_id.startswith("UC") and len(clean_id) == 24:
            return f"UU{clean_id[2:]}"

        params = {"part": "contentDetails"}
        if clean_id.startswith("@"):
            params["forHandle"] = clean_id
        else:
            params["id"] = clean_id

        result = self.get("channels", **params)
        items = result.get("items", [])
        if not items:
            raise ValueError(f"Channel not found on YouTube for identifier: {identifier}")
        uploads = items[0].get("contentDetails", {}).get("relatedPlaylists", {}).get("uploads")
        if not uploads:
            raise ValueError(f"No uploads playlist found for channel: {identifier}")
        return uploads

    def playlist_video_ids(self, playlist_id: str, max_items: int = 150) -> list[str]:
        found: list[str] = []
        page_token: str | None = None
        while True:
            limit = min(50, max_items - len(found)) if max_items else 50
            params = {"part": "contentDetails", "playlistId": playlist_id, "maxResults": str(max(1, limit))}
            if page_token:
                params["pageToken"] = page_token
            result = self.get("playlistItems", **params)
            for item in result.get("items", []):
                video_id = item.get("contentDetails", {}).get("videoId", "")
                if VIDEO_ID.fullmatch(video_id):
                    found.append(video_id)
            page_token = result.get("nextPageToken")
            if not page_token or (max_items and len(found) >= max_items):
                return found[:max_items] if max_items else found

    def source_video_ids(self, source: Source) -> list[str]:
        if source.source_kind == "channel":
            uploads_playlist_id = self.resolve_channel_uploads_playlist(source.source_identifier)
            return self.playlist_video_ids(uploads_playlist_id, max_items=source.max_videos)
        else:
            return self.playlist_video_ids(source.source_identifier, max_items=source.max_videos)

    def videos(self, video_ids: list[str]) -> dict[str, dict[str, Any]]:
        result: dict[str, dict[str, Any]] = {}
        for start in range(0, len(video_ids), 50):
            batch = video_ids[start : start + 50]
            response = self.get("videos", part="snippet,contentDetails,status", id=",".join(batch), maxResults="50")
            for item in response.get("items", []):
                result[item["id"]] = item
        return result


class SupabaseClient:
    def __init__(self, base_url: str, secret_key: str):
        self.base_url = base_url.rstrip("/")
        self.rest_url = f"{self.base_url}/rest/v1"
        self.table_url = f"{self.rest_url}/kajian_videos"
        self.sources_url = f"{self.rest_url}/kajian_sources"
        self.settings_url = f"{self.rest_url}/kajian_ingestion_settings"
        self.runs_url = f"{self.rest_url}/kajian_ingestion_runs"
        self.secret_key = secret_key

    def request(
        self,
        method: str,
        url: str,
        body: Any | None = None,
        prefer: str | None = None,
    ) -> Any:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers = {
            "apikey": self.secret_key,
            "Authorization": f"Bearer {self.secret_key}",
            "Accept": "application/json",
        }
        if data is not None:
            headers["Content-Type"] = "application/json"
        if prefer:
            headers["Prefer"] = prefer
        request = Request(url, data=data, headers=headers, method=method)
        with urlopen(request, timeout=25) as response:
            payload = response.read()
            return json.loads(payload.decode("utf-8")) if payload else None

    def get_settings(self) -> dict[str, Any] | None:
        url = f"{self.settings_url}?id=eq.default&select=*"
        try:
            res = self.request("GET", url)
            if isinstance(res, list) and res:
                return res[0]
        except Exception:
            pass
        return None

    def get_active_sources(self) -> list[Source]:
        url = f"{self.sources_url}?is_active=eq.true&order=created_at.asc"
        try:
            res = self.request("GET", url)
            if not isinstance(res, list):
                return []
            sources: list[Source] = []
            seen: set[tuple[str, str]] = set()
            for item in res:
                kind = str(item.get("source_kind", "playlist")).strip().lower()
                ident = str(item.get("source_identifier", "")).strip()
                cat = str(item.get("category", "")).strip()
                ctype = str(item.get("content_type", "")).strip().lower()
                display = str(item.get("display_name", "")).strip()
                init_status = str(item.get("initial_review_status", "pending")).strip().lower()
                max_v = int(item.get("max_videos", 10))
                if not ident or cat not in VALID_CATEGORIES or ctype not in VALID_CONTENT_TYPES:
                    continue
                key = (kind, ident)
                if key in seen:
                    continue
                seen.add(key)
                sources.append(Source(
                    source_identifier=ident,
                    category=cat,
                    content_type=ctype,
                    source_kind=kind,
                    display_name=display or ident,
                    is_active=True,
                    initial_review_status=init_status,
                    max_videos=max_v,
                ))
            return sources
        except Exception:
            return []

    def is_run_in_progress(self, max_minutes: int = 30) -> bool:
        cutoff = (datetime.now(timezone.utc) - timedelta(minutes=max_minutes)).isoformat()
        url = f"{self.runs_url}?status=eq.running&started_at=gte.{cutoff}&order=started_at.desc&limit=1"
        try:
            res = self.request("GET", url)
            return bool(res and isinstance(res, list) and len(res) > 0)
        except Exception:
            return False

    def start_run(self, trigger_type: str) -> str | None:
        url = self.runs_url
        body = {
            "trigger_type": trigger_type,
            "status": "running",
            "started_at": datetime.now(timezone.utc).isoformat(),
        }
        try:
            res = self.request("POST", url, body=body, prefer="return=representation")
            if isinstance(res, list) and res:
                return res[0].get("id")
        except Exception as e:
            print(f"Warning: Failed to record ingestion run start: {e}", file=sys.stderr)
        return None

    def finish_run(
        self,
        run_id: str | None,
        status: str,
        videos_seen: int = 0,
        candidates_written: int = 0,
        removed_count: int = 0,
        error_message: str | None = None,
    ) -> None:
        if not run_id:
            return
        finished_at = datetime.now(timezone.utc).isoformat()
        url = f"{self.runs_url}?id=eq.{run_id}"
        body = {
            "status": status,
            "finished_at": finished_at,
            "videos_seen": videos_seen,
            "candidates_written": candidates_written,
            "removed_count": removed_count,
            "error_message": error_message,
        }
        try:
            self.request("PATCH", url, body=body, prefer="return=minimal")
        except Exception as e:
            print(f"Warning: Failed to update ingestion run status: {e}", file=sys.stderr)

    def update_settings(self, last_run_at: str, next_run_at: str, clear_requested: bool = True) -> None:
        url = f"{self.settings_url}?id=eq.default"
        body: dict[str, Any] = {
            "last_run_at": last_run_at,
            "next_run_at": next_run_at,
        }
        if clear_requested:
            body["run_requested_at"] = None
        try:
            self.request("PATCH", url, body=body, prefer="return=minimal")
        except Exception as e:
            print(f"Warning: Failed to update ingestion settings: {e}", file=sys.stderr)

    def clear_run_requested(self) -> None:
        url = f"{self.settings_url}?id=eq.default"
        try:
            self.request("PATCH", url, body={"run_requested_at": None}, prefer="return=minimal")
        except Exception as e:
            print(f"Warning: Failed to clear run_requested_at: {e}", file=sys.stderr)

    def all_video_records(self) -> dict[str, dict[str, Any]]:
        base_url = (
            f"{self.table_url}?select=youtube_video_id,category,content_type,review_status,reviewed_at"
            "&order=created_at.asc&limit=1000"
        )
        records: list[dict[str, Any]] = []
        offset = 0
        while True:
            page = self.request("GET", f"{base_url}&offset={offset}")
            records.extend(page)
            if len(page) < 1_000:
                break
            offset += len(page)
        return {record["youtube_video_id"]: record for record in records}

    def upsert(self, rows: list[dict[str, Any]]) -> None:
        url = f"{self.table_url}?on_conflict=youtube_video_id"
        for start in range(0, len(rows), 100):
            self.request(
                "POST",
                url,
                rows[start : start + 100],
                prefer="resolution=merge-duplicates,return=minimal",
            )

    def delete(self, video_id: str) -> None:
        url = f"{self.table_url}?youtube_video_id=eq.{video_id}"
        self.request("DELETE", url, prefer="return=minimal")


def classify_content_type(duration_seconds: int) -> str:
    """Aturan dasar kurasi: jika video >= 5 menit (300 detik) maka 'kajian', jika < 5 menit maka 'short'."""
    return "kajian" if duration_seconds >= 300 else "short"


def ingest_rows(
    videos: dict[str, dict[str, Any]],
    source_by_video: dict[str, Source],
    existing_records: dict[str, dict[str, Any]],
    default_review_status: str = "pending",
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
        # Existing editorial classification/review state is kept on metadata refresh.
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
            row["content_type"] = classify_content_type(row["duration_seconds"])
            is_auto_approved = (
                source.initial_review_status == "approved"
                or default_review_status == "approved"
            )
            row["review_status"] = "approved" if is_auto_approved else "pending"
            row["reviewed_at"] = datetime.now(timezone.utc).isoformat() if is_auto_approved else None
        else:
            row["content_type"] = classify_content_type(row["duration_seconds"])
            row["review_status"] = default_review_status
        rows.append(row)
    return rows, unavailable


def run(force: bool = False) -> int:
    youtube_key = os.environ.get("YOUTUBE_DATA_API_KEY", "")
    supabase_url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    secret_key = os.environ.get("SUPABASE_SECRET_KEY", "")
    force_env = os.environ.get("FORCE_RUN", "").lower() in ("1", "true", "yes")
    force = force or force_env

    if not youtube_key or not supabase_url or not secret_key:
        raise ValueError("Set YOUTUBE_DATA_API_KEY, SUPABASE_URL, and SUPABASE_SECRET_KEY in CI secrets")
    if not supabase_url.startswith("https://"):
        raise ValueError("SUPABASE_URL must use HTTPS")

    supabase = SupabaseClient(supabase_url, secret_key)

    # 1. Check settings and schedule
    settings = supabase.get_settings()
    is_due, trigger_type = should_run(settings, force=force)
    if not is_due:
        next_run_display = settings.get("next_run_at") if settings else "N/A"
        print(f"Ingestion skipped: Next scheduled run is at {next_run_display}.")
        return 0

    # 2. Concurrency protection
    if supabase.is_run_in_progress():
        print("Another ingestion run is currently in progress. Skipping to prevent duplicate work.")
        return 0

    if trigger_type == "manual":
        supabase.clear_run_requested()

    run_start_utc = datetime.now(timezone.utc)
    run_id = supabase.start_run(trigger_type)

    try:
        # 3. Load active sources from Supabase (fallback to config file if empty/unmigrated)
        sources = supabase.get_active_sources()
        if not sources and SOURCE_FILE.is_file():
            sources = load_sources(SOURCE_FILE)

        if not sources:
            print("No active sources found; catalog remains unchanged.")
            next_run_at = calculate_next_run(settings, run_start_utc)
            supabase.finish_run(run_id, status="success", videos_seen=0, candidates_written=0, removed_count=0)
            supabase.update_settings(run_start_utc.isoformat(), next_run_at)
            return 0

        source_by_video: dict[str, Source] = {}
        youtube = YouTubeDataClient(youtube_key)
        for source in sources:
            video_ids = youtube.source_video_ids(source)
            for video_id in video_ids:
                existing = source_by_video.get(video_id)
                if existing and existing != source:
                    # In case a video is in multiple sources, keep first source
                    continue
                source_by_video[video_id] = source

        existing_records = supabase.all_video_records()
        existing_id_set = set(existing_records)
        all_ids = sorted(existing_id_set | set(source_by_video))
        if not all_ids:
            print("Configured sources contain no videos; catalog remains empty.")
            next_run_at = calculate_next_run(settings, run_start_utc)
            supabase.finish_run(run_id, status="success", videos_seen=0, candidates_written=0, removed_count=0)
            supabase.update_settings(run_start_utc.isoformat(), next_run_at)
            return 0

        default_review_status = str(settings.get("default_review_status", "pending")).strip().lower() if settings else "pending"
        youtube_metadata = youtube.videos(all_ids)
        rows, unavailable = ingest_rows(
            youtube_metadata,
            source_by_video,
            existing_records,
            default_review_status=default_review_status,
        )
        supabase.upsert(rows)
        for video_id in set(all_ids) - set(youtube_metadata):
            unavailable.append(video_id)
        for video_id in sorted(set(unavailable)):
            supabase.delete(video_id)

        next_run_at = calculate_next_run(settings, run_start_utc)
        supabase.finish_run(
            run_id,
            status="success",
            videos_seen=len(all_ids),
            candidates_written=len(rows),
            removed_count=len(set(unavailable)),
        )
        supabase.update_settings(run_start_utc.isoformat(), next_run_at)

        print(f"Refreshed {len(rows)} videos; removed {len(set(unavailable))} unavailable or non-embeddable rows.")
        print(f"Next run scheduled for {next_run_at}.")
        print("New videos stay pending until editor approves them in the panel.")
        return 0

    except Exception as error:
        supabase.finish_run(run_id, status="failed", error_message=str(error))
        raise


if __name__ == "__main__":
    try:
        force_arg = "--force" in sys.argv
        raise SystemExit(run(force=force_arg))
    except HTTPError as error:
        print(f"Catalog request failed (HTTP {error.code}). Check API quota and CI secrets.", file=sys.stderr)
        raise SystemExit(1)
    except (URLError, TimeoutError) as error:
        print(f"Catalog network request failed: {getattr(error, 'reason', error)}", file=sys.stderr)
        raise SystemExit(1)
    except (ValueError, json.JSONDecodeError) as error:
        print(f"Catalog ingestion failed: {error}", file=sys.stderr)
        raise SystemExit(1)
