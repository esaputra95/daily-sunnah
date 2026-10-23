package id.kasirq.dailysunnah.quran.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class KhatamPlannerTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test
    fun spreadsRemainingPagesAcrossInclusiveDays() {
        assertEquals(21, KhatamPlanner.pagesPerDay(1, today.plusDays(29), today))
        assertEquals(3, KhatamPlanner.pagesPerDay(600, today.plusDays(1), today))
    }

    @Test
    fun rejectsFinishDateInThePast() {
        assertNull(KhatamPlanner.pagesPerDay(20, today.minusDays(1), today))
    }
}
