package id.kasirq.dailysunnah.kajian.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.kasirq.dailysunnah.kajian.data.KajianCatalogRepository
import id.kasirq.dailysunnah.kajian.data.KajianCatalogSnapshot
import id.kasirq.dailysunnah.kajian.data.KajianFeedSettings
import id.kasirq.dailysunnah.kajian.data.KajianEndBehavior
import id.kasirq.dailysunnah.kajian.data.KajianPlaybackPreferences
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import id.kasirq.dailysunnah.other.AppLanguagePreference
import id.kasirq.dailysunnah.other.AppSettingsStore
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHORTS_APP_REFERER = "https://id.kasirq.dailysunnah/"
private const val SHORTS_APP_ORIGIN = "https://id.kasirq.dailysunnah"
private const val SHORTS_LOG_TAG = "KajianShorts"
private val SHORTS_INTERNAL_YOUTUBE_HOSTS = setOf("www.youtube.com", "youtube.com", "www.youtube-nocookie.com", "youtube-nocookie.com")

class ShortsFeedActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val english: Boolean by lazy { AppSettingsStore(applicationContext).language() == AppLanguagePreference.ENGLISH }
    private lateinit var root: FrameLayout
    private lateinit var contentRoot: FrameLayout
    private lateinit var webView: WebView
    private lateinit var positionLabel: TextView
    private lateinit var categoryLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var titleLabel: TextView
    private lateinit var channelLabel: TextView
    private lateinit var saveLabel: TextView
    private lateinit var saveIcon: ImageView
    private var activePlayer: WebView? = null
    private var videos: List<KajianVideo> = emptyList()
    private var currentIndex = 0
    private var savedVideoIds: Set<String> = emptySet()
    private var handledEndedVideoId: String? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var wasVerticalSwipe = false
    private var velocityTracker: VelocityTracker? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var priorSystemUiVisibility = 0
    private var priorRequestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var nextCatalogOffset = 0
    private var hasMoreCatalogVideos = false
    private var loadingCatalogPage = false
    private var catalogChannelName: String? = null
    private var catalogSearchQuery: String? = null
    private var catalogFeedSettings = KajianFeedSettings.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
        val channels = intent.getStringArrayListExtra(EXTRA_CHANNELS).orEmpty()
        val categories = intent.getStringArrayListExtra(EXTRA_CATEGORIES).orEmpty()
        val descriptions = intent.getStringArrayListExtra(EXTRA_DESCRIPTIONS).orEmpty()
        val thumbnails = intent.getStringArrayListExtra(EXTRA_THUMBNAILS).orEmpty()
        val durations = intent.getIntegerArrayListExtra(EXTRA_DURATIONS).orEmpty()
        val publishedAt = intent.getStringArrayListExtra(EXTRA_PUBLISHED_AT).orEmpty()
        nextCatalogOffset = intent.getIntExtra(EXTRA_NEXT_OFFSET, 0).coerceAtLeast(0)
        hasMoreCatalogVideos = intent.getBooleanExtra(EXTRA_HAS_MORE, false)
        catalogChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
        catalogSearchQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY)
        catalogFeedSettings = KajianFeedSettings(
            sortOrder = intent.getStringExtra(EXTRA_SORT_ORDER) ?: KajianFeedSettings.DEFAULT.sortOrder,
            contentTypes = intent.getStringArrayListExtra(EXTRA_CONTENT_TYPES)
                .orEmpty()
                .mapNotNull { apiValue -> KajianVideo.ContentType.entries.firstOrNull { it.apiValue == apiValue } }
                .toSet()
                .ifEmpty { KajianFeedSettings.DEFAULT.contentTypes },
            minDurationSeconds = intent.getIntExtra(EXTRA_MIN_DURATION, -1).takeIf { it >= 0 },
            maxDurationSeconds = intent.getIntExtra(EXTRA_MAX_DURATION, -1).takeIf { it >= 0 },
        )
        videos = ids.indices.mapNotNull { index ->
            val id = ids.getOrNull(index)?.takeIf(VIDEO_ID_PATTERN::matches) ?: return@mapNotNull null
            KajianVideo(
                youtubeId = id,
                title = titles.getOrNull(index).orEmpty(),
                description = descriptions.getOrNull(index).orEmpty(),
                channelName = channels.getOrNull(index).orEmpty(),
                category = categories.getOrNull(index).orEmpty(),
                contentType = KajianVideo.ContentType.SHORT,
                thumbnailUrl = thumbnails.getOrNull(index)?.takeIf { it.startsWith("https://") },
                durationSeconds = durations.getOrNull(index)?.coerceAtLeast(0) ?: 0,
                publishedAt = publishedAt.getOrNull(index)?.takeIf(String::isNotBlank),
            )
        }
        if (videos.isEmpty()) {
            finish()
            return
        }

        currentIndex = intent.getIntExtra(EXTRA_INITIAL_INDEX, 0).coerceIn(videos.indices)
        savedVideoIds = KajianCatalogRepository(applicationContext).savedVideoIds()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        buildViews()
        configurePlayer()
        renderCurrentVideo()
        activePlayer = webView
        webView.loadDataWithBaseURL(SHORTS_APP_REFERER, shortPlayerHtml(currentVideo().youtubeId), "text/html", "UTF-8", null)
    }

    private fun buildViews() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        contentRoot = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(contentRoot, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            contentRoot.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        webView = WebView(this).apply { setBackgroundColor(Color.BLACK) }
        contentRoot.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(14), 0)
            setBackgroundColor(0x33000000)
        }
        val back = textView("‹", 24f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            contentDescription = if (english) "Back" else "Kembali"
            setOnClickListener { finish() }
        }
        topBar.addView(back, LinearLayout.LayoutParams(dp(48), MATCH_PARENT))
        positionLabel = textView("", 14f, Color.WHITE, bold = true)
        topBar.addView(positionLabel, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        categoryLabel = textView("", 12f, 0xFFE5C36B.toInt()).apply { maxLines = 1 }
        topBar.addView(categoryLabel)
        contentRoot.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, dp(54), Gravity.TOP))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val saveAction = createAction(if (english) "Save" else "Simpan", bookmark = true) { toggleSaved() }
        saveIcon = saveAction.first
        saveLabel = saveAction.second
        actions.addView(saveAction.third, LinearLayout.LayoutParams(MATCH_PARENT, dp(84)))
        actions.addView(createAction(if (english) "Share" else "Bagikan", icon = "↗") { shareCurrentVideo() }.third, LinearLayout.LayoutParams(MATCH_PARENT, dp(84)))
        contentRoot.addView(
            actions,
            FrameLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
                rightMargin = dp(4)
                bottomMargin = dp(124)
            },
        )

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0xB8000000.toInt()),
            )
            setPadding(dp(18), dp(28), dp(92), dp(14))
        }
        statusLabel = textView(if (english) "Connecting to YouTube…" else "Menghubungkan ke YouTube…", 11f, 0xFFD0D0D0.toInt())
        details.addView(statusLabel, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        titleLabel = textView("", 15f, Color.WHITE, bold = true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        details.addView(titleLabel, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5) })
        channelLabel = textView("", 12f, 0xFFE0E0E0.toInt()).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        details.addView(channelLabel, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        contentRoot.addView(
            details,
            FrameLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = dp(4)
            },
        )
    }

    private fun configurePlayer() {
        webView.apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            addJavascriptInterface(PlayerBridge(), "DailySunnah")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Log.d(SHORTS_LOG_TAG, "YouTube Shorts embed loaded")
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        Log.e(SHORTS_LOG_TAG, "Embed load failed: ${error.errorCode}")
                        updateStatus(if (english) "Video could not load. Check your internet connection." else "Video gagal dimuat. Periksa koneksi internet.")
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "intent" || uri.scheme == "youtube" || uri.scheme == "vnd.youtube") return true
                    if (!request.isForMainFrame) return false
                    if (uri.scheme == "https" && uri.host in SHORTS_INTERNAL_YOUTUBE_HOSTS) {
                        if (uri.path == "/watch" || uri.path.orEmpty().startsWith("/shorts/")) {
                            updateStatus("Geser ke atas atau bawah untuk video lainnya")
                            return true
                        }
                        return false
                    }
                    updateStatus(if (english) "Links outside YouTube are blocked in Shorts." else "Tautan di luar YouTube diblokir di feed Shorts")
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreenView != null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    fullscreenView = view
                    fullscreenCallback = callback
                    priorSystemUiVisibility = window.decorView.systemUiVisibility
                    priorRequestedOrientation = requestedOrientation
                    root.removeView(contentRoot)
                    root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }

                override fun onHideCustomView() = hideFullscreen()

                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) Log.w(SHORTS_LOG_TAG, message.message())
                    return false
                }
            }
            installSwipeGesture()
        }
    }

    private fun installSwipeGesture() {
        val touchSlop = dp(8).toFloat()
        webView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    contentRoot.animate().cancel()
                    contentRoot.translationY = 0f
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    wasVerticalSwipe = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.2f) {
                        if (!wasVerticalSwipe) {
                            wasVerticalSwipe = true
                            // Cancel the iframe's long-press gesture as soon as the
                            // vertical feed swipe wins, so YouTube cannot latch 2x speed.
                            val cancelEvent = MotionEvent.obtain(event).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            view.onTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        val nextIndex = if (dy < 0f) currentIndex + 1 else currentIndex - 1
                        val resistance = if (nextIndex in videos.indices) 1f else 0.28f
                        contentRoot.translationY = dy * resistance
                        true
                    } else false
                }
                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    val deltaY = event.rawY - touchStartY
                    if (wasVerticalSwipe) {
                        settleShortSwipe(deltaY, velocityY)
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    val handled = wasVerticalSwipe
                    recycleVelocityTracker()
                    handled
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (wasVerticalSwipe) settleBackToCenter()
                    wasVerticalSwipe = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    recycleVelocityTracker()
                    true
                }
                else -> false
            }
        }
    }

    private fun settleShortSwipe(deltaY: Float, velocityY: Float) {
        val direction = if (deltaY < 0f) 1 else -1
        val targetIndex = currentIndex + direction
        val canMove = targetIndex in videos.indices
        val distanceThreshold = contentRoot.height * 0.18f
        val shouldAdvance = canMove && (abs(deltaY) >= distanceThreshold || abs(velocityY) > dp(900))
        val shouldLoadNext = direction > 0 && targetIndex == videos.size && hasMoreCatalogVideos &&
            (abs(deltaY) >= distanceThreshold || abs(velocityY) > dp(900))
        if (shouldLoadNext) {
            settleBackToCenter()
            loadNextShorts(advanceWhenReady = true)
            return
        }
        if (!shouldAdvance) {
            settleBackToCenter()
            return
        }

        animateToVideo(targetIndex, deltaY < 0f)
    }

    private fun animateToVideo(targetIndex: Int, moveForward: Boolean) {
        if (targetIndex !in videos.indices) return
        val height = contentRoot.height.toFloat().coerceAtLeast(1f)
        val exitOffset = if (moveForward) -height else height
        contentRoot.animate().cancel()
        contentRoot.animate()
            .translationY(exitOffset)
            .setDuration(190)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .withEndAction {
                currentIndex = targetIndex
                handledEndedVideoId = null
                renderCurrentVideo()
                statusLabel.text = if (english) "Loading next video…" else "Memuat video berikutnya…"
                val id = currentVideo().youtubeId
                webView.evaluateJavascript("if(window.loadDailyShort){window.loadDailyShort('$id')}", null)
                contentRoot.translationY = -exitOffset
                contentRoot.animate()
                    .translationY(0f)
                    .setDuration(210)
                    .setInterpolator(DecelerateInterpolator(1.3f))
                    .start()
            }
            .start()
    }

    private fun settleBackToCenter() {
        contentRoot.animate().cancel()
        contentRoot.animate()
            .translationY(0f)
            .setDuration(190)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun renderCurrentVideo() {
        val video = currentVideo()
        KajianCatalogRepository(applicationContext).recordPlayback(video)
        val totalLabel = if (hasMoreCatalogVideos) "${videos.size}+" else videos.size.toString()
        positionLabel.text = if (english) "${currentIndex + 1} of $totalLabel" else "${currentIndex + 1} dari $totalLabel"
        categoryLabel.text = video.category
        titleLabel.text = video.title
        channelLabel.text = "${video.channelName} · ${video.category}"
        val isSaved = video.youtubeId in savedVideoIds
        saveLabel.text = if (english) { if (isSaved) "Saved" else "Save" } else { if (isSaved) "Tersimpan" else "Simpan" }
        saveIcon.setImageDrawable(BookmarkDrawable(if (isSaved) 0xFFE5C36B.toInt() else Color.WHITE, isSaved))
    }

    private fun toggleSaved() {
        savedVideoIds = KajianCatalogRepository(applicationContext).toggleSaved(currentVideo())
        renderCurrentVideo()
    }

    private fun handleVideoEnded() {
        val videoId = currentVideo().youtubeId
        if (handledEndedVideoId == videoId) return
        handledEndedVideoId = videoId
        when (KajianPlaybackPreferences(applicationContext).endBehavior()) {
            KajianEndBehavior.NEXT -> {
                if (currentIndex + 1 in videos.indices) {
                    updateStatus(if (english) "Video finished · playing next video" else "Video selesai · memutar video berikutnya")
                    contentRoot.post { animateToVideo(currentIndex + 1, moveForward = true) }
                } else if (hasMoreCatalogVideos) {
                    updateStatus(if (english) "Loading next video…" else "Memuat video berikutnya…")
                    loadNextShorts(advanceWhenReady = true)
                } else {
                    updateStatus(if (english) "Last video finished" else "Video terakhir selesai")
                    webView.post { webView.evaluateJavascript("if(window.pauseDailyShort){window.pauseDailyShort()}", null) }
                }
            }
            KajianEndBehavior.STOP -> {
                updateStatus(if (english) "Video finished" else "Video selesai")
                webView.post { webView.evaluateJavascript("if(window.pauseDailyShort){window.pauseDailyShort()}", null) }
            }
            KajianEndBehavior.REPLAY -> {
                updateStatus(if (english) "Replaying video" else "Memutar ulang video")
                webView.post { webView.evaluateJavascript("if(window.replayDailyShort){window.replayDailyShort()}", null) }
            }
        }
    }

    private fun loadNextShorts(advanceWhenReady: Boolean = false) {
        if (loadingCatalogPage || !hasMoreCatalogVideos) return
        loadingCatalogPage = true
        activityScope.launch {
            var nextShorts = emptyList<KajianVideo>()
            var failure: String? = null
            while (hasMoreCatalogVideos && nextShorts.isEmpty()) {
                val page = withContext(Dispatchers.IO) {
                    KajianCatalogRepository(applicationContext).loadNextPage(
                        channelName = catalogChannelName,
                        searchQuery = catalogSearchQuery,
                        offset = nextCatalogOffset,
                        feedSettings = catalogFeedSettings,
                    )
                }
                if (page.message != null) {
                    failure = page.message
                    break
                }
                nextCatalogOffset = page.nextOffset
                hasMoreCatalogVideos = page.hasMore
                val loadedIds = videos.mapTo(mutableSetOf()) { it.youtubeId }
                nextShorts = page.videos.filter {
                    it.contentType == KajianVideo.ContentType.SHORT && loadedIds.add(it.youtubeId)
                }
            }
            loadingCatalogPage = false
            if (nextShorts.isNotEmpty()) {
                videos = videos + nextShorts
                if (advanceWhenReady) animateToVideo(currentIndex + 1, moveForward = true)
            } else if (failure != null) {
                updateStatus(if (english) "Could not load the next videos. Swipe up to retry." else "Video berikutnya gagal dimuat. Geser ke atas untuk mencoba lagi.")
            } else if (advanceWhenReady) {
                updateStatus(if (english) "No more Shorts available" else "Tidak ada Shorts berikutnya")
                webView.post { webView.evaluateJavascript("if(window.pauseDailyShort){window.pauseDailyShort()}", null) }
            } else {
                updateStatus(if (english) "No more Shorts available" else "Tidak ada Shorts berikutnya")
            }
        }
    }

    private fun shareCurrentVideo() {
        val video = currentVideo()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://www.youtube.com/watch?v=${video.youtubeId}")
        }
        startActivity(Intent.createChooser(send, if (english) "Share video" else "Bagikan video"))
    }

    private fun currentVideo() = videos[currentIndex]

    private fun createAction(label: String, icon: String = "", bookmark: Boolean = false, onClick: () -> Unit): Triple<ImageView, TextView, View> {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val circle = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFF222222.toInt())
            }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (bookmark) setImageDrawable(BookmarkDrawable(Color.WHITE, false))
            else setImageDrawable(TextIconDrawable(icon, Color.WHITE))
            contentDescription = label
        }
        circle.addView(image, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        val text = textView(label, 10f, Color.WHITE).apply { gravity = Gravity.CENTER }
        column.addView(circle, LinearLayout.LayoutParams(dp(46), dp(46)))
        column.addView(text, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        return Triple(image, text, column)
    }

    private fun textView(text: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun updateStatus(message: String) {
        if (::statusLabel.isInitialized) statusLabel.post { statusLabel.text = message }
    }

    private fun hideFullscreen() {
        val view = fullscreenView ?: return
        root.removeView(view)
        root.addView(contentRoot, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        fullscreenView = null
        window.decorView.systemUiVisibility = priorSystemUiVisibility
        requestedOrientation = priorRequestedOrientation
        ViewCompat.requestApplyInsets(root)
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.onPause()
            webView.evaluateJavascript("if(window.player){player.pauseVideo();}", null)
        }
        super.onPause()
    }

    override fun onDestroy() {
        activityScope.cancel()
        if (fullscreenView != null) hideFullscreen()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface("DailySunnah")
            webView.destroy()
        }
        activePlayer = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class PlayerBridge {
        @JavascriptInterface fun onReady() = updateStatus(if (english) "Player ready · video will start automatically" else "Player siap · video akan diputar otomatis")
        @JavascriptInterface fun onState(state: Int) {
            if (state == 1) handledEndedVideoId = null
            updateStatus(when (state) {
                0 -> if (english) "Video finished" else "Video selesai"
                1 -> if (english) "Playing" else "Sedang diputar"
                2 -> if (english) "Paused" else "Dijeda"
                3 -> if (english) "Loading video…" else "Memuat video…"
                5 -> if (english) "Player ready" else "Player siap diputar"
                else -> if (english) "Connecting to YouTube…" else "Menghubungkan ke YouTube…"
            })
            if (state == 0) webView.post { handleVideoEnded() }
        }
        @JavascriptInterface fun onError(code: Int) {
            Log.e(SHORTS_LOG_TAG, "Short player error: $code")
            updateStatus(when (code) {
                2 -> if (english) "Invalid video ID" else "ID video tidak valid"
                5 -> if (english) "This device’s player does not support the video" else "Video tidak didukung oleh player perangkat ini"
                100 -> if (english) "Video is private, deleted, or unavailable" else "Video privat, dihapus, atau tidak tersedia"
                101, 150 -> if (english) "The owner disabled playback in other apps" else "Pemilik video menonaktifkan pemutaran di aplikasi lain"
                153 -> if (english) "YouTube could not verify the app" else "YouTube gagal memverifikasi aplikasi"
                else -> if (english) "Video playback failed (code $code)" else "Video gagal diputar (kode $code)"
            })
        }
        @JavascriptInterface fun onAutoplayBlocked() = updateStatus(if (english) "Tap the video’s play button" else "Ketuk tombol putar pada video")
    }

    private fun shortPlayerHtml(videoId: String): String = """
        <!doctype html>
        <html><head>
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
          <style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style>
        </head><body>
          <div id="player"></div>
          <script>
            var player;
            var dailyShortId = '$videoId';
            var dailyShortStarted = false;
            var rateResetToken = 0;
            function resetRateForCurrentShort() {
              var token = ++rateResetToken;
              var targetId = dailyShortId;
              [0, 100, 250, 500, 900].forEach(function(delay) {
                setTimeout(function() {
                  if (token !== rateResetToken || targetId !== dailyShortId || !player || !player.getPlaybackRate) return;
                  if (player.getPlaybackRate() !== 1) player.setPlaybackRate(1);
                }, delay);
              });
            }
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width: '100%', height: '100%', videoId: dailyShortId,
                playerVars: { autoplay: 1, controls: 1, enablejsapi: 1, playsinline: 1,
                  origin: '$SHORTS_APP_ORIGIN', hl: '${if (english) "en" else "id"}', rel: 0 },
                events: {
                  onReady: function() {
                    player.setPlaybackRate(1);
                    resetRateForCurrentShort();
                    DailySunnah.onReady();
                  },
                  onStateChange: function(event) {
                    var activeVideoId = player.getVideoData().video_id;
                    if (activeVideoId === dailyShortId && event.data === YT.PlayerState.CUED) {
                      player.setPlaybackRate(1);
                      resetRateForCurrentShort();
                      player.playVideo();
                    }
                    if (activeVideoId === dailyShortId && event.data === YT.PlayerState.PLAYING) {
                      if (player.getPlaybackRate() !== 1) player.setPlaybackRate(1);
                      dailyShortStarted = true;
                    }
                    DailySunnah.onState(event.data);
                  },
                  onPlaybackRateChange: function(event) {
                    if (!dailyShortStarted && player.getVideoData().video_id === dailyShortId && event.data !== 1) {
                      player.setPlaybackRate(1);
                    }
                  },
                  onError: function(event) { DailySunnah.onError(event.data); },
                  onAutoplayBlocked: function() { DailySunnah.onAutoplayBlocked(); }
                }
              });
            }
            function loadDailyShort(id) {
              dailyShortId = id;
              dailyShortStarted = false;
              if (player && player.cueVideoById) {
                player.pauseVideo();
                player.setPlaybackRate(1);
                player.cueVideoById({videoId: id, startSeconds: 0});
                resetRateForCurrentShort();
              }
            }
            function replayDailyShort() {
              if (player) { player.seekTo(0, true); player.playVideo(); }
            }
            function pauseDailyShort() {
              if (player) player.pauseVideo();
            }
          </script>
          <script src="https://www.youtube.com/iframe_api"></script>
        </body></html>
    """.trimIndent()

    companion object {
        const val EXTRA_IDS = "short_ids"
        const val EXTRA_TITLES = "short_titles"
        const val EXTRA_CHANNELS = "short_channels"
        const val EXTRA_CATEGORIES = "short_categories"
        const val EXTRA_DESCRIPTIONS = "short_descriptions"
        const val EXTRA_THUMBNAILS = "short_thumbnails"
        const val EXTRA_DURATIONS = "short_durations"
        const val EXTRA_PUBLISHED_AT = "short_published_at"
        const val EXTRA_INITIAL_INDEX = "short_initial_index"
        const val EXTRA_HAS_MORE = "short_has_more"
        const val EXTRA_NEXT_OFFSET = "short_next_offset"
        const val EXTRA_CHANNEL_NAME = "short_channel_name"
        const val EXTRA_SEARCH_QUERY = "short_search_query"
        const val EXTRA_SORT_ORDER = "short_sort_order"
        const val EXTRA_CONTENT_TYPES = "short_content_types"
        const val EXTRA_MIN_DURATION = "short_min_duration"
        const val EXTRA_MAX_DURATION = "short_max_duration"
        val VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")
        private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT

        fun newIntent(
            context: Context,
            videos: List<KajianVideo>,
            initialIndex: Int,
            catalogSnapshot: KajianCatalogSnapshot? = null,
            channelName: String? = null,
            searchQuery: String? = null,
        ): Intent =
            Intent(context, ShortsFeedActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_IDS, ArrayList(videos.map { it.youtubeId }))
                putStringArrayListExtra(EXTRA_TITLES, ArrayList(videos.map { it.title }))
                putStringArrayListExtra(EXTRA_CHANNELS, ArrayList(videos.map { it.channelName }))
                putStringArrayListExtra(EXTRA_CATEGORIES, ArrayList(videos.map { it.category }))
                putStringArrayListExtra(EXTRA_DESCRIPTIONS, ArrayList(videos.map { it.description }))
                putStringArrayListExtra(EXTRA_THUMBNAILS, ArrayList(videos.map { it.thumbnailUrl.orEmpty() }))
                putIntegerArrayListExtra(EXTRA_DURATIONS, ArrayList(videos.map { it.durationSeconds }))
                putStringArrayListExtra(EXTRA_PUBLISHED_AT, ArrayList(videos.map { it.publishedAt.orEmpty() }))
                putExtra(EXTRA_INITIAL_INDEX, initialIndex)
                catalogSnapshot?.let { snapshot ->
                    putExtra(EXTRA_HAS_MORE, snapshot.hasMore)
                    putExtra(EXTRA_NEXT_OFFSET, snapshot.nextOffset)
                    putExtra(EXTRA_SORT_ORDER, snapshot.feedSettings.sortOrder)
                    putStringArrayListExtra(EXTRA_CONTENT_TYPES, ArrayList(snapshot.feedSettings.contentTypes.map { it.apiValue }))
                    putExtra(EXTRA_MIN_DURATION, snapshot.feedSettings.minDurationSeconds ?: -1)
                    putExtra(EXTRA_MAX_DURATION, snapshot.feedSettings.maxDurationSeconds ?: -1)
                }
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_SEARCH_QUERY, searchQuery)
            }
    }
}

private class BookmarkDrawable(private val color: Int, private val filled: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = if (filled) Paint.Style.FILL else Paint.Style.STROKE; strokeWidth = 2f }
    override fun draw(canvas: Canvas) {
        paint.color = color
        val bounds = RectF(bounds).apply { inset(width() * 0.22f, height() * 0.12f) }
        val path = Path().apply {
            moveTo(bounds.left, bounds.top)
            lineTo(bounds.right, bounds.top)
            lineTo(bounds.right, bounds.bottom)
            lineTo(bounds.centerX(), bounds.bottom - bounds.height() * 0.2f)
            lineTo(bounds.left, bounds.bottom)
            close()
        }
        canvas.drawPath(path, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

private class TextIconDrawable(private val value: String, private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 28f; typeface = Typeface.DEFAULT }
    override fun draw(canvas: Canvas) {
        paint.color = color
        val bounds: Rect = bounds
        canvas.drawText(value, bounds.exactCenterX(), bounds.exactCenterY() - (paint.ascent() + paint.descent()) / 2f, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
