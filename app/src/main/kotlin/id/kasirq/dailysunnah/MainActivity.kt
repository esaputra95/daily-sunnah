package id.kasirq.dailysunnah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import id.kasirq.dailysunnah.home.HomeScreen
import id.kasirq.dailysunnah.kajian.data.KajianVideo
import id.kasirq.dailysunnah.kajian.ui.KajianPlayerScreen
import id.kasirq.dailysunnah.kajian.ui.KajianScreen
import id.kasirq.dailysunnah.quran.data.QuranBookmark
import id.kasirq.dailysunnah.quran.ui.QuranLibraryScreen
import id.kasirq.dailysunnah.quran.ui.QuranReaderScreen
import id.kasirq.dailysunnah.zakat.ZakatSavingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailySunnahTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var selectedName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
                    var readerStartPage by rememberSaveable { mutableIntStateOf(1) }
                    var readerStartVerse by rememberSaveable { mutableStateOf<String?>(null) }
                    var selectedKajianVideo by remember { mutableStateOf<KajianVideo?>(null) }
                    val selected = AppDestination.valueOf(selectedName)
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = selected,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                val direction = targetState.ordinal.compareTo(initialState.ordinal).let { if (it == 0) 1 else it }
                                (fadeIn(tween(190)) + slideInHorizontally(tween(220)) { direction * it / 10 }) togetherWith
                                    (fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { -direction * it / 10 })
                            },
                            label = "main-screen-transition",
                        ) { destination ->
                            when (destination) {
                                AppDestination.HOME -> HomeScreen(
                                    onOpenQuran = { selectedName = AppDestination.QURAN_LIBRARY.name },
                                    onContinueReading = { page ->
                                        readerStartPage = page
                                        readerStartVerse = null
                                        selectedName = AppDestination.QURAN_READER.name
                                    },
                                    onOpenZakat = { selectedName = AppDestination.ZAKAT.name },
                                )
                                AppDestination.QURAN_LIBRARY -> QuranLibraryScreen(
                                    onBack = { selectedName = AppDestination.HOME.name },
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
                                AppDestination.QURAN_READER -> QuranReaderScreen(
                                    startPage = readerStartPage,
                                    startVerseKey = readerStartVerse,
                                    onOpenLibrary = { selectedName = AppDestination.QURAN_LIBRARY.name },
                                )
                                AppDestination.KAJIAN -> KajianScreen(
                                    onOpenVideo = { video ->
                                        selectedKajianVideo = video
                                        selectedName = AppDestination.KAJIAN_PLAYER.name
                                    },
                                )
                                AppDestination.KAJIAN_PLAYER -> selectedKajianVideo?.let { video ->
                                    KajianPlayerScreen(video = video, onBack = { selectedName = AppDestination.KAJIAN.name })
                                } ?: KajianScreen(onOpenVideo = { video -> selectedKajianVideo = video })
                                AppDestination.ZAKAT -> ZakatSavingsScreen(
                                    onBack = { selectedName = AppDestination.HOME.name },
                                )
                            }
                        }
                        if (selected != AppDestination.QURAN_READER && selected != AppDestination.KAJIAN_PLAYER) {
                            NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFFFFFEFA)) {
                                listOf(AppDestination.HOME, AppDestination.QURAN_LIBRARY, AppDestination.KAJIAN, AppDestination.ZAKAT).forEach { destination ->
                                    NavigationBarItem(
                                        selected = selected == destination ||
                                            (destination == AppDestination.QURAN_LIBRARY && selected == AppDestination.QURAN_READER) ||
                                            (destination == AppDestination.KAJIAN && selected == AppDestination.KAJIAN_PLAYER),
                                        onClick = { selectedName = destination.name },
                                        icon = {
                                            Text(
                                                when (destination) {
                                                    AppDestination.HOME -> "⌂"
                                                    AppDestination.QURAN_LIBRARY -> "۞"
                                                    AppDestination.QURAN_READER -> "۞"
                                                    AppDestination.KAJIAN -> "▶"
                                                    AppDestination.KAJIAN_PLAYER -> "▶"
                                                    AppDestination.ZAKAT -> "2,5%"
                                                },
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        label = {
                                            Text(
                                                when (destination) {
                                                    AppDestination.HOME -> "Beranda"
                                                    AppDestination.QURAN_LIBRARY -> "Qur’an"
                                                    AppDestination.QURAN_READER -> "Qur’an"
                                                    AppDestination.KAJIAN -> "Kajian"
                                                    AppDestination.KAJIAN_PLAYER -> "Kajian"
                                                    AppDestination.ZAKAT -> "Zakat"
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AppDestination {
    HOME,
    QURAN_LIBRARY,
    QURAN_READER,
    KAJIAN,
    KAJIAN_PLAYER,
    ZAKAT,
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

