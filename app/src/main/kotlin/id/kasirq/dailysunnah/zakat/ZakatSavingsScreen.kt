package id.kasirq.dailysunnah.zakat

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import id.kasirq.dailysunnah.ui.ModernTextFieldShape
import id.kasirq.dailysunnah.ui.modernTextFieldColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.text.style.TextDecoration
import id.kasirq.dailysunnah.ui.BackNavigationButton
import id.kasirq.dailysunnah.ui.LocalAppEnglish

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatSavingsScreen(onBack: () -> Unit) {
    val english = LocalAppEnglish.current
    var selectedCategory by rememberSaveable { mutableIntStateOf(0) }
    var savingsText by rememberSaveable { mutableStateOf("") }
    var goldPriceText by rememberSaveable { mutableStateOf("") }
    var priceDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var priceSource by rememberSaveable { mutableStateOf("") }
    var haulCompleted by rememberSaveable { mutableStateOf(false) }
    var ownershipConfirmed by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<ZakatSavingsResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSavedResult by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<ZakatSavingsRecord>>(emptyList()) }
    var additionalHistory by remember { mutableStateOf<List<ZakatAdditionalRecord>>(emptyList()) }
    val additionalCategoryState = rememberSaveableStateHolder()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val historyStore = remember { ZakatSavingsHistoryStore(context.applicationContext) }
    val additionalHistoryStore = remember { ZakatAdditionalHistoryStore(context.applicationContext) }
    val currency = remember {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }
    LaunchedEffect(Unit) {
        history = historyStore.all()
        additionalHistory = additionalHistoryStore.all()
    }

    fun clearResult() {
        result = null
        hasSavedResult = false
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (english) "Zakat calculator" else "Kalkulator zakat") },
                    navigationIcon = { BackNavigationButton(onClick = onBack) },
                    actions = {
                        ZakatHistoryButton(
                            count = history.size + additionalHistory.size,
                            onClick = { showHistory = true },
                        )
                    },
                )
                ScrollableTabRow(selectedTabIndex = selectedCategory) {
                    (if (english) listOf("Savings", "Income", "Gold/Silver", "Trade", "Agriculture", "Fitr") else listOf("Tabungan", "Penghasilan", "Emas/Perak", "Perdagangan", "Pertanian", "Fitrah")).forEachIndexed { index, label ->
                        Tab(selected = selectedCategory == index, onClick = { selectedCategory = index }, text = { Text(label) })
                    }
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedCategory != 0) {
                additionalCategoryState.SaveableStateProvider(selectedCategory) {
                    ZakatAdditionalCategoryScreen(selectedCategory) { category, status, summary ->
                        additionalHistory = additionalHistoryStore.save(category, status, summary)
                    }
                }
            } else {
            Text(
                if (english) "Calculate using your total savings on the zakat date. Enter the gold price manually so results do not depend on an external pricing service."
                else "Hitung dari total tabungan yang kamu miliki saat tanggal zakat. Harga emas dimasukkan manual agar hasil tidak bergantung pada layanan harga eksternal.",
                style = MaterialTheme.typography.bodyMedium,
            )
            MoneyInput(if (english) "Total savings (IDR)" else "Total tabungan (Rp)", savingsText) { savingsText = it; clearResult() }
            MoneyInput(if (english) "24-karat gold price per gram (IDR)" else "Harga emas 24 karat per gram (Rp)", goldPriceText) { goldPriceText = it; clearResult() }
            OutlinedTextField(
                value = priceDate,
                onValueChange = { priceDate = it; clearResult() },
                shape = ModernTextFieldShape,
                colors = modernTextFieldColors(),
                label = { Text(if (english) "Gold price date" else "Tanggal harga emas") },
                supportingText = { Text(if (english) "Format: YYYY-MM-DD" else "Format YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = priceSource,
                onValueChange = { priceSource = it; clearResult() },
                shape = ModernTextFieldShape,
                colors = modernTextFieldColors(),
                label = { Text(if (english) "Gold price source" else "Sumber harga emas") },
                placeholder = { Text(if (english) "Example: jeweler or gold provider quote" else "Contoh: harga jual toko atau penyedia emas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ConfirmationRow(
                label = if (english) "I fully own these savings and their source is permissible" else "Saya telah memiliki tabungan ini secara penuh dan sumbernya halal",
                checked = ownershipConfirmed,
                onCheckedChange = { ownershipConfirmed = it; clearResult() },
            )
            ConfirmationRow(
                label = if (english) "These savings have been held for one Hijri year (haul)" else "Tabungan telah dimiliki selama satu tahun Hijriah (haul)",
                checked = haulCompleted,
                onCheckedChange = { haulCompleted = it; clearResult() },
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val savings = savingsText.toBigDecimalOrNull()
                    val goldPrice = goldPriceText.toBigDecimalOrNull()
                    val validDate = runCatching { LocalDate.parse(priceDate) }.isSuccess
                    if (savings == null || savings.signum() < 0 || goldPrice == null || goldPrice.signum() <= 0) {
                        error = if (english) "Enter valid savings and gold price amounts." else "Masukkan nominal tabungan dan harga emas yang valid."
                        result = null
                    } else if (!validDate || priceSource.isBlank()) {
                        error = if (english) "Enter the gold price date and source." else "Lengkapi tanggal dan sumber harga emas."
                        result = null
                    } else {
                        error = null
                        result = ZakatSavingsCalculator.calculate(savings, goldPrice, haulCompleted, ownershipConfirmed)
                        hasSavedResult = false
                    }
                },
            ) { Text(if (english) "Calculate zakat" else "Hitung zakat") }

            result?.let { calculation ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(statusLabel(calculation.status, english), style = MaterialTheme.typography.titleMedium)
                    Text(if (english) "Nisab: 85 grams × ${currency.format(calculation.goldPricePerGramRupiah)} = ${currency.format(calculation.nisabRupiah)}" else "Nisab: 85 gram × ${currency.format(calculation.goldPricePerGramRupiah)} = ${currency.format(calculation.nisabRupiah)}")
                    Text(if (english) "Price reference: $priceSource · $priceDate" else "Acuan harga: $priceSource · $priceDate")
                    Text(if (english) "Savings: ${currency.format(calculation.savingsRupiah)}" else "Tabungan: ${currency.format(calculation.savingsRupiah)}")
                    if (calculation.status == ZakatSavingsStatus.DUE) {
                        Text(if (english) "Zakat: 2.5% × savings = ${currency.format(calculation.dueRupiah)}" else "Zakat: 2,5% × tabungan = ${currency.format(calculation.dueRupiah)}", style = MaterialTheme.typography.titleLarge)
                        Text(if (english) "Result is rounded to the nearest rupiah." else "Hasil dibulatkan ke rupiah terdekat.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                val shareText = buildShareText(calculation, savingsText, goldPriceText, priceSource, priceDate, english)
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText),
                                        if (english) "Share zakat summary" else "Bagikan ringkasan zakat",
                                    ),
                                )
                            },
                        ) { Text(if (english) "Share" else "Bagikan") }
                        Button(
                            enabled = !hasSavedResult,
                            onClick = {
                                val savings = savingsText.toBigDecimalOrNull() ?: return@Button
                                val goldPrice = goldPriceText.toBigDecimalOrNull() ?: return@Button
                                val record = ZakatSavingsRecord.from(
                                    savings, goldPrice, priceDate, priceSource, haulCompleted, ownershipConfirmed, calculation,
                                )
                                history = historyStore.save(record)
                                hasSavedResult = true
                            },
                        ) { Text(if (english) { if (hasSavedResult) "Saved" else "Save locally" } else { if (hasSavedResult) "Tersimpan" else "Simpan lokal" }) }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(if (english) "Rule profile: ${ZakatSavingsCalculator.PROFILE_ID}" else "Profil aturan: ${ZakatSavingsCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
            Text(if (english) "This profile covers simple savings: nisab equal to 85 grams of gold, a 2.5% rate, and the haul requirement. Combining assets, debts/deductions, or special cases requires separate review." else "Profil ini mencakup tabungan sederhana: nisab setara 85 gram emas, kadar 2,5%, dan syarat haul. Penggabungan aset, utang/pengurang, dan kondisi khusus memerlukan telaah terpisah.", style = MaterialTheme.typography.bodySmall)
            Text(
                if (english) "BAZNAS reference: How to Calculate Zakat on Savings" else "Rujukan BAZNAS: Cara Menghitung Zakat Tabungan dengan Benar",
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(BAZNAS_SAVINGS_URL) } },
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(if (english) "History is saved only after you tap Save locally; data stays on this device and is not sent to a server." else "Riwayat tersimpan hanya setelah kamu menekan Simpan lokal; data tetap di perangkat dan tidak dikirim ke server.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showHistory) {
        ZakatHistoryDialog(
            records = history,
            additionalRecords = additionalHistory,
            onDismiss = { showHistory = false },
            onDelete = { id -> history = historyStore.delete(id) },
            onDeleteAdditional = { id -> additionalHistory = additionalHistoryStore.delete(id) },
        )
    }
}

@Composable
private fun ZakatHistoryButton(count: Int, onClick: () -> Unit) {
    val iconColor = MaterialTheme.colorScheme.primary
    val badgeColor = MaterialTheme.colorScheme.primary
    val badgeTextColor = MaterialTheme.colorScheme.onPrimary
    Box(Modifier.padding(end = 8.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Riwayat zakat, $count tersimpan" },
        ) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val iconSize = size.minDimension
                drawArc(
                    color = iconColor,
                    startAngle = 48f,
                    sweepAngle = 292f,
                    useCenter = false,
                    topLeft = Offset(iconSize * 0.16f, iconSize * 0.16f),
                    size = Size(iconSize * 0.68f, iconSize * 0.68f),
                    style = stroke,
                )
                drawLine(iconColor, Offset(iconSize * 0.50f, iconSize * 0.50f), Offset(iconSize * 0.50f, iconSize * 0.31f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(iconColor, Offset(iconSize * 0.50f, iconSize * 0.50f), Offset(iconSize * 0.66f, iconSize * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(iconColor, Offset(iconSize * 0.76f, iconSize * 0.13f), Offset(iconSize * 0.88f, iconSize * 0.15f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(iconColor, Offset(iconSize * 0.88f, iconSize * 0.15f), Offset(iconSize * 0.85f, iconSize * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
        if (count > 0) {
            Surface(
                color = badgeColor,
                contentColor = badgeTextColor,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-1).dp, y = 2.dp)
                    .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                    .clip(CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(count.coerceAtMost(99).toString(), fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private const val BAZNAS_SAVINGS_URL = "https://baznas.go.id/artikel-show/Cara-Menghitung-Zakat-Tabungan-dengan-Benar/2498"

@Composable
private fun ZakatHistoryDialog(
    records: List<ZakatSavingsRecord>,
    additionalRecords: List<ZakatAdditionalRecord>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAdditional: (String) -> Unit,
) {
    val english = LocalAppEnglish.current
    val currency = remember {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Zakat history" else "Riwayat zakat") },
        text = {
            if (records.isEmpty() && additionalRecords.isEmpty()) {
                Text(if (english) "No saved results yet." else "Belum ada hasil yang disimpan.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (records.isNotEmpty()) {
                        item(key = "savings-history-title") { Text(if (english) "Savings" else "Tabungan", style = MaterialTheme.typography.titleMedium) }
                        items(records, key = { "savings-${it.id}" }) { record ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).format(dateFormatter),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text("${record.profileId} · ${statusLabel(record.status, english)} · ${if (english) "Balance" else "Saldo"} ${currency.format(record.savingsRupiah)}")
                                Text("Nisab ${currency.format(record.nisabRupiah)} · Zakat ${currency.format(record.dueRupiah)}")
                                Text("${if (english) "Price" else "Harga"} ${currency.format(record.goldPricePerGramRupiah)}/g · ${record.priceSource} · ${record.priceDate}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { onDelete(record.id) }) { Text(if (english) "Delete" else "Hapus") }
                            }
                        }
                    }
                    if (additionalRecords.isNotEmpty()) {
                        item(key = "additional-history-title") { Text(if (english) "Other categories" else "Kategori lainnya", style = MaterialTheme.typography.titleMedium) }
                        items(additionalRecords, key = { "additional-${it.id}" }) { record ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    "${record.category} · ${Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).format(dateFormatter)}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(record.status, fontWeight = FontWeight.SemiBold)
                                Text(record.summary)
                                TextButton(onClick = { onDeleteAdditional(record.id) }) { Text(if (english) "Delete" else "Hapus") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (english) "Close" else "Tutup") } },
    )
}

private fun buildShareText(
    result: ZakatSavingsResult,
    savings: String,
    goldPrice: String,
    priceSource: String,
    priceDate: String,
    english: Boolean,
): String = buildString {
    appendLine(if (english) "Savings zakat summary" else "Ringkasan zakat tabungan")
    appendLine("${if (english) "Calculation profile" else "Profil aturan"}: ${ZakatSavingsCalculator.PROFILE_ID}")
    appendLine("${if (english) "Status" else "Status"}: ${statusLabel(result.status, english)}")
    appendLine("${if (english) "Savings" else "Tabungan"}: Rp$savings")
    appendLine("Nisab: 85 ${if (english) "grams" else "gram"} × Rp$goldPrice = Rp${result.nisabRupiah.toPlainString()}")
    appendLine("${if (english) "Zakat due" else "Zakat"}: Rp${result.dueRupiah.toPlainString()}")
    appendLine("${if (english) "Gold price source" else "Sumber harga"}: $priceSource · $priceDate")
}

@Composable
private fun MoneyInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(16)) },
        shape = ModernTextFieldShape,
        colors = modernTextFieldColors(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConfirmationRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun statusLabel(status: ZakatSavingsStatus, english: Boolean = false): String = if (english) when (status) {
    ZakatSavingsStatus.INCOMPLETE -> "Inputs incomplete"
    ZakatSavingsStatus.OWNERSHIP_UNCONFIRMED -> "Confirm ownership and permissible source to continue"
    ZakatSavingsStatus.HAUL_NOT_MET -> "One Hijri year (haul) has not been confirmed"
    ZakatSavingsStatus.BELOW_NISAB -> "Savings are below nisab"
    ZakatSavingsStatus.DUE -> "Savings meet nisab and haul requirements"
} else when (status) {
    ZakatSavingsStatus.INCOMPLETE -> "Input belum lengkap"
    ZakatSavingsStatus.OWNERSHIP_UNCONFIRMED -> "Konfirmasi kepemilikan dan sumber tabungan untuk melanjutkan"
    ZakatSavingsStatus.HAUL_NOT_MET -> "Haul satu tahun Hijriah belum dikonfirmasi"
    ZakatSavingsStatus.BELOW_NISAB -> "Tabungan belum mencapai nisab"
    ZakatSavingsStatus.DUE -> "Tabungan memenuhi nisab dan haul"
}
