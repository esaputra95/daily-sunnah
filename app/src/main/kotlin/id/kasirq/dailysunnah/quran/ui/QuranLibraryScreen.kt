package id.kasirq.dailysunnah.quran.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import id.kasirq.dailysunnah.quran.data.JuzStart
import id.kasirq.dailysunnah.quran.data.QuranBookmark
import id.kasirq.dailysunnah.quran.data.QuranBookmarkStore
import id.kasirq.dailysunnah.quran.data.QuranJuzIndex
import id.kasirq.dailysunnah.quran.data.SurahPageIndex
import id.kasirq.dailysunnah.quran.data.SurahStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranLibraryScreen(
    onBack: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onOpenBookmark: (QuranBookmark) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val bookmarkStore = remember { QuranBookmarkStore(context) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var surahs by remember { mutableStateOf<List<SurahStart>>(emptyList()) }
    var bookmarks by remember { mutableStateOf<List<QuranBookmark>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching { SurahPageIndex.load(context) } to bookmarkStore.all()
        }
        surahs = result.first.getOrDefault(emptyList())
        loadError = result.first.isFailure
        bookmarks = result.second
        isLoading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { androidx.compose.material3.TextButton(onClick = onBack) { Text("‹") } },
                title = {
                    Column {
                        Text("Al-Qur’an", fontWeight = FontWeight.SemiBold)
                        Text("Mushaf Madinah", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Surah", "Juz", "Bookmark").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Daftar Surah dan Juz tersedia offline.",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Memuat daftar…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                loadError -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Daftar Surah offline tidak dapat dibaca. Coba perbarui aplikasi.")
                }
                selectedTab == 0 -> SurahList(surahs = surahs, onSelect = { onSelectPage(it.page) })
                selectedTab == 1 -> JuzList(surahs = surahs, onSelect = { onSelectPage(it.page) })
                else -> BookmarkList(
                    surahs = surahs,
                    bookmarks = bookmarks,
                    onSelect = onOpenBookmark,
                )
            }
        }
    }
}

@Composable
private fun SurahList(surahs: List<SurahStart>, onSelect: (SurahStart) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(surahs, key = { it.number }) { surah ->
            QuranIndexRow(
                leading = surah.number.toString(),
                title = surah.name,
                subtitle = "Surah ${surah.number} · Mulai halaman ${surah.page}",
                onClick = { onSelect(surah) },
            )
        }
    }
}

@Composable
private fun JuzList(surahs: List<SurahStart>, onSelect: (JuzStart) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(QuranJuzIndex.starts, key = { it.number }) { juz ->
            val surahName = surahs.firstOrNull { it.number == juz.surah }?.name ?: "Surah ${juz.surah}"
            QuranIndexRow(
                leading = juz.number.toString(),
                title = "Juz ${juz.number}",
                subtitle = "$surahName · Ayat ${juz.ayah} · Halaman ${juz.page}",
                onClick = { onSelect(juz) },
            )
        }
    }
}

@Composable
private fun BookmarkList(
    surahs: List<SurahStart>,
    bookmarks: List<QuranBookmark>,
    onSelect: (QuranBookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Belum ada bookmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Pilih ayat di halaman mushaf, lalu tekan Bookmark.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(bookmarks, key = { it.verseKey }) { bookmark ->
            val parts = bookmark.verseKey.split(':')
            val surahNumber = parts.getOrNull(0)?.toIntOrNull()
            val surahName = surahs.firstOrNull { it.number == surahNumber }?.name ?: "Surah $surahNumber"
            val ayahNumber = parts.getOrNull(1) ?: "?"
            QuranIndexRow(
                leading = "☆",
                title = "$surahName · Ayat $ayahNumber",
                subtitle = "Halaman ${bookmark.page} · Ketuk untuk membuka",
                onClick = { onSelect(bookmark) },
            )
        }
    }
}

@Composable
private fun QuranIndexRow(
    leading: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text(leading, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
