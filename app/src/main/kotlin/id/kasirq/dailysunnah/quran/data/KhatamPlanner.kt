package id.kasirq.dailysunnah.quran.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

object KhatamPlanner {
    /** Inclusive day count: the chosen end date is a reading day too. */
    fun pagesPerDay(currentPage: Int, endDate: LocalDate, today: LocalDate): Int? {
        if (endDate.isBefore(today)) return null
        val page = currentPage.coerceIn(MushafAssetRepository.MIN_PAGE, MushafAssetRepository.MAX_PAGE)
        val remainingPages = MushafAssetRepository.MAX_PAGE - page + 1
        val days = ChronoUnit.DAYS.between(today, endDate) + 1
        return ceil(remainingPages.toDouble() / days).toInt().coerceAtLeast(1)
    }
}
