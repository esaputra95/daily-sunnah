package id.kasirq.dailysunnah.quran.data

import android.content.Context
import android.text.Html
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class QuranVerseContent(
    val verseKey: String,
    val arabic: String? = null,
    val translation: String? = null,
    val tafsir: String? = null,
    val audioUrl: String? = null,
)

/** Content lookup: Indonesian translation ID 33 (same source used by Ashiil), and Kasirq CDN tafsir. */
class QuranVerseContentRepository(context: Context) {
    private val cache = File(context.applicationContext.filesDir, "quran-content-v1").apply { mkdirs() }

    fun loadTranslation(verseKey: String): QuranVerseContent {
        val (surah, ayah) = parseKey(verseKey)
        val translationFile = File(cache, "translation-$surah-$ayah.txt")
        val translation = translationFile.takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)
            ?: fetchTranslation(verseKey).also { writeAtomically(translationFile, it) }
        return QuranVerseContent(verseKey = verseKey, translation = translation)
    }

    fun loadTafsir(verseKey: String): QuranVerseContent {
        val (surah, ayah) = parseKey(verseKey)
        val tafsirFile = File(cache, "tafsir-$surah-$ayah.txt")
        val tafsir = tafsirFile.takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)
            ?: fetchTafsir(surah, ayah).also { writeAtomically(tafsirFile, it) }
        return QuranVerseContent(verseKey = verseKey, tafsir = tafsir)
    }

    fun audioUrl(verseKey: String, recitationId: Int = QuranAudioPreferences.DEFAULT_RECITER_ID): String {
        parseKey(verseKey)
        require(QuranAudioPreferences.RECITERS.any { it.id == recitationId }) { "Qari tidak valid." }
        val root = getJson("https://api.quran.com/api/v4/recitations/$recitationId/by_ayah/$verseKey")
        val data = root.optJSONObject("data")
        val audio = root.optJSONObject("audio_file") ?: root.optJSONObject("audio")
            ?: data?.optJSONObject("audio_file") ?: data?.optJSONObject("audio")
        val audioList = root.optJSONArray("audio_files") ?: data?.optJSONArray("audio_files")
        val rawUrl = audio?.optString("url")?.takeIf(String::isNotBlank)
            ?: audioList?.optJSONObject(0)?.optString("url")?.takeIf(String::isNotBlank)
            ?: error("Audio ayat tidak tersedia dari sumber.")
        return when {
            rawUrl.startsWith("https://") -> rawUrl
            rawUrl.startsWith("/") -> "https://verses.quran.foundation$rawUrl"
            else -> "https://verses.quran.foundation/$rawUrl"
        }
    }

    private fun fetchTranslation(verseKey: String): String {
        val root = getJson("https://api.quran.com/api/v4/verses/by_key/$verseKey?translations=33&fields=text_uthmani")
        val verse = root.optJSONObject("verse") ?: root.optJSONObject("data")?.optJSONObject("verse") ?: root
        val translations = verse.optJSONArray("translations")
        val text = translations?.optJSONObject(0)?.optString("text")
            ?: error("Terjemah Indonesia belum tersedia untuk ayat $verseKey.")
        return cleanHtml(text)
    }

    private fun fetchTafsir(surah: Int, ayah: Int): String {
        val root = getJson("https://quran-cdn.kasirq.id/tafsir/$surah/$ayah")
        val data = root.optJSONObject("data") ?: root
        val tafsir = data.optString("teks").takeIf(String::isNotBlank)
            ?: data.optString("text").takeIf(String::isNotBlank)
            ?: data.optString("tafsir").takeIf(String::isNotBlank)
            ?: error("Tafsir ayat $surah:$ayah belum tersedia.")
        return cleanHtml(tafsir)
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("Sumber konten merespons HTTP $status.")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanHtml(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
        .toString().replace('\u00a0', ' ').trim()

    private fun writeAtomically(file: File, content: String) {
        val temp = File(cache, "${file.name}.part")
        temp.writeText(content)
        if (file.exists()) file.delete()
        check(temp.renameTo(file)) { "Konten ayat gagal disimpan." }
    }

    private fun parseKey(key: String): Pair<Int, Int> {
        val parts = key.split(':')
        val surah = parts.getOrNull(0)?.toIntOrNull() ?: error("Referensi ayat tidak valid.")
        val ayah = parts.getOrNull(1)?.toIntOrNull() ?: error("Referensi ayat tidak valid.")
        require(surah in 1..114 && ayah > 0)
        return surah to ayah
    }
}
