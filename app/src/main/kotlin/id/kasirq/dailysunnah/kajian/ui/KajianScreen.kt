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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.kasirq.dailysunnah.kajian.data.KAJIAN_CATEGORIES
import id.kasirq.dailysunnah.kajian.data.KajianCatalogRepository
import id.kasirq.dailysunnah.kajian.data.KajianCatalogSnapshot
import id.kasirq.dailysunnah.kajian.data.KajianChannel
import id.kasirq.dailysunnah.kajian.data.KajianFeedSettings
import id.kasirq.dailysunnah.kajian.data.KajianEndBehavior
import id.kasirq.dailysunnah.kajian.data.KajianPlaybackPreferences
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import id.kasirq.dailysunnah.ui.ModernTextFieldShape
import id.kasirq.dailysunnah.ui.modernTextFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun KajianScreen(initialContentType: KajianVideo.ContentType = KajianVideo.ContentType.KAJIAN) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { KajianCatalogRepository(context.applicationContext) }
    val playbackPreferences = remember { KajianPlaybackPreferences(context.applicationContext) }
    var snapshot by remember { mutableStateOf<KajianCatalogSnapshot?>(null) }
    var cachedChannels by remember { mutableStateOf(emptyList<KajianChannel>()) }
    var savedIds by remember { mutableStateOf(emptySet<String>()) }
    var savedVideos by remember { mutableStateOf(emptyList<KajianVideo>()) }
    var selectedTab by rememberSaveable(initialContentType) {
        mutableIntStateOf(KajianVideo.ContentType.entries.indexOf(initialContentType).coerceAtLeast(0))
    }
    var selectedCategory by rememberSaveable { mutableStateOf("Semua") }
    var query by rememberSaveable { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedChannelName by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    var showPlaybackSettings by rememberSaveable { mutableStateOf(false) }
    var endBehavior by rememberSaveable { mutableStateOf(playbackPreferences.endBehavior()) }

    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                savedIds = repository.savedVideoIds()
                savedVideos = repository.savedVideos()
                if (firstResume) firstResume = false else reloadKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(reloadKey, selectedChannelName) {
        savedIds = repository.savedVideoIds()
        savedVideos = repository.savedVideos()
        snapshot = withContext(Dispatchers.IO) { repository.load(selectedChannelName) }.also { loaded ->
            if (loaded.channels.isNotEmpty()) cachedChannels = loaded.channels
        }
        savedIds = repository.savedVideoIds()
        savedVideos = repository.savedVideos()
    }

    val configuredContentTypes = snapshot?.feedSettings?.contentTypes ?: KajianFeedSettings.DEFAULT.contentTypes
    val allowedContentTypes = KajianVideo.ContentType.entries.filter { type ->
        type in configuredContentTypes || (savedOnly && type == KajianVideo.ContentType.SHORT)
    }.ifEmpty { KajianVideo.ContentType.entries }
    val effectiveTab = selectedTab.coerceIn(0, allowedContentTypes.lastIndex)
    val selectedContentType = allowedContentTypes[effectiveTab]
    val availableVideos = if (savedOnly) (snapshot?.videos.orEmpty() + savedVideos).distinctBy { it.youtubeId } else snapshot?.videos.orEmpty()
    val videos = availableVideos.filter { video ->
        val matchesTab = video.contentType == selectedContentType
        val matchesChannel = selectedChannelName == null || video.channelName.equals(selectedChannelName, ignoreCase = true)
        val matchesCategory = savedOnly || selectedCategory == "Semua" || video.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = savedOnly || query.isBlank() || listOf(video.title, video.channelName, video.category, video.description)
            .any { it.contains(query.trim(), ignoreCase = true) }
        matchesTab && matchesChannel && matchesCategory && matchesQuery && (!savedOnly || video.youtubeId in savedIds)
    }
    val activeFilterCount = listOfNotNull(
        selectedChannelName,
        selectedCategory.takeUnless { it == "Semua" },
        "saved".takeIf { savedOnly },
    ).size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFilterSheet = true },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "kajian-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Kajian", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("Video pilihan yang telah ditinjau", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { reloadKey += 1 }) { Text("↻", style = MaterialTheme.typography.titleLarge) }
                    IconButton(onClick = { showPlaybackSettings = true }) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            stickyHeader(key = "kajian-tabs") {
                TabRow(selectedTabIndex = effectiveTab) {
                    allowedContentTypes.forEachIndexed { index, type ->
                        val title = if (type == KajianVideo.ContentType.KAJIAN) "Kajian" else "Shorts"
                        Tab(selected = effectiveTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }
            }
            item(key = "kajian-search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    shape = ModernTextFieldShape,
                    colors = modernTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    placeholder = { Text("Cari judul, channel, atau tema") },
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
                        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
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
                        if (selectedContentType == KajianVideo.ContentType.KAJIAN) {
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
                            )
                        } else {
                            KajianShortCard(
                                video = video,
                                isSaved = video.youtubeId in savedIds,
                                onOpen = {
                                    val shortIndex = videos.indexOfFirst { it.youtubeId == video.youtubeId }.coerceAtLeast(0)
                                    context.startActivity(ShortsFeedActivity.newIntent(context, videos, shortIndex))
                                },
                            )
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
                selectedCategory = selectedCategory,
                savedOnly = savedOnly,
                onSelectChannel = { channelName ->
                    if (selectedChannelName != channelName) {
                        selectedChannelName = channelName
                    }
                },
                onSelectCategory = { selectedCategory = it },
                onToggleSaved = {
                    savedOnly = it
                    if (it) {
                        selectedCategory = "Semua"
                        query = ""
                        val shortsIndex = KajianVideo.ContentType.entries
                            .filter { type -> type in configuredContentTypes || type == KajianVideo.ContentType.SHORT }
                            .indexOf(KajianVideo.ContentType.SHORT)
                        if (shortsIndex >= 0) selectedTab = shortsIndex
                    } else {
                        selectedCategory = "Semua"
                    }
                },
                onReset = {
                    selectedChannelName = null
                    selectedCategory = "Semua"
                    savedOnly = false
                },
                onDone = { showFilterSheet = false },
            )
        }
    }
    if (showPlaybackSettings) {
        AlertDialog(
            onDismissRequest = { showPlaybackSettings = false },
            title = { Text("Setelah video selesai") },
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
                                Text(option.label, fontWeight = FontWeight.SemiBold)
                                Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("Berlaku untuk pemutar Kajian dan Shorts. Default: lanjut ke video berikutnya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showPlaybackSettings = false }) { Text("Selesai") } },
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
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            KajianThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("YouTube · ${video.channelName} · ${video.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onToggleSaved) { Text(if (isSaved) "★" else "☆") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onShare) { Text("Bagikan") }
                TextButton(onClick = onOpen) { Text("Putar") }
            }
        }
    }
}

