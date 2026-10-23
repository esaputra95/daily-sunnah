package id.kasirq.dailysunnah.quran.ui

import android.Manifest
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.kasirq.dailysunnah.R
import id.kasirq.dailysunnah.home.DailySunnahWidgetProvider
import id.kasirq.dailysunnah.quran.data.MushafAssetRepository
import id.kasirq.dailysunnah.quran.data.MushafPageAsset
import id.kasirq.dailysunnah.quran.data.MushafPrefetchScheduler
import id.kasirq.dailysunnah.ui.BackNavigationButton
import id.kasirq.dailysunnah.quran.data.QuranBookmark
import id.kasirq.dailysunnah.quran.data.QuranBookmarkStore
import id.kasirq.dailysunnah.quran.data.QuranJuzIndex
import id.kasirq.dailysunnah.quran.data.ReadingGoalReminderScheduler
import id.kasirq.dailysunnah.quran.data.ReadingGoalSnapshot
import id.kasirq.dailysunnah.quran.data.ReadingGoalStore
import id.kasirq.dailysunnah.quran.data.KhatamPlanner
import id.kasirq.dailysunnah.quran.data.QuranReaderPreferences
import id.kasirq.dailysunnah.quran.data.QuranSurahArabicNames
import id.kasirq.dailysunnah.quran.data.QuranSurahOpeningPolicy
import id.kasirq.dailysunnah.quran.data.QuranAudioPreferences
import id.kasirq.dailysunnah.quran.data.QuranAudioEndBehavior
import id.kasirq.dailysunnah.quran.data.QuranVerseKey
import id.kasirq.dailysunnah.quran.data.QuranVerseContent
import id.kasirq.dailysunnah.quran.data.QuranVerseContentRepository
import id.kasirq.dailysunnah.quran.data.SurahStart
import id.kasirq.dailysunnah.quran.data.SurahPageIndex
import id.kasirq.dailysunnah.ui.ModernTextFieldShape
import id.kasirq.dailysunnah.ui.modernTextFieldColors
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlin.math.min
import java.time.LocalTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QuranReaderScreen(
    startPage: Int? = null,
    startVerseKey: String? = null,
    onOpenLibrary: () -> Unit = {},
) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val english = LocalAppEnglish.current
    val repository = remember { MushafAssetRepository(context) }
    val prefetch = remember { MushafPrefetchScheduler(context) }
    val goalStore = remember { ReadingGoalStore(context) }
    val readerPreferences = remember { QuranReaderPreferences(context) }
    val audioPreferences = remember { QuranAudioPreferences(context) }
    val bookmarkStore = remember { QuranBookmarkStore(context) }
    val verseContentRepository = remember { QuranVerseContentRepository(context) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    val pagerState = rememberPagerState(pageCount = { MushafAssetRepository.MAX_PAGE })
    var retryKey by rememberSaveable { mutableIntStateOf(0) }
    var pageInput by rememberSaveable { mutableStateOf("1") }
    var asset by remember { mutableStateOf<MushafPageAsset?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedVerse by remember { mutableStateOf<String?>(null) }
    var showVerseActions by rememberSaveable { mutableStateOf(false) }
    var showVerseContent by rememberSaveable { mutableStateOf(false) }
    var showTafsir by rememberSaveable { mutableStateOf(false) }
    var tafsirInEnglish by rememberSaveable { mutableStateOf(false) }
    var verseContent by remember { mutableStateOf<QuranVerseContent?>(null) }
    var verseContentLoading by remember { mutableStateOf(false) }
    var verseContentError by remember { mutableStateOf<String?>(null) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var audioStatus by remember { mutableStateOf("") }
    var audioPlaying by remember { mutableStateOf(false) }
    var startupPagesScheduled by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var mushafZoomEnabled by remember { mutableStateOf(readerPreferences.mushafZoomEnabled()) }
    var readingGoal by remember { mutableStateOf(ReadingGoalSnapshot(ReadingGoalStore.DEFAULT_TARGET_PAGES, emptyList(), emptySet())) }
    var showGoalSettings by rememberSaveable { mutableStateOf(false) }
    var showPagePicker by rememberSaveable { mutableStateOf(false) }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    var pageRestored by rememberSaveable { mutableStateOf(false) }
    var surahStarts by remember { mutableStateOf<List<SurahStart>>(emptyList()) }
    var bookmarks by remember { mutableStateOf<List<QuranBookmark>>(emptyList()) }
    var pendingBookmarkVerse by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val notificationPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun recordReadPage(pageToRecord: Int) {
        if (asset?.page != pageToRecord || isLoading) return

        readingGoal = goalStore.markPageRead(pageToRecord)
        DailySunnahWidgetProvider.updateAll(context)
        if (readingGoal.isComplete) ReadingGoalReminderScheduler.cancel(context)
        else ReadingGoalReminderScheduler.scheduleNext(context)
    }

    fun leaveReader() {
        recordReadPage(page)
        onOpenLibrary()
    }

    BackHandler {
        when {
            showPagePicker -> showPagePicker = false
            showBookmarks -> showBookmarks = false
            showGoalSettings -> showGoalSettings = false
            showVerseContent -> { showVerseContent = false; showTafsir = false }
            showVerseActions -> showVerseActions = false
            else -> leaveReader()
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { audioPlayer?.release() }; audioPlayer = null }
    }

    LaunchedEffect(english) { tafsirInEnglish = english }

    LaunchedEffect(showVerseContent, showTafsir, tafsirInEnglish, selectedVerse, english) {
        if (showVerseContent && selectedVerse != null) {
            verseContentLoading = true
            verseContentError = null
            verseContent = null
            try {
                verseContent = withContext(Dispatchers.IO) {
                    if (showTafsir) verseContentRepository.loadTafsir(selectedVerse!!, tafsirInEnglish) else verseContentRepository.loadTranslation(selectedVerse!!, english)
                }
            } catch (error: Exception) {
                verseContentError = if (!showTafsir) {
                    verseContentRepository.translationLoadError(error, english)
                } else if (english) {
                    "Tafsir could not be loaded from its source. Please try again later."
                } else {
                    "Tafsir tidak dapat dimuat dari sumber. Coba lagi nanti."
                }
            } finally {
                verseContentLoading = false
            }
        }
    }

    fun playVerse(verseKey: String) {
        audioPlayer?.let { runCatching { it.stop(); it.release() } }
        audioPlayer = null
        audioPlaying = false
                    audioStatus = if (english) "Loading verse audio $verseKey…" else "Memuat audio ayat $verseKey…"
        coroutineScope.launch {
            try {
                val reciterId = audioPreferences.reciterId()
                val address = withContext(Dispatchers.IO) { verseContentRepository.audioUrl(verseKey, reciterId) }
                val player = MediaPlayer()
                audioPlayer = player
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                player.setDataSource(address)
                player.setOnPreparedListener {
                    it.start()
                    audioPlaying = true
                    audioStatus = if (english) "Playing $verseKey" else "Sedang memutar $verseKey"
                }
                player.setOnCompletionListener {
                    when (audioPreferences.endBehavior()) {
                        QuranAudioEndBehavior.REPEAT -> {
                            runCatching { it.seekTo(0); it.start() }
                            audioPlaying = true
                            audioStatus = if (english) "Repeating verse $verseKey" else "Mengulangi ayat $verseKey"
                        }
                        QuranAudioEndBehavior.NEXT_AYAH -> {
                            it.release()
                            if (audioPlayer === it) audioPlayer = null
                            audioPlaying = false
                            val nextVerse = QuranVerseKey.next(verseKey)
                            if (nextVerse == null) {
                                audioStatus = if (english) "Audio finished at the end of Surah An-Nas." else "Audio selesai di akhir Surah An-Nas."
                            } else {
                                playVerse(nextVerse)
                            }
                        }
                        QuranAudioEndBehavior.STOP -> {
                            audioPlaying = false
                            audioStatus = if (english) "Audio finished." else "Audio selesai."
                            it.release()
                            if (audioPlayer === it) audioPlayer = null
                        }
                    }
                }
                player.setOnErrorListener { current, _, _ ->
                    audioPlaying = false
                    audioStatus = if (english) "Audio could not play. Try again when your connection is stable." else "Audio gagal diputar. Coba lagi saat koneksi stabil."
                    current.release()
                    if (audioPlayer === current) audioPlayer = null
                    true
                }
                player.prepareAsync()
            } catch (error: Exception) {
                audioStatus = if (english) "Audio could not load. Check your internet connection." else error.message ?: "Audio gagal dimuat."
            }
        }
    }

    fun playSelectedVerse() {
        selectedVerse?.let(::playVerse)
    }

    LaunchedEffect(startPage, startVerseKey) {
        val (lastPage, goal, savedBookmarks) = withContext(Dispatchers.IO) {
            Triple(readerPreferences.lastReadPage(), goalStore.load(), bookmarkStore.all())
        }
        val openingPage = startPage?.takeIf { it in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE } ?: lastPage
        page = openingPage
        pageInput = openingPage.toString()
        pagerState.scrollToPage(openingPage - 1)
        pendingBookmarkVerse = startVerseKey
        readingGoal = goal
        bookmarks = savedBookmarks
        pageRestored = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000))
            readingGoal = withContext(Dispatchers.IO) { goalStore.load() }
        }
    }

    LaunchedEffect(mushafZoomEnabled) {
        if (!mushafZoomEnabled) {
            zoom = 1f
            pan = Offset.Zero
        }
    }

    LaunchedEffect(page, pageRestored) {
        if (!pageRestored) return@LaunchedEffect
        withContext(Dispatchers.IO) { readerPreferences.saveLastReadPage(page) }
        if (!startupPagesScheduled) {
            startupPagesScheduled = true
            surahStarts = runCatching {
                withContext(Dispatchers.IO) { SurahPageIndex.load(context) }
            }.getOrDefault(emptyList())
            prefetch.prepareSurahStarts(surahStarts.map { it.page })
        }
    }

    LaunchedEffect(page, pageRestored, asset?.page, isLoading) {
        if (pageRestored && !isLoading && asset?.page == page) {
            prefetch.prepareNeighborhood(page)
        }
    }

    LaunchedEffect(pagerState, pageRestored) {
        if (!pageRestored) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledIndex ->
                val settledPage = settledIndex + 1
                if (settledPage != page) {
                    recordReadPage(page)
                    selectedVerse = null
                    showVerseActions = false
                    zoom = 1f
                    pan = Offset.Zero
                    isLoading = asset?.page != settledPage
                    page = settledPage
                    pageInput = settledPage.toString()
                }
            }
    }

    fun openPage(targetPage: Int) {
        if (targetPage in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE && targetPage != pagerState.settledPage + 1) {
            pageInput = targetPage.toString()
            coroutineScope.launch {
                val currentPage = pagerState.currentPage + 1
                if (abs(targetPage - currentPage) == 1) pagerState.animateScrollToPage(targetPage - 1)
                else pagerState.scrollToPage(targetPage - 1)
            }
        }
    }

    val pageReference = remember(asset?.page) {
        asset?.layout?.verseRegions
            ?.mapNotNull { parseVerseReference(it.verseKey) }
            ?.minOrNull()
    }
    val firstVerseOnPage = remember(asset?.page, asset?.layout) {
        asset?.layout?.verseRegions
            ?.mapNotNull { region -> parseVerseReference(region.verseKey)?.let { it to region.verseKey } }
            ?.minByOrNull { it.first }
            ?.second
    }
    val pageBookmarkSaved = firstVerseOnPage != null && bookmarks.any { it.verseKey == firstVerseOnPage }
    val currentSurahNumber = pageReference?.surah
    val currentSurah = currentSurahNumber?.let { number -> surahStarts.firstOrNull { it.number == number } }
        ?: surahStarts.lastOrNull { it.page <= page }
    val currentJuz = QuranJuzIndex.forVerse(pageReference?.surah, pageReference?.ayah)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = { BackNavigationButton(onClick = ::leaveReader) },
                title = {
                    Column {
                        Text("${currentSurah?.number ?: currentSurahNumber ?: 1}. ${currentSurah?.name ?: "Qur’an"}", style = MaterialTheme.typography.titleMedium)
                        Text(if (english) "Page $page" else "Halaman $page", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            pageInput = page.toString()
                            showPagePicker = true
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (english) "Search mushaf page" else "Cari halaman mushaf"
                        },
                    ) {
                        Text("⌕", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Juz $currentJuz", style = MaterialTheme.typography.labelLarge)
                    IconButton(
                        onClick = { showGoalSettings = true },
                        modifier = Modifier.semantics { contentDescription = if (english) "Reading goal settings" else "Pengaturan target bacaan" },
                    ) { Text("⚙", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val bookmarkedVerseKeys = remember(bookmarks) { bookmarks.mapTo(mutableSetOf()) { it.verseKey } }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                reverseLayout = true,
                pageSpacing = 0.dp,
                beyondViewportPageCount = 1,
                userScrollEnabled = pageRestored && (!mushafZoomEnabled || zoom <= 1.001f),
            ) { pageIndex ->
                val pageNumber = pageIndex + 1
                var pageAsset by remember(pageNumber, retryKey) { mutableStateOf<MushafPageAsset?>(null) }
                var pageLoading by remember(pageNumber, retryKey) { mutableStateOf(true) }
                var pageError by remember(pageNumber, retryKey) { mutableStateOf<String?>(null) }

                LaunchedEffect(pageNumber, retryKey, pageRestored) {
                    if (!pageRestored) return@LaunchedEffect
                    pageLoading = true
                    pageError = null
                    try {
                        pageAsset = withContext(Dispatchers.IO) { repository.loadPage(pageNumber) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        pageError = if (english) "Mushaf page could not load." else error.message ?: "Halaman mushaf belum dapat dimuat."
                    } finally {
                        pageLoading = false
                    }
                }

                LaunchedEffect(pageNumber, page, pageAsset, pageLoading, pageError, pendingBookmarkVerse) {
                    if (pageNumber != page) return@LaunchedEffect
                    isLoading = pageLoading
                    val loadedAsset = pageAsset ?: return@LaunchedEffect
                    asset = loadedAsset
                    pendingBookmarkVerse?.let { verse ->
                        selectedVerse = verse.takeIf { key ->
                            loadedAsset.layout.verseRegions.any { it.verseKey == key }
                        }
                        pendingBookmarkVerse = null
                    }
                }

                when {
                    !pageRestored || pageLoading -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(if (english) "Preparing Madinah Mushaf…" else "Menyiapkan Mushaf Madinah…")
                    }
                    pageAsset != null -> MushafPageCanvas(
                        asset = pageAsset!!,
                        zoom = if (mushafZoomEnabled) zoom else 1f,
                        pan = pan,
                        selectedVerse = selectedVerse,
                        bookmarkedVerseKeys = bookmarkedVerseKeys,
                        gesturesEnabled = pageAsset!!.page == page && !isLoading,
                        zoomEnabled = mushafZoomEnabled,
                        onSelectVerse = {
                            selectedVerse = it
                            showVerseActions = true
                        },
                        onTransform = { newZoom, newPan ->
                            zoom = newZoom
                            pan = newPan
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(pageError ?: if (english) "Page is unavailable." else "Halaman belum tersedia.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { retryKey += 1 }) { Text(if (english) "Try again" else "Coba lagi") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { openPage(page - 1) }, enabled = page > 1) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                TextButton(onClick = { showPagePicker = true }) { Text(if (english) "Page $page / 604" else "Hal. $page / 604") }
                IconButton(onClick = { openPage(page + 1) }, enabled = page < 604) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                IconButton(
                    enabled = asset?.page == page && firstVerseOnPage != null,
                    onClick = {
                        val verseKey = firstVerseOnPage ?: return@IconButton
                        bookmarks = bookmarkStore.toggle(verseKey, page)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (english) {
                                    if (pageBookmarkSaved) "Page bookmark removed." else "First verse on page $page saved to bookmarks."
                                } else {
                                    if (pageBookmarkSaved) "Bookmark halaman dihapus." else "Ayat pertama halaman $page disimpan ke bookmark."
                                }
                            )
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (english) {
                            if (pageBookmarkSaved) "Remove bookmark for page $page" else "Bookmark first verse on page $page"
                        } else if (pageBookmarkSaved) "Hapus bookmark awal halaman $page" else "Bookmark ayat pertama halaman $page"
                    },
                ) {
                    BookmarkGlyph(
                        filled = pageBookmarkSaved,
                        color = if (pageBookmarkSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { showBookmarks = true },
                    modifier = Modifier.semantics { contentDescription = if (english) "View verse bookmarks" else "Lihat daftar bookmark ayat" },
                ) {
                    BookmarkListGlyph(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    val verseParts = selectedVerse?.split(":")
    val verseSurahNumber = verseParts?.getOrNull(0)?.toIntOrNull()
    val verseNumber = verseParts?.getOrNull(1)
    val verseSurahName = verseSurahNumber?.let { number -> surahStarts.firstOrNull { it.number == number }?.name }
    if (showVerseActions && selectedVerse != null) {
        ModalBottomSheet(
            onDismissRequest = { showVerseActions = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${verseSurahName ?: "Surah $verseSurahNumber"} : Ayat ${verseNumber ?: ""}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(if (english) "Madinah Mushaf · Page $page" else "Mushaf Madinah · Halaman $page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            bookmarks = bookmarkStore.toggle(selectedVerse!!, page)
                            coroutineScope.launch { snackbarHostState.showSnackbar(if (english) "Verse bookmark updated." else "Bookmark ayat diperbarui.") }
                        },
                    ) {
                        Text(if (bookmarks.any { it.verseKey == selectedVerse }) if (english) "▣ Saved" else "▣ Tersimpan" else "☆ Bookmark")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = ::playSelectedVerse,
                    ) { Text(if (audioPlaying) if (english) "▶ Playing" else "▶ Memutar" else if (english) "▶ Play audio" else "▶ Putar audio") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { showTafsir = false; showVerseContent = true }) { Text(if (english) "文 Translation" else "文 Terjemah") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { tafsirInEnglish = english; showTafsir = true; showVerseContent = true }) { Text(if (english) "Tafsir" else "Tafsir") }
                }
                if (audioStatus.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(audioStatus, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        if (audioPlayer != null) TextButton(onClick = {
                            runCatching { audioPlayer?.stop(); audioPlayer?.release() }
                            audioPlayer = null
                            audioPlaying = false
                            audioStatus = if (english) "Audio stopped." else "Audio dihentikan."
                        }) { Text(if (english) "Stop" else "Stop") }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val reference = if (english) "Qur’an ${verseSurahName ?: "Surah $verseSurahNumber"} : Verse ${verseNumber ?: ""} · Madinah Mushaf, page $page"
                                else "QS. ${verseSurahName ?: "Surah $verseSurahNumber"} : Ayat ${verseNumber ?: ""} · Mushaf Madinah, halaman $page"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reference)
                            }
                        activityContext.startActivity(Intent.createChooser(shareIntent, if (english) "Share verse reference" else "Bagikan referensi ayat"))
                        },
                    ) { Text(if (english) "↗ Share reference" else "↗ Bagikan referensi") }
                }
            }
        }
    }
    if (showVerseContent) {
        ModalBottomSheet(
            onDismissRequest = { showVerseContent = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showTafsir && !english) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !tafsirInEnglish,
                            onClick = { tafsirInEnglish = false },
                            label = { Text("Kasirq · Indonesia") },
                        )
                        FilterChip(
                            selected = tafsirInEnglish,
                            onClick = { tafsirInEnglish = true },
                            label = { Text("Ibn Kathir · English") },
                        )
                    }
                }
                Text(
                    if (showTafsir) (if (english) "Tafsir for verse ${selectedVerse.orEmpty()}" else "${if (tafsirInEnglish) "Tafsir Ibn Kathir (English)" else "Tafsir Kasirq (Indonesia)"} · ${selectedVerse.orEmpty()}")
                    else if (english) "Translation of verse ${selectedVerse.orEmpty()}" else "Terjemah ayat ${selectedVerse.orEmpty()}",
                    style = MaterialTheme.typography.titleLarge,
                )
                when {
                    verseContentLoading -> CircularProgressIndicator()
                    verseContentError != null -> Text(verseContentError!!, color = MaterialTheme.colorScheme.error)
                    showTafsir -> Text(verseContent?.tafsir ?: if (english) "Tafsir is unavailable." else "Tafsir belum tersedia.")
                    else -> Text(verseContent?.translation ?: if (english) "Translation is unavailable." else "Terjemah belum tersedia.")
                }
                verseContent?.tafsirFallbackNotice?.let { notice -> Text(notice, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                if (showTafsir && tafsirInEnglish && !english) {
                    Text("Tafsir versi bahasa Indonesia belum kami sediakan untuk Ibn Kathir. Konten berikut hanya tersedia dalam bahasa Inggris.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (showTafsir) "Source: ${verseContent?.tafsirSource ?: if (english) "Tafsir Ibn Kathir (English)" else "Tafsir Kasirq (Indonesia)"}"
                    else "Source: ${verseContent?.translationSource ?: if (english) "QuranEnc · Rowwad Translation Center · v1.0.19" else "Quran.com · Indonesian translation"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!showTafsir) {
                    verseContent?.translationFootnotes?.let { footnotes ->
                        Text(
                            if (english) "Translator’s notes: $footnotes" else "Catatan penerjemah: $footnotes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    if (english) "Qur’an translations are published interpretations, not the Arabic text. This app uses an established translation and does not automatically translate Qur’anic text. For a more reliable understanding, consult trusted scholarly sources."
                    else "Terjemahan Al-Qur’an adalah penafsiran makna, bukan pengganti teks Arab. Aplikasi memakai terjemahan yang telah diterbitkan dan tidak menerjemahkan ayat secara otomatis. Untuk pemahaman yang lebih valid, rujuk sumber ilmiah tepercaya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        val docsUrl = when {
                            showTafsir -> TAFSIR_DOCS_URL
                            english -> QURANENC_TRANSLATION_DOCS_URL
                            else -> TRANSLATION_DOCS_URL
                        }
                        activityContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(docsUrl)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (english) "Browse official translation and tafsir documentation" else "Lihat dokumentasi resmi terjemah dan tafsir")
                }
            }
        }
    }

    if (showGoalSettings) {
        ReadingGoalSettingsDialog(
            snapshot = readingGoal,
            currentPage = page,
            onDismiss = { showGoalSettings = false },
            onSave = { target, reminders, khatamDate ->
                readingGoal = goalStore.updateSettings(target, reminders, khatamDate)
                DailySunnahWidgetProvider.updateAll(context)
                ReadingGoalReminderScheduler.scheduleNext(context)
                if (reminders.isNotEmpty() && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                showGoalSettings = false
            },
        )
    }
    if (showPagePicker) {
        val targetPage = pageInput.toIntOrNull()?.takeIf { it in 1..MushafAssetRepository.MAX_PAGE }
        AlertDialog(
            onDismissRequest = { showPagePicker = false },
            title = { Text(if (english) "Search mushaf page" else "Cari halaman mushaf") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { input -> pageInput = input.filter(Char::isDigit).take(3) },
                    shape = ModernTextFieldShape,
                    colors = modernTextFieldColors(),
                    label = { Text(if (english) "Page number (1–604)" else "Nomor halaman (1–604)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        targetPage?.let(::openPage)
                        if (targetPage != null) showPagePicker = false
                    }),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = targetPage != null,
                    onClick = {
                        targetPage?.let(::openPage)
                        showPagePicker = false
                    },
                ) { Text(if (english) "Search" else "Cari") }
            },
            dismissButton = { TextButton(onClick = { showPagePicker = false }) { Text(if (english) "Cancel" else "Batal") } },
        )
    }
    if (showBookmarks) {
        QuranBookmarksDialog(
            bookmarks = bookmarks,
            onDismiss = { showBookmarks = false },
            onSelect = { bookmark ->
                if (bookmark.page == page && asset != null) {
                    selectedVerse = bookmark.verseKey
                } else {
                    pendingBookmarkVerse = bookmark.verseKey
                    if (bookmark.page == page) retryKey += 1 else openPage(bookmark.page)
                }
                showBookmarks = false
            },
        )
    }
}

private const val TRANSLATION_DOCS_URL = "https://api-docs.quran.com/docs/content_apis_versioned/4.0.0/list-surah-translations/"
private const val TAFSIR_DOCS_URL = "https://api-docs.quran.com/docs/content_apis_versioned/4.0.0/tafsirs/"
private const val QURANENC_TRANSLATION_DOCS_URL = "https://quranenc.com/en/home/api/"

@Composable
private fun QuranBookmarksDialog(
    bookmarks: List<QuranBookmark>,
    onDismiss: () -> Unit,
    onSelect: (QuranBookmark) -> Unit,
) {
    val english = LocalAppEnglish.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Verse bookmarks" else "Bookmark ayat") },
        text = {
            if (bookmarks.isEmpty()) {
                Text(if (english) "No saved verses yet." else "Belum ada ayat yang disimpan.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(bookmarks, key = { it.verseKey }) { bookmark ->
                        TextButton(onClick = { onSelect(bookmark) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (english) "Verse ${bookmark.verseKey} · Page ${bookmark.page}" else "Ayat ${bookmark.verseKey} · Halaman ${bookmark.page}", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (english) "Close" else "Tutup") } },
    )
}

@Composable
private fun SurahPickerDialog(
    surahs: List<SurahStart>,
    onDismiss: () -> Unit,
    onSelect: (SurahStart) -> Unit,
) {
    val english = LocalAppEnglish.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Choose a surah" else "Pilih surah") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(surahs, key = { it.number }) { surah ->
                    TextButton(onClick = { onSelect(surah) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("${surah.number}. ${surah.name}")
                            Text(if (english) "Page ${surah.page}" else "Halaman ${surah.page}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (english) "Close" else "Tutup") } },
    )
}

@Composable
internal fun ReadingGoalSettingsDialog(
    snapshot: ReadingGoalSnapshot,
    currentPage: Int,
    onDismiss: () -> Unit,
    onSave: (targetPages: Int, reminderMinutes: List<Int>, khatamDate: LocalDate?) -> Unit,
) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    var targetText by remember { mutableStateOf(snapshot.manualTargetPages.toString()) }
    var khatamDateText by remember(snapshot.khatamDate) { mutableStateOf(snapshot.khatamDate?.toString().orEmpty()) }
    val reminders = remember { mutableStateListOf<Int>().apply { addAll(snapshot.reminderMinutes) } }
    val targetPages = targetText.toIntOrNull()?.takeIf { it in 1..MushafAssetRepository.MAX_PAGE }
    val today = LocalDate.now()
    val khatamDate = khatamDateText.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.takeIf { !it.isBefore(today) }
    val khatamPagesPerDay = khatamDate?.let { KhatamPlanner.pagesPerDay(currentPage, it, today) }
    val displayLocale = Locale.forLanguageTag(if (english) "en-US" else "id-ID")
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Daily reading goal" else "Target bacaan harian") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (english) "Choose how many pages you want to complete each day." else "Pilih jumlah halaman yang ingin kamu selesaikan setiap hari.")
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter(Char::isDigit).take(3) },
                    shape = ModernTextFieldShape,
                    colors = modernTextFieldColors(),
                    label = { Text(if (english) "Pages per day" else "Halaman per hari") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                HorizontalDivider()
                Text(if (english) "Khatam plan" else "Rencana khatam", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (english) "Choose a finish date. Daily pages will adjust from your last opened page ($currentPage)."
                    else "Pilih tanggal selesai. Target harian dihitung dari halaman terakhir yang dibuka ($currentPage).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val initial = khatamDate ?: today.plusDays(30)
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> khatamDateText = LocalDate.of(year, month + 1, day).toString() },
                                initial.year,
                                initial.monthValue - 1,
                                initial.dayOfMonth,
                            ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(khatamDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy", displayLocale))
                            ?: if (english) "Choose finish date" else "Pilih tanggal selesai")
                    }
                    if (khatamDateText.isNotBlank()) TextButton(onClick = { khatamDateText = "" }) {
                        Text(if (english) "Clear" else "Hapus")
                    }
                }
                if (khatamDate != null && khatamPagesPerDay != null) {
                    Text(
                        if (english) "Plan: about $khatamPagesPerDay pages per day through ${khatamDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", displayLocale))}."
                        else "Rencana: sekitar $khatamPagesPerDay halaman per hari sampai ${khatamDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", displayLocale))}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(if (english) "Reminders if the goal is not complete" else "Pengingat jika target belum tercapai", style = MaterialTheme.typography.titleSmall)
                reminders.forEachIndexed { index, minute ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalTime.of(minute / 60, minute % 60).format(timeFormatter), modifier = Modifier.weight(1f))
                        TextButton(onClick = { reminders.removeAt(index) }) { Text(if (english) "Remove" else "Hapus") }
                    }
                }
                TextButton(
                    enabled = reminders.size < ReadingGoalStore.MAX_REMINDERS,
                    onClick = {
                        val now = LocalTime.now()
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val selected = hour * 60 + minute
                                if (selected !in reminders) reminders.add(selected)
                                reminders.sort()
                            },
                            now.hour,
                            now.minute,
                            true,
                        ).show()
                    },
                ) { Text(if (english) "Add time" else "Tambah waktu") }
                Text(
                    if (english) "Android may delay reminders while battery saver is active." else "Android dapat menunda pengingat saat mode hemat daya aktif.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = targetPages != null,
                onClick = { targetPages?.let { onSave(it, reminders.toList(), khatamDate) } },
            ) { Text(if (english) "Save" else "Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (english) "Cancel" else "Batal") } },
    )
}

