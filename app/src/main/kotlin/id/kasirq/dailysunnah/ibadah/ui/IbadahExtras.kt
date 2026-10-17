package id.kasirq.dailysunnah.ibadah.ui

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import id.kasirq.dailysunnah.ui.ModernTextFieldShape
import id.kasirq.dailysunnah.ui.modernTextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.kasirq.dailysunnah.R
import id.kasirq.dailysunnah.ibadah.data.PrayerCoordinates
import id.kasirq.dailysunnah.ibadah.data.SunnahFastingReminderScheduler
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun QiblaScreen(coordinates: PrayerCoordinates?) {
    val context = LocalContext.current
    var magneticHeading by remember { mutableStateOf<Float?>(null) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    DisposableEffect(sensor) {
        if (sensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val matrix = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, orientation)
                    magneticHeading = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        } else onDispose { }
    }
    val qiblaBearing = remember(coordinates) { coordinates?.let { bearingToKaaba(it.latitude, it.longitude) } }
    val trueHeading = remember(magneticHeading, coordinates) {
        val location = coordinates
        val heading = magneticHeading
        if (location == null || heading == null) heading else {
            val declination = GeomagneticField(location.latitude.toFloat(), location.longitude.toFloat(), 0f, System.currentTimeMillis()).declination
            (heading + declination + 360f) % 360f
        }
    }
    val relative = if (qiblaBearing != null && trueHeading != null) ((qiblaBearing - trueHeading + 540f) % 360f) - 180f else null
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Arah kiblat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("↑", modifier = Modifier.rotate(relative ?: 0f), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text(qiblaBearing?.let { "Arah Ka’bah ${it.toInt()}° dari utara sejati" } ?: "Aktifkan lokasi presisi untuk menghitung arah kiblat.", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        sensor == null -> "Sensor kompas tidak tersedia; gunakan arah numerik saat perangkat menghadap utara."
                        relative == null -> "Pegang perangkat datar untuk membaca kompas."
                        else -> "Putar perangkat ${relative.toInt()}° menuju kiblat. Kompas bergantung pada kalibrasi sensor."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (trueHeading != null) Text("Arah perangkat ${trueHeading.toInt()}°", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text("Arah dihitung dari koordinat perangkat dan posisi Ka’bah. Gangguan magnetik dapat mengurangi akurasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun bearingToKaaba(latitude: Double, longitude: Double): Float {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(21.4225)
    val deltaLon = Math.toRadians(39.8262 - longitude)
    val y = sin(deltaLon)
    val x = cos(lat1) * kotlin.math.tan(lat2) - sin(lat1) * cos(deltaLon)
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

private data class Dhikr(val arabic: String, val label: String, val target: Int = 33)

private val postPrayerDhikr = listOf(
    Dhikr("سُبْحَانَ اللَّهِ", "Subhanallah · 33 kali"),
    Dhikr("الْحَمْدُ لِلَّهِ", "Alhamdulillah · 33 kali"),
    Dhikr("اللَّهُ أَكْبَرُ", "Allahu akbar · 33 kali"),
    Dhikr("لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ", "Tahlil penutup · 1 kali", 1),
)

private val dhikrArabicFontFamily = FontFamily(Font(R.font.kfgqpc_nastaleeq_regular))

@Composable
fun DhikrScreen() {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var count by rememberSaveable { mutableIntStateOf(dhikrCount(context, 0)) }
    val entry = postPrayerDhikr[selected]
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Dzikir setelah shalat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.arabic,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = dhikrArabicFontFamily,
                        fontSize = 28.sp,
                        lineHeight = 48.sp,
                        textAlign = TextAlign.Center,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("$count / ${entry.target}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Button(onClick = {
                    count = if (count >= entry.target) 0 else count + 1
                    context.getSharedPreferences("ibadah-dhikr", Context.MODE_PRIVATE).edit().putInt("$selected-${LocalDate.now()}", count).apply()
                }, modifier = Modifier.fillMaxWidth()) { Text(if (count >= entry.target) "Ulangi hitungan" else "Hitung dzikir") }
                OutlinedButton(onClick = {
                    count = 0
                    context.getSharedPreferences("ibadah-dhikr", Context.MODE_PRIVATE).edit().putInt("$selected-${LocalDate.now()}", 0).apply()
                }) { Text("Atur ulang") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { selected = (selected - 1).coerceAtLeast(0); count = dhikrCount(context, selected) }) { Text("Sebelumnya") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { selected = (selected + 1).coerceAtMost(postPrayerDhikr.lastIndex); count = dhikrCount(context, selected) }) { Text("Berikutnya") }
        }
        Text("Urutan dzikir dan hitungan bersumber dari Sahih Muslim 597a. Hitungan tersimpan lokal per hari.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun dhikrCount(context: Context, index: Int): Int = context.getSharedPreferences("ibadah-dhikr", Context.MODE_PRIVATE)
    .getInt("$index-${LocalDate.now()}", 0).coerceIn(0, postPrayerDhikr[index].target)

private val trackedActs = listOf("Subuh", "Dzuhur", "Asar", "Maghrib", "Isya", "Tilawah", "Dzikir")

@Composable
fun AmalanTrackerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ibadah-tracker", Context.MODE_PRIVATE) }
    val date = LocalDate.now().toString()
    var uzur by rememberSaveable(date) { mutableStateOf(prefs.getBoolean("uzur-$date", false)) }
    var revision by rememberSaveable(date) { mutableIntStateOf(0) }
    val doneCount = trackedActs.withIndex().count { (index, activity) ->
        !(uzur && index < 5) && prefs.getBoolean("done-$date-$activity", false)
    }
    val lastSevenDays = (0L..6L).map { LocalDate.now().minusDays(it).toString() }
    val weekDone = lastSevenDays.sumOf { day ->
        val isUzur = prefs.getBoolean("uzur-$day", false)
        trackedActs.withIndex().count { (index, activity) -> (!isUzur || index >= 5) && prefs.getBoolean("done-$day-$activity", false) }
    }
    val weekPossible = lastSevenDays.sumOf { day -> trackedActs.size - if (prefs.getBoolean("uzur-$day", false)) 5 else 0 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tracker amalan hari ini", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("$doneCount / ${trackedActs.size - if (uzur) 5 else 0} aktivitas dicatat", style = MaterialTheme.typography.bodyMedium)
        Text("7 hari terakhir: $weekDone / $weekPossible catatan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mode uzur", fontWeight = FontWeight.SemiBold)
                        Text("Menandai shalat sebagai tidak berlaku untuk ringkasan hari ini.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = uzur, onCheckedChange = {
                        uzur = it
                        prefs.edit().putBoolean("uzur-$date", it).apply()
                        revision++
                    })
                }
            }
        }
        trackedActs.forEachIndexed { index, activity ->
            val isPrayer = index < 5
            val checked = prefs.getBoolean("done-$date-$activity", false)
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(activity, fontWeight = FontWeight.Medium)
                        Text(if (uzur && isPrayer) "Tidak berlaku · tidak masuk hitungan" else if (checked) "Sudah dilakukan" else "Belum dicatat", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = checked,
                        enabled = !(uzur && isPrayer),
                        onCheckedChange = { prefs.edit().putBoolean("done-$date-$activity", it).apply(); revision++ },
                    )
                }
            }
        }
        Text("Riwayat dicatat lokal per tanggal. Tracker ini alat pribadi, bukan penilaian ibadah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (revision < 0) Text("")
    }
}

