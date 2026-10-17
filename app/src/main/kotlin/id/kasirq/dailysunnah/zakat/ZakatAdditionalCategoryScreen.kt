package id.kasirq.dailysunnah.zakat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import id.kasirq.dailysunnah.ui.ModernTextFieldShape
import id.kasirq.dailysunnah.ui.modernTextFieldColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ZakatAdditionalCategoryScreen(category: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (category) {
            1 -> IncomeZakatForm()
            2 -> MetalZakatForm()
            else -> FitrahZakatForm()
        }
        Text("Kalkulator memakai profil rujukan yang tercantum, bukan fatwa pribadi. Periksa keputusan terbaru BAZNAS daerah dan konsultasikan kasus khusus kepada amil/reviewer fiqih.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun IncomeZakatForm() {
    var incomeText by rememberSaveable { mutableStateOf("") }
    var periodIndex by rememberSaveable { mutableStateOf(0) }
    var result by remember { mutableStateOf<ZakatIncomeResult?>(null) }
    val period = if (periodIndex == 0) ZakatPeriod.MONTHLY else ZakatPeriod.YEARLY
    val currency = rupiahFormatter()
    Text("Zakat penghasilan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("Profil BAZNAS 2026. Ambang nisab dan pilihan periode terlihat; input pendapatan bruto periode ini.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ZakatPeriod.MONTHLY, ZakatPeriod.YEARLY).forEachIndexed { index, item ->
            if (periodIndex == index) Button(onClick = { periodIndex = index; result = null }) { Text(item.label) }
            else OutlinedButton(onClick = { periodIndex = index; result = null }) { Text(item.label) }
        }
    }
    NumericField("Pendapatan bruto (${period.label.lowercase()}, Rp)", incomeText) { incomeText = it; result = null }
    Button(onClick = { incomeText.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.let { result = ZakatIncomeCalculator.calculate(it, period) } }, modifier = Modifier.fillMaxWidth()) { Text("Hitung zakat") }
    result?.let {
        ResultCard {
            Text(if (it.isDue) "Mencapai nisab periode ${period.label.lowercase()}" else "Belum mencapai nisab", fontWeight = FontWeight.SemiBold)
            Text("Nisab BAZNAS 2026: ${currency.format(it.nisab)}")
            Text("Pendapatan: ${currency.format(it.income)}")
            if (it.isDue) Text("Zakat 2,5%: ${currency.format(it.due)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    Text("Nisab tahunan Rp91.681.728 atau bulanan Rp7.640.144; kadar 2,5%. Nilai profil berlaku untuk 2026, periksa ulang keputusan BAZNAS tahun berjalan.", style = MaterialTheme.typography.bodySmall)
    SourceLink("https://baznas.go.id/zakatpenghasilan", "Sumber: BAZNAS RI · Zakat Penghasilan")
    Text("Profil: ${ZakatIncomeCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun MetalZakatForm() {
    var metal by rememberSaveable { mutableStateOf(PreciousMetal.GOLD) }
    var gramsText by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var priceReference by rememberSaveable { mutableStateOf("") }
    var haul by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<PreciousMetalResult?>(null) }
    val currency = rupiahFormatter()
    Text("Zakat emas dan perak", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PreciousMetal.entries.forEach { option ->
            if (metal == option) Button(onClick = { metal = option; result = null }) { Text(option.label) }
            else OutlinedButton(onClick = { metal = option; result = null }) { Text(option.label) }
        }
    }
    NumericField("Berat ${metal.label.lowercase()} (gram)", gramsText) { gramsText = it; result = null }
    NumericField("Harga ${metal.label.lowercase()} per gram (Rp)", priceText) { priceText = it; result = null }
    OutlinedTextField(
        value = priceReference,
        onValueChange = { priceReference = it },
        shape = ModernTextFieldShape,
        colors = modernTextFieldColors(),
        label = { Text("Sumber dan tanggal harga") },
        placeholder = { Text("Contoh: harga buyback Antam · 2026-10-15") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Kepemilikan memenuhi haul satu tahun Hijriah", modifier = Modifier.weight(1f))
        Switch(checked = haul, onCheckedChange = { haul = it; result = null })
    }
    Button(onClick = {
        val grams = gramsText.toBigDecimalOrNull()
        val price = priceText.toBigDecimalOrNull()
        if (grams != null && grams.signum() >= 0 && price != null && price.signum() > 0 && priceReference.isNotBlank()) {
            result = ZakatMetalCalculator.calculate(metal, grams, price, haul)
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Hitung zakat") }
    result?.let {
        ResultCard {
            Text(if (it.due) "Memenuhi nisab dan haul" else if (it.grams < it.nisabGrams) "Belum mencapai nisab" else "Haul belum dikonfirmasi", fontWeight = FontWeight.SemiBold)
            Text("Nisab ${it.metal.label.lowercase()}: ${it.nisabGrams.toPlainString()} gram")
            Text("Harga acuan: $priceReference")
            if (it.due) Text("Zakat 2,5%: ${currency.format(it.dueRupiah)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    Text("Profil BAZNAS: emas 85 gram, perak 595 gram, kadar 2,5%, serta haul. Harga dimasukkan manual; untuk emas gunakan harga buyback acuan. Ketentuan perhiasan yang dipakai sehari-hari dapat berbeda dan perlu dirujuk ke ahli.", style = MaterialTheme.typography.bodySmall)
    SourceLink("https://www.baznas.go.id/zakatemas", "Sumber: BAZNAS RI · Zakat Emas dan Perak")
    Text("Profil: ${ZakatMetalCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun FitrahZakatForm() {
    var peopleText by rememberSaveable { mutableStateOf("1") }
    var moneyText by rememberSaveable { mutableStateOf("") }
    var regionYear by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<ZakatFitrahResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val currency = rupiahFormatter()
    Text("Zakat fitrah", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("Takaran makanan pokok ditetapkan per jiwa. Nilai uang mengikuti ketetapan BAZNAS wilayah dan tahun; aplikasi tidak menetapkan nominal nasional tetap.")
    NumericField("Jumlah jiwa", peopleText) { peopleText = it; result = null }
    NumericField("Nilai uang per jiwa (Rp, opsional)", moneyText) { moneyText = it; result = null; error = null }
    if (moneyText.isNotBlank()) {
        OutlinedTextField(
            value = regionYear,
            onValueChange = { regionYear = it; error = null },
            shape = ModernTextFieldShape,
            colors = modernTextFieldColors(),
            label = { Text("Wilayah dan tahun ketetapan") },
            placeholder = { Text("Contoh: BAZNAS Kota … · Ramadan 1448 H") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(onClick = {
        val people = peopleText.toIntOrNull()
        val money = moneyText.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        if (people == null || people !in 1..1000 || (moneyText.isNotBlank() && (money == null || money.signum() < 0))) {
            error = "Masukkan jumlah jiwa dan nominal yang valid."
        } else if (money != null && regionYear.isBlank()) {
            error = "Isi wilayah dan tahun keputusan untuk nilai uang."
        } else {
            error = null
            result = ZakatFitrahCalculator.calculate(people, money)
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Hitung zakat fitrah") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    result?.let {
        ResultCard {
            Text("Untuk ${it.people} jiwa", fontWeight = FontWeight.SemiBold)
            Text("Makanan pokok: ${it.riceKg.stripTrailingZeros().toPlainString()} kg total (2,5 kg per jiwa)")
            it.moneyRupiah?.let { amount ->
                Text("Ekuivalen uang · $regionYear: ${currency.format(amount)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    Text("Standar makanan pokok: 2,5 kg atau 3,5 liter per jiwa. Untuk nominal uang, gunakan keputusan resmi wilayah dan tahun terkait.", style = MaterialTheme.typography.bodySmall)
    SourceLink("https://baznas.go.id/zakatfitrah", "Sumber: BAZNAS RI · Zakat Fitrah")
    Text("Profil: ${ZakatFitrahCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun NumericField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' }.take(18)) },
        shape = ModernTextFieldShape,
        colors = modernTextFieldColors(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SourceLink(url: String, label: String) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    androidx.compose.material3.TextButton(onClick = { uriHandler.openUri(url) }) { Text(label) }
}

private fun rupiahFormatter(): NumberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}
