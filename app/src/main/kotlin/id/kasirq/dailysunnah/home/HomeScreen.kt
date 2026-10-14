package id.kasirq.dailysunnah.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import id.kasirq.dailysunnah.quran.data.QuranReaderPreferences
import id.kasirq.dailysunnah.quran.data.ReadingGoalSnapshot
import id.kasirq.dailysunnah.quran.data.ReadingGoalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenQuran: () -> Unit,
    onContinueReading: (Int) -> Unit,
    onOpenZakat: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val readerPreferences = remember { QuranReaderPreferences(context) }
    val goalStore = remember { ReadingGoalStore(context) }
    var lastReadPage by remember { mutableStateOf(1) }
    var readingGoal by remember {
        mutableStateOf(ReadingGoalSnapshot(ReadingGoalStore.DEFAULT_TARGET_PAGES, emptyList(), emptySet()))
    }
    val today = remember { LocalDate.now() }
    val dateLabel = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))
    }

    LaunchedEffect(Unit) {
        val (page, goal) = withContext(Dispatchers.IO) {
            readerPreferences.lastReadPage() to goalStore.load()
        }
        lastReadPage = page
        readingGoal = goal
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Daily Sunnah", fontWeight = FontWeight.SemiBold) }) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Assalamu’alaikum", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(dateLabel.replaceFirstChar { it.uppercase(Locale("id", "ID")) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("LANJUTKAN BACAAN", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f), letterSpacing = 1.2.sp)
                    Text("Mushaf Madinah", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Halaman $lastReadPage dari 604", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Button(onClick = { onContinueReading(lastReadPage) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFEFA), contentColor = MaterialTheme.colorScheme.primary)) {
                        Text("Lanjut membaca  ›", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFFFEFA),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Target tilawah hari ini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${readingGoal.completedCount} dari ${readingGoal.targetPages} halaman", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(readingGoal.completedCount * 100 / readingGoal.targetPages.coerceAtLeast(1))}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { (readingGoal.completedCount.toFloat() / readingGoal.targetPages.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (readingGoal.isComplete) "Target hari ini tercapai. Alhamdulillah."
                        else "Pengingat aktif: ${readingGoal.reminderMinutes.size} waktu pilihan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("Akses cepat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeActionCard(
                    modifier = Modifier.weight(1f),
                    icon = "▤",
                    title = "Baca Qur’an",
                    subtitle = "Mushaf 15 baris",
                    onClick = onOpenQuran,
                )
                HomeActionCard(
                    modifier = Modifier.weight(1f),
                    icon = "2,5%",
                    title = "Hitung zakat",
                    subtitle = "Zakat tabungan",
                    onClick = onOpenZakat,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Column {
                        Text("Tersimpan di perangkat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Halaman mushaf yang pernah dibuka disiapkan agar lebih cepat dibaca lagi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeActionCard(
    modifier: Modifier,
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFEFA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
