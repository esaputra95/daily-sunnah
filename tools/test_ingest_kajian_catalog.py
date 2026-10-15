import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from ingest_kajian_catalog import (
    Source,
    YouTubeDataClient,
    calculate_next_run,
    ingest_rows,
    load_sources,
    parse_duration,
    should_run,
)


class KajianCatalogIngestionTest(unittest.TestCase):
    def test_parses_youtube_iso_durations(self):
        self.assertEqual(parse_duration("PT18M42S"), 1_122)
        self.assertEqual(parse_duration("P1DT2H3M4S"), 93_784)
        self.assertEqual(parse_duration("PT0S"), 0)

    def test_rejects_an_invalid_duration(self):
        with self.assertRaises(ValueError):
            parse_duration("18:42")

    def test_requires_editorial_content_type_and_category(self):
        with tempfile.TemporaryDirectory() as directory:
            source_file = Path(directory) / "sources.json"
            source_file.write_text(json.dumps([{"playlist_id": "PL1", "category": "Fiqih"}]))
            with self.assertRaises(ValueError):
                load_sources(source_file)

    def test_new_candidates_are_classified_without_auto_approval(self):
        video_id = "abcdefghijk"
        rows, unavailable = ingest_rows(
            {
                video_id: {
                    "snippet": {
                        "title": "Kajian", "description": "", "channelTitle": "Channel", "channelId": "UC1",
                        "publishedAt": "2026-10-14T00:00:00Z", "thumbnails": {"high": {"url": "https://i.ytimg.com/x.jpg"}},
                    },
                    "contentDetails": {"duration": "PT45M"},
                    "status": {"privacyStatus": "public", "embeddable": True},
                }
            },
            {video_id: Source("PL1", "Fiqih", "kajian")},
            {},
        )
        self.assertEqual(unavailable, [])
        self.assertEqual(rows[0]["category"], "Fiqih")
        self.assertEqual(rows[0]["content_type"], "kajian")
        self.assertEqual(rows[0]["review_status"], "pending")
        self.assertEqual(rows[0]["duration_seconds"], 2_700)

    def test_excludes_private_and_non_embeddable_videos(self):
        rows, unavailable = ingest_rows(
            {
                "abcdefghijk": {"snippet": {}, "contentDetails": {"duration": "PT1S"}, "status": {"privacyStatus": "private", "embeddable": False}}
            },
            {},
            {},
        )
        self.assertEqual(rows, [])
        self.assertEqual(unavailable, ["abcdefghijk"])

    def test_refresh_does_not_replace_existing_editorial_classification(self):
        video_id = "abcdefghijk"
        rows, _ = ingest_rows(
            {
                video_id: {
                    "snippet": {"title": "Kajian", "channelTitle": "Channel", "channelId": "UC1"},
                    "contentDetails": {"duration": "PT45M"},
                    "status": {"privacyStatus": "public", "embeddable": True},
                }
            },
            {video_id: Source("PL1", "Fiqih", "kajian")},
            {
                video_id: {
                    "category": "Muamalah",
                    "content_type": "kajian",
                    "review_status": "approved",
                    "reviewed_at": "2026-10-13T00:00:00+00:00",
                }
            },
        )
        self.assertEqual(rows[0]["category"], "Muamalah")
        self.assertEqual(rows[0]["review_status"], "approved")
        self.assertEqual(rows[0]["reviewed_at"], "2026-10-13T00:00:00+00:00")

    def test_calculate_next_run_interval(self):
        fixed_time = datetime(2026, 10, 15, 12, 0, 0, tzinfo=timezone.utc)
        settings = {"schedule_mode": "interval", "interval_minutes": 60}
        next_run = calculate_next_run(settings, current_time=fixed_time)
        self.assertEqual(next_run, "2026-10-15T13:00:00+00:00")

    def test_calculate_next_run_daily_asia_jakarta(self):
        # 12:00 UTC is 19:00 WIB. Daily time is 00:00 WIB (which is 17:00 UTC).
        # Since 19:00 WIB > 00:00 WIB, next run is tomorrow at 00:00 WIB = 17:00 UTC today.
        fixed_time = datetime(2026, 10, 15, 12, 0, 0, tzinfo=timezone.utc)
        settings = {"schedule_mode": "daily", "daily_time": "00:00"}
        next_run = calculate_next_run(settings, current_time=fixed_time)
        self.assertEqual(next_run, "2026-10-15T17:00:00+00:00")

    def test_should_run_manual_trigger_takes_precedence(self):
        settings = {
            "run_requested_at": "2026-10-15T10:00:00Z",
            "next_run_at": "2026-10-15T23:59:59Z",
        }
        is_due, trigger = should_run(settings)
        self.assertTrue(is_due)
        self.assertEqual(trigger, "manual")

    def test_should_run_skips_when_next_run_is_in_future(self):
        settings = {
            "run_requested_at": None,
            "next_run_at": "2099-01-01T00:00:00+00:00",
        }
        is_due, trigger = should_run(settings)
        self.assertFalse(is_due)
        self.assertEqual(trigger, "none")

    def test_resolve_channel_uploads_shortcut(self):
        client = YouTubeDataClient("fake_key")
        # Direct channel ID UCxxx maps to UUxxx
        channel_id = "UC1234567890123456789012"
        uploads = client.resolve_channel_uploads_playlist(channel_id)
        self.assertEqual(uploads, "UU1234567890123456789012")


if __name__ == "__main__":
    unittest.main()
