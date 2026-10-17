package id.kasirq.dailysunnah.quran.ui

import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val preferences = remember { QuranAudioPreferences(context) }
    val readerPreferences = remember { QuranReaderPreferences(context) }
    val mushafAssets = remember { MushafAssetRepository(context) }
    val mushafDownloads = remember { MushafPrefetchScheduler(context) }
    var selectedReciterId by rememberSaveable { mutableIntStateOf(preferences.reciterId()) }
    var endBehavior by rememberSaveable { mutableStateOf(preferences.endBehavior()) }
    var mushafZoomEnabled by rememberSaveable { mutableStateOf(readerPreferences.mushafZoomEnabled()) }
    var cachedPageCount by remember { mutableIntStateOf(0) }
    var fullDownloadRequested by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        cachedPageCount = mushafAssets.cachedPageCount()
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
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
                title = {
                    Column {
                        Text("Pengaturan Qur’an", fontWeight = FontWeight.SemiBold)
                        Text("Mushaf dan audio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                SettingsSectionTitle("Pengalaman mushaf")
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
                            Text("Izinkan zoom mushaf", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Aktifkan untuk memperbesar halaman dengan cubit dua jari. Default: nonaktif.",
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
                SettingsSectionTitle("Konten offline")
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Daftar Surah dan Juz tersedia offline", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Indeks navigasi tersimpan di perangkat dan tetap bisa dibuka tanpa koneksi internet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                        Text("Mushaf tersimpan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$cachedPageCount / ${MushafAssetRepository.MAX_PAGE} halaman tersedia offline",
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
                            Text(if (cachedPageCount >= MushafAssetRepository.MAX_PAGE) "Mushaf siap offline" else "Unduh mushaf lengkap via Wi-Fi")
                        }
                        if (fullDownloadRequested && cachedPageCount < MushafAssetRepository.MAX_PAGE) {
                            Text(
                                "Unduhan berjalan saat Wi-Fi tersedia. Halaman yang selesai langsung bisa dibuka offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle("Pemutaran audio")
                Text("Saat audio ayat selesai", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(QuranAudioEndBehavior.entries.size) { index ->
                val option = QuranAudioEndBehavior.entries[index]
                SettingsChoice(
                    title = option.title,
                    description = option.description,
                    selected = endBehavior == option,
                    onClick = {
                        endBehavior = option
                        preferences.setEndBehavior(option)
                    },
                )
            }
            item {
                SettingsSectionTitle("Qari")
                Text("Pilihan ini dipakai saat memutar ayat dari mushaf.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(QuranAudioPreferences.RECITERS.size) { index ->
                val reciter = QuranAudioPreferences.RECITERS[index]
                SettingsChoice(
                    title = reciter.name,
                    description = if (reciter.id == QuranAudioPreferences.DEFAULT_RECITER_ID) "Qari bawaan" else "Audio ayat per ayat",
                    selected = selectedReciterId == reciter.id,
                    onClick = {
                        selectedReciterId = reciter.id
                        preferences.setReciterId(reciter.id)
                    },
                )
            }
            item {
                Text(
                    "Font Arab Mushaf: Uthmanic Hafs · King Fahd Glorious Quran Printing Complex / Quran Foundation",
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
