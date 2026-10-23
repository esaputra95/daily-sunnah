package id.kasirq.dailysunnah.quran.data

import android.content.Context

enum class QuranAudioEndBehavior(val title: String, val description: String, val englishTitle: String, val englishDescription: String) {
    REPEAT("Ulangi ayat", "Ayat yang sama diputar lagi.", "Repeat verse", "Play the same verse again."),
    NEXT_AYAH("Lanjut ayat", "Putar ayat berikutnya secara berurutan.", "Next verse", "Continue with the next verse."),
    STOP("Berhenti", "Audio berhenti setelah ayat selesai.", "Stop", "Stop audio when the verse ends."),
}

data class QuranReciter(val id: Int, val name: String)

class QuranAudioPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun reciterId(): Int = preferences.getInt(KEY_RECITER, DEFAULT_RECITER_ID)
        .takeIf { id -> RECITERS.any { it.id == id } } ?: DEFAULT_RECITER_ID

    fun setReciterId(id: Int) {
        if (RECITERS.any { it.id == id }) preferences.edit().putInt(KEY_RECITER, id).apply()
    }

    fun endBehavior(): QuranAudioEndBehavior = runCatching {
        QuranAudioEndBehavior.valueOf(preferences.getString(KEY_END_BEHAVIOR, null) ?: DEFAULT_END_BEHAVIOR.name)
    }.getOrDefault(DEFAULT_END_BEHAVIOR)

    fun setEndBehavior(value: QuranAudioEndBehavior) {
        preferences.edit().putString(KEY_END_BEHAVIOR, value.name).apply()
    }

    companion object {
        val RECITERS = listOf(
            QuranReciter(7, "Mishary Rashid Alafasy"),
            QuranReciter(6, "Mahmoud Khalil Al-Husary"),
            QuranReciter(3, "Abdur-Rahman as-Sudais"),
            QuranReciter(9, "Mohamed Siddiq al-Minshawi · Murattal"),
        )
        const val DEFAULT_RECITER_ID = 7
        val DEFAULT_END_BEHAVIOR = QuranAudioEndBehavior.NEXT_AYAH
        private const val FILE_NAME = "quran-audio-preferences"
        private const val KEY_RECITER = "reciter-id"
        private const val KEY_END_BEHAVIOR = "end-behavior"
    }
}
