package id.kasirq.dailysunnah.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import id.kasirq.dailysunnah.R
import id.kasirq.dailysunnah.quran.data.QuranReaderPreferences
import id.kasirq.dailysunnah.quran.data.ReadingGoalReminderScheduler
import id.kasirq.dailysunnah.quran.data.ReadingGoalSnapshot
import id.kasirq.dailysunnah.quran.data.ReadingGoalStore
import id.kasirq.dailysunnah.quran.ui.ReadingGoalSettingsDialog
import id.kasirq.dailysunnah.home.DailySunnahWidgetProvider
import id.kasirq.dailysunnah.kajian.data.KajianCatalogRepository
import id.kasirq.dailysunnah.kajian.data.KajianCatalogSnapshot
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import id.kasirq.dailysunnah.kajian.ui.KajianThumbnail
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import id.kasirq.dailysunnah.ibadah.data.HijriCalendarPreferences
import id.kasirq.dailysunnah.ibadah.data.PrayerLocationStore
import id.kasirq.dailysunnah.ibadah.data.PrayerName
import id.kasirq.dailysunnah.ibadah.data.PrayerSettingsStore
import id.kasirq.dailysunnah.ibadah.data.PrayerTimesCalculator
import id.kasirq.dailysunnah.ibadah.data.formatPrayerTime
import id.kasirq.dailysunnah.ibadah.data.formatTimeUntil
import id.kasirq.dailysunnah.ibadah.data.hijriLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onContinueReading: (Int) -> Unit,
    onOpenIbadah: () -> Unit,
    onOpenAllKajian: () -> Unit,
    onOpenAllShorts: () -> Unit,
    onPlayKajian: (List<KajianVideo>, Int) -> Unit,
    onPlayShorts: (List<KajianVideo>, Int) -> Unit,
) {
    val context = LocalContext.current
    val english = LocalAppEnglish.current
    val appContext = context.applicationContext
    val readerPreferences = remember { QuranReaderPreferences(appContext) }
    val goalStore = remember { ReadingGoalStore(appContext) }
    val kajianRepository = remember { KajianCatalogRepository(appContext) }
    var lastReadPage by remember { mutableStateOf(1) }
    var readingGoal by remember {
        mutableStateOf(ReadingGoalSnapshot(ReadingGoalStore.DEFAULT_TARGET_PAGES, emptyList(), emptySet()))
    }
    var kajianSnapshot by remember { mutableStateOf<KajianCatalogSnapshot?>(null) }
    var showGoalSettings by remember { mutableStateOf(false) }
    var showManageReadPages by remember { mutableStateOf(false) }
    var coordinates by remember(appContext) { mutableStateOf(PrayerLocationStore.read(appContext)) }
    var prayerSettings by remember(appContext) { mutableStateOf(PrayerSettingsStore.read(appContext)) }
    val hijriOffset = remember(appContext) { HijriCalendarPreferences.offsetDays(appContext) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val notificationPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val dateLabel = remember(now.toLocalDate(), english) {
        val today = now.toLocalDate()
        today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag(if (english) "en-US" else "id-ID")))
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            coordinates = PrayerLocationStore.read(appContext)
            prayerSettings = PrayerSettingsStore.read(appContext)
            delay(30_000L)
        }
    }

    val prayerDay = coordinates?.let {
        PrayerTimesCalculator.calculate(now.toLocalDate(), it.latitude, it.longitude, correctionMinutes = prayerSettings.correctionMinutes)
    }
    val nextPrayer = coordinates?.let {
        PrayerTimesCalculator.nextPrayer(now, it.latitude, it.longitude, correctionMinutes = prayerSettings.correctionMinutes)
    }
    val currentPrayer = prayerDay?.events.orEmpty()
        .filter { it.name != PrayerName.TERBIT && !it.dateTime.isAfter(now) }
        .maxByOrNull { it.dateTime }
    val afterMaghrib = prayerDay?.event(PrayerName.MAGHRIB)?.dateTime?.let { now >= it } == true
    val hijriReferenceDate = now.toLocalDate().plusDays(if (afterMaghrib) 1 else 0)

    LaunchedEffect(Unit) {
        val (page, goal) = withContext(Dispatchers.IO) {
            readerPreferences.lastReadPage() to goalStore.load()
        }
        lastReadPage = page
        readingGoal = goal
    }

    LaunchedEffect(Unit) {
        kajianSnapshot = withContext(Dispatchers.IO) { kajianRepository.load() }
    }

    val newestKajian = remember(kajianSnapshot) {
        kajianSnapshot?.videos.orEmpty()
            .filter { it.contentType == KajianVideo.ContentType.KAJIAN }
            .sortedByDescending { it.publishedAt.orEmpty() }
            .take(5)
    }
    val newestShorts = remember(kajianSnapshot) {
        kajianSnapshot?.videos.orEmpty()
            .filter { it.contentType == KajianVideo.ContentType.SHORT }
            .sortedByDescending { it.publishedAt.orEmpty() }
            .take(5)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                        )
                        Text("Daily Sunnah", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenIbadah),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
                            }
                            Text(dateLabel.replaceFirstChar { it.uppercase(Locale.forLanguageTag(if (english) "en-US" else "id-ID")) }, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(hijriLabel(hijriReferenceDate, hijriOffset, english).substringBefore(' '), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(hijriLabel(hijriReferenceDate, hijriOffset, english).substringAfter(' '), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.20f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)) {
                            Text("☀", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (english) "NEXT PRAYER" else "SHALAT BERIKUTNYA", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                            Text(nextPrayer?.name?.let { if (english) it.englishLabel else it.label } ?: if (english) "Prayer schedule" else "Jadwal shalat", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(if (nextPrayer != null) formatTimeUntil(nextPrayer.dateTime, now) else if (english) "Enable precise location to view prayer times" else "Aktifkan lokasi presisi untuk melihat jadwal", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(nextPrayer?.let { formatPrayerTime(it.dateTime) } ?: "--:--", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(if (english) "${currentPrayer?.name?.englishLabel ?: "Today"} now" else "${currentPrayer?.name?.label ?: "Hari ini"} sekarang", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onContinueReading(lastReadPage) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (english) "Continue reading the Qur’an" else "Lanjutkan membaca Qur’an", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Madinah Mushaf · Page $lastReadPage" else "Mushaf Madinah · Halaman $lastReadPage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (english) "Today’s reading goal" else "Target tilawah hari ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (english) "${readingGoal.completedCount} of ${readingGoal.targetPages} pages" else "${readingGoal.completedCount} dari ${readingGoal.targetPages} halaman", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            readingGoal.khatamDate?.let { finishDate ->
                                Text(
                                    if (english) "Khatam plan · ${finishDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("en-US")))}"
                                    else "Rencana khatam · ${finishDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID")))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        TextButton(onClick = { showGoalSettings = true }) { Text(if (english) "Set" else "Atur") }
                        Text("${(readingGoal.completedCount * 100 / readingGoal.targetPages.coerceAtLeast(1))}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { (readingGoal.completedCount.toFloat() / readingGoal.targetPages.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        enabled = readingGoal.completedPages.isNotEmpty(),
                        onClick = { showManageReadPages = true },
                    ) {
                        Text(if (english) "Select pages to unmark" else "Pilih halaman untuk dibatalkan")
                    }
                }
            }

            HomeLatestVideosSection(
                title = if (english) "Latest talks" else "Kajian terbaru",
                actionLabel = if (english) "All talks" else "Semua kajian",
                videos = newestKajian,
                isLoading = kajianSnapshot == null,
                isShorts = false,
                emptyMessage = if (english) "No recent talks available." else kajianSnapshot?.message ?: "Belum ada kajian terbaru.",
                onSeeAll = onOpenAllKajian,
                onPlay = { index -> onPlayKajian(newestKajian, index) },
            )
            HomeLatestVideosSection(
                title = if (english) "Latest Shorts" else "Shorts terbaru",
                actionLabel = if (english) "All Shorts" else "Semua Shorts",
                videos = newestShorts,
                isLoading = kajianSnapshot == null,
                isShorts = true,
                emptyMessage = if (english) "No recent Shorts available." else kajianSnapshot?.message ?: "Belum ada Shorts terbaru.",
                onSeeAll = onOpenAllShorts,
                onPlay = { index -> onPlayShorts(newestShorts, index) },
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Column {
                        Text(if (english) "Saved on this device" else "Tersimpan di perangkat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Recently opened mushaf pages are kept ready for quicker reading." else "Halaman mushaf yang pernah dibuka disiapkan agar lebih cepat dibaca lagi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showGoalSettings) {
        ReadingGoalSettingsDialog(
            snapshot = readingGoal,
            currentPage = lastReadPage,
            onDismiss = { showGoalSettings = false },
            onSave = { targetPages, reminderMinutes, khatamDate ->
                readingGoal = goalStore.updateSettings(targetPages, reminderMinutes, khatamDate)
                DailySunnahWidgetProvider.updateAll(context)
                ReadingGoalReminderScheduler.scheduleNext(context)
                if (reminderMinutes.isNotEmpty() && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                showGoalSettings = false
            },
        )
    }

    if (showManageReadPages) {
        ManageReadPagesDialog(
            completedPages = readingGoal.completedPages,
            english = english,
            onDismiss = { showManageReadPages = false },
            onUnmark = { pages ->
                readingGoal = goalStore.unmarkPagesRead(pages)
                DailySunnahWidgetProvider.updateAll(context)
                if (readingGoal.isComplete) ReadingGoalReminderScheduler.cancel(context)
                else ReadingGoalReminderScheduler.scheduleNext(context)
                showManageReadPages = false
            },
        )
    }
}

@Composable
private fun ManageReadPagesDialog(
    completedPages: Set<Int>,
    english: Boolean,
    onDismiss: () -> Unit,
    onUnmark: (Set<Int>) -> Unit,
) {
    val sortedPages = remember(completedPages) { completedPages.sorted() }
    var selectedPages by remember(completedPages) { mutableStateOf(emptySet<Int>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Manage completed pages" else "Kelola halaman selesai") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (english) "Select pages to remove from today’s progress." else "Pilih halaman yang ingin dibatalkan dari progres hari ini.")
                if (sortedPages.isEmpty()) {
                    Text(if (english) "No pages have been marked complete." else "Belum ada halaman yang ditandai selesai.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(sortedPages, key = { it }) { page ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedPages = if (page in selectedPages) selectedPages - page else selectedPages + page
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = page in selectedPages,
                                    onCheckedChange = { checked ->
                                        selectedPages = if (checked) selectedPages + page else selectedPages - page
                                    },
                                )
                                Text(if (english) "Page $page" else "Halaman $page", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedPages.isNotEmpty(),
                onClick = { onUnmark(selectedPages) },
            ) { Text(if (english) "Unmark (${selectedPages.size})" else "Batalkan tanda (${selectedPages.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (english) "Back" else "Kembali") } },
    )
}

@Composable
private fun HomeLatestVideosSection(
    title: String,
    actionLabel: String,
    videos: List<KajianVideo>,
    isLoading: Boolean,
    isShorts: Boolean,
    emptyMessage: String,
    onSeeAll: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    val english = LocalAppEnglish.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onSeeAll) { Text(actionLabel) }
        }
        if (videos.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                Text(
                    if (isLoading) if (english) "Loading latest videos…" else "Memuat video terbaru…" else emptyMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                videos.forEachIndexed { index, video ->
                    Card(
                        modifier = Modifier
                            .size(width = if (isShorts) 156.dp else 252.dp, height = if (isShorts) 330.dp else 252.dp)
                            .clickable { onPlay(index) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column {
                            KajianThumbnail(
                                video = video,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    video.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${video.channelName} · ${video.category}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
