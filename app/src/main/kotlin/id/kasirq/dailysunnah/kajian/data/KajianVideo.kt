package id.kasirq.dailysunnah.kajian.data

data class KajianVideo(
    val youtubeId: String,
    val title: String,
    val description: String,
    val channelName: String,
    val category: String,
    val contentType: ContentType,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val publishedAt: String?,
) {
    enum class ContentType(val apiValue: String) {
        KAJIAN("kajian"),
        SHORT("short"),
    }
}

data class KajianCatalogSnapshot(
    val videos: List<KajianVideo>,
    val isCached: Boolean,
    val message: String? = null,
    val feedSettings: KajianFeedSettings = KajianFeedSettings.DEFAULT,
    val channels: List<KajianChannel> = emptyList(),
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
)

data class KajianCatalogPage(
    val videos: List<KajianVideo>,
    val hasMore: Boolean,
    val nextOffset: Int,
    val message: String? = null,
)

data class KajianChannel(
    val name: String,
    val channelId: String,
)

data class KajianFeedSettings(
    val sortOrder: String,
    val contentTypes: Set<KajianVideo.ContentType>,
    val minDurationSeconds: Int?,
    val maxDurationSeconds: Int?,
) {
    companion object {
        val DEFAULT = KajianFeedSettings(
            sortOrder = "newest",
            contentTypes = KajianVideo.ContentType.entries.toSet(),
            minDurationSeconds = null,
            maxDurationSeconds = null,
        )
    }
}

internal fun applyFeedSettings(videos: List<KajianVideo>, settings: KajianFeedSettings): List<KajianVideo> {
    val filtered = videos.filter { video ->
        video.contentType in settings.contentTypes &&
            (settings.minDurationSeconds == null || video.durationSeconds >= settings.minDurationSeconds) &&
            (settings.maxDurationSeconds == null || video.durationSeconds <= settings.maxDurationSeconds)
    }
    return filtered.sortedWith { first, second ->
        when (settings.sortOrder) {
            "oldest" -> comparePublished(first.publishedAt, second.publishedAt, descending = false)
            "duration_desc" -> second.durationSeconds.compareTo(first.durationSeconds)
            "duration_asc" -> first.durationSeconds.compareTo(second.durationSeconds)
            else -> comparePublished(first.publishedAt, second.publishedAt, descending = true)
        }
    }
}

private fun comparePublished(first: String?, second: String?, descending: Boolean): Int {
    if (first == null && second == null) return 0
    if (first == null) return 1
    if (second == null) return -1
    val result = first.compareTo(second)
    return if (descending) -result else result
}

val KAJIAN_CATEGORIES = listOf(
    "Akidah",
    "Fiqih",
    "Muamalah",
    "Sirah",
    "Tazkiyatun Nufus",
)
