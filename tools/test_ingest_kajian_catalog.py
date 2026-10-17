import unittest
from datetime import datetime, timezone

from ingest_kajian_catalog import (
    Source,
    calculate_next_run,
    collect_source_map,
    ingest_rows,
    is_run_due,
    parse_duration,
    parse_sources,
)


class KajianCatalogIngestionTest(unittest.TestCase):
    def test_parses_youtube_iso_durations(self):
        self.assertEqual(parse_duration("PT18M42S"), 1_122)
        self.assertEqual(parse_duration("P1DT2H3M4S"), 93_784)
        self.assertEqual(parse_duration("PT0S"), 0)

    def test_rejects_an_invalid_duration(self):
        with self.assertRaises(ValueError):
            parse_duration("18:42")

    def test_parses_channel_and_playlist_sources(self):
        sources = parse_sources([
            {
                "source_kind": "channel", "source_identifier": "@nuzuldzikri",
                "display_name": "Nuzul Dzikri", "category": "Fiqih", "content_type": "kajian",
            },
            {
                "source_kind": "playlist", "source_identifier": "PL1234567890",
                "display_name": "Shorts", "category": "Tazkiyatun Nufus", "content_type": "short",
            },
        ])
        self.assertEqual([source.source_kind for source in sources], ["channel", "playlist"])

    def test_rejects_invalid_source_classification(self):
        with self.assertRaises(ValueError):
            parse_sources([{
                "source_kind": "playlist", "source_identifier": "PL1234567890",
                "display_name": "Shorts", "category": "Other", "content_type": "short",
            }])

    def test_schedule_due_and_next_run(self):
        now = datetime(2026, 10, 15, 17, 0, tzinfo=timezone.utc)
        daily = {"schedule_mode": "daily", "daily_time": "00:00:00", "timezone": "Asia/Jakarta"}
        self.assertFalse(is_run_due({**daily, "next_run_at": "2026-10-15T17:15:00+00:00"}, now))
        self.assertTrue(is_run_due({**daily, "next_run_at": "2026-10-15T16:59:00+00:00"}, now))
        self.assertTrue(is_run_due({**daily, "run_requested_at": "2026-10-15T16:00:00+00:00"}, now))
        self.assertEqual(calculate_next_run(daily, now).isoformat(), "2026-10-16T17:00:00+00:00")
        interval = {"schedule_mode": "interval", "interval_minutes": 60}
        self.assertEqual(calculate_next_run(interval, now).isoformat(), "2026-10-15T18:00:00+00:00")

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
            {video_id: Source("playlist", "PL1234567890", "Fiqih playlist", "Fiqih", "kajian")},
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
            {video_id: Source("playlist", "PL1234567890", "Fiqih playlist", "Fiqih", "kajian")},
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

    def test_collects_duplicate_video_with_matching_source_defaults(self):
        video_id = "abcdefghijk"

        class FakeYouTube:
            def source_video_ids(self, _source):
                return [video_id]

        sources = [
            Source("playlist", "PL1234567890", "Playlist", "Fiqih", "kajian"),
            Source("channel", "@nuzul", "Channel", "Fiqih", "kajian"),
        ]
        result = collect_source_map(FakeYouTube(), sources)
        self.assertEqual(result[video_id].source_kind, "playlist")

    def test_rejects_duplicate_video_with_conflicting_source_defaults(self):
        video_id = "abcdefghijk"

        class FakeYouTube:
            def source_video_ids(self, _source):
                return [video_id]

        sources = [
            Source("channel", "@nuzul", "Channel", "Fiqih", "kajian"),
            Source("playlist", "PL1234567890", "Playlist", "Fiqih", "short"),
        ]
        with self.assertRaises(ValueError):
            collect_source_map(FakeYouTube(), sources)


if __name__ == "__main__":
    unittest.main()
