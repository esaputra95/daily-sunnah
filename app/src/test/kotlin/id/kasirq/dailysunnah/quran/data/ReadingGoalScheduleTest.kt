package id.kasirq.dailysunnah.quran.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReadingGoalScheduleTest {
    private val date = LocalDate.of(2026, 10, 13)

    @Test
    fun selectsNextConfiguredReminderToday() {
        val next = ReadingGoalSchedule.nextOccurrence(
            date,
            LocalTime.of(17, 15),
            listOf(21 * 60, 19 * 60, 15 * 60),
        )

        assertEquals(LocalDateTime.of(2026, 10, 13, 19, 0), next)
    }

    @Test
    fun movesToFirstReminderTomorrowAfterLastTime() {
        val next = ReadingGoalSchedule.nextOccurrence(
            date,
            LocalTime.of(23, 59),
            listOf(21 * 60, 19 * 60),
        )

        assertEquals(LocalDateTime.of(2026, 10, 14, 19, 0), next)
    }

    @Test
    fun ignoresInvalidDuplicateTimesAndReturnsNullWhenNoneRemain() {
        assertEquals(
            LocalDateTime.of(2026, 10, 13, 8, 30),
            ReadingGoalSchedule.nextOccurrence(date, LocalTime.of(8, 0), listOf(-1, 510, 510, 1440)),
        )
        assertNull(ReadingGoalSchedule.nextOccurrence(date, LocalTime.NOON, listOf(-1, 1440)))
    }
}
