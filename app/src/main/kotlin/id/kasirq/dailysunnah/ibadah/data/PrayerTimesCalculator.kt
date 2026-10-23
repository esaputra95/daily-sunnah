package id.kasirq.dailysunnah.ibadah.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.acos
import kotlin.math.roundToLong

enum class PrayerName(val label: String, val englishLabel: String) {
    SUBUH("Subuh", "Fajr"),
    TERBIT("Terbit", "Sunrise"),
    DZUHUR("Dzuhur", "Dhuhr"),
    ASHAR("Ashar", "Asr"),
    MAGHRIB("Maghrib", "Maghrib"),
    ISYA("Isya", "Isha"),
}

data class PrayerEvent(val name: PrayerName, val dateTime: LocalDateTime)

data class PrayerDay(val date: LocalDate, val events: List<PrayerEvent>) {
    fun event(name: PrayerName): PrayerEvent? = events.firstOrNull { it.name == name }
}

/** Local Kemenag-style calculation: Subuh -20°, Isya -18°, and Shafi'i Asr. */
object PrayerTimesCalculator {
    private const val SUBUH_ANGLE = 20.0
    private const val ISYA_ANGLE = 18.0
    private const val SUNRISE_SUNSET_ANGLE = 0.833

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault(),
        correctionMinutes: Map<PrayerName, Int> = emptyMap(),
    ): PrayerDay? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val solar = solarPosition(date, latitude, longitude, zoneId) ?: return null
        val sunrise = solar.timeForElevation(-SUNRISE_SUNSET_ANGLE, morning = true) ?: return null
        val sunset = solar.timeForElevation(-SUNRISE_SUNSET_ANGLE, morning = false) ?: return null
        val fajr = solar.timeForElevation(-SUBUH_ANGLE, morning = true) ?: return null
        val isha = solar.timeForElevation(-ISYA_ANGLE, morning = false) ?: return null
        val asrElevation = Math.toDegrees(
            atan(1.0 / (1.0 + tan(Math.toRadians(abs(latitude - solar.declinationDegrees))))),
        )
        val asr = solar.timeForElevation(asrElevation, morning = false) ?: return null

        val events = listOf(
            PrayerEvent(PrayerName.SUBUH, fajr),
            PrayerEvent(PrayerName.TERBIT, sunrise),
            PrayerEvent(PrayerName.DZUHUR, solar.solarNoon.plusMinutes(1)),
            PrayerEvent(PrayerName.ASHAR, asr),
            PrayerEvent(PrayerName.MAGHRIB, sunset),
            PrayerEvent(PrayerName.ISYA, isha),
        )
        val correctedEvents = events.map { event ->
            event.copy(dateTime = event.dateTime.plusMinutes((correctionMinutes[event.name]?.coerceIn(-5, 5) ?: 0).toLong()))
        }
        return PrayerDay(date, correctedEvents)
    }

    fun nextPrayer(
        now: LocalDateTime,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault(),
        correctionMinutes: Map<PrayerName, Int> = emptyMap(),
    ): PrayerEvent? {
        val today = calculate(now.toLocalDate(), latitude, longitude, zoneId, correctionMinutes)?.events.orEmpty()
            .filter { it.name != PrayerName.TERBIT }
        today.firstOrNull { it.dateTime.isAfter(now) }?.let { return it }
        return calculate(now.toLocalDate().plusDays(1), latitude, longitude, zoneId, correctionMinutes)
            ?.events
            ?.firstOrNull { it.name == PrayerName.SUBUH }
    }

    private fun solarPosition(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
    ): SolarPosition? {
        val day = date.dayOfYear
        val gamma = 2.0 * PI / 365.0 * (day - 1)
        val equationOfTime = 229.18 * (
            0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2.0 * gamma) - 0.040849 * sin(2.0 * gamma)
            )
        val declination =
            0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2.0 * gamma) + 0.000907 * sin(2.0 * gamma) -
                0.002697 * cos(3.0 * gamma) + 0.00148 * sin(3.0 * gamma)
        val offsetMinutes = zoneId.rules.getOffset(date.atTime(12, 0).atZone(zoneId).toInstant()).totalSeconds / 60.0
        val solarNoonMinutes = 720.0 - 4.0 * longitude - equationOfTime + offsetMinutes
        val midnight = date.atStartOfDay()
        return SolarPosition(
            latitudeRadians = Math.toRadians(latitude),
            declinationDegrees = Math.toDegrees(declination),
            declinationRadians = declination,
            solarNoon = midnight.plusMinutes(solarNoonMinutes.roundToLong()),
        )
    }

    private data class SolarPosition(
        val latitudeRadians: Double,
        val declinationDegrees: Double,
        val declinationRadians: Double,
        val solarNoon: LocalDateTime,
    ) {
        fun timeForElevation(elevationDegrees: Double, morning: Boolean): LocalDateTime? {
            val elevation = Math.toRadians(elevationDegrees)
            val cosineHourAngle =
                (sin(elevation) - sin(latitudeRadians) * sin(declinationRadians)) /
                    (cos(latitudeRadians) * cos(declinationRadians))
            if (cosineHourAngle !in -1.0..1.0) return null
            val hourAngleDegrees = Math.toDegrees(acos(cosineHourAngle))
            val minutes = (hourAngleDegrees * 4.0).roundToLong()
            return if (morning) solarNoon.minusMinutes(minutes) else solarNoon.plusMinutes(minutes)
        }
    }
}

fun formatPrayerTime(dateTime: LocalDateTime): String = dateTime.toLocalTime().format(
    java.time.format.DateTimeFormatter.ofPattern("HH:mm"),
)

fun formatTimeUntil(target: LocalDateTime, now: LocalDateTime): String {
    val totalMinutes = java.time.Duration.between(now, target).toMinutes().coerceAtLeast(0)
    if (totalMinutes < 1) return "sebentar lagi"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours jam $minutes menit lagi"
        hours > 0 -> "$hours jam lagi"
        else -> "$minutes menit lagi"
    }
}

fun prayerTime(date: LocalDate, time: LocalTime): LocalDateTime = date.atTime(time)
