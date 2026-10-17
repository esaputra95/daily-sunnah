package id.kasirq.dailysunnah.quran.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class QuranBookmark(val verseKey: String, val page: Int, val createdAt: Long)

class QuranBookmarkStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<QuranBookmark> = runCatching {
        val array = JSONArray(preferences.getString(KEY_BOOKMARKS, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val bookmark = QuranBookmark(item.optString("verseKey"), item.optInt("page"), item.optLong("createdAt"))
            bookmark.takeIf {
                QuranVerseKey.isValid(it.verseKey) && it.page in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE
            }
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun contains(verseKey: String): Boolean = all().any { it.verseKey == verseKey }

    @Synchronized
    fun toggle(verseKey: String, page: Int): List<QuranBookmark> {
        require(QuranVerseKey.isValid(verseKey))
        require(page in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE)
        val updated = all().toMutableList()
        val existingIndex = updated.indexOfFirst { it.verseKey == verseKey }
        if (existingIndex >= 0) updated.removeAt(existingIndex)
        else updated.add(QuranBookmark(verseKey, page, System.currentTimeMillis()))
        preferences.edit().putString(
            KEY_BOOKMARKS,
            JSONArray().apply {
                updated.sortedBy { it.verseKey }.forEach { bookmark ->
                    put(JSONObject().apply {
                        put("verseKey", bookmark.verseKey)
                        put("page", bookmark.page)
                        put("createdAt", bookmark.createdAt)
                    })
                }
            }.toString(),
        ).apply()
        return updated.sortedByDescending { it.createdAt }
    }

    companion object {
        private const val FILE_NAME = "quran-bookmarks"
        private const val KEY_BOOKMARKS = "items"
    }
}
