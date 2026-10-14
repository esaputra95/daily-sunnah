#!/usr/bin/env python3
"""Import YouTube playlist metadata into Supabase as pending editorial items."""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass
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


@dataclass(frozen=True)
class Source:
    playlist_id: str
    category: str
    content_type: str


def parse_duration(value: str) -> int:
    match = ISO_DURATION.fullmatch(value)
    if not match:
        raise ValueError(f"Invalid YouTube duration: {value}")
    parts = {name: int(match.group(name) or 0) for name in ("days", "hours", "minutes", "seconds")}
    return parts["days"] * 86_400 + parts["hours"] * 3_600 + parts["minutes"] * 60 + parts["seconds"]


def load_sources(path: Path = SOURCE_FILE) -> list[Source]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError("kajian-sources.json must contain a JSON array")
    sources: list[Source] = []
    seen: set[str] = set()
    for item in raw:
        if not isinstance(item, dict):
            raise ValueError("Every source must be an object")
        playlist_id = str(item.get("playlist_id", "")).strip()
        category = str(item.get("category", "")).strip()
        content_type = str(item.get("content_type", "")).strip().lower()
        if not playlist_id:
            raise ValueError("Every source needs playlist_id")
        if category not in VALID_CATEGORIES:
            raise ValueError(f"Unsupported category for playlist {playlist_id}: {category}")
        if content_type not in VALID_CONTENT_TYPES:
            raise ValueError(f"content_type must be one of {sorted(VALID_CONTENT_TYPES)}")
        if playlist_id in seen:
            raise ValueError(f"Duplicate playlist_id: {playlist_id}")
        seen.add(playlist_id)
        sources.append(Source(playlist_id, category, content_type))
    return sources


class YouTubeDataClient:
    def __init__(self, api_key: str):
        self.api_key = api_key

    def get(self, method: str, **params: str) -> dict[str, Any]:
        query = urlencode({**params, "key": self.api_key})
        request = Request(f"{YOUTUBE_API}/{method}?{query}", headers={"Accept": "application/json"})
        with urlopen(request, timeout=25) as response:
            return json.loads(response.read().decode("utf-8"))

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
        self.table_url = f"{base_url.rstrip('/')}/rest/v1/kajian_videos"
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
            row["content_type"] = source.content_type
            row["review_status"] = "pending"
            row["reviewed_at"] = None
        rows.append(row)
    return rows, unavailable


def run() -> int:
    sources = load_sources()
    if not sources:
        print("No configured playlists; ingestion skipped.")
        return 0

    youtube_key = os.environ.get("YOUTUBE_DATA_API_KEY", "")
    supabase_url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    secret_key = os.environ.get("SUPABASE_SECRET_KEY", "")
    if not youtube_key or not supabase_url or not secret_key:
        raise ValueError("Set YOUTUBE_DATA_API_KEY, SUPABASE_URL, and SUPABASE_SECRET_KEY in CI secrets")
    if not supabase_url.startswith("https://"):
        raise ValueError("SUPABASE_URL must use HTTPS")

    source_by_video: dict[str, Source] = {}
    youtube = YouTubeDataClient(youtube_key)
    for source in sources:
        for video_id in youtube.playlist_video_ids(source.playlist_id):
            existing = source_by_video.get(video_id)
            if existing and existing != source:
                raise ValueError(f"Video {video_id} appears in playlists with conflicting editorial classification")
            source_by_video[video_id] = source

    supabase = SupabaseClient(supabase_url, secret_key)
    existing_records = supabase.all_video_records()
    existing_id_set = set(existing_records)
    all_ids = sorted(existing_id_set | set(source_by_video))
    if not all_ids:
        print("Configured playlists contain no videos; catalog remains empty.")
        return 0

    youtube_metadata = youtube.videos(all_ids)
    rows, unavailable = ingest_rows(youtube_metadata, source_by_video, existing_records)
    supabase.upsert(rows)
    for video_id in set(all_ids) - set(youtube_metadata):
        unavailable.append(video_id)
    for video_id in sorted(set(unavailable)):
        supabase.delete(video_id)

    print(f"Refreshed {len(rows)} videos; removed {len(set(unavailable))} unavailable or non-embeddable rows.")
    print("New playlist videos stay pending until editor approves them.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(run())
    except HTTPError as error:
        # YouTube Data API keys travel in the request query string. Never print
        # HTTPError itself because its representation can contain the full URL.
        print(f"Catalog request failed (HTTP {error.code}). Check API quota and CI secrets.", file=sys.stderr)
        raise SystemExit(1)
    except (URLError, TimeoutError) as error:
        print(f"Catalog network request failed: {getattr(error, 'reason', error)}", file=sys.stderr)
        raise SystemExit(1)
    except (ValueError, json.JSONDecodeError) as error:
        print(f"Catalog ingestion failed: {error}", file=sys.stderr)
        raise SystemExit(1)
        raise SystemExit(1)
