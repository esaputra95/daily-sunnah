package id.kasirq.dailysunnah

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.kasirq.dailysunnah.home.HomeScreen
import id.kasirq.dailysunnah.home.DailySunnahWidgetProvider
import id.kasirq.dailysunnah.ibadah.ui.IbadahScreen
import id.kasirq.dailysunnah.ibadah.ui.HijriCalendarScreen
import id.kasirq.dailysunnah.ibadah.ui.PrayerSettingsScreen
import id.kasirq.dailysunnah.ibadah.data.PrayerReminderScheduler
import id.kasirq.dailysunnah.ibadah.data.RamadanReminderScheduler
import id.kasirq.dailysunnah.kajian.ui.KajianScreen
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import id.kasirq.dailysunnah.kajian.ui.KajianPlayerActivity
import id.kasirq.dailysunnah.kajian.ui.ShortsFeedActivity
import id.kasirq.dailysunnah.other.OtherScreen
import id.kasirq.dailysunnah.other.AppSettingsStore
import id.kasirq.dailysunnah.other.AppThemePreference
import id.kasirq.dailysunnah.other.AppLanguagePreference
import id.kasirq.dailysunnah.quran.data.QuranBookmark
import id.kasirq.dailysunnah.quran.ui.QuranLibraryScreen
import id.kasirq.dailysunnah.quran.ui.QuranReaderScreen
import id.kasirq.dailysunnah.quran.ui.QuranSearchScreen
import id.kasirq.dailysunnah.quran.ui.QuranSettingsScreen
import id.kasirq.dailysunnah.ui.BottomTabIcon
import id.kasirq.dailysunnah.ui.BottomTabIconType
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import id.kasirq.dailysunnah.zakat.ZakatSavingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailySunnahWidgetProvider.updateAll(applicationContext)
        enableEdgeToEdge()
        PrayerReminderScheduler.scheduleNext(applicationContext)
        RamadanReminderScheduler.scheduleNext(applicationContext)
        setContent {
            val startupPermissionPreferences = remember {
                applicationContext.getSharedPreferences("startup_permissions", MODE_PRIVATE)
            }
            var startupPermissionPromptCompleted by rememberSaveable {
                mutableStateOf(startupPermissionPreferences.getBoolean("prompt_completed", false))
            }
            fun completeStartupPermissionPrompt() {
                startupPermissionPreferences.edit().putBoolean("prompt_completed", true).apply()
                startupPermissionPromptCompleted = true
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { completeStartupPermissionPrompt() }
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    completeStartupPermissionPrompt()
                }
            }
            LaunchedEffect(startupPermissionPromptCompleted) {
                if (!startupPermissionPromptCompleted) {
                    val hasFineLocation = ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    when {
                        !hasFineLocation -> locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                        needsNotificationPermission -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> completeStartupPermissionPrompt()
                    }
                }
            }
            val appSettingsStore = remember { AppSettingsStore(applicationContext) }
            var themePreference by remember { mutableStateOf(appSettingsStore.theme()) }
            var languagePreference by remember { mutableStateOf(appSettingsStore.language()) }
            DailySunnahTheme(themePreference) {
                CompositionLocalProvider(LocalAppEnglish provides (languagePreference == AppLanguagePreference.ENGLISH)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var selectedName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
                    var tabHistory by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
                    var readerStartPage by rememberSaveable { mutableIntStateOf(1) }
                    var readerStartVerse by rememberSaveable { mutableStateOf<String?>(null) }
                    var prayerSettingsReturn by rememberSaveable { mutableStateOf(AppDestination.IBADAH.name) }
                    var kajianStartContentType by rememberSaveable { mutableStateOf(KajianVideo.ContentType.KAJIAN.name) }
                    var quranLibraryTabIndex by rememberSaveable { mutableIntStateOf(0) }
                    val quranSurahListState = rememberLazyListState()
                    val quranJuzListState = rememberLazyListState()
                    val quranBookmarkListState = rememberLazyListState()
                    val selected = AppDestination.valueOf(selectedName)

                    fun openInTab(tab: AppDestination, screen: AppDestination = tab) {
                        val history = tabHistory.split('|')
                        if (history.lastOrNull() != tab.name) {
                            tabHistory = (history + tab.name).joinToString("|")
                        }
                        selectedName = screen.name
                    }

                    BackHandler(enabled = selected != AppDestination.HOME || '|' in tabHistory) {
                        when (selected) {
                            AppDestination.QURAN_READER,
                            AppDestination.QURAN_SEARCH,
                            AppDestination.QURAN_SETTINGS -> selectedName = AppDestination.QURAN_LIBRARY.name
                            AppDestination.PRAYER_SETTINGS -> selectedName = prayerSettingsReturn
                            AppDestination.HIJRI_CALENDAR -> selectedName = AppDestination.IBADAH.name
                            AppDestination.ZAKAT -> selectedName = AppDestination.MORE.name
                            AppDestination.KAJIAN_PLAYER -> selectedName = AppDestination.KAJIAN.name
                            else -> {
                                val history = tabHistory.split('|').filter(String::isNotBlank)
                                if (history.size > 1) {
                                    tabHistory = history.dropLast(1).joinToString("|")
                                    selectedName = history[history.lastIndex - 1]
                                } else if (selected != AppDestination.HOME) {
                                    tabHistory = AppDestination.HOME.name
                                    selectedName = AppDestination.HOME.name
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = selected,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val fromIndex = initialState.navigationIndex(prayerSettingsReturn)
                                val toIndex = targetState.navigationIndex(prayerSettingsReturn)
                                val direction = when {
                                    targetState == AppDestination.QURAN_READER || targetState == AppDestination.QURAN_SEARCH || targetState == AppDestination.PRAYER_SETTINGS -> 1
                                    initialState == AppDestination.QURAN_READER || initialState == AppDestination.QURAN_SEARCH || initialState == AppDestination.PRAYER_SETTINGS -> -1
                                    toIndex >= fromIndex -> 1
                                    else -> -1
                                }
                                (fadeIn(tween(125, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(tween(155, easing = FastOutSlowInEasing)) { width -> direction * width / 14 } +
                                    scaleIn(tween(150, easing = FastOutSlowInEasing), initialScale = 0.99f)) togetherWith
                                    (fadeOut(tween(95, easing = FastOutSlowInEasing)) +
                                        slideOutHorizontally(tween(125, easing = FastOutSlowInEasing)) { width -> -direction * width / 20 } +
                                        scaleOut(tween(115, easing = FastOutSlowInEasing), targetScale = 0.995f))
                            },
                            label = "main-screen-transition",
                        ) { destination ->
                            when (destination) {
                                AppDestination.HOME -> HomeScreen(
                                    onContinueReading = { page ->
                                        readerStartPage = page
                                        readerStartVerse = null
                                        openInTab(AppDestination.QURAN_LIBRARY, AppDestination.QURAN_READER)
                                    },
                                    onOpenIbadah = { openInTab(AppDestination.IBADAH) },
                                    onOpenAllKajian = {
                                        kajianStartContentType = KajianVideo.ContentType.KAJIAN.name
                                        openInTab(AppDestination.KAJIAN)
                                    },
                                    onOpenAllShorts = {
                                        kajianStartContentType = KajianVideo.ContentType.SHORT.name
                                        openInTab(AppDestination.KAJIAN)
                                    },
                                    onPlayKajian = { videos, index ->
                                        startActivity(KajianPlayerActivity.newIntent(this@MainActivity, videos, index))
                                    },
                                    onPlayShorts = { videos, index ->
                                        startActivity(ShortsFeedActivity.newIntent(this@MainActivity, videos, index))
                                    },
                                )
                                AppDestination.QURAN_LIBRARY -> QuranLibraryScreen(
                                    surahListState = quranSurahListState,
                                    juzListState = quranJuzListState,
                                    bookmarkListState = quranBookmarkListState,
                                    initialTabIndex = quranLibraryTabIndex,
                                    onTabSelected = { quranLibraryTabIndex = it },
                                    onOpenSettings = { selectedName = AppDestination.QURAN_SETTINGS.name },
                                    onOpenSearch = { selectedName = AppDestination.QURAN_SEARCH.name },
                                    onSelectPage = { page ->
                                        readerStartPage = page
                                        readerStartVerse = null
                                        selectedName = AppDestination.QURAN_READER.name
                                    },
                                    onOpenBookmark = { bookmark: QuranBookmark ->
                                        readerStartPage = bookmark.page
                                        readerStartVerse = bookmark.verseKey
                                        selectedName = AppDestination.QURAN_READER.name
                                    },
                                )
                                AppDestination.QURAN_SEARCH -> QuranSearchScreen(
                                    onBack = { selectedName = AppDestination.QURAN_LIBRARY.name },
                                    onOpenVerse = { result ->
                                        readerStartPage = result.page
                                        readerStartVerse = result.verseKey
                                        selectedName = AppDestination.QURAN_READER.name
                                    },
                                )
                                AppDestination.QURAN_READER -> QuranReaderScreen(
                                    startPage = readerStartPage,
                                    startVerseKey = readerStartVerse,
                                    onOpenLibrary = { selectedName = AppDestination.QURAN_LIBRARY.name },
                                )
                                AppDestination.QURAN_SETTINGS -> QuranSettingsScreen(
                                    onBack = { selectedName = AppDestination.QURAN_LIBRARY.name },
                                )
                                AppDestination.KAJIAN, AppDestination.KAJIAN_PLAYER -> KajianScreen(
                                    initialContentType = KajianVideo.ContentType.entries.firstOrNull { it.name == kajianStartContentType }
                                        ?: KajianVideo.ContentType.KAJIAN,
                                )
                                AppDestination.IBADAH -> IbadahScreen(
                                    onOpenSettings = {
                                        prayerSettingsReturn = AppDestination.IBADAH.name
                                        selectedName = AppDestination.PRAYER_SETTINGS.name
                                    },
                                    onOpenCalendar = { selectedName = AppDestination.HIJRI_CALENDAR.name },
                                    onOpenQuran = { openInTab(AppDestination.QURAN_LIBRARY, AppDestination.QURAN_SETTINGS) },
                                )
                                AppDestination.HIJRI_CALENDAR -> HijriCalendarScreen(
                                    onBack = { selectedName = AppDestination.IBADAH.name },
                                )
                                AppDestination.PRAYER_SETTINGS -> PrayerSettingsScreen(onBack = { selectedName = prayerSettingsReturn })
                                AppDestination.MORE -> OtherScreen(
                                    onOpenZakat = { selectedName = AppDestination.ZAKAT.name },
                                    themePreference = themePreference,
                                    onThemePreferenceChanged = { preference ->
                                        appSettingsStore.setTheme(preference)
                                        themePreference = preference
                                    },
                                    languagePreference = languagePreference,
                                    onLanguagePreferenceChanged = { preference ->
                                        appSettingsStore.setLanguage(preference)
                                        languagePreference = preference
                                        DailySunnahWidgetProvider.updateAll(applicationContext)
                                    },
                                )
                                AppDestination.ZAKAT -> ZakatSavingsScreen(
                                    onBack = { selectedName = AppDestination.MORE.name },
                                )
                            }
                        }
                        if (selected != AppDestination.QURAN_READER && selected != AppDestination.QURAN_SETTINGS && selected != AppDestination.QURAN_SEARCH && selected != AppDestination.PRAYER_SETTINGS && selected != AppDestination.HIJRI_CALENDAR) {
                            DailyBottomBar(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                selectedDestination = selected,
                                english = languagePreference == AppLanguagePreference.ENGLISH,
                                onSelect = { openInTab(it) },
                            )
                        }
                    }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DailySunnahWidgetProvider.updateAll(applicationContext)
        RamadanReminderScheduler.scheduleNext(applicationContext)
    }
}

@Composable
private fun DailyBottomBar(
    modifier: Modifier = Modifier,
    selectedDestination: AppDestination,
    english: Boolean,
    onSelect: (AppDestination) -> Unit,
) {
    val destinations = listOf(
        AppDestination.HOME,
        AppDestination.QURAN_LIBRARY,
        AppDestination.IBADAH,
        AppDestination.KAJIAN,
        AppDestination.MORE,
    )
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val isSelected = destination.isSelectedFor(selectedDestination)
                val itemWeight by animateFloatAsState(
                    targetValue = if (isSelected) 2f else 1f,
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 560f),
                    label = "bottom-tab-weight-${destination.name}",
                )
                val itemColor by animateColorAsState(
                    targetValue = if (isSelected) primary else Color.Transparent,
                    animationSpec = tween(durationMillis = 140),
                    label = "bottom-tab-color-${destination.name}",
                )
                val iconType = when (destination) {
                    AppDestination.HOME -> BottomTabIconType.HOME
                    AppDestination.QURAN_LIBRARY -> BottomTabIconType.QURAN
                    AppDestination.IBADAH -> BottomTabIconType.IBADAH
                    AppDestination.KAJIAN -> BottomTabIconType.KAJIAN
                    AppDestination.MORE -> BottomTabIconType.MORE
                    else -> BottomTabIconType.HOME
                }

                Row(
                    modifier = Modifier
                        .weight(itemWeight)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .clip(CircleShape)
                        .background(itemColor)
                        .clickable(role = Role.Tab, onClick = { onSelect(destination) })
                        .semantics { contentDescription = destination.tabLabel(english) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    BottomTabIcon(type = iconType, selected = isSelected)
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn(tween(110)) + expandHorizontally(tween(150)),
                        exit = fadeOut(tween(80)) + shrinkHorizontally(tween(120)),
                    ) {
                        Text(
                            text = destination.tabLabel(english),
                            modifier = Modifier.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

private fun AppDestination.isSelectedFor(selected: AppDestination): Boolean =
    this == selected ||
        (this == AppDestination.QURAN_LIBRARY && selected in setOf(AppDestination.QURAN_READER, AppDestination.QURAN_SEARCH)) ||
        (this == AppDestination.KAJIAN && selected == AppDestination.KAJIAN_PLAYER) ||
        (this == AppDestination.MORE && selected == AppDestination.ZAKAT)

private fun AppDestination.tabLabel(english: Boolean): String = when (this) {
    AppDestination.HOME -> if (english) "Home" else "Beranda"
    AppDestination.QURAN_LIBRARY, AppDestination.QURAN_READER, AppDestination.QURAN_SEARCH -> "Qur’an"
    AppDestination.IBADAH -> if (english) "Worship" else "Ibadah"
    AppDestination.KAJIAN, AppDestination.KAJIAN_PLAYER -> if (english) "Talks" else "Kajian"
    AppDestination.MORE, AppDestination.ZAKAT -> if (english) "More" else "Lainnya"
    AppDestination.QURAN_SETTINGS, AppDestination.PRAYER_SETTINGS -> if (english) "Settings" else "Pengaturan"
    AppDestination.HIJRI_CALENDAR -> if (english) "Calendar" else "Kalender"
}

private enum class AppDestination {
    HOME,
    QURAN_LIBRARY,
    QURAN_READER,
    QURAN_SETTINGS,
    QURAN_SEARCH,
    KAJIAN,
    KAJIAN_PLAYER,
    IBADAH,
    PRAYER_SETTINGS,
    HIJRI_CALENDAR,
    MORE,
    ZAKAT,
}

private fun AppDestination.navigationIndex(prayerSettingsReturn: String): Int = when (this) {
    AppDestination.HOME -> 0
    AppDestination.QURAN_LIBRARY, AppDestination.QURAN_READER, AppDestination.QURAN_SETTINGS, AppDestination.QURAN_SEARCH -> 1
    AppDestination.IBADAH, AppDestination.HIJRI_CALENDAR -> 2
    AppDestination.KAJIAN, AppDestination.KAJIAN_PLAYER -> 3
    AppDestination.MORE, AppDestination.ZAKAT -> 4
    AppDestination.PRAYER_SETTINGS -> if (prayerSettingsReturn == AppDestination.HOME.name) 0 else 2
}

@Composable
private fun DailySunnahTheme(preference: AppThemePreference, content: @Composable () -> Unit) {
    val useDarkColors = when (preference) {
        AppThemePreference.SYSTEM -> isSystemInDarkTheme()
        AppThemePreference.LIGHT -> false
        AppThemePreference.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkColors) darkColorScheme(
            primary = Color(0xFF4F8589),
            onPrimary = Color(0xFFE1E9E7),
            primaryContainer = Color(0xFF2C4548),
            onPrimaryContainer = Color(0xFFD1DFDD),
            secondary = Color(0xFFC6A46B),
            onSecondary = Color(0xFF292318),
            tertiary = Color(0xFF6F9F88),
            background = Color(0xFF171B1C),
            onBackground = Color(0xFFD6DCDA),
            surface = Color(0xFF202627),
            onSurface = Color(0xFFD6DCDA),
            surfaceVariant = Color(0xFF2A3233),
            onSurfaceVariant = Color(0xFFADB7B5),
            outline = Color(0xFF5C6968),
        ) else lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF0E7490),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFCDECF0),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF003B46),
            secondary = androidx.compose.ui.graphics.Color(0xFFD97706),
            onSecondary = androidx.compose.ui.graphics.Color.White,
            tertiary = androidx.compose.ui.graphics.Color(0xFF047857),
            background = androidx.compose.ui.graphics.Color(0xFFF7F3EA),
            onBackground = androidx.compose.ui.graphics.Color(0xFF202A2C),
            surface = androidx.compose.ui.graphics.Color(0xFFFFFEFA),
            onSurface = androidx.compose.ui.graphics.Color(0xFF202A2C),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF526467),
            outline = androidx.compose.ui.graphics.Color(0xFFB7C7C8),
        ),
        content = content,
    )
}

