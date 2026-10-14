package id.kasirq.dailysunnah.zakat

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.text.style.TextDecoration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatSavingsScreen(onBack: () -> Unit) {
    var savingsText by rememberSaveable { mutableStateOf("") }
    var goldPriceText by rememberSaveable { mutableStateOf("") }
    var priceDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var priceSource by rememberSaveable { mutableStateOf("") }
    var haulCompleted by rememberSaveable { mutableStateOf(false) }
    var ownershipConfirmed by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<ZakatSavingsResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSavedResult by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<ZakatSavingsRecord>>(emptyList()) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val historyStore = remember { ZakatSavingsHistoryStore(context.applicationContext) }
    val currency = remember {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }
    LaunchedEffect(Unit) { history = historyStore.all() }

    fun clearResult() {
        result = null
        hasSavedResult = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zakat tabungan") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Kembali") } },
                actions = { TextButton(onClick = { showHistory = true }) { Text("Riwayat ${history.size}") } },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Hitung dari total tabungan yang kamu miliki saat tanggal zakat. Harga emas dimasukkan manual agar hasil tidak bergantung pada layanan harga eksternal.",
                style = MaterialTheme.typography.bodyMedium,
            )
            MoneyInput("Total tabungan (Rp)", savingsText) { savingsText = it; clearResult() }
            MoneyInput("Harga emas 24 karat per gram (Rp)", goldPriceText) { goldPriceText = it; clearResult() }
            OutlinedTextField(
                value = priceDate,
                onValueChange = { priceDate = it; clearResult() },
                label = { Text("Tanggal harga emas") },
                supportingText = { Text("Format YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = priceSource,
                onValueChange = { priceSource = it; clearResult() },
                label = { Text("Sumber harga emas") },
                placeholder = { Text("Contoh: harga jual toko atau penyedia emas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ConfirmationRow(
                label = "Saya telah memiliki tabungan ini secara penuh dan sumbernya halal",
                checked = ownershipConfirmed,
                onCheckedChange = { ownershipConfirmed = it; clearResult() },
            )
            ConfirmationRow(
                label = "Tabungan telah mencapai nisab dan dimiliki selama satu tahun Hijriah (haul)",
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
                        error = "Masukkan nominal tabungan dan harga emas yang valid."
                        result = null
                    } else if (!validDate || priceSource.isBlank()) {
                        error = "Lengkapi tanggal dan sumber harga emas."
                        result = null
                    } else {
                        error = null
                        result = ZakatSavingsCalculator.calculate(savings, goldPrice, haulCompleted, ownershipConfirmed)
                        hasSavedResult = false
                    }
                },
            ) { Text("Hitung zakat") }

            result?.let { calculation ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(statusLabel(calculation.status), style = MaterialTheme.typography.titleMedium)
                    Text("Nisab: 85 gram × ${currency.format(calculation.goldPricePerGramRupiah)} = ${currency.format(calculation.nisabRupiah)}")
                    Text("Acuan harga: $priceSource · $priceDate")
                    Text("Tabungan: ${currency.format(calculation.savingsRupiah)}")
                    if (calculation.status == ZakatSavingsStatus.DUE) {
                        Text("Zakat: 2,5% × tabungan = ${currency.format(calculation.dueRupiah)}", style = MaterialTheme.typography.titleLarge)
                        Text("Hasil dibulatkan ke rupiah terdekat.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                val shareText = buildShareText(calculation, savingsText, goldPriceText, priceSource, priceDate)
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText),
                                        "Bagikan ringkasan zakat",
                                    ),
                                )
                            },
                        ) { Text("Bagikan") }
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
                        ) { Text(if (hasSavedResult) "Tersimpan" else "Simpan lokal") }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("Profil aturan: ${ZakatSavingsCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
            Text("Profil ini mencakup tabungan sederhana: nisab setara 85 gram emas, kadar 2,5%, dan syarat haul. Penggabungan aset, utang/pengurang, dan kondisi khusus memerlukan telaah terpisah.", style = MaterialTheme.typography.bodySmall)
            Text("Profil awal ini menunggu persetujuan reviewer fiqih sebelum rilis publik.", style = MaterialTheme.typography.bodySmall)
            Text(
                "Rujukan BAZNAS: Cara Menghitung Zakat Tabungan dengan Benar",
                modifier = Modifier.clickable { uriHandler.openUri(BAZNAS_SAVINGS_URL) },
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Riwayat tersimpan hanya setelah kamu menekan Simpan lokal; data tetap di perangkat dan tidak dikirim ke server.", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showHistory) {
        ZakatHistoryDialog(
            records = history,
            onDismiss = { showHistory = false },
            onDelete = { id -> history = historyStore.delete(id) },
        )
    }
}

private const val BAZNAS_SAVINGS_URL = "https://baznas.go.id/artikel-show/Cara-Menghitung-Zakat-Tabungan-dengan-Benar/2498"

@Composable
private fun ZakatHistoryDialog(
    records: List<ZakatSavingsRecord>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val currency = remember {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Riwayat zakat tabungan") },
        text = {
            if (records.isEmpty()) {
                Text("Belum ada hasil yang disimpan.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(records, key = { it.id }) { record ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).format(dateFormatter),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text("${record.profileId} · ${statusLabel(record.status)} · Saldo ${currency.format(record.savingsRupiah)}")
                            Text("Nisab ${currency.format(record.nisabRupiah)} · Zakat ${currency.format(record.dueRupiah)}")
                            Text("Harga ${currency.format(record.goldPricePerGramRupiah)}/g · ${record.priceSource} · ${record.priceDate}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onDelete(record.id) }) { Text("Hapus") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

private fun buildShareText(
    result: ZakatSavingsResult,
    savings: String,
    goldPrice: String,
    priceSource: String,
    priceDate: String,
): String = buildString {
    appendLine("Ringkasan zakat tabungan")
    appendLine("Profil aturan: ${ZakatSavingsCalculator.PROFILE_ID}")
    appendLine("Status: ${statusLabel(result.status)}")
    appendLine("Tabungan: Rp$savings")
    appendLine("Nisab: 85 gram × Rp$goldPrice = Rp${result.nisabRupiah.toPlainString()}")
    appendLine("Zakat: Rp${result.dueRupiah.toPlainString()}")
    appendLine("Sumber harga: $priceSource · $priceDate")
}

@Composable
private fun MoneyInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(16)) },
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

private fun statusLabel(status: ZakatSavingsStatus): String = when (status) {
    ZakatSavingsStatus.INCOMPLETE -> "Input belum lengkap"
    ZakatSavingsStatus.OWNERSHIP_UNCONFIRMED -> "Konfirmasi kepemilikan dan sumber tabungan untuk melanjutkan"
    ZakatSavingsStatus.HAUL_NOT_MET -> "Haul satu tahun Hijriah belum dikonfirmasi"
    ZakatSavingsStatus.BELOW_NISAB -> "Tabungan belum mencapai nisab"
    ZakatSavingsStatus.DUE -> "Tabungan memenuhi nisab dan haul"
}
