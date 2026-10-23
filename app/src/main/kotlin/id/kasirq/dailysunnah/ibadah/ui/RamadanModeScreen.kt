package id.kasirq.dailysunnah.ibadah.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.kasirq.dailysunnah.ibadah.data.PrayerCoordinates
import id.kasirq.dailysunnah.ibadah.data.PrayerName
import id.kasirq.dailysunnah.ibadah.data.PrayerTimesCalculator
import id.kasirq.dailysunnah.ibadah.data.RamadanReminderPreferences
import id.kasirq.dailysunnah.ibadah.data.RamadanReminderScheduler
import id.kasirq.dailysunnah.ibadah.data.RamadanReminderSettings
import id.kasirq.dailysunnah.ibadah.data.formatPrayerTime
import id.kasirq.dailysunnah.quran.data.MushafAssetRepository
import id.kasirq.dailysunnah.quran.data.QuranReaderPreferences
import id.kasirq.dailysunnah.quran.data.ReadingGoalStore
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

@Composable
fun RamadanModeScreen(
    now: LocalDateTime,
    coordinates: PrayerCoordinates?,
    correctionMinutes: Map<PrayerName, Int>,
    hijriDate: LocalDate,
    hijriOffsetDays: Int,
    onOpenQuran: () -> Unit,
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val todayHijri = remember(hijriDate, hijriOffsetDays) { HijrahDate.from(hijriDate.plusDays(hijriOffsetDays.toLong())) }
    val hijriYear = todayHijri.get(ChronoField.YEAR)
    val hijriMonth = todayHijri.get(ChronoField.MONTH_OF_YEAR)
    val isRamadan = hijriMonth == 9
    val ramadanYear = if (isRamadan || hijriMonth < 9) hijriYear else hijriYear + 1
    val ramadanStart = remember(ramadanYear) { runCatching { LocalDate.ofEpochDay(HijrahDate.of(ramadanYear, 9, 1).toEpochDay()) }.getOrNull() }
    val scheduleDate = if (isRamadan) now.toLocalDate() else ramadanStart ?: now.toLocalDate()
    val schedule = coordinates?.let {
        PrayerTimesCalculator.calculate(scheduleDate, it.latitude, it.longitude, correctionMinutes = correctionMinutes)
    }
    val fajr = schedule?.event(PrayerName.SUBUH)?.dateTime
    val maghrib = schedule?.event(PrayerName.MAGHRIB)?.dateTime
    val imsak = fajr?.minusMinutes(10)
    val daysInRamadan = remember(ramadanYear) {
        val start = HijrahDate.of(ramadanYear, 9, 1).toEpochDay()
        val shawwal = HijrahDate.of(ramadanYear, 10, 1).toEpochDay()
        (shawwal - start).toInt().coerceIn(29, 30)
    }
    val pagesPerDay = (MushafAssetRepository.MAX_PAGE + daysInRamadan - 1) / daysInRamadan
    val readingGoal = remember(now.toLocalDate()) { ReadingGoalStore(context).load(now.toLocalDate()) }
    val lastReadPage = remember { QuranReaderPreferences(context).lastReadPage() }
    var reminderSettings by remember { mutableStateOf(RamadanReminderPreferences.read(context)) }
    var permissionNotice by remember { mutableStateOf(false) }
    val notificationPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionNotice = !granted
        if (!granted) {
            reminderSettings = reminderSettings.copy(sahurEnabled = false, iftarEnabled = false)
            RamadanReminderPreferences.save(context, reminderSettings)
            RamadanReminderScheduler.scheduleNext(context)
        }
    }

    fun saveReminders(updated: RamadanReminderSettings) {
        reminderSettings = updated
        RamadanReminderPreferences.save(context, updated)
        RamadanReminderScheduler.scheduleNext(context)
        if ((updated.sahurEnabled || updated.iftarEnabled) && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (english) "Ramadan mode" else "Mode Ramadan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (isRamadan) {
                        if (english) "Day ${todayHijri.get(ChronoField.DAY_OF_MONTH)} of Ramadan ${hijriYear} AH"
                        else "Hari ke-${todayHijri.get(ChronoField.DAY_OF_MONTH)} Ramadan ${hijriYear} H"
                    } else {
                        if (english) "Estimated Ramadan start: ${ramadanStart ?: "unavailable"}"
                        else "Perkiraan awal Ramadan: ${ramadanStart ?: "belum tersedia"}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    if (english) "Umm al-Qura estimate. Local moon-sighting announcements may differ."
                    else "Perkiraan kalender Umm al-Qura. Penetapan rukyat setempat dapat berbeda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }

        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (english) "Sahur and iftar" else "Sahur dan berbuka", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (coordinates == null || imsak == null || maghrib == null) {
                    Text(if (english) "Set prayer location to calculate estimated imsak and iftar times." else "Atur lokasi shalat untuk menghitung perkiraan waktu imsak dan berbuka.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeTile(if (english) "Estimated imsak" else "Perkiraan imsak", formatPrayerTime(imsak), Modifier.weight(1f))
                        TimeTile(if (english) "Iftar · Maghrib" else "Berbuka · Maghrib", formatPrayerTime(maghrib), Modifier.weight(1f))
                    }
                    Text(
                        if (english) "Estimated imsak is set 10 minutes before calculated Fajr. Follow your local mosque/Kemenag schedule when available."
                        else "Perkiraan imsak ditampilkan 10 menit sebelum Subuh hasil perhitungan. Ikuti jadwal masjid/Kemenag setempat bila tersedia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isRamadan && maghrib.isAfter(now)) {
                        val minutes = Duration.between(now, maghrib).toMinutes().coerceAtLeast(0)
                        Text(
                            if (english) "${minutes / 60}h ${minutes % 60}m until iftar"
                            else "${minutes / 60}j ${minutes % 60}m menuju berbuka",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (english) "Ramadan Qur’an plan" else "Target tilawah Ramadan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (english) "One khatam is about $pagesPerDay pages a day across $daysInRamadan days. Your current daily target is ${readingGoal.targetPages} pages; last opened page $lastReadPage."
                    else "Satu kali khatam sekitar $pagesPerDay halaman per hari selama $daysInRamadan hari. Target harianmu saat ini ${readingGoal.targetPages} halaman; terakhir membuka halaman $lastReadPage.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenQuran, modifier = Modifier.fillMaxWidth()) {
                    Text(if (english) "Set reading goal and khatam date" else "Atur target tilawah dan tanggal khatam")
                }
            }
        }

        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (english) "Reminders" else "Pengingat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ReminderToggle(
                    title = if (english) "Sahur reminder" else "Pengingat sahur",
                    subtitle = if (english) "20 minutes before estimated imsak" else "20 menit sebelum perkiraan imsak",
                    checked = reminderSettings.sahurEnabled,
                    enabled = coordinates != null,
                    onCheckedChange = { saveReminders(reminderSettings.copy(sahurEnabled = it)) },
                )
                ReminderToggle(
                    title = if (english) "Iftar reminder" else "Pengingat berbuka",
                    subtitle = if (english) "10 minutes before Maghrib" else "10 menit sebelum Maghrib",
                    checked = reminderSettings.iftarEnabled,
                    enabled = coordinates != null,
                    onCheckedChange = { saveReminders(reminderSettings.copy(iftarEnabled = it)) },
                )
                if (coordinates == null) Text(
                    if (english) "Set a prayer location first to schedule these reminders." else "Atur lokasi shalat terlebih dahulu untuk menjadwalkan pengingat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (permissionNotice) Text(
                    if (english) "Allow notifications in Android settings to receive reminders." else "Izinkan notifikasi di pengaturan Android agar pengingat bisa diterima.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TimeTile(title: String, time: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReminderToggle(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
