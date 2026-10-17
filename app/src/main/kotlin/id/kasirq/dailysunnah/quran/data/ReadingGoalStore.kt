package id.kasirq.dailysunnah.quran.data

import android.content.Context
import java.time.LocalDate

data class ReadingGoalSnapshot(
    val targetPages: Int,
    val reminderMinutes: List<Int>,
    val completedPages: Set<Int>,
) {
    val completedCount: Int get() = completedPages.size
    val isComplete: Boolean get() = completedCount >= targetPages
}

class ReadingGoalStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(today: LocalDate = LocalDate.now()): ReadingGoalSnapshot {
        val date = today.toString()
        val storedDate = preferences.getString(KEY_PROGRESS_DATE, null)
        val completedPages = if (storedDate == date) {
            preferences.getString(KEY_COMPLETED_PAGES, "")
                .orEmpty()
                .split(',')
                .mapNotNull(String::toIntOrNull)
                .filter { it in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE }
                .toSet()
        } else {
            preferences.edit()
                .putString(KEY_PROGRESS_DATE, date)
                .putString(KEY_COMPLETED_PAGES, "")
                .apply()
            emptySet()
        }
        return ReadingGoalSnapshot(
            targetPages = preferences.getInt(KEY_TARGET_PAGES, DEFAULT_TARGET_PAGES).coerceIn(1, MushafAssetRepository.MAX_PAGE),
            reminderMinutes = preferences.getString(KEY_REMINDERS, "")
                .orEmpty()
                .split(',')
                .mapNotNull(String::toIntOrNull)
                .filter { it in 0..1439 }
                .distinct()
                .sorted(),
            completedPages = completedPages,
        )
    }

    @Synchronized
    fun togglePage(page: Int, today: LocalDate = LocalDate.now()): ReadingGoalSnapshot {
        require(page in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE)
        val current = load(today)
        val updated = current.completedPages.toMutableSet().apply {
            if (!add(page)) remove(page)
        }
        preferences.edit()
            .putString(KEY_PROGRESS_DATE, today.toString())
            .putString(KEY_COMPLETED_PAGES, updated.sorted().joinToString(","))
            .apply()
        return current.copy(completedPages = updated)
    }

    @Synchronized
    fun markPageRead(page: Int, today: LocalDate = LocalDate.now()): ReadingGoalSnapshot {
        require(page in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE)
        val current = load(today)
        if (page in current.completedPages) return current

        val updated = current.completedPages + page
        preferences.edit()
            .putString(KEY_PROGRESS_DATE, today.toString())
            .putString(KEY_COMPLETED_PAGES, updated.sorted().joinToString(","))
            .apply()
        return current.copy(completedPages = updated)
    }

    @Synchronized
    fun unmarkPagesRead(pages: Set<Int>, today: LocalDate = LocalDate.now()): ReadingGoalSnapshot {
        require(pages.all { it in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE })
        val current = load(today)
        val updated = current.completedPages - pages
        if (updated == current.completedPages) return current

        preferences.edit()
            .putString(KEY_PROGRESS_DATE, today.toString())
            .putString(KEY_COMPLETED_PAGES, updated.sorted().joinToString(","))
            .apply()
        return current.copy(completedPages = updated)
    }

    @Synchronized
    fun updateSettings(targetPages: Int, reminderMinutes: List<Int>): ReadingGoalSnapshot {
        require(targetPages in 1..MushafAssetRepository.MAX_PAGE)
        val normalizedReminders = reminderMinutes.distinct().filter { it in 0..1439 }.sorted().take(MAX_REMINDERS)
        preferences.edit()
            .putInt(KEY_TARGET_PAGES, targetPages)
            .putString(KEY_REMINDERS, normalizedReminders.joinToString(","))
            .apply()
        return load()
    }

    companion object {
        const val DEFAULT_TARGET_PAGES = 5
        const val MAX_REMINDERS = 6
        private const val FILE_NAME = "quran-reading-goal"
        private const val KEY_TARGET_PAGES = "target-pages"
        private const val KEY_REMINDERS = "reminder-minutes"
        private const val KEY_PROGRESS_DATE = "progress-date"
        private const val KEY_COMPLETED_PAGES = "completed-pages"
    }
}