@Composable
fun SunnahFastingScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sunnah-fasting-reminder", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var time by remember { mutableStateOf(prefs.getString("time", "17:00") ?: "17:00") }
    var hour by remember { mutableIntStateOf(time.substringBefore(':').toIntOrNull() ?: 17) }
    var minute by remember { mutableIntStateOf(time.substringAfter(':').toIntOrNull() ?: 0) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            enabled = false
            prefs.edit().putBoolean("enabled", false).apply()
            SunnahFastingReminderScheduler.schedule(context)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Puasa sunnah", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Pengingat Senin dan Kamis. Waktu pengingat bisa kamu atur; kalender Hijriah tetap menjadi rujukan untuk hari khusus.")
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Pengingat mingguan", fontWeight = FontWeight.SemiBold); Text("Senin dan Kamis · $time", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean("enabled", it).apply()
                    SunnahFastingReminderScheduler.schedule(context)
                    if (it && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
            }
        }
        OutlinedTextField(
            value = time,
            onValueChange = { value ->
                val candidate = value.take(5)
                if (candidate.matches(Regex("\\d{0,2}:?\\d{0,2}"))) {
                    time = candidate
                    val parts = candidate.split(':')
                    hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: hour
                    minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: minute
                }
            },
            shape = ModernTextFieldShape,
            colors = modernTextFieldColors(),
            label = { Text("Jam pengingat (HH:mm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            if (time.matches(Regex("\\d{2}:\\d{2}")) && hour in 0..23 && minute in 0..59) {
                prefs.edit().putString("time", time).apply()
                SunnahFastingReminderScheduler.schedule(context)
            }
        }) { Text("Simpan waktu") }
        Text("Hari Tasyrik dan hari yang tidak dianjurkan berpuasa tidak dihitung otomatis pada pengingat mingguan; nonaktifkan/pastikan jadwal sesuai kondisi pribadi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
