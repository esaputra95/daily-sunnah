package id.kasirq.dailysunnah.quran.data

import android.content.Context
import android.text.Html
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException

data class QuranVerseContent(
    val verseKey: String,
    val arabic: String? = null,
    val translation: String? = null,
    val tafsir: String? = null,
    val audioUrl: String? = null,
    val translationSource: String? = null,
    val translationFootnotes: String? = null,
    val tafsirSource: String? = null,
    val tafsirFallbackNotice: String? = null,
)

/** Published QuranEnc English translation, Quran.com Indonesian translation, and the existing tafsir sources. */
class QuranVerseContentRepository(context: Context) {
    private val cache = File(context.applicationContext.filesDir, "quran-content-v2").apply { mkdirs() }

    fun loadTranslation(verseKey: String, english: Boolean = false): QuranVerseContent {
        val (surah, ayah) = parseKey(verseKey)
        val cacheKey = if (english) "quranenc-$ENGLISH_TRANSLATION_KEY-$ENGLISH_TRANSLATION_VERSION" else "qurancom-$INDONESIAN_TRANSLATION_ID"
        val translationFile = File(cache, "translation-$cacheKey-$surah-$ayah.json")
        val cached = translationFile.takeIf(File::isFile)?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val content = cached?.optString("text")?.takeIf(String::isNotBlank)?.let { text ->
            TranslationPayload(text, cached.optString("footnotes").takeIf { it.isNotBlank() && it != "null" })
        } ?: (if (english) fetchQuranEncTranslation(surah, ayah) else TranslationPayload(fetchTranslation(verseKey, INDONESIAN_TRANSLATION_ID)))
            .also { payload ->
                writeAtomically(
                    translationFile,
                    JSONObject().put("text", payload.text).put("footnotes", payload.footnotes).toString(),
                )
            }
        return QuranVerseContent(
            verseKey = verseKey,
            translation = content.text,
            translationFootnotes = content.footnotes,
            translationSource = if (english) ENGLISH_TRANSLATION_NAME else INDONESIAN_TRANSLATION_NAME,
        )
    }

