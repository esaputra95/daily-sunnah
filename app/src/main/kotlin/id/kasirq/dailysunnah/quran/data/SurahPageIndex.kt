package id.kasirq.dailysunnah.quran.data

import android.content.Context
import org.json.JSONObject

data class SurahStart(val number: Int, val name: String, val page: Int)

object QuranSurahArabicNames {
    private val names = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
        "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
        "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
        "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
        "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
        "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
        "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
        "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
        "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
        "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
        "المسد", "الإخلاص", "الفلق", "الناس",
    )

    fun get(number: Int): String? = names.getOrNull(number - 1)
}

object QuranSurahOpeningPolicy {
    fun needsStandaloneBasmala(number: Int): Boolean = number in 1..114 && number != 1 && number != 9
}

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

