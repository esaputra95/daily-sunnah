package id.kasirq.dailysunnah.kajian.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.kasirq.dailysunnah.kajian.data.KAJIAN_CATEGORIES
import id.kasirq.dailysunnah.kajian.data.KajianCatalogRepository
import id.kasirq.dailysunnah.kajian.data.KajianCatalogSnapshot
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KajianScreen(onOpenVideo: (KajianVideo) -> Unit) {
    val context = LocalContext.current
    val repository = remember { KajianCatalogRepository(context.applicationContext) }
    var snapshot by remember { mutableStateOf<KajianCatalogSnapshot?>(null) }
    var savedIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedCategory by rememberSaveable { mutableStateOf("Semua") }
    var query by rememberSaveable { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        snapshot = withContext(Dispatchers.IO) { repository.load() }
        savedIds = repository.savedVideoIds()
    }

    val videos = snapshot?.videos.orEmpty().filter { video ->
        val matchesTab = if (selectedTab == 0) video.contentType == KajianVideo.ContentType.KAJIAN
        else video.contentType == KajianVideo.ContentType.SHORT
        val matchesCategory = selectedCategory == "Semua" || video.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = query.isBlank() || listOf(video.title, video.channelName, video.category, video.description)
            .any { it.contains(query.trim(), ignoreCase = true) }
        matchesTab && matchesCategory && matchesQuery && (!savedOnly || video.youtubeId in savedIds)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kajian", fontWeight = FontWeight.SemiBold)
                        Text("Video pilihan yang telah ditinjau", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { reloadKey += 1 }) { Text("↻", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Kajian", "Shorts").forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                singleLine = true,
                label = { Text("Cari judul, channel, atau tema") },
                leadingIcon = { Text("⌕") },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf("Semua") + KAJIAN_CATEGORIES, key = { it }) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                    )
                }
                item {
                    FilterChip(
                        selected = savedOnly,
                        onClick = { savedOnly = !savedOnly },
                        label = { Text("Tersimpan") },
                    )
                }
            }
            snapshot?.message?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            when {
                snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                videos.isEmpty() -> EmptyKajianState(
                    isConfigured = snapshot?.message?.contains("belum dikonfigurasi", ignoreCase = true) != true,
                    isSavedOnly = savedOnly,
                )
                selectedTab == 0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(videos, key = { it.youtubeId }) { video ->
                        KajianVideoCard(
                            video = video,
                            isSaved = video.youtubeId in savedIds,
                            onOpen = { onOpenVideo(video) },
                            onToggleSaved = { savedIds = repository.toggleSaved(video.youtubeId) },
                            onShare = { shareVideo(context, video) },
                        )
                    }
                }
                else -> ShortsFeed(
                    videos = videos,
                    savedIds = savedIds,
                    onToggleSaved = { savedIds = repository.toggleSaved(it) },
                    onShare = { video -> shareVideo(context, video) },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun KajianPlayerScreen(video: KajianVideo, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
                title = { Text("Pemutar YouTube", style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            YouTubePlayer(
                video = video,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                autoplay = true,
            )
            Text(video.title, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("YouTube · ${video.channelName} · ${video.category}", modifier = Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (video.description.isNotBlank()) {
                Text(video.description, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Text("Video diputar melalui YouTube.", modifier = Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                TextButton(onClick = onOpen) { Text("Tonton") }
            }
        }
    }
}

@Composable
private fun ShortsFeed(
    videos: List<KajianVideo>,
    savedIds: Set<String>,
    onToggleSaved: (String) -> Unit,
    onShare: (KajianVideo) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { videos.size })
    var activePlayerId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pagerState.currentPage) { activePlayerId = null }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        pageSpacing = 8.dp,
    ) { pageIndex ->
        val video = videos[pageIndex]
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF101719)),
                contentAlignment = Alignment.Center,
            ) {
                if (activePlayerId == video.youtubeId && pageIndex == pagerState.currentPage) {
                    key(video.youtubeId) {
                        YouTubePlayer(video, Modifier.fillMaxSize(), autoplay = true)
                    }
                } else {
                    KajianThumbnail(video, Modifier.fillMaxSize())
                    Button(
                        onClick = { activePlayerId = video.youtubeId },
                        modifier = Modifier.align(Alignment.Center),
                    ) { Text("▶  Putar di YouTube") }
                }
            }
            Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("YouTube · ${video.channelName} · ${video.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onToggleSaved(video.youtubeId) }) { Text(if (video.youtubeId in savedIds) "★ Tersimpan" else "☆ Simpan") }
                TextButton(onClick = { onShare(video) }) { Text("Bagikan") }
            }
        }
    }
}

@Composable
private fun EmptyKajianState(isConfigured: Boolean, isSavedOnly: Boolean) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
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
private fun KajianThumbnail(video: KajianVideo, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = video.thumbnailUrl) {
        value = withContext(Dispatchers.IO) { downloadThumbnail(video.thumbnailUrl) }
    }
    Box(modifier.background(Color(0xFF243336)), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun YouTubePlayer(video: KajianVideo, modifier: Modifier = Modifier, autoplay: Boolean = false) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val webView = remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadDataWithBaseURL(PLAYER_ORIGIN, playerHtml(video.youtubeId, autoplay), "text/html", "UTF-8", null)
                webView.value = this
            }
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    webView.value?.evaluateJavascript("window.pauseDailySunnahPlayer && window.pauseDailySunnahPlayer()", null)
                    webView.value?.onPause()
                }
                Lifecycle.Event.ON_RESUME -> webView.value?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView.value = null
        }
    }
}

private fun playerHtml(videoId: String, autoplay: Boolean): String = """
    <!doctype html>
    <html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style>
    </head><body>
    <iframe id="player" title="YouTube video player"
      src="https://www.youtube.com/embed/$videoId?enablejsapi=1&amp;autoplay=${if (autoplay) 1 else 0}&amp;controls=1&amp;playsinline=1&amp;origin=$PLAYER_ORIGIN"
      allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
    <script>function pauseDailySunnahPlayer(){var p=document.getElementById('player').contentWindow;p.postMessage(JSON.stringify({event:'command',func:'pauseVideo',args:[]}),'https://www.youtube.com');}</script>
    </body></html>
""".trimIndent()

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

private const val PLAYER_ORIGIN = "https://id.kasirq.dailysunnah"
