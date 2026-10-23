package id.kasirq.dailysunnah.ibadah.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import id.kasirq.dailysunnah.ibadah.data.RamadanReminderScheduler
import id.kasirq.dailysunnah.ibadah.data.PrayerSettingsStore
import id.kasirq.dailysunnah.home.DailySunnahWidgetProvider
import id.kasirq.dailysunnah.ibadah.data.PrayerTimesCalculator
import id.kasirq.dailysunnah.ibadah.data.formatPrayerTime
import id.kasirq.dailysunnah.ibadah.data.formatTimeUntil
import id.kasirq.dailysunnah.ibadah.data.hijriDateFor
import id.kasirq.dailysunnah.ibadah.data.hijriMonthLabel
import id.kasirq.dailysunnah.ibadah.data.localDateForHijri
import id.kasirq.dailysunnah.ui.BackNavigationButton
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IbadahScreen(onOpenSettings: () -> Unit = {}, onOpenCalendar: () -> Unit = {}, onOpenQuran: () -> Unit = {}) {
    val english = LocalAppEnglish.current
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
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val tabPagerState = rememberPagerState(pageCount = { 6 })

    fun refreshLocation() {
        isRefreshing = true
        locationMessage = if (english) "Finding device location…" else "Mencari posisi perangkat…"
        PrecisePrayerLocationProvider.refresh(
            context = context.applicationContext,
            onLocation = { result ->
                coordinates = result
                DailySunnahWidgetProvider.updateAll(context)
                locationMessage = null
                isRefreshing = false
                PrayerReminderScheduler.scheduleNext(context)
                RamadanReminderScheduler.scheduleNext(context)
            },
            onFailure = { error ->
                locationMessage = if (english) "Could not get your precise location. Check location permission and GPS." else error
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
            locationMessage = if (english) "Allow precise location so prayer times use your device coordinates." else "Izinkan lokasi presisi agar waktu shalat dihitung dari posisi perangkat, bukan perkiraan kota."
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
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (english) "Worship" else "Ibadah", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(
                        onClick = onOpenCalendar,
                        modifier = Modifier.semantics { contentDescription = if (english) "Open Hijri calendar" else "Buka kalender Hijriah" },
                    ) { CalendarIcon() }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.semantics { contentDescription = if (english) "Worship settings" else "Pengaturan ibadah" },
                    ) { SettingsGearIcon() }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                scrollBehavior = topBarScrollBehavior,
            )
        },
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            ScrollableTabRow(
                selectedTabIndex = tabPagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 20.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                (if (english) listOf("Prayer", "Qibla", "Dhikr", "Tracker", "Fasting", "Ramadan") else listOf("Shalat", "Kiblat", "Dzikir", "Amalan", "Puasa", "Ramadan")).forEachIndexed { index, label ->
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
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .padding(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (page) {
                        0 -> {
                            NextPrayerCard(nextPrayer, now)
                            PrayerScheduleCard(prayerDay, now, coordinates, prayerSettings.correctionMinutes)
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
                                        locationMessage = if (english) "Finding device location…" else "Mencari posisi perangkat…"
                                        PrecisePrayerLocationProvider.refresh(
                                            context.applicationContext,
                                            onLocation = { coordinates = it; DailySunnahWidgetProvider.updateAll(context); locationMessage = null; isRefreshing = false; PrayerReminderScheduler.scheduleNext(context); RamadanReminderScheduler.scheduleNext(context) },
                                            onFailure = { locationMessage = if (english) "Could not get your precise location. Check location permission and GPS." else it; isRefreshing = false },
                                        )
                                    }
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        1 -> QiblaScreen(coordinates)
                        2 -> DhikrScreen()
                        3 -> AmalanTrackerScreen()
                        4 -> SunnahFastingScreen()
                        else -> RamadanModeScreen(
                            now = now,
                            coordinates = coordinates,
                            correctionMinutes = prayerSettings.correctionMinutes,
                            hijriDate = hijriCalendarDate,
                            hijriOffsetDays = hijriOffset,
                            onOpenQuran = onOpenQuran,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarIcon() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp)) {
        val stroke = size.minDimension * 0.085f
        val left = size.width * 0.16f
        val top = size.height * 0.22f
        val width = size.width * 0.68f
        val height = size.height * 0.62f
        drawRoundRect(color, Offset(left, top), Size(width, height), androidx.compose.ui.geometry.CornerRadius(stroke * 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawLine(color, Offset(left, size.height * 0.40f), Offset(left + width, size.height * 0.40f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.34f, size.height * 0.12f), Offset(size.width * 0.34f, size.height * 0.30f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.66f, size.height * 0.12f), Offset(size.width * 0.66f, size.height * 0.30f), stroke, cap = StrokeCap.Round)
        drawCircle(color, radius = stroke * 0.75f, center = Offset(size.width * 0.36f, size.height * 0.57f))
        drawCircle(color, radius = stroke * 0.75f, center = Offset(size.width * 0.62f, size.height * 0.57f))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HijriCalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var coordinates by remember { mutableStateOf(PrayerLocationStore.read(context)) }
    var hijriOffset by remember { mutableIntStateOf(HijriCalendarPreferences.offsetDays(context)) }
    val prayerSettings = remember { PrayerSettingsStore.read(context) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            coordinates = PrayerLocationStore.read(context)
            kotlinx.coroutines.delay(30_000L)
        }
    }

    val maghrib = coordinates?.let {
        PrayerTimesCalculator.calculate(now.toLocalDate(), it.latitude, it.longitude, correctionMinutes = prayerSettings.correctionMinutes)
            ?.event(PrayerName.MAGHRIB)?.dateTime
    }
    val today = now.toLocalDate().plusDays(if (maghrib != null && now >= maghrib) 1 else 0)
    val todayHijri = remember(today, hijriOffset) { HijrahDate.from(today.plusDays(hijriOffset.toLong())) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { BackNavigationButton(onClick = onBack) },
                title = { Text(if (english) "Hijri calendar" else "Kalender Hijriah", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (english) "TODAY IN HIJRI" else "HARI INI DALAM HIJRIAH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f), letterSpacing = 1.2.sp)
                    Text("${todayHijri.get(ChronoField.DAY_OF_MONTH)} ${hijriMonthLabel(todayHijri.get(ChronoField.YEAR), todayHijri.get(ChronoField.MONTH_OF_YEAR), english)}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    Text(today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag(if (english) "en-US" else "id-ID"))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f))
                }
            }
            HijriMonthCalendar(
                today = today,
                offsetDays = hijriOffset,
                onOffsetChange = { offset ->
                    hijriOffset = offset.coerceIn(-2, 2)
                    HijriCalendarPreferences.saveOffsetDays(context, hijriOffset)
                    RamadanReminderScheduler.scheduleNext(context)
                },
            )
            Text(
                if (english) "Based on the Umm al-Qura calendar. Dates may differ from official local moon-sighting announcements." else "Menggunakan kalender Umm al-Qura. Tanggal dapat berbeda dari penetapan resmi berdasarkan rukyat hilal di Indonesia.",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsGearIcon() {
    val gearColor = MaterialTheme.colorScheme.primary
    val holeColor = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(24.dp)) {
        val centerPoint = center
        val outerRadius = size.minDimension * 0.30f
        val toothWidth = size.minDimension * 0.14f
        val toothHeight = size.minDimension * 0.16f
        repeat(8) { index ->
            rotate(index * 45f, pivot = centerPoint) {
                drawRoundRect(
                    color = gearColor,
                    topLeft = Offset(centerPoint.x - toothWidth / 2f, centerPoint.y - outerRadius - toothHeight),
                    size = Size(toothWidth, toothHeight),
                    cornerRadius = CornerRadius(toothWidth * 0.30f),
                )
            }
        }
        drawCircle(color = gearColor, radius = outerRadius, center = centerPoint)
        drawCircle(color = holeColor, radius = outerRadius * 0.42f, center = centerPoint)
    }
}

@Composable
private fun NextPrayerCard(nextPrayer: PrayerEvent?, now: LocalDateTime) {
    val english = LocalAppEnglish.current
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
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)) {
                Text("☀", modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (english) "NEXT PRAYER" else "SHALAT BERIKUTNYA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), letterSpacing = 1.sp)
                Text(nextPrayer?.name?.let { if (english) it.englishLabel else it.label } ?: if (english) "Schedule unavailable" else "Jadwal belum tersedia", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    nextPrayer?.let { formatTimeUntil(it.dateTime, now) } ?: if (english) "Enable precise location to view prayer times." else "Aktifkan lokasi presisi untuk melihat jadwal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
                )
            }
            Text(nextPrayer?.let { formatPrayerTime(it.dateTime) } ?: "--:--", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
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
    val english = LocalAppEnglish.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (english) "Location" else "Lokasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (coordinates != null) {
                Text(
                    "${"%.6f".format(Locale.US, coordinates.latitude)}, ${"%.6f".format(Locale.US, coordinates.longitude)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (english) { if (coordinates.accuracyMeters > 0f) "Device accuracy ±${coordinates.accuracyMeters.toInt()} m" else "Coordinate accuracy unavailable" }
                    else if (coordinates.accuracyMeters > 0f) "Akurasi perangkat ±${coordinates.accuracyMeters.toInt()} m" else "Akurasi koordinat tidak tersedia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(if (english) "Precise coordinates are unavailable. Prayer times will not use an approximate city location." else "Belum ada koordinat presisi. Jadwal tidak akan memakai lokasi kota perkiraan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(if (english) { if (hasPrecisePermission) "Refresh precise location" else "Enable precise location" } else { if (hasPrecisePermission) "Perbarui lokasi presisi" else "Aktifkan lokasi presisi" })
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
    val english = LocalAppEnglish.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("☼", modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (english) "Today" else "Hari ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        now.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag(if (english) "en-US" else "id-ID"))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(if (english) "TODAY" else "HARI INI", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(Modifier.fillMaxWidth().height(1.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)) {}
            if (prayerDay == null) {
                Text(if (english) "Schedule appears when precise location is available." else "Jadwal tampil setelah lokasi presisi tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val display = listOf(PrayerName.SUBUH, PrayerName.TERBIT, PrayerName.DZUHUR, PrayerName.ASHAR, PrayerName.MAGHRIB, PrayerName.ISYA)
                val nextPrayer = coordinates?.let {
                    PrayerTimesCalculator.nextPrayer(now, it.latitude, it.longitude, correctionMinutes = corrections)
                }
                display.forEach { name ->
                    val event = prayerDay.event(name) ?: return@forEach
                    PrayerTimeRow(
                        event = event,
                        now = now,
                        highlighted = nextPrayer?.dateTime == event.dateTime,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerTimeRow(event: PrayerEvent, now: LocalDateTime, highlighted: Boolean = false) {
    val english = LocalAppEnglish.current
    val isPast = !highlighted && event.dateTime.isBefore(now)
    val accent = when (event.name) {
        PrayerName.SUBUH -> "☾"
        PrayerName.TERBIT -> "↗"
        PrayerName.DZUHUR -> "☀"
        PrayerName.ASHAR -> "◒"
        PrayerName.MAGHRIB -> "◐"
        PrayerName.ISYA -> "☽"
    }
    val status = when {
        highlighted -> if (english) "Next prayer" else "Berikutnya"
        isPast -> if (english) "Completed" else "Lewat"
        else -> if (english) "Upcoming" else "Akan datang"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (highlighted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isPast) 0.55f else 1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(accent, color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (english) event.name.englishLabel else event.name.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatPrayerTime(event.dateTime),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HijriMonthCalendar(
    today: LocalDate,
    offsetDays: Int,
    onOffsetChange: (Int) -> Unit,
) {
    val english = LocalAppEnglish.current
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
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    if (visibleMonth == 1) { visibleMonth = 12; visibleYear-- } else visibleMonth--
                }) { Text("‹") }
                Text(hijriMonthLabel(visibleYear, visibleMonth, english), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    if (visibleMonth == 12) { visibleMonth = 1; visibleYear++ } else visibleMonth++
                }) { Text("›") }
            }

            Row(Modifier.fillMaxWidth()) {
                (if (english) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")).forEach { weekday ->
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
                    Text(if (english) "Date adjustment" else "Koreksi tanggal", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Text(if (english) "${if (offsetDays > 0) "+" else ""}$offsetDays days" else "${if (offsetDays > 0) "+" else ""}$offsetDays hari", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onOffsetChange(offsetDays - 1) }, enabled = offsetDays > -2) { Text("−1") }
                TextButton(onClick = { onOffsetChange(offsetDays + 1) }, enabled = offsetDays < 2) { Text("+1") }
            }
        }
    }
}
