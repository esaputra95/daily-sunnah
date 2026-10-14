import json
import tempfile
import unittest
from pathlib import Path

from ingest_kajian_catalog import Source, ingest_rows, load_sources, parse_duration


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


if __name__ == "__main__":
    unittest.main()
