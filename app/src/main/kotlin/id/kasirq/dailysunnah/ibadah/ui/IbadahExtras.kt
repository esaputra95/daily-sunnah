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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import id.kasirq.dailysunnah.ui.ModernTextFieldShape
import id.kasirq.dailysunnah.ui.modernTextFieldColors
import id.kasirq.dailysunnah.ui.LocalAppEnglish
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val english = LocalAppEnglish.current
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
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (english) "Find the Qibla" else "Arah Kiblat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (english) "Point your device toward the Kaaba" else "Arahkan perangkat menuju Ka’bah", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outline
                val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
                val secondaryColor = MaterialTheme.colorScheme.secondary
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(276.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                            val centerPoint = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.minDimension * 0.47f
                            drawCircle(primaryColor.copy(alpha = 0.045f), radius, centerPoint)
                            drawCircle(outlineColor.copy(alpha = 0.55f), radius, centerPoint, style = Stroke(width = 1.5.dp.toPx()))
                            drawCircle(outlineColor.copy(alpha = 0.20f), radius * 0.68f, centerPoint, style = Stroke(width = 1.dp.toPx()))
                            repeat(60) { tick ->
                                val angle = Math.toRadians((tick * 6).toDouble())
                                val major = tick % 5 == 0
                                val innerRadius = radius * if (major) 0.82f else 0.89f
                                val start = Offset(centerPoint.x + kotlin.math.sin(angle).toFloat() * innerRadius, centerPoint.y - kotlin.math.cos(angle).toFloat() * innerRadius)
                                val end = Offset(centerPoint.x + kotlin.math.sin(angle).toFloat() * radius * 0.96f, centerPoint.y - kotlin.math.cos(angle).toFloat() * radius * 0.96f)
                                drawLine(
                                    color = mutedColor.copy(alpha = if (major) 0.62f else 0.28f),
                                    start = start,
                                    end = end,
                                    strokeWidth = if (major) 1.8.dp.toPx() else 1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(top = 19.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("S", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 19.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text("W", modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text("E", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Canvas(Modifier.fillMaxSize().padding(50.dp)) {
                            val centerPoint = Offset(size.width / 2f, size.height / 2f)
                            rotate(degrees = relative ?: 0f, pivot = centerPoint) {
                                val radius = size.minDimension * 0.43f
                                val arrow = Path().apply {
                                    moveTo(centerPoint.x, centerPoint.y - radius)
                                    lineTo(centerPoint.x - radius * 0.17f, centerPoint.y - radius * 0.56f)
                                    lineTo(centerPoint.x, centerPoint.y - radius * 0.64f)
                                    lineTo(centerPoint.x + radius * 0.17f, centerPoint.y - radius * 0.56f)
                                    close()
                                }
                                drawPath(arrow, primaryColor)
                                drawLine(
                                    color = secondaryColor,
                                    start = centerPoint,
                                    end = Offset(centerPoint.x, centerPoint.y + radius * 0.42f),
                                    strokeWidth = 5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        KaabaMarker(Modifier.size(38.dp))
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QiblaReading(
                        modifier = Modifier.weight(1f),
                        label = if (english) "QIBLA BEARING" else "ARAH KA’BAH",
                        value = qiblaBearing?.let { "${it.toInt()}°" } ?: "--°",
                    )
                    QiblaReading(
                        modifier = Modifier.weight(1f),
                        label = if (english) "DEVICE HEADING" else "ARAH PERANGKAT",
                        value = trueHeading?.let { "${it.toInt()}°" } ?: "--°",
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                coordinates == null -> if (english) "Allow location to calculate the Qibla bearing." else "Izinkan lokasi agar arah Ka’bah dapat dihitung."
                                sensor == null -> if (english) "Compass sensor unavailable. Use the bearing with your device facing north." else "Sensor kompas tidak tersedia. Gunakan angka arah saat perangkat menghadap utara."
                                relative == null -> if (english) "Hold the device flat and move away from magnetic objects." else "Pegang perangkat mendatar dan jauhkan dari benda bermagnet."
                                else -> if (english) "Turn ${kotlin.math.abs(relative).toInt()}° ${if (relative >= 0f) "clockwise" else "counterclockwise"} until the pointer faces the Kaaba." else "Putar ${kotlin.math.abs(relative).toInt()}° ${if (relative >= 0f) "searah jarum jam" else "berlawanan arah jarum jam"} sampai penunjuk mengarah ke Ka’bah."
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Text(
            if (english) "Direction uses your device location and compass sensor. Calibrate the compass if the pointer looks unstable." else "Arah memakai lokasi perangkat dan sensor kompas. Kalibrasikan kompas bila penunjuk terasa tidak stabil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QiblaReading(modifier: Modifier = Modifier, label: String, value: String) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KaabaMarker(modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(9.dp), color = androidx.compose.ui.graphics.Color(0xFF252A29), shadowElevation = 5.dp) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            drawLine(
                androidx.compose.ui.graphics.Color(0xFFD9B45E),
                Offset(0f, size.height * 0.34f),
                Offset(size.width, size.height * 0.34f),
                strokeWidth = size.height * 0.12f,
            )
            drawRect(
                androidx.compose.ui.graphics.Color(0xFFD9B45E),
                topLeft = Offset(size.width * 0.62f, size.height * 0.52f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.18f, size.height * 0.48f),
            )
        }
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

private data class PostPrayerDhikr(
    val id: String,
    val arabic: String,
    val label: String,
    val meaning: String,
    val target: Int = 1,
    val source: String,
    val labelEnglish: String = label,
    val meaningEnglish: String = meaning,
    val sourceEnglish: String = source,
)

private val postPrayerDhikr = listOf(
    PostPrayerDhikr(
        id = "istighfar-salam",
        arabic = "أَسْتَغْفِرُ اللَّهَ\nأَسْتَغْفِرُ اللَّهَ\nأَسْتَغْفِرُ اللَّهَ\nاللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ",
        label = "Istighfar 3 kali, lalu doa setelah salam",
        meaning = "Memohon ampun kepada Allah, lalu memohon keselamatan dan keberkahan dari-Nya.",
        source = "Dzikir Sesudah Shalat Fardhu · HR. Muslim 591",
        labelEnglish = "Seek forgiveness 3 times, then the post-prayer supplication",
        meaningEnglish = "Ask Allah for forgiveness, then ask Him for peace and blessing.",
        sourceEnglish = "Post-Prayer Remembrance · Sahih Muslim 591",
    ),
    PostPrayerDhikr(
        id = "tahlil-after-salah",
        arabic = "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ اللَّهُمَّ لَا مَانِعَ لِمَا أَعْطَيْتَ وَلَا مُعْطِيَ لِمَا مَنَعْتَ وَلَا يَنْفَعُ ذَا الْجَدِّ مِنْكَ الْجَدُّ",
        label = "Tahlil dan doa setelah shalat",
        meaning = "Menegaskan keesaan Allah dan bahwa pemberian maupun ketetapan-Nya tidak dapat dihalangi.",
        source = "Dzikir Sesudah Shalat Fardhu · HR. Al-Bukhari dan Muslim",
        labelEnglish = "Tahlil and supplication after prayer",
        meaningEnglish = "Affirm Allah’s oneness and that no one can withhold what He gives or grant what He withholds.",
        sourceEnglish = "Post-Prayer Remembrance · Sahih al-Bukhari and Muslim",
    ),
    PostPrayerDhikr(
        id = "tahlil-pujian",
        arabic = "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ لَا إِلَٰهَ إِلَّا اللَّهُ وَلَا نَعْبُدُ إِلَّا إِيَّاهُ لَهُ النِّعْمَةُ وَلَهُ الْفَضْلُ وَلَهُ الثَّنَاءُ الْحَسَنُ لَا إِلَٰهَ إِلَّا اللَّهُ مُخْلِصِينَ لَهُ الدِّينَ وَلَوْ كَرِهَ الْكَافِرُونَ",
        label = "Tahlil, hauqalah, dan pujian",
        meaning = "Memuji Allah dan memurnikan ibadah hanya untuk-Nya.",
        source = "Dzikir Sesudah Shalat Fardhu · HR. Muslim",
        labelEnglish = "Tahlil, declaration of reliance, and praise",
        meaningEnglish = "Praise Allah and devote worship sincerely to Him alone.",
        sourceEnglish = "Post-Prayer Remembrance · Sahih Muslim",
    ),
    PostPrayerDhikr(
        id = "subuh-maghrib-tahlil",
        arabic = "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ يُحْيِي وَيُمِيتُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ",
        label = "Khusus Subuh & Maghrib · 10 kali",
        meaning = "Menegaskan bahwa hanya Allah yang berhak disembah dan berkuasa atas hidup dan mati.",
        target = 10,
        source = "Dzikir Sesudah Shalat Fardhu · bacaan tambahan Subuh dan Maghrib",
        labelEnglish = "For Fajr and Maghrib only · 10 times",
        meaningEnglish = "Affirm that Allah alone is worthy of worship and has power over life and death.",
        sourceEnglish = "Post-Prayer Remembrance · additional recitation after Fajr and Maghrib",
    ),
    PostPrayerDhikr(
        id = "tasbih",
        arabic = "سُبْحَانَ اللَّهِ",
        label = "Subhanallah · 33 kali",
        meaning = "Menyucikan Allah dari segala kekurangan.",
        target = 33,
        source = "Dzikir Sesudah Shalat Fardhu · HR. Muslim 597",
        labelEnglish = "Glory be to Allah · 33 times",
        meaningEnglish = "Declare Allah free from every imperfection.",
        sourceEnglish = "Post-Prayer Remembrance · Sahih Muslim 597",
    ),
    PostPrayerDhikr(
        id = "tahmid",
        arabic = "الْحَمْدُ لِلَّهِ",
        label = "Alhamdulillah · 33 kali",
        meaning = "Segala puji dan syukur adalah milik Allah.",
        target = 33,
        source = "Dzikir Sesudah Shalat Fardhu · HR. Muslim 597",
        labelEnglish = "Praise be to Allah · 33 times",
        meaningEnglish = "All praise and gratitude belong to Allah.",
        sourceEnglish = "Post-Prayer Remembrance · Sahih Muslim 597",
    ),
    PostPrayerDhikr(
        id = "takbir",
        arabic = "اللَّهُ أَكْبَرُ",
        label = "Allahu akbar · 33 kali",
        meaning = "Allah Mahabesar, melebihi segala sesuatu.",
        target = 33,
        source = "Dzikir Sesudah Shalat Fardhu · HR. Muslim 597",
        labelEnglish = "Allah is the Greatest · 33 times",
        meaningEnglish = "Allah is greater than everything.",
        sourceEnglish = "Post-Prayer Remembrance · Sahih Muslim 597",
    ),
    PostPrayerDhikr(
        id = "tahlil-completion",
        arabic = "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ",
        label = "Penyempurna hitungan ke-100 · 1 kali",
        meaning = "Menyempurnakan rangkaian tasbih dengan mengulang tahlil.",
        source = "Dzikir Sesudah Shalat Fardhu · HR. Muslim 597",
        labelEnglish = "To complete the count of 100 · once",
        meaningEnglish = "Complete the remembrance sequence by repeating the declaration of Allah’s oneness.",
        sourceEnglish = "Post-Prayer Remembrance · Sahih Muslim 597",
    ),
    PostPrayerDhikr(
        id = "ayat-kursi-after-salah",
        arabic = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ وَلَا يَئُودُهُ حِفْظُهُمَا وَهُوَ الْعَلِيُّ الْعَظِيمُ",
        label = "Ayat Kursi · 1 kali",
        meaning = "Menegaskan keesaan, ilmu, kekuasaan, dan penjagaan Allah atas seluruh makhluk.",
        source = "Dzikir Sesudah Shalat Fardhu · An-Nasa’i al-Kubra 9848",
        labelEnglish = "Verse of the Throne · once",
        meaningEnglish = "Affirm Allah’s oneness, knowledge, power, and protection over all creation.",
        sourceEnglish = "Post-Prayer Remembrance · Sunan al-Nasa’i al-Kubra 9848",
    ),
    PostPrayerDhikr(
        id = "three-quls-after-salah",
        arabic = "قُلْ هُوَ اللَّهُ أَحَدٌ ۞ اللَّهُ الصَّمَدُ ۞ لَمْ يَلِدْ وَلَمْ يُولَدْ ۞ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ ۞\n\nقُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۞ مِنْ شَرِّ مَا خَلَقَ ۞ وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ ۞ وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ۞ وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ ۞\n\nقُلْ أَعُوذُ بِرَبِّ النَّاسِ ۞ مَلِكِ النَّاسِ ۞ إِلَٰهِ النَّاسِ ۞ مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ۞ الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ۞ مِنَ الْجِنَّةِ وَالنَّاسِ ۞",
        label = "Tiga Qul · khusus Subuh & Maghrib · 3 kali",
        meaning = "Memohon perlindungan Allah dari kejahatan makhluk, hasad, dan bisikan setan.",
        target = 3,
        source = "Dzikir Sesudah Shalat Fardhu · HR. Abu Dawud 1523",
        labelEnglish = "The three Quls · Fajr and Maghrib only · 3 times",
        meaningEnglish = "Seek Allah’s protection from created evil, envy, and the whispering of Satan.",
        sourceEnglish = "Post-Prayer Remembrance · Sunan Abi Dawud 1523",
    ),
)

private enum class DhikrPeriod(val title: String, val englishTitle: String, val preferenceKey: String) {
    MORNING("Pagi", "Morning", "morning"),
    EVENING("Petang", "Evening", "evening"),
    AFTER_PRAYER("Setelah Shalat", "After prayer", "after-prayer"),
}

private data class MorningEveningDhikr(
    val id: String,
    val title: String,
    val morningArabic: String,
    val eveningArabic: String = morningArabic,
    val meaning: String,
    val target: Int = 1,
    val source: String,
    val englishTitle: String = title,
    val englishMeaning: String = meaning,
    val englishSource: String = source,
)

private val morningEveningDhikr = listOf(
    MorningEveningDhikr(
        id = "ayat-kursi",
        title = "Ayat Kursi",
        morningArabic = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
        meaning = "Pujian kepada Allah Yang Maha Hidup dan terus mengurus seluruh makhluk; seluruh langit dan bumi berada dalam kekuasaan-Nya.",
        source = "Al-Qur’an, Al-Baqarah 2:255",
        englishTitle = "Verse of the Throne",
        englishMeaning = "Praise of Allah, the Ever-Living and Sustainer of all creation; the heavens and earth belong to Him.",
        englishSource = "Qur’an, Al-Baqarah 2:255",
    ),
    MorningEveningDhikr(
        id = "tiga-qul",
        title = "Al-Ikhlas, Al-Falaq, dan An-Nas",
        morningArabic = "قُلْ هُوَ اللَّهُ أَحَدٌ ۝ اللَّهُ الصَّمَدُ ۝ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ ۝\n\nقُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۝ مِنْ شَرِّ مَا خَلَقَ ۝ وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ ۝ وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ۝ وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ ۝\n\nقُلْ أَعُوذُ بِرَبِّ النَّاسِ ۝ مَلِكِ النَّاسِ ۝ إِلَٰهِ النَّاسِ ۝ مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ۝ الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ۝ مِنَ الْجِنَّةِ وَالنَّاسِ ۝",
        meaning = "Membaca tiga surah perlindungan: Al-Ikhlas, Al-Falaq, dan An-Nas.",
        target = 3,
        source = "HR. Abu Dawud 5082; At-Tirmidzi 3575",
        englishTitle = "Al-Ikhlas, Al-Falaq, and An-Nas",
        englishMeaning = "Recite the three chapters of protection: Al-Ikhlas, Al-Falaq, and An-Nas.",
        englishSource = "Sunan Abi Dawud 5082; Jami` at-Tirmidhi 3575",
    ),
    MorningEveningDhikr(
        id = "hasbiyallah",
        title = "Hasbiyallahu la ilaha illa huwa",
        morningArabic = "حَسْبِيَ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
        meaning = "Cukuplah Allah bagiku; kepada-Nya aku bertawakal, Rabb pemilik Arsy yang agung.",
        target = 7,
        source = "HR. Ibn as-Sunni 71",
        englishMeaning = "Allah is sufficient for me; in Him I place my trust, the Lord of the Mighty Throne.",
        englishSource = "Ibn al-Sunni 71",
    ),
    MorningEveningDhikr(
        id = "bismillahilladzi",
        title = "Bismillahilladzi la yadurru",
        morningArabic = "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
        meaning = "Dengan nama Allah, bersama nama-Nya tidak ada sesuatu di bumi maupun di langit yang dapat memberi bahaya; Dia Maha Mendengar, Maha Mengetahui.",
        target = 3,
        source = "HR. Abu Dawud 5088; At-Tirmidzi 3388",
        englishMeaning = "In the name of Allah, with whose name nothing on earth or in heaven can cause harm; He is the All-Hearing, All-Knowing.",
        englishSource = "Sunan Abi Dawud 5088; Jami` at-Tirmidhi 3388",
    ),
    MorningEveningDhikr(
        id = "raditu-billah",
        title = "Raditu billahi rabba",
        morningArabic = "رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا",
        meaning = "Aku ridha Allah sebagai Rabb, Islam sebagai agama, dan Muhammad ﷺ sebagai nabi.",
        target = 3,
        source = "HR. Abu Dawud; At-Tirmidzi 3389",
        englishMeaning = "I am pleased with Allah as my Lord, Islam as my religion, and Muhammad ﷺ as my Prophet.",
        englishSource = "Sunan Abi Dawud; Jami` at-Tirmidhi 3389",
    ),
    MorningEveningDhikr(
        id = "sayyidul-istighfar",
        title = "Sayyidul Istighfar",
        morningArabic = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ خَلَقْتَنِي وَأَنَا عَبْدُكَ وَأَنَا عَلَىٰ عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
        meaning = "Pengakuan atas rububiyah Allah, nikmat-Nya, dan dosa hamba, disertai permohonan ampun.",
        source = "HR. Al-Bukhari 6306",
        englishMeaning = "A confession of Allah’s lordship, His blessings, and the servant’s sins, followed by a plea for forgiveness.",
        englishSource = "Sahih al-Bukhari 6306",
    ),
    MorningEveningDhikr(
        id = "afini-fi-badani",
        title = "Allahumma ‘afini fi badani",
        morningArabic = "اللَّهُمَّ عَافِنِي فِي بَدَنِي اللَّهُمَّ عَافِنِي فِي سَمْعِي اللَّهُمَّ عَافِنِي فِي بَصَرِي لَا إِلَٰهَ إِلَّا أَنْتَ اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ وَالْفَقْرِ اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ عَذَابِ الْقَبْرِ لَا إِلَٰهَ إِلَّا أَنْتَ",
        meaning = "Memohon kesehatan tubuh, pendengaran, dan penglihatan serta perlindungan dari kekufuran, kefakiran, dan azab kubur.",
        target = 3,
        source = "HR. Abu Dawud 5090",
        englishMeaning = "A plea for well-being in the body, hearing, and sight, and for protection from disbelief, poverty, and the punishment of the grave.",
        englishSource = "Sunan Abi Dawud 5090",
    ),
    MorningEveningDhikr(
        id = "bika-asbahna",
        title = "Allahumma bika asbahna",
        morningArabic = "اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ",
        eveningArabic = "اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ الْمَصِيرُ",
        meaning = "Dengan pertolongan Allah kita memasuki pagi atau petang, hidup dan mati; kepada-Nya tempat kembali.",
        source = "HR. Abu Dawud; At-Tirmidzi (hasan), Riyadhus Shalihin 1453",
        englishMeaning = "By Allah’s help we enter morning or evening, live and die; to Him is our return.",
        englishSource = "Sunan Abi Dawud; Jami` at-Tirmidhi (hasan), Riyad as-Salihin 1453",
    ),
    MorningEveningDhikr(
        id = "asbahna-wa-asbahal-mulku",
        title = "Asbahna wa asbahal-mulku lillah",
        morningArabic = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ",
        eveningArabic = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ وَالْحَمْدُ لِلَّهِ لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ",
        meaning = "Memuji Allah atas pergantian waktu dan memohon kebaikan serta perlindungan pada hari atau malam ini.",
        source = "HR. Muslim 2723",
        englishMeaning = "Praise Allah for the change of time and ask Him for good and protection throughout this day or night.",
        englishSource = "Sahih Muslim 2723",
    ),
    MorningEveningDhikr(
        id = "allahumma-fatir",
        title = "Allahumma fatiras-samawati wal-ardh",
        morningArabic = "اللَّهُمَّ فَاطِرَ السَّمَاوَاتِ وَالْأَرْضِ عَالِمَ الْغَيْبِ وَالشَّهَادَةِ رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا أَنْتَ أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي وَشَرِّ الشَّيْطَانِ وَشِرْكِهِ",
        meaning = "Memohon perlindungan Allah dari keburukan diri, setan, dan ajakannya kepada kesyirikan.",
        source = "HR. Abu Dawud 5067",
        englishMeaning = "A plea for Allah’s protection from the evil within oneself, Satan, and his call to associate partners with Allah.",
        englishSource = "Sunan Abi Dawud 5067",
    ),
    MorningEveningDhikr(
        id = "afiyah",
        title = "Memohon ampunan dan afiyah",
        morningArabic = "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي دِينِي وَدُنْيَايَ وَأَهْلِي وَمَالِي اللَّهُمَّ اسْتُرْ عَوْرَاتِي وَآمِنْ رَوْعَاتِي وَاحْفَظْنِي مِنْ بَيْنِ يَدَيَّ وَمِنْ خَلْفِي وَعَنْ يَمِينِي وَعَنْ شِمَالِي وَمِنْ فَوْقِي وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي",
        meaning = "Memohon ampunan, keselamatan, perlindungan keluarga dan harta, serta penjagaan dari segala arah.",
        source = "HR. Ibnu Majah 3871; Abu Dawud 5074",
        englishTitle = "Seeking pardon and well-being",
        englishMeaning = "A plea for pardon, well-being, protection for family and wealth, and safeguarding from every direction.",
        englishSource = "Sunan Ibn Majah 3871; Sunan Abi Dawud 5074",
    ),
    MorningEveningDhikr(
        id = "subhanallah-wa-bihamdihi",
        title = "Subhanallahi wa bihamdihi",
        morningArabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        meaning = "Mahasuci Allah dan segala puji bagi-Nya.",
        target = 100,
        source = "HR. Muslim; Riyadhus Shalihin 1451",
        englishMeaning = "Glory and praise be to Allah.",
        englishSource = "Sahih Muslim; Riyad as-Salihin 1451",
    ),
)

private val dhikrArabicFontFamily = FontFamily(Font(R.font.kfgqpc_nastaleeq_regular))

@Composable
fun DhikrScreen() {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("ibadah-dhikr", Context.MODE_PRIVATE) }
    val today = LocalDate.now().toString()
    var selectedPeriodName by rememberSaveable { mutableStateOf(DhikrPeriod.MORNING.name) }
    var revision by remember { mutableIntStateOf(0) }
    val selectedPeriod = DhikrPeriod.valueOf(selectedPeriodName)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (english) "Remembrance" else "Dzikir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DhikrPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriodName = period.name },
                    label = { Text(if (english) period.englishTitle else period.title) },
                )
            }
        }

        if (selectedPeriod == DhikrPeriod.AFTER_PRAYER) {
            PostPrayerDhikrScreen()
        } else {
            val finishedCount = remember(revision, selectedPeriod, today) {
                morningEveningDhikr.count { entry ->
                    preferences.getInt("${selectedPeriod.preferenceKey}-${entry.id}-$today", 0) >= entry.target
                }
            }
            Text(
                if (selectedPeriod == DhikrPeriod.MORNING) {
                    if (english) "Recite after Fajr until sunrise." else "Dibaca setelah Subuh hingga matahari terbit."
                } else {
                    if (english) "Recite after Asr until sunset." else "Dibaca setelah Asar hingga matahari terbenam."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(if (english) "$finishedCount / ${morningEveningDhikr.size} readings completed" else "$finishedCount / ${morningEveningDhikr.size} bacaan selesai", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            morningEveningDhikr.forEach { entry ->
                val count = remember(revision, selectedPeriod, today, entry.id) {
                    preferences.getInt("${selectedPeriod.preferenceKey}-${entry.id}-$today", 0).coerceIn(0, entry.target)
                }
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (english) entry.englishTitle else entry.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${entry.target}×", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (selectedPeriod == DhikrPeriod.MORNING) entry.morningArabic else entry.eveningArabic,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = dhikrArabicFontFamily,
                                fontSize = 27.sp,
                                lineHeight = 46.sp,
                                textAlign = TextAlign.Center,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(if (english) entry.englishMeaning else entry.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (english) entry.englishSource else entry.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$count / ${entry.target}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = {
                                preferences.edit().putInt("${selectedPeriod.preferenceKey}-${entry.id}-$today", 0).apply()
                                revision++
                            }, enabled = count > 0) { Text(if (english) "Reset" else "Ulangi") }
                            Button(onClick = {
                                preferences.edit().putInt("${selectedPeriod.preferenceKey}-${entry.id}-$today", (count + 1).coerceAtMost(entry.target)).apply()
                                revision++
                            }, enabled = count < entry.target) { Text(if (count >= entry.target) if (english) "Done" else "Selesai" else if (english) "Count" else "Hitung") }
                        }
                    }
                }
            }
            Text(
                if (english) "Reading reference: Morning and Evening Remembrance and Remembrance After Obligatory Prayers by Yazid bin Abdul Qadir Jawas. Sources are listed for each recitation; counts are stored on this device each day."
                else "Acuan bacaan: Dzikir Pagi Petang dan Sesudah Shalat Fardhu, Yazid bin Abdul Qadir Jawas. Rujukan tiap bacaan dicantumkan; hitungan tersimpan di perangkat per hari.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PostPrayerDhikrScreen() {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    val date = LocalDate.now().toString()
    val preferences = remember { context.getSharedPreferences("ibadah-dhikr", Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    val finishedCount = remember(revision, date) {
        postPrayerDhikr.withIndex().count { (index, entry) -> postPrayerCount(context, index) >= entry.target }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (english) "Remembrance after prayer" else "Dzikir setelah shalat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(if (english) "$finishedCount / ${postPrayerDhikr.size} readings completed · read in any order." else "$finishedCount / ${postPrayerDhikr.size} bacaan selesai · bisa dibaca dalam urutan pilihanmu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        postPrayerDhikr.forEachIndexed { index, entry ->
            val count = remember(revision, date, entry.id) { postPrayerCount(context, index) }
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (english) entry.labelEnglish else entry.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${entry.target}×", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
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
                    Text(if (english) entry.meaningEnglish else entry.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (english) entry.sourceEnglish else entry.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$count / ${entry.target}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = {
                            preferences.edit().putInt("post-${entry.id}-$date", 0).apply()
                            revision++
                        }, enabled = count > 0) { Text(if (english) "Reset" else "Ulangi") }
                        Button(onClick = {
                            preferences.edit().putInt("post-${entry.id}-$date", (count + 1).coerceAtMost(entry.target)).apply()
                            revision++
                        }, enabled = count < entry.target) { Text(if (count >= entry.target) if (english) "Done" else "Selesai" else if (english) "Count" else "Hitung") }
                    }
                }
            }
        }
        Text(if (english) "Reference: Morning and Evening Remembrance and Remembrance After Obligatory Prayers by Yazid bin Abdul Qadir Jawas. Counts are stored locally each day." else "Acuan: Dzikir Pagi Petang dan Sesudah Shalat Fardhu, Yazid bin Abdul Qadir Jawas. Hitungan tersimpan lokal per hari.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun postPrayerCount(context: Context, index: Int): Int {
    val entry = postPrayerDhikr[index]
    val date = LocalDate.now()
    val preferences = context.getSharedPreferences("ibadah-dhikr", Context.MODE_PRIVATE)
    val legacyIndex = when (entry.id) {
        "tasbih" -> 0
        "tahmid" -> 1
        "takbir" -> 2
        "tahlil-completion" -> 3
        else -> null
    }
    val savedCount = preferences.getInt(
        "post-${entry.id}-$date",
        legacyIndex?.let { preferences.getInt("$it-$date", 0) } ?: 0,
    )
    return savedCount.coerceIn(0, entry.target)
}

private val trackedActs = listOf("Subuh", "Dzuhur", "Asar", "Maghrib", "Isya", "Tilawah", "Dzikir")

@Composable
fun AmalanTrackerScreen() {
    val english = LocalAppEnglish.current
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
        Text(if (english) "Daily worship tracker" else "Tracker amalan hari ini", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(if (english) "$doneCount / ${trackedActs.size - if (uzur) 5 else 0} activities recorded" else "$doneCount / ${trackedActs.size - if (uzur) 5 else 0} aktivitas dicatat", style = MaterialTheme.typography.bodyMedium)
        Text(if (english) "Last 7 days: $weekDone / $weekPossible records" else "7 hari terakhir: $weekDone / $weekPossible catatan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (english) "Excused mode" else "Mode uzur", fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Exclude prayers from today’s summary." else "Menandai shalat sebagai tidak berlaku untuk ringkasan hari ini.", style = MaterialTheme.typography.bodySmall)
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
                        Text(if (english) when (activity) {
                            "Subuh" -> "Fajr"
                            "Dzuhur" -> "Dhuhr"
                            "Asar" -> "Asr"
                            "Maghrib" -> "Maghrib"
                            "Isya" -> "Isha"
                            "Tilawah" -> "Qur’an reading"
                            else -> "Dhikr"
                        } else activity, fontWeight = FontWeight.Medium)
                        Text(if (uzur && isPrayer) { if (english) "Excused · excluded from count" else "Tidak berlaku · tidak masuk hitungan" } else if (checked) { if (english) "Completed" else "Sudah dilakukan" } else { if (english) "Not recorded" else "Belum dicatat" }, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = checked,
                        enabled = !(uzur && isPrayer),
                        onCheckedChange = { prefs.edit().putBoolean("done-$date-$activity", it).apply(); revision++ },
                    )
                }
            }
        }
        Text(if (english) "History is stored locally by date. This tracker is a personal tool, not an assessment of worship." else "Riwayat dicatat lokal per tanggal. Tracker ini alat pribadi, bukan penilaian ibadah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (revision < 0) Text("")
    }
}

@Composable
fun SunnahFastingScreen() {
    val english = LocalAppEnglish.current
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
        Text(if (english) "Voluntary fasting" else "Puasa sunnah", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(if (english) "Monday and Thursday reminders. Set the reminder time; use the Hijri calendar for special dates." else "Pengingat Senin dan Kamis. Waktu pengingat bisa kamu atur; kalender Hijriah tetap menjadi rujukan untuk hari khusus.")
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(if (english) "Weekly reminder" else "Pengingat mingguan", fontWeight = FontWeight.SemiBold); Text(if (english) "Monday and Thursday · $time" else "Senin dan Kamis · $time", style = MaterialTheme.typography.bodySmall) }
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
            label = { Text(if (english) "Reminder time (HH:mm)" else "Jam pengingat (HH:mm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            if (time.matches(Regex("\\d{2}:\\d{2}")) && hour in 0..23 && minute in 0..59) {
                prefs.edit().putString("time", time).apply()
                SunnahFastingReminderScheduler.schedule(context)
            }
        }) { Text(if (english) "Save time" else "Simpan waktu") }
        Text(if (english) "The weekly reminder does not automatically exclude the Days of Tashriq or other days when fasting is discouraged. Adjust or disable it as appropriate." else "Hari Tasyrik dan hari yang tidak dianjurkan berpuasa tidak dihitung otomatis pada pengingat mingguan; nonaktifkan/pastikan jadwal sesuai kondisi pribadi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
