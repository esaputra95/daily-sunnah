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
)

val KAJIAN_CATEGORIES = listOf(
    "Akidah",
    "Fiqih",
    "Muamalah",
    "Sirah",
    "Tazkiyatun Nufus",
)
