package id.kasirq.dailysunnah.ibadah.data

import android.content.Context
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

object HijriCalendarPreferences {
    private const val PREFS = "hijri_calendar"
    private const val KEY_OFFSET_DAYS = "offset_days"

    fun offsetDays(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_OFFSET_DAYS, 0)
        .coerceIn(-2, 2)

    fun saveOffsetDays(context: Context, offset: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_OFFSET_DAYS, offset.coerceIn(-2, 2))
            .apply()
    }
}

private val hijriMonthNames = listOf(
    "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jumadilawal", "Jumadilakhir",
    "Rajab", "Syakban", "Ramadan", "Syawal", "Zulkaidah", "Zulhijah",
)

fun hijriLabel(date: LocalDate, offsetDays: Int = 0): String {
    val hijri = HijrahDate.from(date.plusDays(offsetDays.toLong()))
    return "${hijri.get(ChronoField.DAY_OF_MONTH)} ${hijriMonthNames[hijri.get(ChronoField.MONTH_OF_YEAR) - 1]} ${hijri.get(ChronoField.YEAR)} H"
}

fun hijriMonthLabel(year: Int, month: Int): String =
    "${hijriMonthNames[month - 1]} $year H"

fun hijriDateFor(year: Int, month: Int, day: Int): HijrahDate = HijrahDate.of(year, month, day)

fun localDateForHijri(year: Int, month: Int, day: Int): LocalDate =
    LocalDate.ofEpochDay(hijriDateFor(year, month, day).toEpochDay())
