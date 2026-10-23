package id.kasirq.dailysunnah.quran.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.kasirq.dailysunnah.quran.data.JuzStart
import id.kasirq.dailysunnah.quran.data.QuranBookmark
import id.kasirq.dailysunnah.quran.data.QuranBookmarkStore
import id.kasirq.dailysunnah.quran.data.QuranJuzIndex
import id.kasirq.dailysunnah.quran.data.SurahPageIndex
import id.kasirq.dailysunnah.quran.data.SurahStart
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuranLibraryScreen(
    surahListState: LazyListState,
    juzListState: LazyListState,
    bookmarkListState: LazyListState,
    initialTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onOpenBookmark: (QuranBookmark) -> Unit,
) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current.applicationContext
    val bookmarkStore = remember { QuranBookmarkStore(context) }
    val scope = rememberCoroutineScope()
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState(initialPage = initialTabIndex.coerceIn(0, 2), pageCount = { 3 })
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
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onTabSelected(it) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Al-Qur’an", fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Madinah Mushaf" else "Mushaf Madinah", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                },
                scrollBehavior = topBarScrollBehavior,
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                listOf("Surah", "Juz", "Bookmark").forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (english) "Loading list…" else "Memuat daftar…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    loadError -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(if (english) "The offline surah list could not be read. Try updating the app." else "Daftar Surah offline tidak dapat dibaca. Coba perbarui aplikasi.")
                    }
                    page == 0 -> SurahList(
                        surahs = surahs,
                        state = surahListState,
                        onOpenSearch = onOpenSearch,
                        onSelect = { onSelectPage(it.page) },
                    )
                    page == 1 -> JuzList(
                        surahs = surahs,
                        state = juzListState,
                        onOpenSearch = onOpenSearch,
                        onSelect = { onSelectPage(it.page) },
                    )
                    else -> BookmarkList(
                        surahs = surahs,
                        bookmarks = bookmarks,
                        state = bookmarkListState,
                        onOpenSearch = onOpenSearch,
                        onSelect = onOpenBookmark,
                    )
                }
            }
        }
    }
}

@Composable
private fun SurahList(
    surahs: List<SurahStart>,
    state: LazyListState,
    onOpenSearch: () -> Unit,
    onSelect: (SurahStart) -> Unit,
) {
    val english = LocalAppEnglish.current
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "quran-search") { QuranSearchEntry(onOpenSearch) }
        items(surahs, key = { it.number }) { surah ->
            QuranIndexRow(
                leading = surah.number.toString(),
                title = surah.name,
                subtitle = if (english) "Surah ${surah.number} · Starts on page ${surah.page}" else "Surah ${surah.number} · Mulai halaman ${surah.page}",
                onClick = { onSelect(surah) },
            )
        }
    }
}

@Composable
private fun JuzList(
    surahs: List<SurahStart>,
    state: LazyListState,
    onOpenSearch: () -> Unit,
    onSelect: (JuzStart) -> Unit,
) {
    val english = LocalAppEnglish.current
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "quran-search") { QuranSearchEntry(onOpenSearch) }
        items(QuranJuzIndex.starts, key = { it.number }) { juz ->
            val surahName = surahs.firstOrNull { it.number == juz.surah }?.name ?: "Surah ${juz.surah}"
            QuranIndexRow(
                leading = juz.number.toString(),
                title = "Juz ${juz.number}",
                subtitle = if (english) "$surahName · Verse ${juz.ayah} · Page ${juz.page}" else "$surahName · Ayat ${juz.ayah} · Halaman ${juz.page}",
                onClick = { onSelect(juz) },
            )
        }
    }
}

@Composable
private fun BookmarkList(
    surahs: List<SurahStart>,
    bookmarks: List<QuranBookmark>,
    state: LazyListState,
    onOpenSearch: () -> Unit,
    onSelect: (QuranBookmark) -> Unit,
) {
    val english = LocalAppEnglish.current
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "quran-search") { QuranSearchEntry(onOpenSearch) }
        if (bookmarks.isEmpty()) {
            item(key = "empty-bookmarks") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (english) "No bookmarks yet" else "Belum ada bookmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (english) "Select a verse on the mushaf page, then tap Bookmark." else "Pilih ayat di halaman mushaf, lalu tekan Bookmark.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(bookmarks, key = { it.verseKey }) { bookmark ->
            val parts = bookmark.verseKey.split(':')
            val surahNumber = parts.getOrNull(0)?.toIntOrNull()
            val surahName = surahs.firstOrNull { it.number == surahNumber }?.name ?: "Surah $surahNumber"
            val ayahNumber = parts.getOrNull(1) ?: "?"
            QuranIndexRow(
                leading = "☆",
                title = if (english) "$surahName · Verse $ayahNumber" else "$surahName · Ayat $ayahNumber",
                subtitle = if (english) "Page ${bookmark.page} · Tap to open" else "Halaman ${bookmark.page} · Ketuk untuk membuka",
                onClick = { onSelect(bookmark) },
            )
        }
    }
}

@Composable
private fun QuranSearchEntry(onOpenSearch: () -> Unit) {
    val english = LocalAppEnglish.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onOpenSearch),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Text(
            if (english) "⌕   Search translation or phrase" else "⌕   Cari terjemah atau frasa",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
