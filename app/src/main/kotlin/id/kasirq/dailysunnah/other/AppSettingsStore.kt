package id.kasirq.dailysunnah.other

import android.content.Context

enum class AppThemePreference(val label: String) {
    SYSTEM("Ikuti perangkat"),
    LIGHT("Terang"),
    DARK("Gelap"),
}

enum class AppLanguagePreference(val label: String) {
    INDONESIAN("Bahasa Indonesia"),
    ENGLISH("English"),
}

class AppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun theme(): AppThemePreference = runCatching {
        AppThemePreference.valueOf(preferences.getString(KEY_THEME, null) ?: AppThemePreference.LIGHT.name)
    }.getOrDefault(AppThemePreference.LIGHT)

    fun setTheme(value: AppThemePreference) {
        preferences.edit().putString(KEY_THEME, value.name).apply()
    }

    fun language(): AppLanguagePreference = runCatching {
        AppLanguagePreference.valueOf(preferences.getString(KEY_LANGUAGE, null) ?: AppLanguagePreference.INDONESIAN.name)
    }.getOrDefault(AppLanguagePreference.INDONESIAN)

    fun setLanguage(value: AppLanguagePreference) {
        preferences.edit().putString(KEY_LANGUAGE, value.name).apply()
    }

    private companion object {
        const val FILE_NAME = "daily-sunnah-app-settings"
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
    }
}
