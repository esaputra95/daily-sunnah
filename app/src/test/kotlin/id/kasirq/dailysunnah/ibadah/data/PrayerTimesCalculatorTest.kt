package id.kasirq.dailysunnah.ibadah.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PrayerTimesCalculatorTest {
    private val date = LocalDate.of(2026, 2, 20)
    private val jakarta = ZoneId.of("Asia/Jakarta")
    private val latitude = -6.2088
    private val longitude = 106.8456

    @Test
    fun jakartaPrayerTimesAreOrderedAndInsideExpectedLocalRanges() {
        val day = PrayerTimesCalculator.calculate(date, latitude, longitude, jakarta)
        assertNotNull(day)
        val events = day!!.events

        assertEquals(
            listOf(PrayerName.SUBUH, PrayerName.TERBIT, PrayerName.DZUHUR, PrayerName.ASHAR, PrayerName.MAGHRIB, PrayerName.ISYA),
            events.map { it.name },
        )
        assertTrue(events.zipWithNext().all { (first, second) -> first.dateTime.isBefore(second.dateTime) })
        assertTrue(day.event(PrayerName.SUBUH)!!.dateTime.hour in 3..5)
        assertTrue(day.event(PrayerName.TERBIT)!!.dateTime.hour in 5..7)
        assertTrue(day.event(PrayerName.DZUHUR)!!.dateTime.hour in 11..13)
        assertTrue(day.event(PrayerName.ASHAR)!!.dateTime.hour in 14..16)
        assertTrue(day.event(PrayerName.MAGHRIB)!!.dateTime.hour in 17..19)
        assertTrue(day.event(PrayerName.ISYA)!!.dateTime.hour in 18..21)
    }

    @Test
    fun nextPrayerSkipsSunriseAndAdvancesToTomorrowAfterIsha() {
        val atMorning = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 10, 0)
        assertEquals(
            PrayerName.DZUHUR,
            PrayerTimesCalculator.nextPrayer(atMorning, latitude, longitude, jakarta)?.name,
        )

        val lateNight = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 23, 0)
        val next = PrayerTimesCalculator.nextPrayer(lateNight, latitude, longitude, jakarta)
        assertEquals(PrayerName.SUBUH, next?.name)
        assertEquals(date.plusDays(1), next?.dateTime?.toLocalDate())
    }

    @Test
    fun invalidOrUnsupportedCoordinatesDoNotInventSchedule() {
        assertNull(PrayerTimesCalculator.calculate(date, 91.0, 0.0, jakarta))
        assertNull(PrayerTimesCalculator.calculate(date, 90.0, 0.0, jakarta))
    }

    @Test
    fun minuteCorrectionsAreAppliedPerEventAndClamped() {
        val base = PrayerTimesCalculator.calculate(date, latitude, longitude, jakarta)!!
        val adjusted = PrayerTimesCalculator.calculate(
            date,
            latitude,
            longitude,
            jakarta,
            mapOf(PrayerName.DZUHUR to 3, PrayerName.ASHAR to 99),
        )!!

        assertEquals(base.event(PrayerName.DZUHUR)!!.dateTime.plusMinutes(3), adjusted.event(PrayerName.DZUHUR)!!.dateTime)
        assertEquals(base.event(PrayerName.ASHAR)!!.dateTime.plusMinutes(5), adjusted.event(PrayerName.ASHAR)!!.dateTime)
        assertEquals(base.event(PrayerName.MAGHRIB)!!.dateTime, adjusted.event(PrayerName.MAGHRIB)!!.dateTime)
    }

    @Test
    fun countdownAndHijriCorrectionAreReadable() {
        assertEquals(
            "1 jam 20 menit lagi",
            formatTimeUntil(LocalDateTime.of(date.year, date.month, date.dayOfMonth, 12, 20), LocalDateTime.of(date.year, date.month, date.dayOfMonth, 11, 0)),
        )
        assertTrue(hijriLabel(date, 0) != hijriLabel(date, 1))
    }
}
