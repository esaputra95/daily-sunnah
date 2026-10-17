package id.kasirq.dailysunnah.kajian.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KajianFeedSettingsTest {
    private val olderKajian = video("olderkaj01", KajianVideo.ContentType.KAJIAN, 900, "2026-10-10T00:00:00Z")
    private val newerShort = video("newershort", KajianVideo.ContentType.SHORT, 45, "2026-10-12T00:00:00Z")
    private val newerKajian = video("newerkaj01", KajianVideo.ContentType.KAJIAN, 1200, "2026-10-13T00:00:00Z")

    @Test
    fun ordersByNewestAndOldestWithNullDatesLast() {
        val videos = listOf(olderKajian, newerShort, newerKajian, video("nodatevid01", KajianVideo.ContentType.SHORT, 30, null))
        assertEquals(
            listOf("newerkaj01", "newershort", "olderkaj01", "nodatevid01"),
            applyFeedSettings(videos, settings("newest")).map { it.youtubeId },
        )
        assertEquals(
            listOf("olderkaj01", "newershort", "newerkaj01", "nodatevid01"),
            applyFeedSettings(videos, settings("oldest")).map { it.youtubeId },
        )
    }

    @Test
    fun ordersByDurationAndAppliesTypeAndDurationFilters() {
        val videos = listOf(olderKajian, newerShort, newerKajian)
        assertEquals(
            listOf("newerkaj01", "olderkaj01", "newershort"),
            applyFeedSettings(videos, settings("duration_desc")).map { it.youtubeId },
        )
        val filtered = settings("duration_asc").copy(
            contentTypes = setOf(KajianVideo.ContentType.KAJIAN),
            minDurationSeconds = 1000,
            maxDurationSeconds = 1500,
        )
        assertEquals(listOf("newerkaj01"), applyFeedSettings(videos, filtered).map { it.youtubeId })
    }

    private fun settings(order: String) = KajianFeedSettings(
        sortOrder = order,
        contentTypes = KajianVideo.ContentType.entries.toSet(),
        minDurationSeconds = null,
        maxDurationSeconds = null,
    )

    private fun video(id: String, type: KajianVideo.ContentType, duration: Int, publishedAt: String?) = KajianVideo(
        youtubeId = id,
        title = id,
        description = "",
        channelName = "Test",
        category = "Fiqih",
        contentType = type,
        thumbnailUrl = null,
        durationSeconds = duration,
        publishedAt = publishedAt,
    )
}