@Composable
private fun KajianShortCard(
    video: KajianVideo,
    isSaved: Boolean,
    onOpen: () -> Unit,
) {
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
                    Text("${video.channelName} · ${video.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isSaved) Text("🔖", modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun KajianFilterSheet(
    channels: List<KajianChannel>,
    selectedChannelName: String?,
    selectedCategory: String,
    savedOnly: Boolean,
    onSelectChannel: (String?) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleSaved: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Filter kajian", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onReset) { Text("Reset") }
        }
        HorizontalDivider()
        Text("Channel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "all-channels") {
                FilterChip(
                    selected = selectedChannelName == null,
                    onClick = { onSelectChannel(null) },
                    label = { Text("Semua channel") },
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
        Text("Kategori", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Semua") + KAJIAN_CATEGORIES, key = { it }) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category) },
                )
            }
        }
        FilterChip(
            selected = savedOnly,
            onClick = { onToggleSaved(!savedOnly) },
            label = { Text("Video tersimpan saja") },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Selesai") }
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
    Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    isSavedOnly -> "Belum ada video tersimpan"
                    !isConfigured -> "Katalog kajian belum dikonfigurasi"
                    else -> "Belum ada video untuk filter ini"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    isSavedOnly -> "Simpan video dari katalog untuk melihatnya di sini."
                    !isConfigured -> "Tambahkan URL Supabase dan anon key, lalu isi katalog dengan video berstatus approved."
                    else -> "Konten yang lolos review akan muncul di sini."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
