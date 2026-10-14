package id.kasirq.dailysunnah.kajian.data

import android.content.Context
import android.net.Uri
import id.kasirq.dailysunnah.BuildConfig
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

class KajianCatalogRepository(context: Context) {
    private val preferences = context.getSharedPreferences("kajian_catalog", Context.MODE_PRIVATE)

    fun load(): KajianCatalogSnapshot {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            return cachedSnapshot("Katalog belum dikonfigurasi. Daftar tersimpan tetap bisa dibuka offline.")
        }

        return runCatching {
            require(baseUrl.startsWith("https://")) { "Supabase harus menggunakan HTTPS." }
            val endpoint = Uri.parse("$baseUrl/rest/v1/kajian_videos").buildUpon()
                .appendQueryParameter(
                    "select",
                    "youtube_video_id,title,description,channel_name,category,content_type,thumbnail_url,duration_seconds,published_at,review_status",
                )
                .appendQueryParameter("review_status", "eq.approved")
                .appendQueryParameter("order", "published_at.desc.nullslast")
                .build()
            val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7_000
                readTimeout = 10_000
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Katalog kajian belum dapat dimuat (HTTP $code).")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val videos = parseVideos(body)
                preferences.edit()
                    .putString(KEY_CACHE, body)
                    .putLong(KEY_CACHED_AT, System.currentTimeMillis())
                    .apply()
                KajianCatalogSnapshot(videos = videos, isCached = false)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            cachedSnapshot(error.message ?: "Katalog tidak dapat dimuat.")
        }
    }

    fun savedVideoIds(): Set<String> = preferences.getStringSet(KEY_SAVED, emptySet()).orEmpty().toSet()

    fun toggleSaved(youtubeId: String): Set<String> {
        val updated = savedVideoIds().toMutableSet().apply {
            if (!add(youtubeId)) remove(youtubeId)
        }
        preferences.edit().putStringSet(KEY_SAVED, updated).apply()
        return updated
    }

    private fun cachedSnapshot(message: String): KajianCatalogSnapshot {
        val cachedAt = preferences.getLong(KEY_CACHED_AT, 0L)
        val isWithinYouTubeRefreshWindow = cachedAt > 0L &&
            System.currentTimeMillis() - cachedAt < Duration.ofDays(29).toMillis()
        val cached = preferences.getString(KEY_CACHE, null)
        if (!isWithinYouTubeRefreshWindow || cached == null) {
            preferences.edit().remove(KEY_CACHE).remove(KEY_CACHED_AT).apply()
            return KajianCatalogSnapshot(emptyList(), isCached = true, message = message)
        }
        return KajianCatalogSnapshot(parseVideos(cached), isCached = true, message = message)
    }

    private fun parseVideos(json: String): List<KajianVideo> {
        val rows = JSONArray(json)
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                if (row.optString("review_status") != "approved") continue
                val youtubeId = row.optString("youtube_video_id")
                if (!YOUTUBE_ID.matches(youtubeId)) continue
                val type = KajianVideo.ContentType.entries.firstOrNull {
                    it.apiValue == row.optString("content_type")
                } ?: continue
                val title = row.optString("title").takeIf(String::isNotBlank) ?: continue
                val thumbnail = row.optString("thumbnail_url").takeIf { it.startsWith("https://") }
                add(
                    KajianVideo(
                        youtubeId = youtubeId,
                        title = title,
                        description = row.optString("description"),
                        channelName = row.optString("channel_name").ifBlank { "YouTube" },
                        category = row.optString("category").ifBlank { "Kajian" },
                        contentType = type,
                        thumbnailUrl = thumbnail,
                        durationSeconds = row.optInt("duration_seconds", 0).coerceAtLeast(0),
                        publishedAt = row.optString("published_at").takeIf(String::isNotBlank),
                    ),
                )
            }
        }
    }

    private companion object {
        const val KEY_CACHE = "approved_catalog_json"
        const val KEY_CACHED_AT = "approved_catalog_cached_at"
        const val KEY_SAVED = "saved_video_ids"
        val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
    }
}
