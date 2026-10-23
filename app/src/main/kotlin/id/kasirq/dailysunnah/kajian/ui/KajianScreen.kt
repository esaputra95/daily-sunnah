package id.kasirq.dailysunnah.kajian.ui

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.kasirq.dailysunnah.kajian.data.KajianCatalogRepository
import id.kasirq.dailysunnah.kajian.data.KajianCatalogSnapshot
import id.kasirq.dailysunnah.kajian.data.KajianChannel
import id.kasirq.dailysunnah.kajian.data.KajianFeedSettings
import id.kasirq.dailysunnah.kajian.data.KajianEndBehavior
import id.kasirq.dailysunnah.kajian.data.KajianPlaybackPreferences
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import id.kasirq.dailysunnah.ui.ModernTextFieldShape
import id.kasirq.dailysunnah.ui.modernTextFieldColors
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun KajianScreen(initialContentType: KajianVideo.ContentType = KajianVideo.ContentType.KAJIAN) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { KajianCatalogRepository(context.applicationContext) }
    val playbackPreferences = remember { KajianPlaybackPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<KajianCatalogSnapshot?>(null) }
    var cachedChannels by remember { mutableStateOf(emptyList<KajianChannel>()) }
    var savedIds by remember { mutableStateOf(emptySet<String>()) }
    var savedVideos by remember { mutableStateOf(emptyList<KajianVideo>()) }
    var historyVideos by remember { mutableStateOf(emptyList<KajianVideo>()) }
    var queueVideos by remember { mutableStateOf(emptyList<KajianVideo>()) }
    var personalList by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable(initialContentType) {
        mutableIntStateOf(KajianVideo.ContentType.entries.indexOf(initialContentType).coerceAtLeast(0))
    }
    var query by rememberSaveable { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedChannelName by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var showPlaybackSettings by rememberSaveable { mutableStateOf(false) }
    var endBehavior by rememberSaveable { mutableStateOf(playbackPreferences.endBehavior()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var retryLoadMoreKey by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                savedIds = repository.savedVideoIds()
                savedVideos = repository.savedVideos()
                historyVideos = repository.playbackHistory()
                queueVideos = repository.queuedVideos()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(query) {
        delay(350)
        appliedQuery = query.trim()
    }

    LaunchedEffect(reloadKey, selectedChannelName, appliedQuery) {
        savedIds = repository.savedVideoIds()
        savedVideos = repository.savedVideos()
        historyVideos = repository.playbackHistory()
        queueVideos = repository.queuedVideos()
        snapshot = null
        loadMoreError = null
        isLoadingMore = false
        snapshot = withContext(Dispatchers.IO) { repository.load(selectedChannelName, appliedQuery) }.also { loaded ->
            if (loaded.channels.isNotEmpty()) cachedChannels = loaded.channels
        }
        savedIds = repository.savedVideoIds()
        savedVideos = repository.savedVideos()
        historyVideos = repository.playbackHistory()
        queueVideos = repository.queuedVideos()
    }

    val configuredContentTypes = snapshot?.feedSettings?.contentTypes ?: KajianFeedSettings.DEFAULT.contentTypes
    val allowedContentTypes = KajianVideo.ContentType.entries.filter { type ->
        type in configuredContentTypes || (savedOnly && type == KajianVideo.ContentType.SHORT)
    }.ifEmpty { KajianVideo.ContentType.entries }
    val pagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, allowedContentTypes.lastIndex),
        pageCount = { allowedContentTypes.size },
    )
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val effectiveTab = pagerState.currentPage.coerceIn(0, allowedContentTypes.lastIndex)
    val kajianListState = rememberLazyListState()
    val shortsListState = rememberLazyListState()
    val activeListState = if (allowedContentTypes.getOrNull(effectiveTab) == KajianVideo.ContentType.KAJIAN) kajianListState else shortsListState
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { selectedTab = it }
    }
    LaunchedEffect(selectedTab, allowedContentTypes.size) {
        val targetPage = selectedTab.coerceIn(0, allowedContentTypes.lastIndex)
        if (targetPage != selectedTab) selectedTab = targetPage
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }
    LaunchedEffect(
        activeListState,
        snapshot?.nextOffset,
        snapshot?.hasMore,
        effectiveTab,
        savedOnly,
        query,
        appliedQuery,
        selectedChannelName,
        retryLoadMoreKey,
    ) {
        val current = snapshot ?: return@LaunchedEffect
        if (!current.hasMore || savedOnly || query.trim() != appliedQuery) return@LaunchedEffect
        snapshotFlow {
            val layout = activeListState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 4
        }.distinctUntilChanged().collect { nearBottom ->
            if (!nearBottom || isLoadingMore) return@collect
            val currentSnapshot = snapshot ?: return@collect
            if (!currentSnapshot.hasMore) return@collect
            isLoadingMore = true
            loadMoreError = null
            val page = withContext(Dispatchers.IO) {
                repository.loadNextPage(
                    channelName = selectedChannelName,
                    searchQuery = appliedQuery,
                    offset = currentSnapshot.nextOffset,
                    feedSettings = currentSnapshot.feedSettings,
                )
            }
            isLoadingMore = false
            if (page.message != null) {
                loadMoreError = page.message
            } else {
                snapshot = currentSnapshot.copy(
                    videos = (currentSnapshot.videos + page.videos).distinctBy { it.youtubeId },
                    hasMore = page.hasMore,
                    nextOffset = page.nextOffset,
                )
            }
        }
    }
    val availableVideos = if (savedOnly) (snapshot?.videos.orEmpty() + savedVideos).distinctBy { it.youtubeId } else snapshot?.videos.orEmpty()
    val activeFilterCount = listOfNotNull(
        selectedChannelName,
        "saved".takeIf { savedOnly },
    ).size

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (english) "Talks" else "Kajian", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (english) "Reviewed video selections" else "Video pilihan yang telah ditinjau", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { personalList = "history" }) {
                        Text("◷", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { personalList = "queue" }) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("☷", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            if (queueVideos.isNotEmpty()) {
                                Surface(modifier = Modifier.align(Alignment.TopEnd), shape = CircleShape, color = MaterialTheme.colorScheme.tertiary) {
                                    Text(queueVideos.size.coerceAtMost(99).toString(), modifier = Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    IconButton(onClick = { reloadKey += 1 }) { Text("↻", style = MaterialTheme.typography.titleLarge) }
                    IconButton(onClick = { showPlaybackSettings = true }) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                scrollBehavior = topBarScrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFilterSheet = true },
                modifier = Modifier.offset(y = (-84).dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FilterGlyph(Modifier.padding(14.dp))
                    if (activeFilterCount > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                        ) {
                            Text(
                                activeFilterCount.toString(),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.onTertiary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(selectedTabIndex = effectiveTab) {
                allowedContentTypes.forEachIndexed { index, type ->
                    val title = if (type == KajianVideo.ContentType.KAJIAN) { if (english) "Talks" else "Kajian" } else "Shorts"
                    Tab(
                        selected = effectiveTab == index,
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
                val contentType = allowedContentTypes[page]
                val videos = remember(availableVideos, contentType, selectedChannelName, savedOnly, query, savedIds) {
                    availableVideos.filter { video ->
                        val matchesChannel = selectedChannelName == null || video.channelName.equals(selectedChannelName, ignoreCase = true)
                        val matchesQuery = savedOnly || query.isBlank() || listOf(video.title, video.channelName, video.category, video.description)
                            .any { it.contains(query.trim(), ignoreCase = true) }
                        video.contentType == contentType && matchesChannel && matchesQuery &&
                            (!savedOnly || video.youtubeId in savedIds)
                    }
                }
                val listState = if (contentType == KajianVideo.ContentType.KAJIAN) kajianListState else shortsListState
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "kajian-search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            shape = ModernTextFieldShape,
                            colors = modernTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            singleLine = true,
                            placeholder = { Text(if (english) "Search title, channel, or topic" else "Cari judul, channel, atau tema") },
                            leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleMedium) },
                        )
                    }
                    snapshot?.message?.let { message ->
                        item(key = "kajian-message") {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(if (english) "Kajian catalog could not be loaded. Check your connection and app configuration." else message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    when {
                        snapshot == null -> item(key = "kajian-loading") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        videos.isEmpty() -> item(key = "kajian-empty") {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                EmptyKajianState(
                                    isConfigured = snapshot?.message?.contains("belum dikonfigurasi", ignoreCase = true) != true,
                                    isSavedOnly = savedOnly,
                                )
                            }
                        }
                        else -> items(videos, key = { it.youtubeId }) { video ->
                            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                if (contentType == KajianVideo.ContentType.KAJIAN) {
                                    KajianVideoCard(
                                        video = video,
                                        isSaved = video.youtubeId in savedIds,
                                        onOpen = {
                                            val kajianVideos = videos.filter { it.contentType == KajianVideo.ContentType.KAJIAN }
                                            val kajianIndex = kajianVideos.indexOfFirst { it.youtubeId == video.youtubeId }.coerceAtLeast(0)
                                            openYouTubeVideo(context, kajianVideos, kajianIndex)
                                        },
                                        onToggleSaved = { savedIds = repository.toggleSaved(video); savedVideos = repository.savedVideos() },
                                        onShare = { shareVideo(context, video) },
                                        onAddQueue = { queueVideos = repository.addToQueue(video) },
                                    )
                                } else {
                                    KajianShortCard(
                                        video = video,
                                        isSaved = video.youtubeId in savedIds,
                                        onOpen = {
                                            val shortIndex = videos.indexOfFirst { it.youtubeId == video.youtubeId }.coerceAtLeast(0)
                                            context.startActivity(
                                                ShortsFeedActivity.newIntent(
                                                    context = context,
                                                    videos = videos,
                                                    initialIndex = shortIndex,
                                                    catalogSnapshot = snapshot?.takeUnless { savedOnly },
                                                    channelName = selectedChannelName,
                                                    searchQuery = appliedQuery.takeIf { it.isNotBlank() },
                                                ),
                                            )
                                        },
                                        onAddQueue = { queueVideos = repository.addToQueue(video) },
                                    )
                                }
                            }
                        }
                    }
                    if (isLoadingMore) {
                        item(key = "kajian-loading-more") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else if (loadMoreError != null) {
                        item(key = "kajian-load-more-error") {
                            TextButton(
                                onClick = { retryLoadMoreKey++ },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                Text(if (english) "Could not load more. Tap to retry." else "Video berikutnya gagal dimuat. Ketuk untuk mencoba lagi.")
                            }
                        }
                    }
                }
            }
        }
    }
    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            KajianFilterSheet(
                channels = snapshot?.channels?.takeIf { it.isNotEmpty() } ?: cachedChannels,
                selectedChannelName = selectedChannelName,
                savedOnly = savedOnly,
                onSelectChannel = { channelName ->
                    if (selectedChannelName != channelName) {
                        selectedChannelName = channelName
                    }
                },
                onToggleSaved = {
                    savedOnly = it
                    if (it) {
                        query = ""
                        val shortsIndex = KajianVideo.ContentType.entries
                            .filter { type -> type in configuredContentTypes || type == KajianVideo.ContentType.SHORT }
                            .indexOf(KajianVideo.ContentType.SHORT)
                        if (shortsIndex >= 0) selectedTab = shortsIndex
                    }
                },
                onReset = {
                    selectedChannelName = null
                    savedOnly = false
                },
                onDone = { showFilterSheet = false },
            )
        }
    }
    personalList?.let { listType ->
        val listVideos = if (listType == "history") historyVideos else queueVideos
        ModalBottomSheet(onDismissRequest = { personalList = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (listType == "history") { if (english) "Recently watched" else "Riwayat tontonan" }
                        else { if (english) "Watch queue" else "Antrean kajian" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (listType == "history" && listVideos.isNotEmpty()) {
                        TextButton(onClick = { repository.clearPlaybackHistory(); historyVideos = emptyList() }) {
                            Text(if (english) "Clear" else "Hapus")
                        }
                    }
                }
                if (listVideos.isEmpty()) {
                    Text(
                        if (listType == "history") { if (english) "Videos you play will appear here." else "Video yang kamu putar akan muncul di sini." }
                        else { if (english) "Add videos to your queue with the + button." else "Tambahkan video ke antrean lewat tombol +." },
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(listVideos, key = { it.youtubeId }) { video ->
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    KajianThumbnail(video, Modifier.size(width = 84.dp, height = 56.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${video.channelName} · ${video.category}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = {
                                        val matching = listVideos.filter { it.contentType == video.contentType }
                                        val index = matching.indexOfFirst { it.youtubeId == video.youtubeId }.coerceAtLeast(0)
                                        if (video.contentType == KajianVideo.ContentType.SHORT) {
                                            context.startActivity(ShortsFeedActivity.newIntent(context, matching, index))
                                        } else {
                                            openYouTubeVideo(context, matching, index)
                                        }
                                        personalList = null
                                    }) { Text(if (english) "Play" else "Putar") }
                                    if (listType == "queue") {
                                        TextButton(onClick = { queueVideos = repository.removeFromQueue(video.youtubeId) }) { Text("×") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showPlaybackSettings) {
        AlertDialog(
            onDismissRequest = { showPlaybackSettings = false },
            title = { Text(if (english) "After playback ends" else "Setelah video selesai") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KajianEndBehavior.entries.forEach { option ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                endBehavior = option
                                playbackPreferences.setEndBehavior(option)
                            },
                            color = if (endBehavior == option) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(if (english) option.englishLabel else option.label, fontWeight = FontWeight.SemiBold)
                                Text(if (english) option.englishDescription else option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(if (english) "Applies to Talks and Shorts players. Default: play the next video." else "Berlaku untuk pemutar Kajian dan Shorts. Default: lanjut ke video berikutnya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showPlaybackSettings = false }) { Text(if (english) "Done" else "Selesai") } },
        )
    }
}

@Composable
private fun KajianVideoCard(
    video: KajianVideo,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onToggleSaved: () -> Unit,
    onShare: () -> Unit,
    onAddQueue: () -> Unit,
) {
    val english = LocalAppEnglish.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            KajianThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("YouTube · ${video.channelName} · ${displayKajianCategory(video.category, english)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onToggleSaved) { Text(if (isSaved) "★" else "☆") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAddQueue) { Text(if (english) "＋ Queue" else "＋ Antrean") }
                TextButton(onClick = onShare) { Text(if (english) "Share" else "Bagikan") }
                TextButton(onClick = onOpen) { Text(if (english) "Play" else "Putar") }
            }
        }
    }
}

@Composable
private fun KajianShortCard(
    video: KajianVideo,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onAddQueue: () -> Unit,
) {
    val english = LocalAppEnglish.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).background(Color(0xFF101719))) {
                KajianThumbnail(video, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.68f),
                ) {
                    Text("▶", modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), color = Color.White)
                }
                Text(
                    "SHORTS  ·  ${video.durationSeconds}s",
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${video.channelName} · ${displayKajianCategory(video.category, english)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isSaved) Text("🔖", modifier = Modifier.padding(start = 10.dp))
                TextButton(onClick = onAddQueue) { Text("＋") }
            }
        }
    }
}

@Composable
private fun KajianFilterSheet(
    channels: List<KajianChannel>,
    selectedChannelName: String?,
    savedOnly: Boolean,
    onSelectChannel: (String?) -> Unit,
    onToggleSaved: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val english = LocalAppEnglish.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (english) "Filter talks" else "Filter kajian", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onReset) { Text(if (english) "Reset" else "Reset") }
        }
        HorizontalDivider()
        Text("Channel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "all-channels") {
                FilterChip(
                    selected = selectedChannelName == null,
                    onClick = { onSelectChannel(null) },
                    label = { Text(if (english) "All channels" else "Semua channel") },
                )
            }
            items(channels, key = { it.channelId.ifBlank { it.name } }) { channel ->
                FilterChip(
                    selected = selectedChannelName == channel.name,
                    onClick = { onSelectChannel(channel.name) },
                    label = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        FilterChip(
            selected = savedOnly,
            onClick = { onToggleSaved(!savedOnly) },
            label = { Text(if (english) "Saved videos only" else "Video tersimpan saja") },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text(if (english) "Done" else "Selesai") }
        }
    }
}

@Composable
private fun FilterGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(24.dp)) {
        val outline = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.2f)
            lineTo(size.width * 0.88f, size.height * 0.2f)
            lineTo(size.width * 0.58f, size.height * 0.51f)
            lineTo(size.width * 0.58f, size.height * 0.78f)
            lineTo(size.width * 0.42f, size.height * 0.9f)
            lineTo(size.width * 0.42f, size.height * 0.51f)
            close()
        }
        drawPath(
            path = outline,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun EmptyKajianState(isConfigured: Boolean, isSavedOnly: Boolean) {
    val english = LocalAppEnglish.current
    Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (english) when {
                    isSavedOnly -> "No saved videos yet"
                    !isConfigured -> "Talk catalog is not configured"
                    else -> "No videos match these filters"
                } else when {
                    isSavedOnly -> "Belum ada video tersimpan"
                    !isConfigured -> "Katalog kajian belum dikonfigurasi"
                    else -> "Belum ada video untuk filter ini"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (english) when {
                    isSavedOnly -> "Save a video from the catalog to see it here."
                    !isConfigured -> "Add the Supabase URL and anon key, then populate the catalog with approved videos."
                    else -> "Reviewed content will appear here."
                } else when {
                    isSavedOnly -> "Simpan video dari katalog untuk melihatnya di sini."
                    !isConfigured -> "Tambahkan URL Supabase dan anon key, lalu isi katalog dengan video berstatus approved."
                    else -> "Konten yang lolos review akan muncul di sini."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun displayKajianCategory(category: String, english: Boolean): String = if (!english) category else when (category.lowercase()) {
    "semua" -> "All"
    "akidah" -> "Creed"
    "fiqih", "fikih" -> "Jurisprudence"
    "muamalah" -> "Transactions"
    "sirah" -> "Prophetic biography"
    "tafsir" -> "Tafsir"
    "hadits", "hadis" -> "Hadith"
    "akhlak" -> "Character"
    "keluarga" -> "Family"
    "tazkiyatun nafs" -> "Purification of the soul"
    else -> category
}

@Composable
internal fun KajianThumbnail(
    video: KajianVideo,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = video.thumbnailUrl) {
        value = withContext(Dispatchers.IO) { downloadThumbnail(video.thumbnailUrl) }
    }
    Box(modifier.background(Color(0xFF243336)), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        }
    }
}

private fun downloadThumbnail(url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        val parsed = Uri.parse(url)
        if (parsed.scheme != "https" || parsed.host !in setOf("i.ytimg.com", "img.youtube.com")) return null
        val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 7_000
        }
        try {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private fun shareVideo(context: android.content.Context, video: KajianVideo) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://www.youtube.com/watch?v=${video.youtubeId}")
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan video"))
}

private fun openYouTubeVideo(context: Context, videos: List<KajianVideo>, index: Int) {
    context.startActivity(KajianPlayerActivity.newIntent(context, videos, index.coerceAtLeast(0)))
}
