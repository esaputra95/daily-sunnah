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

    fun mushafZoomEnabled(): Boolean = preferences.getBoolean(KEY_MUSHAF_ZOOM_ENABLED, false)

    fun setMushafZoomEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_MUSHAF_ZOOM_ENABLED, enabled).apply()
    }

    companion object {
        private const val FILE_NAME = "quran-reader"
        private const val KEY_LAST_PAGE = "last-read-page"
        private const val KEY_MUSHAF_ZOOM_ENABLED = "mushaf-zoom-enabled"
    }
}
