package id.kasirq.dailysunnah.kajian.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import id.kasirq.dailysunnah.kajian.data.KajianEndBehavior
import id.kasirq.dailysunnah.kajian.data.KajianCatalogRepository
import id.kasirq.dailysunnah.kajian.data.KajianPlaybackPreferences
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import id.kasirq.dailysunnah.other.AppLanguagePreference
import id.kasirq.dailysunnah.other.AppSettingsStore

class KajianPlayerActivity : ComponentActivity() {
    private val english: Boolean by lazy { AppSettingsStore(applicationContext).language() == AppLanguagePreference.ENGLISH }
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var playerSurface: FrameLayout
    private lateinit var webView: WebView
    private lateinit var playbackStatus: TextView
    private var playerErrorView: TextView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var priorSystemUiVisibility = 0
    private var priorRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var videos: List<KajianVideo> = emptyList()
    private var currentVideoIndex = 0
    private var handledEndedVideoId: String? = null
    private lateinit var playbackTitle: TextView
    private lateinit var playbackMetadata: TextView
    private lateinit var playbackDescription: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videos = readIntentVideos()
        if (videos.isEmpty()) {
            finish()
            return
        }
        currentVideoIndex = intent.getIntExtra(EXTRA_INITIAL_INDEX, 0).coerceIn(videos.indices)
        val video = currentVideo()
        KajianCatalogRepository(applicationContext).recordPlayback(video)

