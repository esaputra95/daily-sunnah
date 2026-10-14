package id.kasirq.dailysunnah.quran.data

import android.content.Context

class QuranReaderPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun lastReadPage(): Int = preferences.getInt(KEY_LAST_PAGE, MushafAssetRepository.MIN_PAGE)
        .coerceIn(MushafAssetRepository.MIN_PAGE, MushafAssetRepository.MAX_PAGE)

    fun saveLastReadPage(page: Int) {
        if (page in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE) {
            preferences.edit().putInt(KEY_LAST_PAGE, page).apply()
        }
    }

    companion object {
        private const val FILE_NAME = "quran-reader"
        private const val KEY_LAST_PAGE = "last-read-page"
    }
}