    fun loadTafsir(verseKey: String, english: Boolean = false): QuranVerseContent {
        val (surah, ayah) = parseKey(verseKey)
        val indonesianFile = File(cache, "tafsir-$INDONESIAN_TAFSIR_CACHE_KEY-$surah-$ayah.txt")
        val englishFile = File(cache, "tafsir-$IBN_KATHIR_ENGLISH_ID-$surah-$ayah.txt")
        val primaryFile = if (english) englishFile else indonesianFile
        val cachedPrimary = primaryFile.takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)
        if (cachedPrimary != null) {
            return QuranVerseContent(
                verseKey = verseKey,
                tafsir = cachedPrimary,
                tafsirSource = if (english) IBN_KATHIR_ENGLISH_NAME else INDONESIAN_TAFSIR_NAME,
            )
        }
        val primaryContent = runCatching {
            if (english) fetchIbnKathirEnglish(verseKey) else fetchTafsir(surah, ayah)
        }
        if (primaryContent.isSuccess) {
            val text = primaryContent.getOrThrow()
            writeAtomically(primaryFile, text)
            return QuranVerseContent(
                verseKey = verseKey,
                tafsir = text,
                tafsirSource = if (english) IBN_KATHIR_ENGLISH_NAME else INDONESIAN_TAFSIR_NAME,
            )
        }
        if (english) throw primaryContent.exceptionOrNull() ?: error("English tafsir is unavailable.")
        val fallback = englishFile.takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)
            ?: fetchIbnKathirEnglish(verseKey).also { writeAtomically(englishFile, it) }
        return QuranVerseContent(
            verseKey = verseKey,
            tafsir = fallback,
            tafsirSource = IBN_KATHIR_ENGLISH_NAME,
            tafsirFallbackNotice = "Tafsir Indonesianya belum tersedia; ditampilkan versi bahasa Inggris.",
        )
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

    private fun fetchTranslation(verseKey: String, translationId: Int): String {
        val root = getJson("https://api.quran.com/api/v4/verses/by_key/$verseKey?translations=$translationId&fields=text_uthmani")
        val verse = root.optJSONObject("verse") ?: root.optJSONObject("data")?.optJSONObject("verse") ?: root
        val translations = verse.optJSONArray("translations")
        val text = translations?.optJSONObject(0)?.optString("text")?.takeIf(String::isNotBlank)
            ?: error("Terjemah $translationId belum tersedia untuk ayat $verseKey.")
        return cleanHtml(text)
    }

    private fun fetchQuranEncTranslation(surah: Int, ayah: Int): TranslationPayload {
        val root = getJson("https://quranenc.com/api/v1/translation/aya/$ENGLISH_TRANSLATION_KEY/$surah/$ayah")
        val result = root.optJSONObject("result") ?: error("QuranEnc did not return a result for $surah:$ayah.")
        val text = result.optString("translation").takeIf(String::isNotBlank)
            ?: error("QuranEnc has no English translation for $surah:$ayah.")
        val footnotes = result.optString("footnotes").takeIf { it.isNotBlank() && it != "null" }
        return TranslationPayload(cleanHtml(text), footnotes?.let(::cleanHtml))
    }

    private fun fetchIbnKathirEnglish(verseKey: String): String {
        val root = getJson("https://api.quran.com/api/v4/tafsirs/$IBN_KATHIR_ENGLISH_ID/by_ayah/$verseKey")
        val data = root.optJSONObject("tafsir") ?: root.optJSONObject("data")?.optJSONObject("tafsir") ?: root
        val text = data.optString("text").takeIf(String::isNotBlank)
            ?: data.optString("tafsir").takeIf(String::isNotBlank)
            ?: error("English Tafsir Ibn Kathir is not available for ayah $verseKey.")
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
            if (status !in 200..299) error("Quran content service returned HTTP $status.")
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

    fun translationLoadError(error: Exception, english: Boolean): String = if (english) {
        when {
            error is UnknownHostException || error is SocketTimeoutException || error is IOException ->
                "Could not reach the Quran translation service. Check your connection or try again later."
            error.message?.contains("HTTP") == true -> "The Quran translation service returned an error. Please try again later."
            else -> "No published translation was returned for this verse. Please try again later."
        }
    } else {
        when {
            error is UnknownHostException || error is SocketTimeoutException || error is IOException ->
                "Tidak dapat terhubung ke layanan terjemah. Periksa koneksi atau coba lagi nanti."
            error.message?.contains("HTTP") == true -> "Layanan terjemah sedang bermasalah. Coba lagi nanti."
            else -> "Terjemahan ayat ini belum tersedia dari sumber. Coba lagi nanti."
        }
    }

    private fun parseKey(key: String): Pair<Int, Int> {
        val parts = key.split(':')
        val surah = parts.getOrNull(0)?.toIntOrNull() ?: error("Referensi ayat tidak valid.")
        val ayah = parts.getOrNull(1)?.toIntOrNull() ?: error("Referensi ayat tidak valid.")
        require(surah in 1..114 && ayah > 0)
        return surah to ayah
    }

    private companion object {
        const val INDONESIAN_TRANSLATION_ID = 33
        const val ENGLISH_TRANSLATION_KEY = "english_rwwad"
        const val ENGLISH_TRANSLATION_VERSION = "1.0.19"
        const val IBN_KATHIR_ENGLISH_ID = 169
        const val INDONESIAN_TAFSIR_CACHE_KEY = "kasirq-id"
        const val INDONESIAN_TRANSLATION_NAME = "Terjemah Indonesia (Quran.com, ID 33)"
        const val ENGLISH_TRANSLATION_NAME = "QuranEnc · Rowwad Translation Center · v1.0.19"
        const val INDONESIAN_TAFSIR_NAME = "Tafsir Kasirq (Indonesia)"
        const val IBN_KATHIR_ENGLISH_NAME = "Tafsir Ibn Kathir (English)"
    }

    private data class TranslationPayload(val text: String, val footnotes: String? = null)
}
