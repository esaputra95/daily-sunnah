package id.kasirq.dailysunnah.ibadah.data

import android.content.Context

enum class PrayerSoundMode(val label: String) {
    ADHAN("Adzan"), SHORT("Singkat"), SILENT("Senyap"),
}

data class PrayerSettings(
    val correctionMinutes: Map<PrayerName, Int> = emptyMap(),
    val notificationsEnabled: Map<PrayerName, Boolean> = emptyMap(),
    val preparationMinutes: Int = 0,
    val soundMode: PrayerSoundMode = PrayerSoundMode.ADHAN,
    val adhanVolume: Int = 50,
) {
    fun correctionFor(name: PrayerName): Int = correctionMinutes[name] ?: 0
    fun notificationFor(name: PrayerName): Boolean = notificationsEnabled[name] ?: (name != PrayerName.TERBIT)
}

object PrayerSettingsStore {
    private const val PREFS = "prayer_settings"
    private val prayerNotifications = listOf(
        PrayerName.SUBUH, PrayerName.DZUHUR, PrayerName.ASHAR, PrayerName.MAGHRIB, PrayerName.ISYA,
    )

    fun read(context: Context): PrayerSettings {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val corrections = PrayerName.entries.associateWith { name ->
            preferences.getInt("correction_${name.name.lowercase()}", 0).coerceIn(-5, 5)
        }
        val notifications = prayerNotifications.associateWith { name ->
            preferences.getBoolean("notification_${name.name.lowercase()}", true)
        }
        return PrayerSettings(
            correctionMinutes = corrections,
            notificationsEnabled = notifications,
            preparationMinutes = preferences.getInt("preparation_minutes", 0).coerceIn(0, 15),
            soundMode = preferences.getString("sound_mode", PrayerSoundMode.ADHAN.name)
                ?.let { runCatching { PrayerSoundMode.valueOf(it) }.getOrNull() } ?: PrayerSoundMode.ADHAN,
            adhanVolume = preferences.getInt("adhan_volume", 50).coerceIn(0, 100),
        )
    }

    fun save(context: Context, settings: PrayerSettings) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("preparation_minutes", settings.preparationMinutes.coerceIn(0, 15))
            .putString("sound_mode", settings.soundMode.name)
            .putInt("adhan_volume", settings.adhanVolume.coerceIn(0, 100))
        PrayerName.entries.forEach { name ->
            editor.putInt("correction_${name.name.lowercase()}", settings.correctionFor(name).coerceIn(-5, 5))
        }
        prayerNotifications.forEach { name ->
            editor.putBoolean("notification_${name.name.lowercase()}", settings.notificationFor(name))
        }
        editor.apply()
    }

    fun notificationNames(): List<PrayerName> = prayerNotifications
}
