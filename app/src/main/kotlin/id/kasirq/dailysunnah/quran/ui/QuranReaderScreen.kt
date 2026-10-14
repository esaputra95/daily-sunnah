package id.kasirq.dailysunnah.quran.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.kasirq.dailysunnah.quran.data.MushafAssetRepository
import id.kasirq.dailysunnah.quran.data.MushafPageAsset
import id.kasirq.dailysunnah.quran.data.MushafPrefetchScheduler
import id.kasirq.dailysunnah.quran.data.QuranBookmark
import id.kasirq.dailysunnah.quran.data.QuranBookmarkStore
import id.kasirq.dailysunnah.quran.data.QuranJuzIndex
import id.kasirq.dailysunnah.quran.data.ReadingGoalReminderScheduler
import id.kasirq.dailysunnah.quran.data.ReadingGoalSnapshot
import id.kasirq.dailysunnah.quran.data.ReadingGoalStore
import id.kasirq.dailysunnah.quran.data.QuranReaderPreferences
import id.kasirq.dailysunnah.quran.data.SurahStart
import id.kasirq.dailysunnah.quran.data.SurahPageIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranReaderScreen(
    startPage: Int? = null,
    startVerseKey: String? = null,
    onOpenLibrary: () -> Unit = {},
) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val repository = remember { MushafAssetRepository(context) }
    val prefetch = remember { MushafPrefetchScheduler(context) }
    val goalStore = remember { ReadingGoalStore(context) }
    val readerPreferences = remember { QuranReaderPreferences(context) }
    val bookmarkStore = remember { QuranBookmarkStore(context) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var retryKey by rememberSaveable { mutableIntStateOf(0) }
    var pageInput by rememberSaveable { mutableStateOf("1") }
    var asset by remember { mutableStateOf<MushafPageAsset?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedVerse by remember { mutableStateOf<String?>(null) }
    var showVerseActions by rememberSaveable { mutableStateOf(false) }
    var showTranslationInfo by rememberSaveable { mutableStateOf(false) }
    var showAudioInfo by rememberSaveable { mutableStateOf(false) }
    var startupPagesScheduled by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
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

    LaunchedEffect(startPage, startVerseKey) {
        val (lastPage, goal, savedBookmarks) = withContext(Dispatchers.IO) {
            Triple(readerPreferences.lastReadPage(), goalStore.load(), bookmarkStore.all())
        }
        val openingPage = startPage?.takeIf { it in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE } ?: lastPage
        page = openingPage
        pageInput = openingPage.toString()
        pendingBookmarkVerse = startVerseKey
        if (startVerseKey != null && asset?.page == openingPage) {
            selectedVerse = startVerseKey
            pendingBookmarkVerse = null
        }
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

    LaunchedEffect(page, pageRestored) {
        if (pageRestored) withContext(Dispatchers.IO) { readerPreferences.saveLastReadPage(page) }
    }

    LaunchedEffect(page, retryKey, pageRestored) {
        if (!pageRestored) return@LaunchedEffect
        selectedVerse = null
        showVerseActions = false
        zoom = 1f
        pan = Offset.Zero
        isLoading = true
        errorMessage = null
        try {
            val loadedAsset = repository.loadPage(page)
            asset = loadedAsset
            selectedVerse = pendingBookmarkVerse?.takeIf { verse ->
                loadedAsset.layout.verseRegions.any { it.verseKey == verse }
            }
            pendingBookmarkVerse = null
        } catch (error: Exception) {
            errorMessage = error.message ?: "Halaman mushaf belum dapat dimuat."
        } finally {
            isLoading = false
            prefetch.prepareNeighborhood(page)
            if (!startupPagesScheduled) {
                startupPagesScheduled = true
                surahStarts = runCatching {
                    withContext(Dispatchers.IO) { SurahPageIndex.load(context) }
                }.getOrDefault(emptyList())
                prefetch.prepareSurahStarts(surahStarts.map { it.page })
            }
        }
    }

    fun openPage(targetPage: Int) {
        if (targetPage in MushafAssetRepository.MIN_PAGE..MushafAssetRepository.MAX_PAGE && targetPage != page) {
            pageInput = targetPage.toString()
            page = targetPage
        }
    }

    val pageReference = remember(asset?.page) {
        asset?.layout?.verseRegions
            ?.mapNotNull { parseVerseReference(it.verseKey) }
            ?.minOrNull()
    }
    val currentSurahNumber = pageReference?.surah
    val currentSurah = currentSurahNumber?.let { number -> surahStarts.firstOrNull { it.number == number } }
        ?: surahStarts.lastOrNull { it.page <= page }
    val currentJuz = QuranJuzIndex.forVerse(pageReference?.surah, pageReference?.ayah)
    Scaffold(
        containerColor = Color(0xFFF7F3EA),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onOpenLibrary) { Text("‹") } },
                title = {
                    Column {
                        Text("${currentSurah?.number ?: currentSurahNumber ?: 1}. ${currentSurah?.name ?: "Qur’an"}", style = MaterialTheme.typography.titleMedium)
                        Text("Halaman $page", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    Text("Juz $currentJuz", style = MaterialTheme.typography.labelLarge)
                    IconButton(
                        onClick = { showGoalSettings = true },
                        modifier = Modifier.semantics { contentDescription = "Pengaturan target bacaan" },
                    ) { Text("⚙", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState = asset,
                    transitionSpec = {
                        val movingForward = (targetState?.page ?: 0) > (initialState?.page ?: 0)
                        (
                            slideInHorizontally(
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> if (movingForward) -fullWidth else fullWidth },
                            ) + fadeIn(tween(100))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> if (movingForward) fullWidth else -fullWidth },
                            ) + fadeOut(tween(100))
                        )
                    },
                    contentKey = { it?.page },
                    label = "mushaf-page-transition",
                ) { pageAsset ->
                    when {
                        pageAsset != null -> MushafPageCanvas(
                            asset = pageAsset,
                            zoom = zoom,
                            pan = pan,
                            selectedVerse = selectedVerse,
                            bookmarkedVerseKeys = bookmarks.mapTo(mutableSetOf()) { it.verseKey },
                            gesturesEnabled = !isLoading && pageAsset.page == page,
                            onSelectVerse = {
                                selectedVerse = it
                                showVerseActions = true
                            },
                            onSwipePage = { direction -> if (!isLoading) openPage(page + direction) },
                            onTransform = { newZoom, newPan ->
                                zoom = newZoom
                                pan = newPan
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        isLoading -> Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Menyiapkan Mushaf Madinah…")
                        }
                        else -> Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(errorMessage ?: "Halaman belum tersedia.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { retryKey += 1 }) { Text("Coba lagi") }
                        }
                    }
                }
                if (isLoading && asset?.page != page && asset != null) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
                if (errorMessage != null && asset?.page != page && !isLoading) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(errorMessage ?: "Halaman belum tersedia.")
                            Button(onClick = { retryKey += 1 }) { Text("Coba lagi") }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { openPage(page - 1) }, enabled = page > 1) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                TextButton(onClick = { showPagePicker = true }) { Text("Hal. $page / 604") }
                IconButton(onClick = { openPage(page + 1) }, enabled = page < 604) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                val pageRead = page in readingGoal.completedPages
                IconButton(
                    enabled = asset?.page == page,
                    onClick = {
                        readingGoal = goalStore.togglePage(page)
                        if (readingGoal.isComplete) ReadingGoalReminderScheduler.cancel(context)
                        else ReadingGoalReminderScheduler.scheduleNext(context)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (pageRead) "Hapus tanda halaman selesai dibaca" else "Tandai halaman selesai dibaca"
                    },
                ) {
                    BookmarkGlyph(
                        filled = pageRead,
                        color = if (pageRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { showBookmarks = true },
                    modifier = Modifier.semantics { contentDescription = "Lihat daftar bookmark ayat" },
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
            containerColor = Color(0xFFFFFEFA),
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
                Text("Mushaf Madinah · Halaman $page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            bookmarks = bookmarkStore.toggle(selectedVerse!!, page)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Bookmark ayat diperbarui.") }
                        },
                    ) {
                        Text(if (bookmarks.any { it.verseKey == selectedVerse }) "▣ Tersimpan" else "☆ Bookmark")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAudioInfo = true
                        },
                    ) { Text("▶ Putar audio") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { showTranslationInfo = true }) { Text("文 Terjemah") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val reference = "QS. ${verseSurahName ?: "Surah $verseSurahNumber"} : Ayat ${verseNumber ?: ""} · Mushaf Madinah, halaman $page"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reference)
                            }
                            activityContext.startActivity(Intent.createChooser(shareIntent, "Bagikan referensi ayat"))
                        },
                    ) { Text("↗ Bagikan") }
                }
            }
        }
    }
    if (showTranslationInfo) {
        AlertDialog(
            onDismissRequest = { showTranslationInfo = false },
            title = { Text("Terjemah ayat") },
            text = { Text("Referensi ayat ${selectedVerse ?: ""} sudah dipilih. Paket terjemahan belum disertakan pada build ini.") },
            confirmButton = { TextButton(onClick = { showTranslationInfo = false }) { Text("Tutup") } },
        )
    }
    if (showAudioInfo) {
        AlertDialog(
            onDismissRequest = { showAudioInfo = false },
            title = { Text("Audio murottal") },
            text = { Text("Paket audio belum disertakan pada build ini. Pilihan ayat dan bookmark tetap tersimpan di perangkat.") },
            confirmButton = { TextButton(onClick = { showAudioInfo = false }) { Text("Tutup") } },
        )
    }

    if (showGoalSettings) {
        ReadingGoalSettingsDialog(
            snapshot = readingGoal,
            onDismiss = { showGoalSettings = false },
            onSave = { target, reminders ->
                readingGoal = goalStore.updateSettings(target, reminders)
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
            title = { Text("Lompat ke halaman") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { input -> pageInput = input.filter(Char::isDigit).take(3) },
                    label = { Text("Halaman 1–604") },
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
                ) { Text("Buka") }
            },
            dismissButton = { TextButton(onClick = { showPagePicker = false }) { Text("Batal") } },
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

@Composable
private fun QuranBookmarksDialog(
    bookmarks: List<QuranBookmark>,
    onDismiss: () -> Unit,
    onSelect: (QuranBookmark) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmark ayat") },
        text = {
            if (bookmarks.isEmpty()) {
                Text("Belum ada ayat yang disimpan.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(bookmarks, key = { it.verseKey }) { bookmark ->
                        TextButton(onClick = { onSelect(bookmark) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ayat ${bookmark.verseKey} · Halaman ${bookmark.page}", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun SurahPickerDialog(
    surahs: List<SurahStart>,
    onDismiss: () -> Unit,
    onSelect: (SurahStart) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih surah") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(surahs, key = { it.number }) { surah ->
                    TextButton(onClick = { onSelect(surah) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("${surah.number}. ${surah.name}")
                            Text("Halaman ${surah.page}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun ReadingGoalSettingsDialog(
    snapshot: ReadingGoalSnapshot,
    onDismiss: () -> Unit,
    onSave: (targetPages: Int, reminderMinutes: List<Int>) -> Unit,
) {
    val context = LocalContext.current
    var targetText by remember { mutableStateOf(snapshot.targetPages.toString()) }
    val reminders = remember { mutableStateListOf<Int>().apply { addAll(snapshot.reminderMinutes) } }
    val targetPages = targetText.toIntOrNull()?.takeIf { it in 1..MushafAssetRepository.MAX_PAGE }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Target bacaan harian") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pilih jumlah halaman yang ingin kamu selesaikan setiap hari.")
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Halaman per hari") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("Pengingat jika target belum tercapai", style = MaterialTheme.typography.titleSmall)
                reminders.forEachIndexed { index, minute ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalTime.of(minute / 60, minute % 60).format(timeFormatter), modifier = Modifier.weight(1f))
                        TextButton(onClick = { reminders.removeAt(index) }) { Text("Hapus") }
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
                ) { Text("Tambah waktu") }
                Text(
                    "Android dapat menunda pengingat saat mode hemat daya aktif.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = targetPages != null,
                onClick = { targetPages?.let { onSave(it, reminders.toList()) } },
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
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
    onSelectVerse: (String) -> Unit,
    onSwipePage: (Int) -> Unit,
    onTransform: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(asset.bitmap) { asset.bitmap.asImageBitmap() }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnSwipePage by rememberUpdatedState(onSwipePage)
    DisposableEffect(asset.bitmap) {
        onDispose {
            if (!asset.bitmap.isRecycled) asset.bitmap.recycle()
        }
    }
    Canvas(
        modifier = modifier
            .background(Color(0xFFF8F6EF))
            .pointerInput(asset.page, gesturesEnabled) {
                if (gesturesEnabled) {
                    detectTapGestures { touch ->
                        val viewBox = asset.layout.renderViewBox
                        val fitScale = size.width / viewBox.width
                        if (fitScale <= 0f) return@detectTapGestures
                        val drawnWidth = viewBox.width * fitScale
                        val drawnHeight = viewBox.height * fitScale
                        val left = (size.width - drawnWidth) / 2f
                        val top = (size.height - drawnHeight) / 2f
                        val baseX = (touch.x - currentPan.x) / currentZoom
                        val baseY = (touch.y - currentPan.y) / currentZoom
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
            .pointerInput(asset.page, gesturesEnabled) {
                if (gesturesEnabled) {
                    var horizontalDrag = 0f
                    var pageChangeStarted = false
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val activeZoom = currentZoom
                        val activePan = currentPan
                        val isPinch = abs(zoomChange - 1f) > 0.002f
                        if (activeZoom > 1.001f || isPinch) {
                            val nextZoom = (activeZoom * zoomChange).coerceIn(1f, 4f)
                            if (nextZoom == 1f) {
                                currentOnTransform(1f, Offset.Zero)
                            } else {
                                val pointUnderFinger = (centroid - activePan) / activeZoom
                                val nextPan = centroid - pointUnderFinger * nextZoom + panChange
                                currentOnTransform(nextZoom, nextPan)
                            }
                        } else {
                            horizontalDrag += panChange.x
                            if (!pageChangeStarted && abs(horizontalDrag) >= 72f) {
                                pageChangeStarted = true
                                currentOnSwipePage(if (horizontalDrag > 0f) 1 else -1)
                            }
                        }
                    }
                }
            },
    ) {
        val viewBox = asset.layout.renderViewBox
        val fitScale = size.width / viewBox.width
        if (fitScale <= 0f) return@Canvas

        val drawnWidth = viewBox.width * fitScale
        val drawnHeight = viewBox.height * fitScale
        val left = (size.width - drawnWidth) / 2f
        val top = (size.height - drawnHeight) / 2f
        val transformedLeft = left * zoom + pan.x
        val transformedTop = top * zoom + pan.y
        val transformedWidth = drawnWidth * zoom
        val transformedHeight = drawnHeight * zoom

        drawImage(
            image = imageBitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset(transformedLeft.toInt(), transformedTop.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(transformedWidth.toInt(), transformedHeight.toInt()),
        )

        asset.layout.verseRegions.forEach { region ->
            val x = transformedLeft + (region.rect.x - viewBox.x) * fitScale * zoom
            val y = transformedTop + (region.rect.y - viewBox.y) * fitScale * zoom
            val width = region.rect.width * fitScale * zoom
            val height = region.rect.height * fitScale * zoom
            if (region.verseKey == selectedVerse) {
                drawRect(
                    color = Color(0x550E7490),
                    topLeft = Offset(x, y),
                    size = Size(width, height),
                )
            }
            if (region.verseKey in bookmarkedVerseKeys) {
                drawRect(
                    color = Color(0xFFD97706),
                    topLeft = Offset(x, y),
                    size = Size(width, height),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
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

