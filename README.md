# Daily Sunnah Android

Native Android starter for the Daily Sunnah 1.0 PRD. This is a new Kotlin and Jetpack Compose project; it does not modify the legacy Flutter Ashiilapp or the existing `ashiil-react-native` app.

## Implemented foundation

- The app opens on a Home screen with continue-reading, daily tilawah progress, quick links to Quran and Zakat, and a bottom navigation bar. Opening Quran from the menu shows offline Surah, Juz, and Bookmark tabs before the reader.
- Home shows the next prayer and live countdown, plus the Hijri date. The Ibadah tab calculates Subuh, sunrise, Dzuhur, Ashar, Maghrib, and Isya locally from precise foreground device coordinates, and includes a month-grid Hijri calendar with a ±2-day correction. Location stays on-device; without precise permission the app does not guess from a city.
- Mushaf Madinah reader loads the WebP image and its matching layout JSON from the product CDN.
- Each pair is downloaded to temporary files, checked for page/schema consistency, then installed into persistent app storage with a ready marker.
- Local page pairs open without a CDN request. The reader supports page navigation, verse hit-testing, selection highlight, pinch zoom, and pan.
- The reader follows the Stitch reference with a warm Mushaf canvas that fills the available screen width edge to edge, a full-width horizontal gallery-style slide on swipe, a header showing the current surah and juz with reading settings, compact page-read and bookmark-list icons, and a verse action sheet for bookmark/audio/share/translation.
- Surah openings draw a full-width Madinah-style ornamental divider and standalone Bismillah where applicable. The divider uses the bundled KFGQPC Uthmanic Hafs font; Bismillah uses the exact `hafs.18.ttf` asset from Ashiil with synthetic bold weight. Font attribution is shown in Quran settings and embedded font license metadata is retained. Font source: [King Fahd Quran Complex](https://qurancomplex.gov.sa/en/techquran/dev/) / [Quran Foundation font docs](https://api-docs.quran.com/docs/tutorials/fonts/font-rendering/).
- The first Mushaf page and its layout are bundled as a small bootstrap pair, so a first launch can show page 1 without waiting for the CDN.
- The last opened page is restored after relaunch. A bundled surah list jumps directly to its start page without a metadata API.
- Tapping a verified verse region selects its `surah:ayah` key; the reader can save/remove an on-device bookmark and reopen it at the matching page and region.
- WorkManager prioritizes the five neighboring pages on either side and prepares the distinct first pages of all 114 surahs. The bundled surah index is generated from the CDN's own 604 layout files.
- Surah names/start pages and the 30 Madinah juz start references/pages are bundled locally; their lists do not require a network connection. Verse bookmarks are stored on-device and can be opened from either the library tab or reader toolbar.
- A bundled integrity manifest pins image/layout hashes, byte sizes, and image dimensions for all 604 pages. Startup rejects a CDN asset that differs from the app's declared mushaf version.
- Daily reading target is configurable from 1–604 pages; users mark pages as read, and local reminders repeat at configured times until the target is met. Progress and settings remain on-device.
- An offline zakat savings calculator uses a transparent 85-gram gold nisab and 2.5% profile; gold price and its source/date are user-entered, and eligibility requires explicit haul/ownership confirmation. Users can explicitly save up to 50 local results or share a text summary.
- Kajian now has long-form and vertical Shorts tabs, title/channel/theme search, saved videos, and video sharing. Only approved Supabase rows appear; the latest approved catalog is cached on-device for offline browsing.
- Video playback uses YouTube's embedded IFrame player in Android WebView. Playback starts only after the user taps Play, pauses when the app leaves the foreground, and keeps YouTube player controls and branding visible.
- The `supabase/migrations/202610140001_kajian_videos.sql` migration defines the public, read-only catalog table. Pending/rejected rows stay hidden by row-level security; the ingestion worker classifies videos shorter than five minutes as Shorts and longer videos as Kajian.
- Migration 202610160001_kajian_ingestion_settings.sql adds admin-managed channel/playlist sources, schedules/run history, and public Android feed settings. Admin changes use authenticated RLS; ingestion credentials stay in GitHub Actions secrets.
- No Quran.com API or GitHub Raw request is used by this starter.

## Build

Open this directory in Android Studio or run:

```sh
./gradlew assembleDebug
```

Run the current unit tests and create a debug APK with:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The current build uses Android Gradle Plugin 8.13.1, Kotlin 2.2.10, Compose BOM 2026.03.00, and compile/target SDK 36. If Android SDK is installed outside its default path, create `local.properties` with `sdk.dir=/absolute/path/to/Android/sdk`.

## Current boundary

This is not yet the complete PRD. Quran content, prayer modules, zakat categories, tracker, account sync, and release requirements continue to evolve. Kajian admin manages ingestion sources, schedule, archive backfill, review defaults, and public app feed. New videos follow the configured pending/auto-approve setting; the app shows approved videos only. The workflow polls settings every 15 minutes, though GitHub queue delays can shift start time. Verse bookmarks and sharing work; Quran audio and translation packages are not included. The Quran reader uses its CDN image/layout pair without Quran.com, Quran Foundation, or GitHub Raw runtime APIs. Supabase account sync is not wired yet. Bundled surah names need editorial review. Reading reminders use Android inexact idle alarms and may be delayed by battery policies. Zakat calculations need fiqh reviewer approval before public release.

To connect the catalog on this machine, `local.properties` is gitignored and can contain:

```properties
dailySunnahSupabaseUrl=https://YOUR_PROJECT.supabase.co
dailySunnahSupabasePublishableKey=YOUR_SUPABASE_PUBLISHABLE_KEY
```

The app sends the publishable key in the `apikey` header. RLS allows clients to read approved rows only. Never put a Supabase secret key in the Android app.

The scheduled ingestion job reads active channel and playlist sources from Supabase `kajian_sources`, managed in Kajian Admin. Channel handles resolve to their official uploads playlist. The daily sync follows each source's `max_videos` setting; archive backfill retrieves older videos in resumable batches without changing the daily schedule. Existing video IDs are updated instead of duplicated, and existing review decisions survive metadata refresh. New-video review behavior follows the admin's pending or auto-approve setting.

Set GitHub Actions secrets `YOUTUBE_DATA_API_KEY`, `SUPABASE_URL`, and `SUPABASE_SECRET_KEY`. The workflow polls every 15 minutes, then runs when the configured schedule is due, an admin requested a run, or a repository `workflow_dispatch` forces a run. Each run logs status and counts to Supabase. Unavailable or non-embeddable videos are removed from the app catalog. Android reads `kajian_feed_settings` and falls back to newest-first if that setting is unavailable. Test the importer with `python3 -B -m unittest discover -s tools -p 'test_*.py'`.

The generated `app/src/main/assets/quran/surah-page-index.json` records the minimum page containing a verse box for each surah in CDN layout v1. Regenerate it with:

```sh
python3 tools/generate_surah_page_index.py /path/to/cdn-quran/dist/quran-mushaf/v1/layouts app/src/main/assets/quran/surah-page-index.json
```

Before publishing new CDN assets, validate the complete image/layout set and regenerate the pinned integrity manifest:

```sh
python3 tools/generate_mushaf_manifest.py /path/to/cdn-quran/dist/quran-mushaf/v1 app/src/main/assets/quran/mushaf-v1-manifest.json
```

The current local CDN build passed validation for all 604 WebP/layout pairs and 13,273 verse regions. It totals about 173 MB for a complete image download; the 98 unique surah-start images plus layouts total about 26 MB. Surah display names are bundled strings and should receive editorial review before release.

