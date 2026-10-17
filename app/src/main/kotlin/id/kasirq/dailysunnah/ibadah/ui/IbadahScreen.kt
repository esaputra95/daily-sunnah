package id.kasirq.dailysunnah.ibadah.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.kasirq.dailysunnah.ibadah.data.HijriCalendarPreferences
import id.kasirq.dailysunnah.ibadah.data.PrecisePrayerLocationProvider
import id.kasirq.dailysunnah.ibadah.data.PrayerCoordinates
import id.kasirq.dailysunnah.ibadah.data.PrayerDay
import id.kasirq.dailysunnah.ibadah.data.PrayerEvent
import id.kasirq.dailysunnah.ibadah.data.PrayerLocationStore
import id.kasirq.dailysunnah.ibadah.data.PrayerName
import id.kasirq.dailysunnah.ibadah.data.PrayerReminderScheduler
import id.kasirq.dailysunnah.ibadah.data.PrayerSettingsStore
import id.kasirq.dailysunnah.ibadah.data.PrayerTimesCalculator
import id.kasirq.dailysunnah.ibadah.data.formatPrayerTime
import id.kasirq.dailysunnah.ibadah.data.formatTimeUntil
import id.kasirq.dailysunnah.ibadah.data.hijriDateFor
import id.kasirq.dailysunnah.ibadah.data.hijriLabel
import id.kasirq.dailysunnah.ibadah.data.hijriMonthLabel
import id.kasirq.dailysunnah.ibadah.data.localDateForHijri
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IbadahScreen(onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var coordinates by remember { mutableStateOf(PrayerLocationStore.read(context)) }
    var hasPrecisePermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var hijriOffset by remember { mutableIntStateOf(HijriCalendarPreferences.offsetDays(context)) }
    var prayerSettings by remember { mutableStateOf(PrayerSettingsStore.read(context)) }
    val scope = rememberCoroutineScope()
    val tabPagerState = rememberPagerState(pageCount = { 5 })

    fun refreshLocation() {
        isRefreshing = true
        locationMessage = "Mencari posisi perangkat…"
        PrecisePrayerLocationProvider.refresh(
            context = context.applicationContext,
            onLocation = { result ->
                coordinates = result
                locationMessage = null
                isRefreshing = false
                PrayerReminderScheduler.scheduleNext(context)
            },
            onFailure = { error ->
                locationMessage = error
                isRefreshing = false
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPrecisePermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPrecisePermission) {
            locationMessage = null
        } else {
            locationMessage = "Izinkan lokasi presisi agar waktu shalat dihitung dari posisi perangkat, bukan perkiraan kota."
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            kotlinx.coroutines.delay(30_000L)
        }
    }

    LaunchedEffect(hasPrecisePermission) {
        if (hasPrecisePermission) refreshLocation()
    }

    val prayerDay = remember(now.toLocalDate(), coordinates, prayerSettings.correctionMinutes) {
        coordinates?.let { PrayerTimesCalculator.calculate(now.toLocalDate(), it.latitude, it.longitude, correctionMinutes = prayerSettings.correctionMinutes) }
    }
    val nextPrayer = coordinates?.let {
        PrayerTimesCalculator.nextPrayer(now, it.latitude, it.longitude, correctionMinutes = prayerSettings.correctionMinutes)
    }
    val afterMaghrib = prayerDay?.event(PrayerName.MAGHRIB)?.dateTime?.let { now >= it } == true
    val hijriCalendarDate = now.toLocalDate().plusDays(if (afterMaghrib) 1 else 0)
    val currentHijri = remember(hijriCalendarDate, hijriOffset) {
        hijriLabel(hijriCalendarDate, hijriOffset)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ibadah", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = onOpenSettings) { Text("Pengaturan") } },
            )
        },
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            ScrollableTabRow(
                selectedTabIndex = tabPagerState.currentPage,
                edgePadding = 0.dp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                listOf("Shalat", "Kiblat", "Dzikir", "Amalan", "Puasa").forEachIndexed { index, label ->
                    Tab(
                        selected = tabPagerState.currentPage == index,
                        onClick = { scope.launch { tabPagerState.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
                }
            }
            HorizontalPager(
                state = tabPagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (page) {
                        0 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Jadwal shalat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                                Text("Dihitung lokal dari koordinat presisi perangkat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            LocationCard(
                                coordinates = coordinates,
                                hasPrecisePermission = hasPrecisePermission,
                                isRefreshing = isRefreshing,
                                message = locationMessage,
                                onRequestPermission = {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                },
                                onRefresh = {
                                    if (hasPrecisePermission) {
                                        isRefreshing = true
                                        locationMessage = "Mencari posisi perangkat…"
                                        PrecisePrayerLocationProvider.refresh(
                                            context.applicationContext,
                                            onLocation = { coordinates = it; locationMessage = null; isRefreshing = false; PrayerReminderScheduler.scheduleNext(context) },
                                            onFailure = { locationMessage = it; isRefreshing = false },
                                        )
                                    }
                                },
                            )

                            NextPrayerCard(nextPrayer, now)
                            PrayerScheduleCard(prayerDay, now, coordinates, prayerSettings.correctionMinutes)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Kalender Hijriah", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Text(currentHijri, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Text("Kalender Umm al-Qura; tanggal dapat berbeda dari penetapan resmi Indonesia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HijriMonthCalendar(
                                today = hijriCalendarDate,
                                offsetDays = hijriOffset,
                                onOffsetChange = { offset ->
                                    hijriOffset = offset.coerceIn(-2, 2)
                                    HijriCalendarPreferences.saveOffsetDays(context, hijriOffset)
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        1 -> QiblaScreen(coordinates)
                        2 -> DhikrScreen()
                        3 -> AmalanTrackerScreen()
                        else -> SunnahFastingScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun NextPrayerCard(nextPrayer: PrayerEvent?, now: LocalDateTime) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.14f)) {
                Text("☀", modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = Color(0xFFFFD88A), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("SHALAT BERIKUTNYA", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), letterSpacing = 1.sp)
                Text(nextPrayer?.name?.label ?: "Jadwal belum tersedia", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    nextPrayer?.let { formatTimeUntil(it.dateTime, now) } ?: "Aktifkan lokasi presisi untuk melihat jadwal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f),
                )
            }
            Text(nextPrayer?.let { formatPrayerTime(it.dateTime) } ?: "--:--", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LocationCard(
    coordinates: PrayerCoordinates?,
    hasPrecisePermission: Boolean,
    isRefreshing: Boolean,
    message: String?,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFEFA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Lokasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (coordinates != null) {
                Text(
                    "${"%.6f".format(Locale.US, coordinates.latitude)}, ${"%.6f".format(Locale.US, coordinates.longitude)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (coordinates.accuracyMeters > 0f) "Akurasi perangkat ±${coordinates.accuracyMeters.toInt()} m" else "Akurasi koordinat tidak tersedia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Belum ada koordinat presisi. Jadwal tidak akan memakai lokasi kota perkiraan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = if (hasPrecisePermission) onRefresh else onRequestPermission,
                enabled = !isRefreshing,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (hasPrecisePermission) "Perbarui lokasi presisi" else "Aktifkan lokasi presisi")
            }
        }
    }
}

@Composable
private fun PrayerScheduleCard(
    prayerDay: PrayerDay?,
    now: LocalDateTime,
    coordinates: PrayerCoordinates?,
    corrections: Map<PrayerName, Int>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFEFA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hari ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (prayerDay == null) {
                Text("Jadwal tampil setelah lokasi presisi tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val display = listOf(PrayerName.SUBUH, PrayerName.TERBIT, PrayerName.DZUHUR, PrayerName.ASHAR, PrayerName.MAGHRIB, PrayerName.ISYA)
                val nextPrayer = coordinates?.let {
                    PrayerTimesCalculator.nextPrayer(now, it.latitude, it.longitude, correctionMinutes = corrections)
                }
                display.forEach { name ->
                    val event = prayerDay.event(name) ?: return@forEach
                    PrayerTimeRow(event, highlighted = nextPrayer?.dateTime == event.dateTime)
                }
            }
        }
    }
}

@Composable
private fun PrayerTimeRow(event: PrayerEvent, highlighted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(event.name.label, style = MaterialTheme.typography.bodyLarge, color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal)
        Text(formatPrayerTime(event.dateTime), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HijriMonthCalendar(
    today: LocalDate,
    offsetDays: Int,
    onOffsetChange: (Int) -> Unit,
) {
    val todayHijri = remember(today, offsetDays) { HijrahDate.from(today.plusDays(offsetDays.toLong())) }
    var visibleYear by remember(offsetDays) { mutableIntStateOf(todayHijri.get(ChronoField.YEAR)) }
    var visibleMonth by remember(offsetDays) { mutableIntStateOf(todayHijri.get(ChronoField.MONTH_OF_YEAR)) }
    val firstGregorianDay = remember(visibleYear, visibleMonth) { localDateForHijri(visibleYear, visibleMonth, 1) }
    val monthLength = remember(visibleYear, visibleMonth) { hijriDateFor(visibleYear, visibleMonth, 1).lengthOfMonth() }
    val leadingCells = firstGregorianDay.dayOfWeek.value - 1
    val cellCount = ((leadingCells + monthLength + 6) / 7) * 7

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFEFA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    if (visibleMonth == 1) { visibleMonth = 12; visibleYear-- } else visibleMonth--
                }) { Text("‹") }
                Text(hijriMonthLabel(visibleYear, visibleMonth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    if (visibleMonth == 12) { visibleMonth = 1; visibleYear++ } else visibleMonth++
                }) { Text("›") }
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { weekday ->
                    Text(weekday, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            (0 until cellCount step 7).forEach { rowStart ->
                Row(Modifier.fillMaxWidth()) {
                    (rowStart until rowStart + 7).forEach { cell ->
                        val day = cell - leadingCells + 1
                        if (day !in 1..monthLength) {
                            Box(Modifier.weight(1f).height(54.dp))
                        } else {
                            val gregorianDate = localDateForHijri(visibleYear, visibleMonth, day)
                            val isToday = gregorianDate == today.plusDays(offsetDays.toLong())
                            Column(
                                modifier = Modifier.weight(1f).height(54.dp).padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                ) {
                                    Text(
                                        "$day",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                                Text("${gregorianDate.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Koreksi tanggal", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Text("${if (offsetDays > 0) "+" else ""}$offsetDays hari", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onOffsetChange(offsetDays - 1) }, enabled = offsetDays > -2) { Text("−1") }
                TextButton(onClick = { onOffsetChange(offsetDays + 1) }, enabled = offsetDays < 2) { Text("+1") }
            }
        }
    }
}
