package id.kasirq.dailysunnah.quran.data

import android.content.Context
import org.json.JSONObject

data class SurahStart(val number: Int, val name: String, val page: Int)

object SurahPageIndex {
    fun load(context: Context): List<SurahStart> {
        val json = context.assets.open("quran/surah-page-index.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == 2) { "Unsupported surah page index schema" }
        require(root.getString("assetVersion") == MushafAssetRepository.ASSET_VERSION) { "Surah index targets another asset version" }
        require(root.getInt("pageCount") == MushafAssetRepository.MAX_PAGE) { "Surah index targets another mushaf edition" }
        val pages = root.getJSONArray("startPages")
        require(pages.length() == 114) { "Expected a start page for all 114 surahs" }
        val surahs = root.getJSONArray("surahs")
        require(surahs.length() == 114) { "Expected names and start pages for all surahs" }
        return (0 until surahs.length()).map { index ->
            val item = surahs.getJSONObject(index)
            val number = item.getInt("number")
            val page = item.getInt("startPage")
            require(number == index + 1) { "Surah index is not sequential at ${index + 1}" }
            require(page == pages.getInt(index) && page in 1..MushafAssetRepository.MAX_PAGE) {
                "Surah $number page differs from CDN-derived index"
            }
            val name = item.getString("name").trim()
            require(name.isNotEmpty()) { "Surah $number has no display name" }
            SurahStart(number, name, page)
        }.distinct()
    }
}

