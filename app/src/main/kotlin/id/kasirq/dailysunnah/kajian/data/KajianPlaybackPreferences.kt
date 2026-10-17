package id.kasirq.dailysunnah.kajian.data

import android.content.Context

enum class KajianEndBehavior(val label: String, val description: String) {
    NEXT("Lanjut video berikutnya", "Setelah selesai, putar video selanjutnya."),
    STOP("Berhenti", "Berhenti setelah video selesai."),
    REPLAY("Ulangi video", "Putar video yang sama dari awal."),
}

class KajianPlaybackPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun endBehavior(): KajianEndBehavior = runCatching {
        KajianEndBehavior.valueOf(preferences.getString(KEY_END_BEHAVIOR, null) ?: KajianEndBehavior.NEXT.name)
    }.getOrDefault(KajianEndBehavior.NEXT)

    fun setEndBehavior(value: KajianEndBehavior) {
        preferences.edit().putString(KEY_END_BEHAVIOR, value.name).apply()
    }

    companion object {
        private const val FILE_NAME = "kajian-playback-preferences"
        private const val KEY_END_BEHAVIOR = "end-behavior"
    }
}
