package id.kasirq.dailysunnah.quran.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.kasirq.dailysunnah.quran.data.QuranAudioEndBehavior
import id.kasirq.dailysunnah.quran.data.QuranAudioPreferences
import id.kasirq.dailysunnah.quran.data.MushafAssetRepository
import id.kasirq.dailysunnah.quran.data.MushafPrefetchScheduler
import id.kasirq.dailysunnah.quran.data.QuranReaderPreferences
import id.kasirq.dailysunnah.quran.data.ReadingGoalSnapshot
import id.kasirq.dailysunnah.quran.data.ReadingGoalStore
import id.kasirq.dailysunnah.quran.data.ReadingGoalReminderScheduler
import id.kasirq.dailysunnah.ui.BackNavigationButton
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSettingsScreen(onBack: () -> Unit) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current.applicationContext
    val preferences = remember { QuranAudioPreferences(context) }
    val readerPreferences = remember { QuranReaderPreferences(context) }
    val mushafAssets = remember { MushafAssetRepository(context) }
    val mushafDownloads = remember { MushafPrefetchScheduler(context) }
    val readingGoalStore = remember { ReadingGoalStore(context) }
    var selectedReciterId by rememberSaveable { mutableIntStateOf(preferences.reciterId()) }
    var endBehavior by rememberSaveable { mutableStateOf(preferences.endBehavior()) }
    var mushafZoomEnabled by rememberSaveable { mutableStateOf(readerPreferences.mushafZoomEnabled()) }
    var cachedPageCount by remember { mutableIntStateOf(0) }
    var fullDownloadRequested by rememberSaveable { mutableStateOf(false) }
    var readingGoal by remember { mutableStateOf(ReadingGoalSnapshot(ReadingGoalStore.DEFAULT_TARGET_PAGES, emptyList(), emptySet())) }
    var showReadingGoalDialog by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        cachedPageCount = mushafAssets.cachedPageCount()
        readingGoal = readingGoalStore.load()
    }
    LaunchedEffect(fullDownloadRequested) {
        while (fullDownloadRequested && cachedPageCount < MushafAssetRepository.MAX_PAGE) {
            delay(4_000)
            cachedPageCount = mushafAssets.cachedPageCount()
        }
        if (cachedPageCount >= MushafAssetRepository.MAX_PAGE) fullDownloadRequested = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { BackNavigationButton(onClick = onBack) },
                title = {
                    Column {
                        Text(if (english) "Qur’an settings" else "Pengaturan Qur’an", fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Mushaf and audio" else "Mushaf dan audio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSectionTitle(if (english) "Mushaf experience" else "Pengalaman mushaf")
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val enabled = !mushafZoomEnabled
                            mushafZoomEnabled = enabled
                            readerPreferences.setMushafZoomEnabled(enabled)
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (english) "Enable mushaf zoom" else "Izinkan zoom mushaf", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (english) "Pinch with two fingers to zoom the page. Off by default." else "Aktifkan untuk memperbesar halaman dengan cubit dua jari. Default: nonaktif.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = mushafZoomEnabled,
                            onCheckedChange = null,
                        )
                    }
                }
            }
            item {
                SettingsSectionTitle(if (english) "Daily reading and khatam plan" else "Target tilawah dan rencana khatam")
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (english) "${readingGoal.targetPages} pages per day" else "${readingGoal.targetPages} halaman per hari",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            readingGoal.khatamDate?.let { date ->
                                if (english) "Khatam target: $date" else "Target khatam: $date"
                            } ?: if (english) "Set a daily goal or choose a date to finish the Qur’an." else "Atur target harian atau pilih tanggal selesai membaca Al-Qur’an.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { showReadingGoalDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (english) "Set reading plan" else "Atur rencana tilawah")
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle(if (english) "Offline content" else "Konten offline")
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (english) "Surah and Juz lists are available offline" else "Daftar Surah dan Juz tersedia offline", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if (english) "Navigation index is stored on this device and remains available without internet." else "Indeks navigasi tersimpan di perangkat dan tetap bisa dibuka tanpa koneksi internet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 1.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(if (english) "Downloaded mushaf" else "Mushaf tersimpan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (english) "$cachedPageCount / ${MushafAssetRepository.MAX_PAGE} pages available offline" else "$cachedPageCount / ${MushafAssetRepository.MAX_PAGE} halaman tersedia offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { cachedPageCount.toFloat() / MushafAssetRepository.MAX_PAGE },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                mushafDownloads.prepareCompleteMushaf(wifiOnly = true)
                                fullDownloadRequested = true
                            },
                            enabled = cachedPageCount < MushafAssetRepository.MAX_PAGE,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (english) { if (cachedPageCount >= MushafAssetRepository.MAX_PAGE) "Mushaf ready offline" else "Download full mushaf over Wi-Fi" } else { if (cachedPageCount >= MushafAssetRepository.MAX_PAGE) "Mushaf siap offline" else "Unduh mushaf lengkap via Wi-Fi" })
                        }
                        if (fullDownloadRequested && cachedPageCount < MushafAssetRepository.MAX_PAGE) {
                            Text(
                                if (english) "Download continues when Wi-Fi is available. Completed pages can be opened offline." else "Unduhan berjalan saat Wi-Fi tersedia. Halaman yang selesai langsung bisa dibuka offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle(if (english) "Audio playback" else "Pemutaran audio")
                Text(if (english) "When verse audio ends" else "Saat audio ayat selesai", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(QuranAudioEndBehavior.entries.size) { index ->
                val option = QuranAudioEndBehavior.entries[index]
                SettingsChoice(
                    title = if (english) option.englishTitle else option.title,
                    description = if (english) option.englishDescription else option.description,
                    selected = endBehavior == option,
                    onClick = {
                        endBehavior = option
                        preferences.setEndBehavior(option)
                    },
                )
            }
            item {
                SettingsSectionTitle(if (english) "Reciter" else "Qari")
                Text(if (english) "Used when playing verses from the mushaf." else "Pilihan ini dipakai saat memutar ayat dari mushaf.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(QuranAudioPreferences.RECITERS.size) { index ->
                val reciter = QuranAudioPreferences.RECITERS[index]
                SettingsChoice(
                    title = reciter.name,
                    description = if (english) { if (reciter.id == QuranAudioPreferences.DEFAULT_RECITER_ID) "Default reciter" else "Verse-by-verse audio" } else { if (reciter.id == QuranAudioPreferences.DEFAULT_RECITER_ID) "Qari bawaan" else "Audio ayat per ayat" },
                    selected = selectedReciterId == reciter.id,
                    onClick = {
                        selectedReciterId = reciter.id
                        preferences.setReciterId(reciter.id)
                    },
                )
            }
            item {
                Text(
                    if (english) "Mushaf Arabic font: Uthmanic Hafs · King Fahd Glorious Quran Printing Complex / Quran Foundation" else "Font Arab Mushaf: Uthmanic Hafs · King Fahd Glorious Quran Printing Complex / Quran Foundation",
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showReadingGoalDialog) {
        ReadingGoalSettingsDialog(
            snapshot = readingGoal,
            currentPage = readerPreferences.lastReadPage(),
            onDismiss = { showReadingGoalDialog = false },
            onSave = { target, reminders, khatamDate ->
                readingGoal = readingGoalStore.updateSettings(target, reminders, khatamDate)
                ReadingGoalReminderScheduler.scheduleNext(context)
                if (reminders.isNotEmpty() && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                showReadingGoalDialog = false
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsChoice(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
