package id.kasirq.dailysunnah.quran.data

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class QuranSearchResult(val verseKey: String, val page: Int, val arabic: String, val translation: String)

class QuranSearchRepository {
    fun search(query: String, limit: Int = 20): List<QuranSearchResult> {
        require(query.isNotBlank())
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val root = request("https://api.quran.com/api/v4/search?q=$encoded&size=${limit.coerceIn(1, 20)}&page=1&language=id&translations=33")
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
                val details = runCatching { request("https://api.quran.com/api/v4/verses/by_key/$verseKey?fields=page_number&translations=33") }.getOrNull()
                val detailVerse = details?.optJSONObject("verse") ?: details?.optJSONObject("data")?.optJSONObject("verse")
                val page = detailVerse?.optInt("page_number", 0)?.takeIf { it in 1..604 }
                    ?: verse.optInt("page_number", 0).takeIf { it in 1..604 }
                    ?: continue
                val translation = verse.optJSONArray("translations")?.optJSONObject(0)?.optString("text")
                    ?: detailVerse?.optJSONArray("translations")?.optJSONObject(0)?.optString("text")
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

    private fun request(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Pencarian Quran.com merespons HTTP $status.")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun clean(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0', ' ').trim()
}
