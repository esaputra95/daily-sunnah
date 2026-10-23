package id.kasirq.dailysunnah.quran.data

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class QuranSearchResult(val verseKey: String, val page: Int, val arabic: String, val translation: String)

class QuranSearchRepository {
    fun search(query: String, english: Boolean = false, limit: Int = 20): List<QuranSearchResult> {
        require(query.isNotBlank())
        val translationId = if (english) ENGLISH_TRANSLATION_ID else INDONESIAN_TRANSLATION_ID
        val language = if (english) "en" else "id"
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val root = request("https://api.quran.com/api/v4/search?q=$encoded&size=${limit.coerceIn(1, 20)}&page=1&language=$language&translations=$translationId")
        val resultRoot = root.optJSONObject("search") ?: root.optJSONObject("result") ?: root
        val results = resultRoot.optJSONArray("results")
            ?: resultRoot.optJSONArray("verses")
            ?: root.optJSONArray("results")
            ?: root.optJSONObject("data")?.optJSONArray("results")
            ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val verse = results.optJSONObject(index) ?: continue
                val verseKey = verse.optString("verse_key").takeIf(String::isNotBlank)
                    ?: verse.optString("key").takeIf { it.contains(':') }
                    ?: continue
                val details = runCatching { request("https://api.quran.com/api/v4/verses/by_key/$verseKey?fields=page_number&translations=$translationId") }.getOrNull()
                val detailVerse = details?.optJSONObject("verse") ?: details?.optJSONObject("data")?.optJSONObject("verse")
                val page = detailVerse?.optInt("page_number", 0)?.takeIf { it in 1..604 }
                    ?: verse.optInt("page_number", 0).takeIf { it in 1..604 }
                    ?: continue
                val translation = findTranslation(verse.optJSONArray("translations"), translationId, english)
                    ?: findTranslation(detailVerse?.optJSONArray("translations"), translationId, english)
                    ?: ""
                add(
                    QuranSearchResult(
                        verseKey = verseKey,
                        page = page,
                        arabic = clean(verse.optString("text", verse.optString("name", ""))),
                        translation = clean(translation),
                    ),
                )
            }
        }
    }

    private fun findTranslation(translations: org.json.JSONArray?, translationId: Int, english: Boolean): String? {
        if (translations == null) return null
        val entries = (0 until translations.length()).mapNotNull(translations::optJSONObject)
        val selected = entries.firstOrNull { it.optInt("resource_id") == translationId }
            ?: entries.firstOrNull { it.optString("language_name").equals(if (english) "english" else "indonesian", ignoreCase = true) }
        return selected?.optString("text")?.takeIf(String::isNotBlank)
    }

    private fun request(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error(if (status == 404) "Search content is unavailable for the selected language." else "Quran.com search returned HTTP $status.")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun clean(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0', ' ').trim()

    private companion object {
        const val INDONESIAN_TRANSLATION_ID = 33
        const val ENGLISH_TRANSLATION_ID = 131
    }
}
