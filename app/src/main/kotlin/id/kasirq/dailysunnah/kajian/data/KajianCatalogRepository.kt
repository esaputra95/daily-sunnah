package id.kasirq.dailysunnah.kajian.data

import android.content.Context
import android.net.Uri
import id.kasirq.dailysunnah.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

class KajianCatalogRepository(context: Context) {
    private val preferences = context.getSharedPreferences("kajian_catalog", Context.MODE_PRIVATE)

    fun load(channelName: String? = null): KajianCatalogSnapshot {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            return cachedSnapshot("Katalog belum dikonfigurasi. Daftar tersimpan tetap bisa dibuka offline.", channelName)
        }

        return runCatching {
            require(baseUrl.startsWith("https://")) { "Supabase harus menggunakan HTTPS." }
            val channelResult = runCatching { fetchChannels(baseUrl, publishableKey) }
            val channels = channelResult.getOrElse { cachedChannels() }
            if (channelResult.isSuccess) cacheChannels(channels)
            val feedSettings = runCatching { fetchFeedSettings(baseUrl, publishableKey) }
                .getOrDefault(KajianFeedSettings.DEFAULT)
            val endpoint = buildCatalogEndpoint(baseUrl, feedSettings, channelName)
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
                val videos = applyFeedSettings(parseVideos(body), feedSettings)
                preferences.edit()
                    .putString(cacheKey(KEY_CACHE, channelName), body)
                    .putString(cacheKey(KEY_FEED_SETTINGS, channelName), serializeFeedSettings(feedSettings))
                    .putLong(cacheKey(KEY_CACHED_AT, channelName), System.currentTimeMillis())
                    .apply()
                KajianCatalogSnapshot(videos = videos, isCached = false, feedSettings = feedSettings, channels = channels)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            cachedSnapshot(error.message ?: "Katalog tidak dapat dimuat.", channelName)
        }
    }

    fun savedVideoIds(): Set<String> = preferences.getStringSet(KEY_SAVED, emptySet()).orEmpty().toSet()

    fun savedVideos(): List<KajianVideo> {
        val json = runCatching { JSONObject(preferences.getString(KEY_SAVED_VIDEOS, "{}") ?: "{}") }.getOrNull()
            ?: return emptyList()
        return buildList {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                json.optJSONObject(key)?.let { row -> videoFromJson(key, row) }?.let(::add)
            }
        }
    }

    fun toggleSaved(video: KajianVideo): Set<String> {
        val youtubeId = video.youtubeId
        val updated = savedVideoIds().toMutableSet().apply {
            if (!add(youtubeId)) remove(youtubeId)
        }
        val savedVideos = runCatching { JSONObject(preferences.getString(KEY_SAVED_VIDEOS, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        if (youtubeId in updated) savedVideos.put(youtubeId, videoToJson(video)) else savedVideos.remove(youtubeId)
        preferences.edit().putStringSet(KEY_SAVED, updated).putString(KEY_SAVED_VIDEOS, savedVideos.toString()).apply()
        return updated
    }

    private fun videoToJson(video: KajianVideo): JSONObject = JSONObject()
        .put("title", video.title)
        .put("description", video.description)
        .put("channel_name", video.channelName)
        .put("category", video.category)
        .put("content_type", video.contentType.apiValue)
        .put("thumbnail_url", video.thumbnailUrl ?: JSONObject.NULL)
        .put("duration_seconds", video.durationSeconds)
        .put("published_at", video.publishedAt ?: JSONObject.NULL)

    private fun videoFromJson(youtubeId: String, row: JSONObject): KajianVideo? {
        if (!YOUTUBE_ID.matches(youtubeId)) return null
        val type = KajianVideo.ContentType.entries.firstOrNull { it.apiValue == row.optString("content_type") } ?: return null
        return KajianVideo(
            youtubeId = youtubeId,
            title = row.optString("title"),
            description = row.optString("description"),
            channelName = row.optString("channel_name"),
            category = row.optString("category"),
            contentType = type,
            thumbnailUrl = row.optString("thumbnail_url").takeIf { it.startsWith("https://") },
            durationSeconds = row.optInt("duration_seconds").coerceAtLeast(0),
            publishedAt = row.optString("published_at").takeIf(String::isNotBlank),
        ).takeIf { it.title.isNotBlank() }
    }

    private fun fetchFeedSettings(baseUrl: String, publishableKey: String): KajianFeedSettings {
        val endpoint = Uri.parse("$baseUrl/rest/v1/kajian_feed_settings").buildUpon()
            .appendQueryParameter("select", "sort_order,content_types,min_duration_seconds,max_duration_seconds")
            .appendQueryParameter("id", "eq.default")
            .appendQueryParameter("limit", "1")
            .build()
        val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 7_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Pengaturan feed belum dapat dimuat (HTTP $code).")
            val rows = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            if (rows.length() == 0) return KajianFeedSettings.DEFAULT
            val row = rows.optJSONObject(0) ?: return KajianFeedSettings.DEFAULT
            return parseFeedSettings(row)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchChannels(baseUrl: String, publishableKey: String): List<KajianChannel> {
        val endpoint = Uri.parse("$baseUrl/rest/v1/kajian_channels").buildUpon()
            .appendQueryParameter("select", "channel_name,channel_id")
            .appendQueryParameter("order", "channel_name.asc")
            .build()
        val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 7_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Daftar channel video approved belum dapat dimuat (HTTP $code).")
            val rows = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            return buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val name = row.optString("channel_name").trim()
                    val channelId = row.optString("channel_id").trim().takeUnless { it.isBlank() || it == "null" } ?: name
                    if (name.isNotBlank()) {
                        add(KajianChannel(name, channelId))
                    }
                }
            }.distinctBy { it.name.lowercase() }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildCatalogEndpoint(baseUrl: String, settings: KajianFeedSettings, channelName: String?): Uri {
        val order = when (settings.sortOrder) {
            "oldest" -> "published_at.asc.nullslast"
            "duration_desc" -> "duration_seconds.desc.nullslast"
            "duration_asc" -> "duration_seconds.asc.nullslast"
            else -> "published_at.desc.nullslast"
        }
        val builder = Uri.parse("$baseUrl/rest/v1/kajian_videos").buildUpon()
            .appendQueryParameter(
                "select",
                "youtube_video_id,title,description,channel_name,category,content_type,thumbnail_url,duration_seconds,published_at,review_status",
            )
            .appendQueryParameter("review_status", "eq.approved")
            .appendQueryParameter("order", order)
        if (settings.contentTypes.size == 1) {
            builder.appendQueryParameter("content_type", "eq.${settings.contentTypes.first().apiValue}")
        }
        channelName?.takeIf(String::isNotBlank)?.let {
            builder.appendQueryParameter("channel_name", "eq.$it")
        }
        val durationFilters = buildList {
            settings.minDurationSeconds?.let { add("duration_seconds.gte.$it") }
            settings.maxDurationSeconds?.let { add("duration_seconds.lte.$it") }
        }
        when (durationFilters.size) {
            1 -> builder.appendQueryParameter("duration_seconds", durationFilters.single().substringAfter("duration_seconds."))
            2 -> builder.appendQueryParameter("and", "(${durationFilters.joinToString(",")})")
        }
        return builder.build()
    }

    private fun cachedSnapshot(message: String, channelName: String? = null): KajianCatalogSnapshot {
        val channelCacheKey = cacheKey(KEY_CACHE, channelName)
        val channelSettingsKey = cacheKey(KEY_FEED_SETTINGS, channelName)
        val channelCachedAtKey = cacheKey(KEY_CACHED_AT, channelName)
        val cachedAt = preferences.getLong(channelCachedAtKey, 0L)
        var cacheIsFresh = cachedAt > 0L &&
            System.currentTimeMillis() - cachedAt < Duration.ofDays(29).toMillis()
        var cached = if (cacheIsFresh) preferences.getString(channelCacheKey, null) else null
        var settingsJson = if (cached != null) preferences.getString(channelSettingsKey, null) else null
        if (cached == null && channelName != null) {
            val globalCachedAt = preferences.getLong(KEY_CACHED_AT, 0L)
            val globalIsFresh = globalCachedAt > 0L && System.currentTimeMillis() - globalCachedAt < Duration.ofDays(29).toMillis()
            if (globalIsFresh) {
                cached = preferences.getString(KEY_CACHE, null)
                settingsJson = preferences.getString(KEY_FEED_SETTINGS, null)
                cacheIsFresh = cached != null
            }
        }
        if (!cacheIsFresh || cached == null) {
            if (channelName == null) {
                preferences.edit().remove(KEY_CACHE).remove(KEY_FEED_SETTINGS).remove(KEY_CACHED_AT).apply()
            } else {
                preferences.edit().remove(channelCacheKey).remove(channelSettingsKey).remove(channelCachedAtKey).apply()
            }
            return KajianCatalogSnapshot(emptyList(), isCached = true, message = message, channels = cachedChannels())
        }
        val settings = settingsJson?.let {
            runCatching { parseFeedSettings(JSONObject(it)) }.getOrNull()
        } ?: KajianFeedSettings.DEFAULT
        val matchingVideos = parseVideos(cached).let { videos ->
            channelName?.let { selected -> videos.filter { it.channelName.equals(selected, ignoreCase = true) } } ?: videos
        }
        return KajianCatalogSnapshot(
            videos = applyFeedSettings(matchingVideos, settings),
            isCached = true,
            message = message,
            feedSettings = settings,
            channels = cachedChannels(),
        )
    }

    private fun cacheKey(baseKey: String, channelName: String?): String =
        if (channelName.isNullOrBlank()) baseKey else "$baseKey-channel-${Uri.encode(channelName)}"

    private fun cacheChannels(channels: List<KajianChannel>) {
        val array = JSONArray().apply {
            channels.forEach { channel ->
                put(JSONObject().put("channel_name", channel.name).put("channel_id", channel.channelId))
            }
        }
        preferences.edit().putString(KEY_CHANNELS, array.toString()).apply()
    }

    private fun cachedChannels(): List<KajianChannel> = runCatching {
        val rows = JSONArray(preferences.getString(KEY_CHANNELS, "[]"))
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val name = row.optString("channel_name").trim()
                val channelId = row.optString("channel_id").trim().takeUnless { it.isBlank() || it == "null" } ?: name
                if (name.isNotBlank()) add(KajianChannel(name, channelId))
            }
        }.distinctBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    private fun parseFeedSettings(row: JSONObject): KajianFeedSettings {
        val sort = row.optString("sort_order").takeIf {
            it in setOf("newest", "oldest", "duration_desc", "duration_asc")
        } ?: "newest"
        val configuredTypes = row.optJSONArray("content_types")?.let { values ->
            buildSet {
                for (index in 0 until values.length()) {
                    val value = values.optString(index)
                    KajianVideo.ContentType.entries.firstOrNull { it.apiValue == value }?.let(::add)
                }
            }
        }.orEmpty().ifEmpty { KajianVideo.ContentType.entries.toSet() }
        val minDuration = if (row.isNull("min_duration_seconds")) null else row.optInt("min_duration_seconds").coerceAtLeast(0)
        val maxDuration = if (row.isNull("max_duration_seconds")) null else row.optInt("max_duration_seconds").coerceAtLeast(0)
        return KajianFeedSettings(sort, configuredTypes, minDuration, maxDuration)
    }

    private fun serializeFeedSettings(settings: KajianFeedSettings): String = JSONObject()
        .put("sort_order", settings.sortOrder)
        .put("content_types", JSONArray(settings.contentTypes.map { it.apiValue }))
        .put("min_duration_seconds", settings.minDurationSeconds ?: JSONObject.NULL)
        .put("max_duration_seconds", settings.maxDurationSeconds ?: JSONObject.NULL)
        .toString()

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
        const val KEY_FEED_SETTINGS = "approved_catalog_feed_settings"
        const val KEY_CACHED_AT = "approved_catalog_cached_at"
        const val KEY_SAVED = "saved_video_ids"
        const val KEY_SAVED_VIDEOS = "saved_video_metadata"
        const val KEY_CHANNELS = "approved_video_channels"
        val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
    }
}
