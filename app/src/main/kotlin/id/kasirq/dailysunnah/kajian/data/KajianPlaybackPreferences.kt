package id.kasirq.dailysunnah.kajian.data

import android.content.Context

enum class KajianEndBehavior(val label: String, val description: String, val englishLabel: String, val englishDescription: String) {
    NEXT("Lanjut video berikutnya", "Setelah selesai, putar video selanjutnya.", "Play next video", "Start the next video when this one ends."),
    STOP("Berhenti", "Berhenti setelah video selesai.", "Stop", "Stop when this video ends."),
    REPLAY("Ulangi video", "Putar video yang sama dari awal.", "Replay video", "Play this video again from the beginning."),
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
