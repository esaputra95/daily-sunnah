package id.kasirq.dailysunnah.ibadah.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.kasirq.dailysunnah.ibadah.data.PrecisePrayerLocationProvider
import id.kasirq.dailysunnah.ibadah.data.PrayerCoordinates
import id.kasirq.dailysunnah.ibadah.data.PrayerLocationStore
import id.kasirq.dailysunnah.ibadah.data.PrayerName
import id.kasirq.dailysunnah.ibadah.data.PrayerReminderScheduler
import id.kasirq.dailysunnah.ibadah.data.RamadanReminderScheduler
import id.kasirq.dailysunnah.ibadah.data.PrayerSettings
import id.kasirq.dailysunnah.ibadah.data.PrayerSettingsStore
import id.kasirq.dailysunnah.home.DailySunnahWidgetProvider
import id.kasirq.dailysunnah.ibadah.data.PrayerSoundMode
import id.kasirq.dailysunnah.ui.BackNavigationButton
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSettingsScreen(onBack: () -> Unit) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    var settings by remember { mutableStateOf(PrayerSettingsStore.read(context)) }
    var volumeDraft by remember(settings.adhanVolume) { mutableFloatStateOf(settings.adhanVolume.toFloat()) }
    var coordinates by remember { mutableStateOf(PrayerLocationStore.read(context)) }
    var hasPrecisePermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var showMethodInfo by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testRequested by remember { mutableStateOf(false) }

    fun refreshLocation() {
        isRefreshing = true
        locationMessage = if (english) "Finding precise location…" else "Mencari lokasi presisi…"
        PrecisePrayerLocationProvider.refresh(
            context.applicationContext,
            onLocation = {
                coordinates = it
                DailySunnahWidgetProvider.updateAll(context)
                locationMessage = if (english) "Precise location updated." else "Lokasi presisi diperbarui."
                isRefreshing = false
                PrayerReminderScheduler.scheduleNext(context)
                RamadanReminderScheduler.scheduleNext(context)
            },
            onFailure = {
                locationMessage = if (english) "Could not get precise location. Check location permission and GPS." else it
                isRefreshing = false
            },
        )
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPrecisePermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPrecisePermission) refreshLocation()
        else locationMessage = if (english) "Allow precise location to calculate times from your device coordinates." else "Izinkan lokasi presisi untuk menghitung jadwal dari koordinat perangkat."
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && testRequested) {
            PrayerReminderScheduler.scheduleTest(context)
            testStatus = if (english) "Test notification scheduled in 5 seconds." else "Notifikasi uji dijadwalkan dalam 5 detik."
        } else if (!granted) {
            testStatus = if (english) "Notification permission was not granted." else "Izin notifikasi belum diberikan."
        }
        testRequested = false
    }

    fun updateSettings(transform: (PrayerSettings) -> PrayerSettings) {
        settings = transform(settings)
        PrayerSettingsStore.save(context, settings)
        PrayerReminderScheduler.scheduleNext(context)
        RamadanReminderScheduler.scheduleNext(context)
        DailySunnahWidgetProvider.updateAll(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { BackNavigationButton(onClick = onBack) },
                title = { Text(if (english) "Prayer settings" else "Pengaturan Sholat", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection(if (english) "Calculation method" else "Metode Perhitungan") {
                SettingsCard(onClick = { showMethodInfo = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (english) "Indonesia Ministry of Religious Affairs / SIHAT" else "Kemenag/SIHAT Indonesia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (english) "Local calculation using Indonesian parameters" else "Perhitungan lokal dengan parameter Indonesia", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(if (english) "Recommended for Indonesia. Times are calculated on-device using precise location." else "Direkomendasikan untuk Indonesia. Jadwal dihitung di perangkat menggunakan lokasi presisi.", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsSection(if (english) "Madhhab (Asr time)" else "Mazhab (Waktu Ashar)") {
                SettingsCard(enabled = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (english) "Shafi‘i madhhab" else "Mazhab Syafi’i", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (english) "The current calculation uses the Shafi‘i parameter." else "Metode Kemenag/SIHAT saat ini memakai parameter Syafi’i.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("▣", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(if (english) "Madhhab selection is locked while the Kemenag/SIHAT profile is active." else "Pilihan mazhab dikunci selama profil Kemenag/SIHAT aktif.", modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsSection(if (english) "Time adjustments" else "Koreksi Waktu") {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (english) "Prayer time adjustments" else "Koreksi waktu sholat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Match your local mosque or ministry timetable. Range: −5 to +5 minutes." else "Sesuaikan dengan jadwal masjid atau Kemenag setempat. Rentang −5 sampai +5 menit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrayerName.entries.forEach { prayer ->
                            CorrectionRow(
                                prayer = prayer,
                                english = english,
                                value = settings.correctionFor(prayer),
                                onChange = { value -> updateSettings { current -> current.copy(correctionMinutes = current.correctionMinutes + (prayer to value)) } },
                            )
                        }
                        TextButton(
                            onClick = { updateSettings { it.copy(correctionMinutes = PrayerName.entries.associateWith { 0 }) } },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(if (english) "Reset adjustments" else "Reset koreksi") }
                    }
                }
            }

            SettingsSection(if (english) "Adhan audio" else "Audio Adzan") {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (english) "Adhan volume" else "Volume adzan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${volumeDraft.toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("◖))", color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = volumeDraft,
                            onValueChange = { volumeDraft = it },
                            onValueChangeFinished = { updateSettings { it.copy(adhanVolume = volumeDraft.toInt()) } },
                            valueRange = 0f..100f,
                        )
                        Text(if (english) "A separate adhan audio track is not included yet. This preference is saved for the player; notification volume follows Android settings." else "Audio adzan khusus belum disertakan. Preferensi ini tersimpan untuk pemutarnya; nada notifikasi mengikuti volume Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SettingsSection(if (english) "Notification sound" else "Suara Notifikasi") {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (english) "Sound mode" else "Mode suara", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Adhan uses the device alarm tone; Short uses the notification tone; Silent has no sound." else "Adzan memakai nada alarm perangkat; Singkat memakai nada notifikasi; Senyap tanpa suara.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            PrayerSoundMode.entries.forEach { mode ->
                                ChoiceChip(
                                    label = if (english) when (mode) {
                                        PrayerSoundMode.ADHAN -> "Adhan"
                                        PrayerSoundMode.SHORT -> "Short"
                                        PrayerSoundMode.SILENT -> "Silent"
                                    } else mode.label,
                                    selected = settings.soundMode == mode,
                                    modifier = Modifier.weight(1f),
                                    onClick = { updateSettings { it.copy(soundMode = mode) } },
                                )
                            }
                        }
                    }
                }
            }

            SettingsSection(if (english) "Reminders" else "Pengingat") {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (english) "Prayer notifications" else "Notifikasi per shalat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Choose which prayers send a reminder." else "Pilih waktu shalat yang mengirim pengingat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        PrayerSettingsStore.notificationNames().forEach { prayer ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (english) prayer.englishLabel else prayer.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(if (english) "${settings.correctionFor(prayer).let { if (it > 0) "+$it" else "$it" }} min adjustment" else "${settings.correctionFor(prayer).let { if (it > 0) "+$it" else "$it" }} menit koreksi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = settings.notificationFor(prayer),
                                    onCheckedChange = { enabled ->
                                        updateSettings { it.copy(notificationsEnabled = it.notificationsEnabled + (prayer to enabled)) }
                                        if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                )
                            }
                        }
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            Text(if (english) "Notification permission is required for reminders." else "Izin notifikasi diperlukan agar pengingat bisa muncul.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text(if (english) "Allow notifications" else "Izinkan notifikasi")
                            }
                        }
                    }
                }
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (english) "Advance reminder" else "Pengingat persiapan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Send a reminder before prayer time." else "Kirim pengingat sebelum waktu shalat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(0, 5, 10, 15).forEach { minutes ->
                                ChoiceChip(
                                    label = if (minutes == 0) { if (english) "Off" else "Mati" } else if (english) "$minutes min" else "$minutes mnt",
                                    selected = settings.preparationMinutes == minutes,
                                    modifier = Modifier.weight(1f),
                                    onClick = { updateSettings { it.copy(preparationMinutes = minutes) } },
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            testRequested = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PrayerReminderScheduler.scheduleTest(context)
                            testStatus = if (english) "Test notification scheduled in 5 seconds." else "Notifikasi uji dijadwalkan dalam 5 detik."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text(if (english) "Test adhan notification (5 seconds)" else "Uji notifikasi adzan (5 detik)", fontWeight = FontWeight.SemiBold) }
                if (testStatus != null) Text(testStatus!!, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            SettingsSection(if (english) "Location" else "Lokasi") {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (coordinates != null) {
                            Text(if (english) "Precise coordinates" else "Koordinat presisi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${"%.6f".format(Locale.US, coordinates!!.latitude)}, ${"%.6f".format(Locale.US, coordinates!!.longitude)}", style = MaterialTheme.typography.bodyLarge)
                            Text(if (english) { if (coordinates!!.accuracyMeters > 0f) "Accuracy ±${coordinates!!.accuracyMeters.toInt()} m" else "Coordinates stored on device" } else if (coordinates!!.accuracyMeters > 0f) "Akurasi ±${coordinates!!.accuracyMeters.toInt()} m" else "Koordinat tersimpan di perangkat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(if (english) "Precise location unavailable" else "Lokasi presisi belum tersedia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (locationMessage != null) Text(locationMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = if (hasPrecisePermission) ::refreshLocation else {
                                { locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                            },
                            enabled = !isRefreshing,
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (english) { if (hasPrecisePermission) "Refresh precise location" else "Allow precise location" } else { if (hasPrecisePermission) "Perbarui lokasi presisi" else "Izinkan lokasi presisi" })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showMethodInfo) {
        AlertDialog(
            onDismissRequest = { showMethodInfo = false },
            title = { Text(if (english) "Kemenag/SIHAT Indonesia" else "Kemenag/SIHAT Indonesia") },
            text = { Text(if (english) "The local profile uses a 20° Fajr angle, 18° Isha angle, −0.833° sunrise/sunset angle, and Shafi‘i Asr. Manual adjustments below are applied after calculation." else "Profil lokal memakai sudut Subuh 20°, Isya 18°, waktu terbit/terbenam −0,833°, dan Ashar Syafi’i. Koreksi manual di bawah diterapkan setelah perhitungan.") },
            confirmButton = { TextButton(onClick = { showMethodInfo = false }) { Text(if (english) "Close" else "Tutup") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = Modifier.padding(start = 2.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun CorrectionRow(prayer: PrayerName, english: Boolean, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (english) prayer.englishLabel else prayer.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        SmallAction("−", enabled = value > -5) { onChange(value - 1) }
        Text("${if (value > 0) "+" else ""}$value m", modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        SmallAction("+", enabled = value < 5) { onChange(value + 1) }
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(38.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(42.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, modifier = Modifier.padding(horizontal = 5.dp), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
