package id.kasirq.dailysunnah.quran.data

import org.json.JSONObject

data class MushafRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(pointX: Float, pointY: Float): Boolean =
        pointX >= x && pointX <= right && pointY >= y && pointY <= bottom
}

data class MushafVerseRegion(
    val verseKey: String,
    val lineNumber: Int,
    val rect: MushafRect,
)

data class MushafSectionHeader(
    val chapterId: Int,
    val lineNumber: Int,
    val rect: MushafRect,
)

data class MushafPageLayout(
    val schemaVersion: Int,
    val page: Int,
    val renderViewBox: MushafRect,
    val verseRegions: List<MushafVerseRegion>,
    val sectionHeaders: List<MushafSectionHeader> = emptyList(),
)

data class MushafPageAsset(
    val page: Int,
    val bitmap: android.graphics.Bitmap,
    val layout: MushafPageLayout,
)

object MushafLayoutParser {
    fun parse(json: String, expectedPage: Int): MushafPageLayout {
        val root = JSONObject(json)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion == 1) { "Unsupported mushaf layout schema: $schemaVersion" }

        val page = root.getInt("page")
        require(page == expectedPage) { "Layout page $page does not match requested page $expectedPage" }

        val viewBox = root.getJSONObject("renderViewBox").toRect()
        require(viewBox.width > 0f && viewBox.height > 0f) { "Invalid renderViewBox on page $page" }

        val boxes = root.getJSONArray("verseBoxes")
        require(boxes.length() > 0) { "No verse regions found on page $page" }
        val regions = buildList(boxes.length()) {
            for (index in 0 until boxes.length()) {
                val box = boxes.getJSONObject(index)
                val verseKey = box.getString("verseKey")
                require(QuranVerseKey.isValid(verseKey)) { "Invalid verseKey at page $page box $index" }
                val rect = box.toRect()
                require(rect.width > 0f && rect.height > 0f) { "Invalid verse box at page $page box $index" }
                require(
                    rect.x >= viewBox.x - GEOMETRY_EPSILON &&
                        rect.y >= viewBox.y - GEOMETRY_EPSILON &&
                        rect.right <= viewBox.right + GEOMETRY_EPSILON &&
                        rect.bottom <= viewBox.bottom + GEOMETRY_EPSILON,
                ) { "Verse box is outside renderViewBox on page $page" }
                val lineNumber = box.getInt("lineNumber")
                require(lineNumber > 0) { "Invalid lineNumber on page $page box $index" }
                add(MushafVerseRegion(verseKey, lineNumber, rect))
            }
        }

        val sectionHeaderBoxes = root.optJSONArray("sectionHeaderBoxes")
        val sectionHeaders = if (sectionHeaderBoxes == null) {
            emptyList()
        } else {
            buildList(sectionHeaderBoxes.length()) {
                for (index in 0 until sectionHeaderBoxes.length()) {
                    val header = runCatching {
                        val box = sectionHeaderBoxes.getJSONObject(index)
                        val chapterId = box.getInt("chapterId")
                        val lineNumber = box.getInt("lineNumber")
                        val rect = box.toRect()
                        val isInsideViewBox = rect.x >= viewBox.x - GEOMETRY_EPSILON &&
                            rect.y >= viewBox.y - GEOMETRY_EPSILON &&
                            rect.right <= viewBox.right + GEOMETRY_EPSILON &&
                            rect.bottom <= viewBox.bottom + GEOMETRY_EPSILON
                        if (chapterId !in 1..114 || lineNumber <= 0 || rect.width <= 0f || rect.height <= 0f || !isInsideViewBox) {
                            null
                        } else {
                            MushafSectionHeader(chapterId, lineNumber, rect)
                        }
                    }.getOrNull()
                    if (header != null) add(header)
                }
            }
        }

        return MushafPageLayout(schemaVersion, page, viewBox, regions, sectionHeaders)
    }

    private fun JSONObject.toRect() = MushafRect(
        x = getDouble("x").toFloat(),
        y = getDouble("y").toFloat(),
        width = getDouble("width").toFloat(),
        height = getDouble("height").toFloat(),
    )

    private const val GEOMETRY_EPSILON = 0.1f
}