@Composable
private fun MushafPageCanvas(
    asset: MushafPageAsset,
    zoom: Float,
    pan: Offset,
    selectedVerse: String?,
    bookmarkedVerseKeys: Set<String>,
    gesturesEnabled: Boolean,
    zoomEnabled: Boolean,
    onSelectVerse: (String) -> Unit,
    onTransform: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(asset.bitmap) { asset.bitmap.asImageBitmap() }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mushafInkColor = MaterialTheme.colorScheme.onSurface
    val pageColorFilter = remember(isDarkTheme, mushafInkColor) {
        if (!isDarkTheme) null else ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -mushafInkColor.red, 0f, 0f, 0f, mushafInkColor.red * 255f,
                    0f, -mushafInkColor.green, 0f, 0f, mushafInkColor.green * 255f,
                    0f, 0f, -mushafInkColor.blue, 0f, mushafInkColor.blue * 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    val context = LocalContext.current
    val hafsTypeface = remember(context) {
        runCatching {
            Typeface.createFromAsset(context.assets, "fonts/UthmanicHafs1Ver18.ttf")
        }.getOrElse { Typeface.create("serif", Typeface.NORMAL) }
    }
    val nastaleeqTypeface = remember(context) {
        runCatching {
            context.resources.getFont(R.font.kfgqpc_nastaleeq_regular)
        }.getOrElse { hafsTypeface }
    }
    val surahDecorations = remember(asset.layout, hafsTypeface, nastaleeqTypeface, isDarkTheme, mushafInkColor) {
        buildSurahDecorations(asset.layout, hafsTypeface, nastaleeqTypeface, isDarkTheme, mushafInkColor.toArgb())
    }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val currentOnTransform by rememberUpdatedState(onTransform)
    Canvas(
        modifier = modifier
            .background(if (isDarkTheme) Color(0xFF101415) else Color.White)
            .pointerInput(asset.page, gesturesEnabled) {
                if (gesturesEnabled) {
                    detectTapGestures { touch ->
                        val viewBox = asset.layout.renderViewBox
                        val fitScale = size.width / viewBox.width * mushafPageDisplayScale(asset.page)
                        if (fitScale <= 0f) return@detectTapGestures
                        val drawnWidth = viewBox.width * fitScale
                        val drawnHeight = viewBox.height * fitScale
                        val left = (size.width - drawnWidth) / 2f
                        val top = mushafPageTop(size.height.toFloat(), drawnHeight, asset.page)
                        val baseX = (touch.x - currentPan.x) / currentZoom
                        val verticalOverflow = (drawnHeight - size.height).coerceAtLeast(0f)
                        val effectivePanY = when {
                            currentZoom > 1.001f -> currentPan.y
                            verticalOverflow > 0f -> currentPan.y.coerceIn(-verticalOverflow, 0f)
                            else -> 0f
                        }
                        val baseY = (touch.y - effectivePanY) / currentZoom
                        if (baseX < left || baseX > left + drawnWidth || baseY < top || baseY > top + drawnHeight) {
                            return@detectTapGestures
                        }
                        val viewX = viewBox.x + (baseX - left) / fitScale
                        val viewY = viewBox.y + (baseY - top) / fitScale
                        asset.layout.verseRegions.firstOrNull { it.rect.contains(viewX, viewY) }
                            ?.let { onSelectVerse(it.verseKey) }
                    }
                }
            }
            .pointerInput(asset.page, gesturesEnabled, zoomEnabled) {
                if (gesturesEnabled) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var usedMultiTouch = false
                        var accumulatedDrag = Offset.Zero
                        var dragAxis = 0 // 0 = undecided, 1 = vertical, 2 = horizontal
                        val viewBox = asset.layout.renderViewBox
                        val contentScale = size.width.toFloat() / viewBox.width * mushafPageDisplayScale(asset.page)
                        val verticalOverflow = (viewBox.height * contentScale - size.height).coerceAtLeast(0f)
                        val touchSlop = viewConfiguration.touchSlop

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                usedMultiTouch = true
                                if (zoomEnabled) {
                                    val centroid = pressed.fold(Offset.Zero) { sum, change -> sum + change.position } / pressed.size.toFloat()
                                    val oldCentroid = pressed.fold(Offset.Zero) { sum, change -> sum + change.previousPosition } / pressed.size.toFloat()
                                    val span = pressed.map { (it.position - centroid).getDistance() }.average().toFloat()
                                    val oldSpan = pressed.map { (it.previousPosition - oldCentroid).getDistance() }.average().toFloat()
                                    val activeZoom = currentZoom
                                    val activePan = currentPan
                                    val zoomChange = if (oldSpan > 0f) span / oldSpan else 1f
                                    val panChange = centroid - oldCentroid
                                    val nextZoom = (activeZoom * zoomChange).coerceIn(1f, 4f)
                                    if (nextZoom == 1f) {
                                        currentOnTransform(1f, Offset.Zero)
                                    } else {
                                        val pointUnderFinger = (centroid - activePan) / activeZoom
                                        currentOnTransform(nextZoom, centroid - pointUnderFinger * nextZoom + panChange)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } else {
                                val change = pressed.firstOrNull()
                                if (change != null) {
                                    val panChange = change.positionChange()
                                    if (panChange != Offset.Zero && !usedMultiTouch) {
                                        if (currentZoom > 1.001f) {
                                            currentOnTransform(currentZoom, currentPan + panChange)
                                            change.consume()
                                        } else if (verticalOverflow > 0f) {
                                            accumulatedDrag += panChange
                                            if (dragAxis == 0 && accumulatedDrag.getDistance() >= touchSlop) {
                                                dragAxis = when {
                                                    kotlin.math.abs(accumulatedDrag.y) > kotlin.math.abs(accumulatedDrag.x) * 1.15f -> 1
                                                    kotlin.math.abs(accumulatedDrag.x) > kotlin.math.abs(accumulatedDrag.y) * 1.15f -> 2
                                                    else -> 0
                                                }
                                                if (dragAxis == 1) {
                                                    val nextY = (currentPan.y + accumulatedDrag.y).coerceIn(-verticalOverflow, 0f)
                                                    currentOnTransform(1f, Offset(currentPan.x, nextY))
                                                    change.consume()
                                                }
                                            } else if (dragAxis == 1) {
                                                val nextY = (currentPan.y + panChange.y).coerceIn(-verticalOverflow, 0f)
                                                currentOnTransform(1f, Offset(currentPan.x, nextY))
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
    ) {
        val viewBox = asset.layout.renderViewBox
        val fitScale = size.width / viewBox.width * mushafPageDisplayScale(asset.page)
        if (fitScale <= 0f) return@Canvas

        val drawnWidth = viewBox.width * fitScale
        val drawnHeight = viewBox.height * fitScale
        val left = (size.width - drawnWidth) / 2f
        val top = mushafPageTop(size.height, drawnHeight, asset.page)
        val verticalOverflow = (drawnHeight - size.height).coerceAtLeast(0f)
        val effectivePanY = when {
            zoom > 1.001f -> pan.y
            verticalOverflow > 0f -> pan.y.coerceIn(-verticalOverflow, 0f)
            else -> 0f
        }
        val transformedLeft = left * zoom + pan.x
        val transformedTop = top * zoom + effectivePanY
        val transformedWidth = drawnWidth * zoom
        val transformedHeight = drawnHeight * zoom

        drawImage(
            image = imageBitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset(transformedLeft.toInt(), transformedTop.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(transformedWidth.toInt(), transformedHeight.toInt()),
            colorFilter = pageColorFilter,
        )

        // Keep the frame and surah cartouche at normal page size while the sparse opening-page
        // text is scaled and lifted within it.
        val frameScale = size.width / viewBox.width * zoom
        val frameWidth = viewBox.width * frameScale
        val frameHeight = viewBox.height * frameScale
        val frameLeft = ((size.width - viewBox.width * (size.width / viewBox.width)) / 2f) * zoom + pan.x
        val normalPageHeight = viewBox.height * size.width / viewBox.width
        val frameBaseTop = if (normalPageHeight > size.height) 0f else (size.height - normalPageHeight) / 2f
        val frameTop = frameBaseTop * zoom + effectivePanY
        val pageCenterX = frameLeft + frameWidth / 2f
        val sourceScale = fitScale * zoom
        val dividerScale = frameScale
        surahDecorations.forEach { decoration ->
            val header = decoration.header.rect
            val headerWidth = header.width * dividerScale
            val headerHeight = header.height * dividerScale
            val headerTop = frameTop + (header.y - viewBox.y) * dividerScale
            drawSurahDivider(
                left = frameLeft,
                top = headerTop,
                width = frameWidth,
                height = headerHeight,
                plaqueWidth = minOf(frameWidth * 0.44f, headerWidth * 1.28f),
                scale = dividerScale,
                darkTheme = isDarkTheme,
            )

            drawArabicLayout(
                canvas = drawContext.canvas.nativeCanvas,
                layout = decoration.title,
                left = pageCenterX - decoration.title.width * dividerScale / 2f,
                top = headerTop + (headerHeight - decoration.title.height * dividerScale) / 2f,
                scaleX = dividerScale,
                scaleY = dividerScale,
            )

            val basmalaLayout = decoration.basmala ?: return@forEach
            // Page 2 has a custom standalone basmala above the opening ayah. Its source
            // typography is larger than the printed ayah, so reduce it to match the mushaf.
            val pageTwoScale = if (asset.page == 2) PAGE_TWO_BASMALA_SCALE else 1f
            val basmalaScaleX = sourceScale * pageTwoScale * if (asset.page == 2) PAGE_TWO_BASMALA_WIDTH_ADJUSTMENT else 1f
            val basmalaScaleY = sourceScale * pageTwoScale * if (asset.page == 2) PAGE_TWO_BASMALA_HEIGHT_ADJUSTMENT else 1f
            val firstAyahTop = if (asset.page == 2) {
                asset.layout.verseRegions
                    .filter { it.verseKey == "${decoration.header.chapterId}:1" }
                    .minOfOrNull { it.rect.y }
            } else {
                null
            }
            val basmalaTop = firstAyahTop?.let { ayahTop ->
                transformedTop + (ayahTop - viewBox.y) * sourceScale -
                    basmalaLayout.height * basmalaScaleY - size.height * 0.012f * zoom
            } ?: (transformedTop + (decoration.basmalaTop - viewBox.y) * sourceScale)
            drawArabicLayout(
                canvas = drawContext.canvas.nativeCanvas,
                layout = basmalaLayout,
                left = pageCenterX - basmalaLayout.width * basmalaScaleX / 2f,
                top = basmalaTop,
                scaleX = basmalaScaleX,
                scaleY = basmalaScaleY,
            )
        }

        asset.layout.verseRegions.forEach { region ->
            val x = transformedLeft + (region.rect.x - viewBox.x) * fitScale * zoom
            val y = transformedTop + (region.rect.y - viewBox.y) * fitScale * zoom
            val width = region.rect.width * fitScale * zoom
            val height = region.rect.height * fitScale * zoom
            if (region.verseKey == selectedVerse) {
                drawRect(
                    color = if (isDarkTheme) Color(0x665A8D90) else Color(0x550E7490),
                    topLeft = Offset(x, y),
                    size = Size(width, height),
                )
            }
            if (region.verseKey in bookmarkedVerseKeys) {
                drawRect(
                    color = if (isDarkTheme) Color(0xFFC6A46B) else Color(0xFFD97706),
                    topLeft = Offset(x, y),
                    size = Size(width, height),
                    style = Stroke(width = 2f),
                )
            }
        }

        // Draw within the visible reader viewport so horizontal edges do not get clipped
        // when the full page image is taller than the available canvas.
        val frameInset = 2f * zoom
        val verticalScrollActive = verticalOverflow > 0f && zoom <= 1.001f
        drawMushafFrame(
            left = frameInset + pan.x,
            top = frameInset + if (verticalScrollActive) 0f else effectivePanY,
            width = ((size.width - 4f) * zoom).coerceAtLeast(0f),
            height = ((size.height - 4f) * zoom).coerceAtLeast(0f),
            darkTheme = isDarkTheme,
        )
    }
}

private fun mushafPageDisplayScale(page: Int): Float =
    if (page == 1 || page == 2) 0.78f else 1f

private const val PAGE_TWO_BASMALA_SCALE = 0.495f
private const val PAGE_TWO_BASMALA_HEIGHT_ADJUSTMENT = 1.05f
private const val PAGE_TWO_BASMALA_WIDTH_ADJUSTMENT = 1.10f

private fun mushafPageTop(canvasHeight: Float, drawnHeight: Float, page: Int): Float =
    if (drawnHeight > canvasHeight) 0f
    else (canvasHeight - drawnHeight) / 2f + mushafPageVerticalOffset(page, canvasHeight)

private fun mushafPageVerticalOffset(page: Int, canvasHeight: Float): Float =
    if (page == 1 || page == 2) -canvasHeight * 0.25f else 0f

private data class SurahDecoration(
    val header: id.kasirq.dailysunnah.quran.data.MushafSectionHeader,
    val title: StaticLayout,
    val basmala: StaticLayout?,
    val basmalaTop: Float,
)

private fun buildSurahDecorations(
    layout: id.kasirq.dailysunnah.quran.data.MushafPageLayout,
    titleTypeface: Typeface,
    basmalaTypeface: Typeface,
    darkTheme: Boolean,
    inkColor: Int,
): List<SurahDecoration> = layout.sectionHeaders.mapNotNull { header ->
    val arabicName = QuranSurahArabicNames.get(header.chapterId) ?: return@mapNotNull null
    val titleText = "سورة $arabicName"
    val plaqueWidth = minOf(layout.renderViewBox.width * 0.44f, header.rect.width * 1.28f)
    val titleLayout = createArabicLayout(
        text = titleText,
        width = plaqueWidth * 0.80f,
        textSize = header.rect.height * 0.54f,
        color = if (darkTheme) inkColor else android.graphics.Color.rgb(29, 77, 68),
        typeface = titleTypeface,
    )

    val openingAyahRegions = layout.verseRegions.filter { it.verseKey == "${header.chapterId}:1" }
    val firstAyahTop = openingAyahRegions.minOfOrNull { it.rect.y }
    val openingLineNumber = openingAyahRegions.minByOrNull { it.rect.y }?.lineNumber
    val openingLineRegions = openingLineNumber?.let { lineNumber ->
        layout.verseRegions.filter { it.lineNumber == lineNumber }
    }.orEmpty()
    val quranLineHeight = openingLineRegions.maxOfOrNull { it.rect.height } ?: 0f
    val quranLineWidth = if (openingLineRegions.isEmpty()) {
        layout.renderViewBox.width * 0.82f
    } else {
        openingLineRegions.maxOf { it.rect.right } - openingLineRegions.minOf { it.rect.x }
    }
    // Keep Bismillah centered, but let its word shape span a little more of the opening Quran line.
    val basmalaTargetWidth = minOf(quranLineWidth * 0.62f, layout.renderViewBox.width * 0.76f)
    val gapTop = header.rect.bottom + 2f
    val gapBottom = (firstAyahTop ?: gapTop) - 2f
    val gapHeight = gapBottom - gapTop
    val basmala = if (!QuranSurahOpeningPolicy.needsStandaloneBasmala(header.chapterId) || gapHeight < 8f) {
        null
    } else {
        val desiredTextSize = minOf(quranLineHeight * 1.52f, gapHeight * 0.96f).coerceAtLeast(1f)
        val candidate = createArabicLayout(
            text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
            width = layout.renderViewBox.width * 0.94f,
            textSize = desiredTextSize,
            color = inkColor,
            typeface = basmalaTypeface,
            strokeWidth = desiredTextSize * 0.008f,
            targetTextWidth = basmalaTargetWidth,
        )
        if (candidate.height <= gapHeight * 0.97f) {
            candidate
        } else {
            val fittedTextSize = desiredTextSize * (gapHeight * 0.96f / candidate.height)
            createArabicLayout(
                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                width = layout.renderViewBox.width * 0.94f,
                textSize = fittedTextSize,
                color = inkColor,
                typeface = basmalaTypeface,
                strokeWidth = fittedTextSize * 0.008f,
                targetTextWidth = basmalaTargetWidth,
            )
        }
    }
    val basmalaTop = if (basmala == null) 0f else (gapTop + gapBottom - basmala.height) / 2f
    SurahDecoration(header, titleLayout, basmala, basmalaTop)
}

private fun createArabicLayout(
    text: String,
    width: Float,
    textSize: Float,
    color: Int,
    typeface: Typeface,
    strokeWidth: Float = 0f,
    targetTextWidth: Float? = null,
): StaticLayout {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.color = color
        this.textSize = textSize
        this.typeface = typeface
        if (strokeWidth > 0f) {
            style = Paint.Style.FILL_AND_STROKE
            this.strokeWidth = strokeWidth
        }
    }
    var measuredWidth = paint.measureText(text)
    if (measuredWidth > width && measuredWidth > 0f) {
        paint.textSize *= width / measuredWidth * 0.96f
        measuredWidth = paint.measureText(text)
    }
    if (targetTextWidth != null && measuredWidth > 0f) {
        paint.textScaleX = (targetTextWidth / measuredWidth).coerceIn(1f, 1.3f)
        val scaledWidth = paint.measureText(text)
        if (scaledWidth > width) paint.textScaleX *= width / scaledWidth * 0.96f
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setTextDirection(TextDirectionHeuristics.RTL)
        .setIncludePad(false)
        .setMaxLines(1)
        .build()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSurahDivider(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    plaqueWidth: Float,
    scale: Float,
    darkTheme: Boolean,
) {
    val green = if (darkTheme) Color(0xFF789C95) else Color(0xFF245F58)
    val greenLight = if (darkTheme) Color(0xFF526F6A) else Color(0xFF6D9388)
    val gold = if (darkTheme) Color(0xFFB59D72) else Color(0xFFB69A5A)
    val parchment = if (darkTheme) Color(0xFF252D2D) else Color(0xFFF5F0E6)
    val line = maxOf(1f, scale * 0.65f)
    val inset = height * 0.075f
    val right = left + width
    val bottom = top + height
    val centerY = top + height / 2f

    // Full-width double frame, with a soft parchment field behind the ornament.
    drawRect(parchment, Offset(left, top), Size(width, height))
    drawRect(green, Offset(left, top), Size(width, height), style = Stroke(line * 1.4f))
    drawRect(
        gold,
        Offset(left + inset, top + inset),
        Size((width - inset * 2f).coerceAtLeast(0f), (height - inset * 2f).coerceAtLeast(0f)),
        style = Stroke(line),
    )

    val plaqueLeft = left + (width - plaqueWidth) / 2f
    val plaqueRight = plaqueLeft + plaqueWidth
    val plaqueTop = top + height * 0.12f
    val plaqueBottom = bottom - height * 0.12f
    val plaqueHeight = plaqueBottom - plaqueTop
    val plaqueShape = Path().apply {
        moveTo(plaqueLeft + plaqueHeight * 0.40f, plaqueTop)
        lineTo(plaqueRight - plaqueHeight * 0.40f, plaqueTop)
        quadraticTo(plaqueRight, plaqueTop, plaqueRight, centerY)
        quadraticTo(plaqueRight, plaqueBottom, plaqueRight - plaqueHeight * 0.40f, plaqueBottom)
        lineTo(plaqueLeft + plaqueHeight * 0.40f, plaqueBottom)
        quadraticTo(plaqueLeft, plaqueBottom, plaqueLeft, centerY)
        quadraticTo(plaqueLeft, plaqueTop, plaqueLeft + plaqueHeight * 0.40f, plaqueTop)
        close()
    }
    val leftPanelStart = left + inset * 1.5f
    val leftPanelEnd = plaqueLeft - height * 0.07f
    val rightPanelStart = plaqueRight + height * 0.07f
    val rightPanelEnd = right - inset * 1.5f
    drawSurahPatternPanel(leftPanelStart, leftPanelEnd, top, height, green, greenLight, gold, parchment)
    drawSurahPatternPanel(rightPanelStart, rightPanelEnd, top, height, green, greenLight, gold, parchment)

    drawPath(plaqueShape, parchment)
    drawPath(plaqueShape, green, style = Stroke(line * 1.35f))
    drawPath(plaqueShape, gold, style = Stroke(line * 0.65f))
    drawSurahRosette(plaqueLeft + plaqueHeight * 0.20f, centerY, height * 0.105f, green, greenLight, gold)
    drawSurahRosette(plaqueRight - plaqueHeight * 0.20f, centerY, height * 0.105f, green, greenLight, gold)
}

/** A restrained ornamental frame that follows the page while it is swiped or zoomed. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMushafFrame(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    darkTheme: Boolean,
) {
    val shortSide = minOf(width, height)
    if (shortSide < 80f) return

    val green = if (darkTheme) Color(0xFF789C95) else Color(0xFF245F58)
    val greenLight = if (darkTheme) Color(0xFF526F6A) else Color(0xFF6D9388)
    val gold = if (darkTheme) Color(0xFFB59D72) else Color(0xFFB69A5A)
    // Keep ornament at the page edge so it does not crowd the printed Arabic text.
    val inset = (shortSide * 0.004f).coerceAtLeast(2f)
    val railWidth = (shortSide * 0.018f).coerceIn(6f, 13f)
    val line = (shortSide * 0.0018f).coerceIn(0.8f, 1.8f)
    val right = left + width
    val bottom = top + height
    val frameLeft = left + inset
    val frameTop = top + inset
    val frameRight = right - inset
    val frameBottom = bottom - inset

    // Double rules make a clear boundary; the small repeated diamonds keep it ornamental.
    drawRect(
        color = green,
        topLeft = Offset(frameLeft, frameTop),
        size = Size(frameRight - frameLeft, frameBottom - frameTop),
        style = Stroke(width = line * 1.5f),
    )
    val innerInset = railWidth * 0.62f
    drawRect(
        color = gold,
        topLeft = Offset(frameLeft + innerInset, frameTop + innerInset),
        size = Size(
            (frameRight - frameLeft - innerInset * 2f).coerceAtLeast(0f),
            (frameBottom - frameTop - innerInset * 2f).coerceAtLeast(0f),
        ),
        style = Stroke(width = line * 0.8f),
    )

    val cornerRadius = railWidth * 0.42f
    val cornerInset = railWidth * 0.55f
    val cornerCenters = listOf(
        Offset(frameLeft + cornerInset, frameTop + cornerInset),
        Offset(frameRight - cornerInset, frameTop + cornerInset),
        Offset(frameLeft + cornerInset, frameBottom - cornerInset),
        Offset(frameRight - cornerInset, frameBottom - cornerInset),
    )
    cornerCenters.forEach { center ->
        drawSurahRosette(center.x, center.y, cornerRadius, green, greenLight, gold)
    }

    val step = (shortSide * 0.105f).coerceAtLeast(28f)
    val diamondRadius = railWidth * 0.20f
    val horizontalStart = frameLeft + railWidth * 1.7f
    val horizontalEnd = frameRight - railWidth * 1.7f
    var x = horizontalStart + step / 2f
    while (x < horizontalEnd) {
        drawSurahDiamond(x, frameTop + railWidth * 0.53f, diamondRadius, greenLight, gold, line)
        drawSurahDiamond(x, frameBottom - railWidth * 0.53f, diamondRadius, greenLight, gold, line)
        x += step
    }

    val verticalStart = frameTop + railWidth * 1.7f
    val verticalEnd = frameBottom - railWidth * 1.7f
    var y = verticalStart + step / 2f
    while (y < verticalEnd) {
        drawSurahDiamond(frameLeft + railWidth * 0.53f, y, diamondRadius, greenLight, gold, line)
        drawSurahDiamond(frameRight - railWidth * 0.53f, y, diamondRadius, greenLight, gold, line)
        y += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSurahPatternPanel(
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    green: Color,
    greenLight: Color,
    gold: Color,
    background: Color,
) {
    if (right <= left) return
    val centerY = top + height / 2f
    val bottom = top + height
    val inset = height * 0.12f
    val line = maxOf(1f, height * 0.018f)
    drawRect(background, Offset(left, top + inset), Size(right - left, height - inset * 2f))
    drawLine(green, Offset(left, top + inset), Offset(right, top + inset), line)
    drawLine(green, Offset(left, bottom - inset), Offset(right, bottom - inset), line)
    drawLine(gold, Offset(left, top + inset + line * 2f), Offset(right, top + inset + line * 2f), line * 0.55f)
    drawLine(gold, Offset(left, bottom - inset - line * 2f), Offset(right, bottom - inset - line * 2f), line * 0.55f)

    // Linked diamonds form an interlaced border behind the flower clusters.
    val chainStep = height * 0.34f
    var chainX = left + height * 0.15f
    while (chainX < right - height * 0.12f) {
        drawSurahDiamond(chainX, centerY, height * 0.13f, greenLight, gold, line)
        chainX += chainStep
    }

    // Dense rosettes and paired leaves fill the band like the printed Madinah cartouche.
    val flowerStep = height * 0.70f
    var flowerX = left + height * 0.34f
    while (flowerX < right - height * 0.20f) {
        drawSurahRosette(flowerX, centerY, height * 0.245f, green, greenLight, gold)
        val between = flowerX + flowerStep / 2f
        if (between < right - height * 0.10f) {
            drawSurahLeaf(between, centerY - height * 0.20f, height * 0.11f, -90f, greenLight, green, line)
            drawSurahLeaf(between, centerY + height * 0.20f, height * 0.11f, 90f, greenLight, green, line)
        }
        flowerX += flowerStep
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSurahRosette(
    centerX: Float,
    centerY: Float,
    radius: Float,
    green: Color,
    greenLight: Color,
    gold: Color,
) {
    if (radius < 1f) return
    for (petal in 0 until 8) {
        rotate(petal * 45f, Offset(centerX, centerY)) {
            val leaf = Path().apply {
                moveTo(centerX, centerY - radius * 0.18f)
                cubicTo(
                    centerX - radius * 0.48f, centerY - radius * 0.48f,
                    centerX - radius * 0.34f, centerY - radius * 0.91f,
                    centerX, centerY - radius,
                )
                cubicTo(
                    centerX + radius * 0.34f, centerY - radius * 0.91f,
                    centerX + radius * 0.48f, centerY - radius * 0.48f,
                    centerX, centerY - radius * 0.18f,
                )
                close()
            }
            drawPath(leaf, if (petal % 2 == 0) green else greenLight)
            drawPath(leaf, gold.copy(alpha = 0.78f), style = Stroke(maxOf(0.7f, radius * 0.055f)))
        }
    }
    val diamond = Path().apply {
        moveTo(centerX, centerY - radius * 0.38f)
        lineTo(centerX + radius * 0.38f, centerY)
        lineTo(centerX, centerY + radius * 0.38f)
        lineTo(centerX - radius * 0.38f, centerY)
        close()
    }
    drawPath(diamond, gold)
    drawPath(diamond, green, style = Stroke(maxOf(0.7f, radius * 0.045f)))
    drawCircle(Color(0xFFE8EEE7), radius * 0.13f, Offset(centerX, centerY))
    drawCircle(gold, radius * 0.065f, Offset(centerX, centerY))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSurahDiamond(
    centerX: Float,
    centerY: Float,
    radius: Float,
    fill: Color,
    outline: Color,
    line: Float,
) {
    if (radius < 1f) return
    val shape = Path().apply {
        moveTo(centerX, centerY - radius)
        lineTo(centerX + radius * 0.78f, centerY)
        lineTo(centerX, centerY + radius)
        lineTo(centerX - radius * 0.78f, centerY)
        close()
    }
    drawPath(shape, fill)
    drawPath(shape, outline, style = Stroke(maxOf(0.7f, line * 0.75f)))
    drawCircle(Color(0xFFF5F0E6), radius * 0.16f, Offset(centerX, centerY))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSurahLeaf(
    centerX: Float,
    centerY: Float,
    radius: Float,
    angle: Float,
    fill: Color,
    outline: Color,
    line: Float,
) {
    if (radius < 1f) return
    rotate(angle, Offset(centerX, centerY)) {
        val leaf = Path().apply {
            moveTo(centerX, centerY - radius)
            cubicTo(centerX + radius * 0.9f, centerY - radius * 0.42f, centerX + radius * 0.48f, centerY + radius * 0.56f, centerX, centerY + radius)
            cubicTo(centerX - radius * 0.48f, centerY + radius * 0.56f, centerX - radius * 0.9f, centerY - radius * 0.42f, centerX, centerY - radius)
            close()
        }
        drawPath(leaf, fill)
        drawPath(leaf, outline, style = Stroke(maxOf(0.7f, line * 0.7f)))
        drawLine(outline, Offset(centerX, centerY - radius * 0.55f), Offset(centerX, centerY + radius * 0.65f), maxOf(0.6f, line * 0.55f))
    }
}

private fun drawArabicLayout(
    canvas: android.graphics.Canvas,
    layout: StaticLayout,
    left: Float,
    top: Float,
    scaleX: Float,
    scaleY: Float,
) {
    val save = canvas.save()
    canvas.translate(left, top)
    canvas.scale(scaleX, scaleY)
    layout.draw(canvas)
    canvas.restoreToCount(save)
}

@Composable
private fun BookmarkGlyph(filled: Boolean, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val glyph = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.10f)
            lineTo(size.width * 0.78f, size.height * 0.10f)
            lineTo(size.width * 0.78f, size.height * 0.90f)
            lineTo(size.width * 0.50f, size.height * 0.70f)
            lineTo(size.width * 0.22f, size.height * 0.90f)
            close()
        }
        drawPath(
            path = glyph,
            color = color,
            style = if (filled) Fill else Stroke(width = 1.8.dp.toPx()),
        )
    }
}

@Composable
private fun BookmarkListGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val dotRadius = 1.5.dp.toPx()
        val lineStart = size.width * 0.38f
        val lineEnd = size.width * 0.88f
        listOf(0.24f, 0.50f, 0.76f).forEach { fraction ->
            val y = size.height * fraction
            drawCircle(color, dotRadius, Offset(size.width * 0.16f, y))
            drawLine(color, Offset(lineStart, y), Offset(lineEnd, y), strokeWidth = 1.8.dp.toPx())
        }
    }
}

private data class VerseReference(val surah: Int, val ayah: Int) : Comparable<VerseReference> {
    override fun compareTo(other: VerseReference): Int =
        compareValuesBy(this, other, VerseReference::surah, VerseReference::ayah)
}

private fun parseVerseReference(verseKey: String): VerseReference? {
    val parts = verseKey.split(':')
    val surah = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val ayah = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return VerseReference(surah, ayah)
}

