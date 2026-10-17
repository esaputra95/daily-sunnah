package id.kasirq.dailysunnah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import id.kasirq.dailysunnah.home.HomeScreen
import id.kasirq.dailysunnah.ibadah.ui.IbadahScreen
import id.kasirq.dailysunnah.ibadah.ui.PrayerSettingsScreen
import id.kasirq.dailysunnah.ibadah.data.PrayerReminderScheduler
import id.kasirq.dailysunnah.kajian.ui.KajianScreen
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import id.kasirq.dailysunnah.kajian.ui.KajianPlayerActivity
import id.kasirq.dailysunnah.kajian.ui.ShortsFeedActivity
import id.kasirq.dailysunnah.quran.data.QuranBookmark
import id.kasirq.dailysunnah.quran.ui.QuranLibraryScreen
import id.kasirq.dailysunnah.quran.ui.QuranReaderScreen
import id.kasirq.dailysunnah.quran.ui.QuranSearchScreen
import id.kasirq.dailysunnah.quran.ui.QuranSettingsScreen
import id.kasirq.dailysunnah.ui.BottomTabIcon
import id.kasirq.dailysunnah.ui.BottomTabIconType
import id.kasirq.dailysunnah.zakat.ZakatSavingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PrayerReminderScheduler.scheduleNext(applicationContext)
        setContent {
            DailySunnahTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var selectedName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
                    var readerStartPage by rememberSaveable { mutableIntStateOf(1) }
                    var readerStartVerse by rememberSaveable { mutableStateOf<String?>(null) }
                    var prayerSettingsReturn by rememberSaveable { mutableStateOf(AppDestination.IBADAH.name) }
                    var kajianStartContentType by rememberSaveable { mutableStateOf(KajianVideo.ContentType.KAJIAN.name) }
                    val selected = AppDestination.valueOf(selectedName)
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = selected,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                val fromIndex = initialState.navigationIndex(prayerSettingsReturn)
                                val toIndex = targetState.navigationIndex(prayerSettingsReturn)
                                val direction = when {
                                    targetState == AppDestination.QURAN_READER || targetState == AppDestination.QURAN_SEARCH || targetState == AppDestination.PRAYER_SETTINGS -> 1
                                    initialState == AppDestination.QURAN_READER || initialState == AppDestination.QURAN_SEARCH || initialState == AppDestination.PRAYER_SETTINGS -> -1
                                    toIndex >= fromIndex -> 1
                                    else -> -1
                                }
                                (fadeIn(tween(145)) + slideInHorizontally(tween(175)) { width -> direction * width / 12 }) togetherWith
                                    (fadeOut(tween(105)) + slideOutHorizontally(tween(145)) { width -> -direction * width / 16 })
                            },
                            label = "main-screen-transition",
                        ) { destination ->
                            when (destination) {
                                AppDestination.HOME -> HomeScreen(
                                    onContinueReading = { page ->
                                        readerStartPage = page
                                        readerStartVerse = null
                                        selectedName = AppDestination.QURAN_READER.name
                                    },
                                    onOpenIbadah = { selectedName = AppDestination.IBADAH.name },
                                    onOpenPrayerSettings = {
                                        prayerSettingsReturn = AppDestination.HOME.name
                                        selectedName = AppDestination.PRAYER_SETTINGS.name
                                    },
                                    onOpenAllKajian = {
                                        kajianStartContentType = KajianVideo.ContentType.KAJIAN.name
                                        selectedName = AppDestination.KAJIAN.name
                                    },
                                    onOpenAllShorts = {
                                        kajianStartContentType = KajianVideo.ContentType.SHORT.name
                                        selectedName = AppDestination.KAJIAN.name
                                    },
                                    onPlayKajian = { videos, index ->
                                        startActivity(KajianPlayerActivity.newIntent(this@MainActivity, videos, index))
                                    },
                                    onPlayShorts = { videos, index ->
                                        startActivity(ShortsFeedActivity.newIntent(this@MainActivity, videos, index))
                                    },
                                )
                                AppDestination.QURAN_LIBRARY -> QuranLibraryScreen(
                                    onBack = { selectedName = AppDestination.HOME.name },
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
                                AppDestination.IBADAH -> IbadahScreen(onOpenSettings = {
                                    prayerSettingsReturn = AppDestination.IBADAH.name
                                    selectedName = AppDestination.PRAYER_SETTINGS.name
                                })
                                AppDestination.PRAYER_SETTINGS -> PrayerSettingsScreen(onBack = { selectedName = prayerSettingsReturn })
                                AppDestination.ZAKAT -> ZakatSavingsScreen(
                                    onBack = { selectedName = AppDestination.HOME.name },
                                )
                            }
                        }
                        if (selected != AppDestination.QURAN_READER && selected != AppDestination.QURAN_SETTINGS && selected != AppDestination.QURAN_SEARCH && selected != AppDestination.PRAYER_SETTINGS) {
                            DailyBottomBar(
                                selectedDestination = selected,
                                onSelect = { selectedName = it.name },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyBottomBar(
    selectedDestination: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    val destinations = listOf(
        AppDestination.HOME,
        AppDestination.QURAN_LIBRARY,
        AppDestination.IBADAH,
        AppDestination.KAJIAN,
        AppDestination.ZAKAT,
    )
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(50),
        color = Color(0xFFE7F0F2),
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
                    AppDestination.ZAKAT -> BottomTabIconType.ZAKAT
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
                        .semantics { contentDescription = destination.tabLabel() },
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
                            text = destination.tabLabel(),
                            modifier = Modifier.padding(end = 8.dp),
                            color = Color.White,
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
        (this == AppDestination.KAJIAN && selected == AppDestination.KAJIAN_PLAYER)

private fun AppDestination.tabLabel(): String = when (this) {
    AppDestination.HOME -> "Beranda"
    AppDestination.QURAN_LIBRARY, AppDestination.QURAN_READER, AppDestination.QURAN_SEARCH -> "Qur’an"
    AppDestination.IBADAH -> "Ibadah"
    AppDestination.KAJIAN, AppDestination.KAJIAN_PLAYER -> "Kajian"
    AppDestination.ZAKAT -> "Zakat"
    AppDestination.QURAN_SETTINGS, AppDestination.PRAYER_SETTINGS -> "Pengaturan"
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
    ZAKAT,
}

private fun AppDestination.navigationIndex(prayerSettingsReturn: String): Int = when (this) {
    AppDestination.HOME -> 0
    AppDestination.QURAN_LIBRARY, AppDestination.QURAN_READER, AppDestination.QURAN_SETTINGS, AppDestination.QURAN_SEARCH -> 1
    AppDestination.IBADAH -> 2
    AppDestination.KAJIAN, AppDestination.KAJIAN_PLAYER -> 3
    AppDestination.ZAKAT -> 4
    AppDestination.PRAYER_SETTINGS -> if (prayerSettingsReturn == AppDestination.HOME.name) 0 else 2
}

@Composable
private fun DailySunnahTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
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