        window.statusBarColor = COLOR_BACKGROUND
        window.navigationBarColor = COLOR_BACKGROUND
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        root = FrameLayout(this).apply { setBackgroundColor(COLOR_BACKGROUND) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
        }
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        content.addView(createToolbar(), LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))

        playerSurface = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        Log.e(TAG, "YouTube main-frame load failed: ${error.errorCode}")
                        showPlayerError(if (english) "Video could not load. Check your connection and tap reload." else "Video gagal dimuat. Periksa koneksi lalu ketuk muat ulang.")
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    val scheme = uri.scheme.orEmpty()
                    val host = uri.host.orEmpty()
                    if (scheme == "intent" || scheme == "youtube" || scheme == "vnd.youtube") {
                        Log.w(TAG, "Blocked external YouTube app navigation: $uri")
                        showPlaybackStatus(if (english) "This video tried to open another app. Playback stays in Daily Sunnah." else "Video ini mencoba membuka aplikasi lain. Pemutaran tetap di Daily Sunnah.")
                        return true
                    }
                    if (scheme == "https" && host in INTERNAL_YOUTUBE_HOSTS) {
                        if (uri.path == "/watch" || uri.path.orEmpty().startsWith("/shorts/")) {
                            Log.w(TAG, "Blocked YouTube watch-page navigation: $uri")
                            showPlaybackStatus(if (english) "Use the player’s play button to keep the video in Daily Sunnah." else "Gunakan tombol putar di player agar video tetap diputar di Daily Sunnah.")
                            return true
                        }
                        return false
                    }
                    if (!request.isForMainFrame) return false
                    Log.w(TAG, "Blocked external player navigation: $uri")
                    showPlaybackStatus(if (english) "External link blocked. Play the video using the player controls above." else "Tautan eksternal diblokir. Putar video melalui kontrol player di atas.")
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "YouTube embed loaded")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    showFullscreen(view, callback)
                }

                override fun onHideCustomView() {
                    hideFullscreen()
                }

                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                    showPlaybackStatus(if (english) "Links outside the player are blocked. The video stays in Daily Sunnah." else "Tautan di luar player tidak dibuka. Video tetap di Daily Sunnah.")
                    return false
                }

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.w(TAG, message.message())
                    }
                    return false
                }
            }
        }

        val playerHeightDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density * 9f / 16f).toInt()
        playerSurface.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(playerSurface, LinearLayout.LayoutParams(MATCH_PARENT, dp(playerHeightDp.coerceAtLeast(200))))

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        playbackStatus = textView(if (english) "Connecting to player…" else "Menghubungkan ke player…", 14f, COLOR_MUTED).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(COLOR_STATUS_BACKGROUND)
        }
        details.addView(playbackStatus, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        playbackTitle = textView(video.title, 20f, COLOR_TEXT, bold = true)
        details.addView(playbackTitle)
        playbackMetadata = textView(listOf(video.channelName, video.category).filter(String::isNotBlank).joinToString(" · "), 14f, COLOR_MUTED).apply {
            setPadding(0, dp(10), 0, 0)
        }
        details.addView(playbackMetadata)
        playbackDescription = textView(video.description, 15f, COLOR_TEXT).apply { setPadding(0, dp(18), 0, 0) }
        details.addView(playbackDescription)
        val scrollView = ScrollView(this).apply { addView(details) }
        content.addView(scrollView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        webView.addJavascriptInterface(PlayerBridge(), "DailySunnah")
        webView.loadDataWithBaseURL(APP_REFERER, playerHtml(video.youtubeId), "text/html", "UTF-8", null)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenView != null) hideFullscreen() else finish()
            }
        })
    }

    private fun createToolbar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(12), 0)
        val back = textView("‹", 24f, COLOR_PRIMARY)
        back.gravity = Gravity.CENTER
        back.contentDescription = if (english) "Back" else "Kembali"
        back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        addView(back, LinearLayout.LayoutParams(dp(48), MATCH_PARENT))
        addView(textView(if (english) "YouTube player" else "Pemutar YouTube", 18f, COLOR_TEXT, bold = true),
            LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        val reload = textView("↻", 26f, COLOR_PRIMARY)
        reload.gravity = Gravity.CENTER
        reload.contentDescription = if (english) "Reload video" else "Muat ulang video"
        reload.setOnClickListener { retryPlayback() }
        addView(reload, LinearLayout.LayoutParams(dp(48), MATCH_PARENT))
    }

    private fun textView(text: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        priorSystemUiVisibility = window.decorView.systemUiVisibility
        priorRequestedOrientation = requestedOrientation
        content.visibility = View.GONE
        root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun hideFullscreen() {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        content.visibility = View.VISIBLE
        ViewCompat.requestApplyInsets(root)
        window.decorView.systemUiVisibility = priorSystemUiVisibility
        requestedOrientation = priorRequestedOrientation
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (fullscreenView != null) hideFullscreen()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun playerHtml(videoId: String): String = """
        <!doctype html>
        <html><head>
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
          <style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style>
        </head><body>
          <div id="player"></div>
          <script>
            var player;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width: '100%', height: '100%', videoId: '$videoId',
                playerVars: { autoplay: 0, controls: 1, enablejsapi: 1, playsinline: 1,
                  origin: '$APP_ORIGIN', hl: '${if (english) "en" else "id"}', rel: 0 },
                events: {
                  onReady: function() { DailySunnah.onReady(); },
                  onStateChange: function(event) { DailySunnah.onState(event.data); },
                  onError: function(event) { DailySunnah.onError(event.data); },
                  onAutoplayBlocked: function() { DailySunnah.onAutoplayBlocked(); }
                }
              });
            }
            function loadKajianVideo(id) { if (player) player.loadVideoById({videoId:id, startSeconds:0}); }
            function replayKajianVideo() { if (player) { player.seekTo(0, true); player.playVideo(); } }
            function pauseKajianVideo() { if (player) player.pauseVideo(); }
          </script>
          <script src="https://www.youtube.com/iframe_api"></script>
        </body></html>
    """.trimIndent()

    private fun showPlaybackStatus(message: String) {
        if (!::playbackStatus.isInitialized) return
        playbackStatus.post { playbackStatus.text = message }
    }

    private fun showPlayerError(message: String) {
        showPlaybackStatus(message)
        if (!::playerSurface.isInitialized || !::webView.isInitialized) return
        playerSurface.post {
            webView.visibility = View.GONE
            val errorView = playerErrorView ?: TextView(this).apply {
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setBackgroundColor(0xFF20292C.toInt())
                playerErrorView = this
                playerSurface.addView(this, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
            errorView.text = message
        }
    }

    private fun retryPlayback() {
        playerErrorView?.let(playerSurface::removeView)
        playerErrorView = null
        if (::webView.isInitialized) {
            webView.visibility = View.VISIBLE
            webView.reload()
            showPlaybackStatus(if (english) "Connecting to player…" else "Menghubungkan ke player…")
        }
    }

    private fun readIntentVideos(): List<KajianVideo> {
        val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        if (ids.isEmpty()) {
            val id = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf(VIDEO_ID_PATTERN::matches) ?: return emptyList()
            return listOf(
                KajianVideo(
                    youtubeId = id,
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty(),
                    channelName = intent.getStringExtra(EXTRA_CHANNEL).orEmpty(),
                    category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty(),
                    contentType = KajianVideo.ContentType.KAJIAN,
                    thumbnailUrl = null,
                    durationSeconds = 0,
                    publishedAt = null,
                ),
            )
        }
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
        val channels = intent.getStringArrayListExtra(EXTRA_CHANNELS).orEmpty()
        val categories = intent.getStringArrayListExtra(EXTRA_CATEGORIES).orEmpty()
        val descriptions = intent.getStringArrayListExtra(EXTRA_DESCRIPTIONS).orEmpty()
        return ids.indices.mapNotNull { index ->
            val id = ids[index].takeIf(VIDEO_ID_PATTERN::matches) ?: return@mapNotNull null
            KajianVideo(
                youtubeId = id,
                title = titles.getOrNull(index).orEmpty(),
                description = descriptions.getOrNull(index).orEmpty(),
                channelName = channels.getOrNull(index).orEmpty(),
                category = categories.getOrNull(index).orEmpty(),
                contentType = KajianVideo.ContentType.KAJIAN,
                thumbnailUrl = null,
                durationSeconds = 0,
                publishedAt = null,
            )
        }
    }

    private fun currentVideo(): KajianVideo = videos[currentVideoIndex]

    private fun handleVideoEnded() {
        val currentId = currentVideo().youtubeId
        if (handledEndedVideoId == currentId) return
        handledEndedVideoId = currentId
        when (KajianPlaybackPreferences(applicationContext).endBehavior()) {
            KajianEndBehavior.NEXT -> {
                if (currentVideoIndex + 1 in videos.indices) {
                    currentVideoIndex++
                    handledEndedVideoId = null
                    val next = currentVideo()
                    KajianCatalogRepository(applicationContext).recordPlayback(next)
                    playbackTitle.text = next.title
                    playbackMetadata.text = listOf(next.channelName, next.category).filter(String::isNotBlank).joinToString(" · ")
                    playbackDescription.text = next.description
                    showPlaybackStatus(if (english) "Loading next video…" else "Memuat video berikutnya…")
                    webView.evaluateJavascript("if(window.loadKajianVideo){window.loadKajianVideo('${next.youtubeId}')}", null)
                } else {
                    showPlaybackStatus(if (english) "Last video finished." else "Video terakhir selesai.")
                    webView.evaluateJavascript("if(window.pauseKajianVideo){window.pauseKajianVideo()}", null)
                }
            }
            KajianEndBehavior.STOP -> {
                showPlaybackStatus(if (english) "Video finished." else "Video selesai.")
                webView.evaluateJavascript("if(window.pauseKajianVideo){window.pauseKajianVideo()}", null)
            }
            KajianEndBehavior.REPLAY -> {
                showPlaybackStatus(if (english) "Replaying video." else "Memutar ulang video.")
                webView.evaluateJavascript("if(window.replayKajianVideo){window.replayKajianVideo()}", null)
            }
        }
    }

    private fun playbackErrorMessage(code: Int): String = if (english) when (code) {
        2 -> "Invalid video ID."
        5 -> "This device’s HTML5 player cannot play the video."
        100 -> "This video is private, deleted, or unavailable."
        101, 150 -> "This video cannot play in Daily Sunnah because its owner disabled embedded playback. Try another video."
        153 -> "YouTube could not verify the app identity for playback."
        else -> "YouTube player failed to play the video (code $code)."
    } else when (code) {
        2 -> "ID video tidak valid."
        5 -> "Video tidak dapat diputar oleh player HTML5 di perangkat ini."
        100 -> "Video ini privat, dihapus, atau tidak tersedia untuk akun ini."
        101, 150 -> "Video ini tidak dapat diputar di Daily Sunnah karena pemilik menonaktifkan pemutaran embed. Pilih video kajian lain."
        153 -> "YouTube tidak dapat memverifikasi identitas aplikasi untuk pemutaran ini."
        else -> "Player YouTube gagal memutar video (kode $code)."
    }

    private inner class PlayerBridge {
        @android.webkit.JavascriptInterface
        fun onReady() = showPlaybackStatus(if (english) "Player ready. Tap the video’s play button." else "Player siap. Ketuk tombol putar pada video.")

        @android.webkit.JavascriptInterface
        fun onState(state: Int) {
            if (state == 1) handledEndedVideoId = null
            val message = when (state) {
                0 -> if (english) "Video finished." else "Video selesai."
                1 -> if (english) "Video playing in Daily Sunnah." else "Video sedang diputar di Daily Sunnah."
                2 -> if (english) "Video paused." else "Video dijeda."
                3 -> if (english) "Loading video…" else "Video sedang dimuat…"
                5 -> if (english) "Player ready. Tap the video’s play button." else "Player siap. Ketuk tombol putar pada video."
                else -> null
            }
            if (message != null) showPlaybackStatus(message)
            if (state == 0) webView.post { handleVideoEnded() }
        }

        @android.webkit.JavascriptInterface
        fun onError(code: Int) {
            Log.e(TAG, "YouTube IFrame error: $code")
            showPlayerError(playbackErrorMessage(code))
        }

        @android.webkit.JavascriptInterface
        fun onAutoplayBlocked() = showPlaybackStatus(if (english) "Tap the player button to play the video." else "Putar video dengan mengetuk tombol pada player.")
    }

    companion object {
        private const val TAG = "KajianPlayer"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_DESCRIPTION = "description"
        private const val EXTRA_IDS = "kajian_video_ids"
        private const val EXTRA_TITLES = "kajian_video_titles"
        private const val EXTRA_CHANNELS = "kajian_video_channels"
        private const val EXTRA_CATEGORIES = "kajian_video_categories"
        private const val EXTRA_DESCRIPTIONS = "kajian_video_descriptions"
        private const val EXTRA_INITIAL_INDEX = "kajian_initial_index"
        private const val APP_REFERER = "https://id.kasirq.dailysunnah/"
        private const val APP_ORIGIN = "https://id.kasirq.dailysunnah"
        private val INTERNAL_YOUTUBE_HOSTS = setOf("www.youtube.com", "youtube.com", "www.youtube-nocookie.com", "youtube-nocookie.com")
        private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        private const val COLOR_BACKGROUND = 0xFFF7F3EA.toInt()
        private const val COLOR_STATUS_BACKGROUND = 0xFFECE7DD.toInt()
        private const val COLOR_TEXT = 0xFF202A2C.toInt()
        private const val COLOR_MUTED = 0xFF526467.toInt()
        private const val COLOR_PRIMARY = 0xFF0E7490.toInt()
        private val VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")

        fun newIntent(context: Context, video: KajianVideo): Intent = newIntent(context, listOf(video), 0)

        fun newIntent(context: Context, videos: List<KajianVideo>, initialIndex: Int): Intent =
            Intent(context, KajianPlayerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_IDS, ArrayList(videos.map { it.youtubeId }))
                putStringArrayListExtra(EXTRA_TITLES, ArrayList(videos.map { it.title }))
                putStringArrayListExtra(EXTRA_CHANNELS, ArrayList(videos.map { it.channelName }))
                putStringArrayListExtra(EXTRA_CATEGORIES, ArrayList(videos.map { it.category }))
                putStringArrayListExtra(EXTRA_DESCRIPTIONS, ArrayList(videos.map { it.description }))
                putExtra(EXTRA_INITIAL_INDEX, initialIndex)
            }
    }
}
