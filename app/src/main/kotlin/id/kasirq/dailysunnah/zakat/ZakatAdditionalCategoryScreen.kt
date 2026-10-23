package id.kasirq.dailysunnah.zakat

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.kasirq.dailysunnah.ui.ModernTextFieldShape
import id.kasirq.dailysunnah.ui.modernTextFieldColors
import id.kasirq.dailysunnah.ui.LocalAppEnglish
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ZakatAdditionalCategoryScreen(
    category: Int,
    onSaveRecord: (category: String, status: String, summary: String) -> Unit,
) {
    val english = LocalAppEnglish.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (category) {
            1 -> IncomeZakatForm(onSaveRecord)
            2 -> MetalZakatForm(onSaveRecord)
            3 -> TradeZakatForm(onSaveRecord)
            4 -> AgricultureZakatForm(onSaveRecord)
            else -> FitrahZakatForm(onSaveRecord)
        }
        Text(
            if (english) "Calculators use the listed reference profiles, not a personal fatwa. Check your local BAZNAS guidance and consult a zakat officer or fiqh reviewer for special cases."
            else "Kalkulator memakai profil rujukan yang tercantum, bukan fatwa pribadi. Periksa keputusan BAZNAS daerah dan konsultasikan kasus khusus kepada amil/reviewer fiqih.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IncomeZakatForm(onSaveRecord: (String, String, String) -> Unit) {
    val english = LocalAppEnglish.current
    var incomeText by rememberSaveable { mutableStateOf("") }
    var periodIndex by rememberSaveable { mutableStateOf(0) }
    var result by rememberSaveable { mutableStateOf<ZakatIncomeResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val period = if (periodIndex == 0) ZakatPeriod.MONTHLY else ZakatPeriod.YEARLY
    val currency = rupiahFormatter()

    FormTitle(if (english) "Income zakat" else "Zakat penghasilan", if (english) "Calculate using gross income for the selected period." else "Hitung dari total pendapatan bruto pada periode yang dipilih.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZakatPeriod.entries.forEachIndexed { index, option ->
            if (periodIndex == index) Button(onClick = { periodIndex = index; result = null; error = null }) { Text(periodLabel(option, english)) }
            else OutlinedButton(onClick = { periodIndex = index; result = null; error = null }) { Text(periodLabel(option, english)) }
        }
    }
    NumericField(if (english) "Gross ${periodLabel(period, true).lowercase()} income (IDR)" else "Pendapatan bruto ${period.label.lowercase()} (Rp)", incomeText) {
        incomeText = it
        result = null
        error = null
    }
    error?.let { FormError(it) }
    Button(
        onClick = {
            val income = incomeText.toBigDecimalOrNull()
            if (income == null || income.signum() < 0) {
                error = if (english) "Enter a valid income amount without thousands separators." else "Masukkan pendapatan yang valid, tanpa pemisah ribuan."
                result = null
            } else {
                error = null
                result = ZakatIncomeCalculator.calculate(income, period)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (english) "Calculate zakat" else "Hitung zakat") }
    result?.let {
        val status = if (english) { if (it.isDue) "Income meets nisab" else "Income is below nisab" } else { if (it.isDue) "Pendapatan mencapai nisab" else "Pendapatan belum mencapai nisab" }
        val summary = buildString {
            appendLine("${if (english) "Period" else "Periode"}: ${periodLabel(period, english).lowercase()}")
            appendLine("${if (english) "Income" else "Pendapatan"}: ${currency.format(it.income)}")
            appendLine("Nisab: ${currency.format(it.nisab)}")
            append("Zakat: ${currency.format(it.due)}")
        }
        ResultCard {
            Text(status, fontWeight = FontWeight.SemiBold)
            Text(if (english) "BAZNAS 2026 ${periodLabel(period, true).lowercase()} nisab: ${currency.format(it.nisab)}" else "Nisab BAZNAS 2026 (${period.label.lowercase()}): ${currency.format(it.nisab)}")
            Text(if (english) "Income: ${currency.format(it.income)}" else "Pendapatan: ${currency.format(it.income)}")
            if (it.isDue) Text(if (english) "Zakat 2.5%: ${currency.format(it.due)}" else "Zakat 2,5%: ${currency.format(it.due)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        SaveAndShareActions(if (english) "Income" else "Penghasilan", status, summary, onSaveRecord)
    }
    Text(if (english) "BAZNAS Indonesia 2026 reference: IDR 91,681,728 yearly or IDR 7,640,144 monthly; 2.5% of gross income. Nisab changes each year." else "Acuan BAZNAS RI 2026: Rp91.681.728 per tahun atau Rp7.640.144 per bulan; kadar 2,5% dari pendapatan bruto. Nilai nisab berubah setiap tahun.", style = MaterialTheme.typography.bodySmall)
    SourceLink(
        "https://kabminahasa.baznas.go.id/berita/news-show/baznas-ri-tetapkan-nisab-zakat-pendapatan-2026-rp764-juta-per-bulan/38774",
        if (english) "Source: BAZNAS Indonesia · Income nisab 2026" else "Sumber: BAZNAS RI · Nisab Penghasilan 2026",
    )
    Text("${if (english) "Profile" else "Profil"}: ${ZakatIncomeCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun MetalZakatForm(onSaveRecord: (String, String, String) -> Unit) {
    val english = LocalAppEnglish.current
    var metal by rememberSaveable { mutableStateOf(PreciousMetal.GOLD) }
    var gramsText by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var priceReference by rememberSaveable { mutableStateOf("") }
    var haul by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<PreciousMetalResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val currency = rupiahFormatter()

    FormTitle(if (english) "Gold and silver zakat" else "Zakat emas dan perak", if (english) "Enter the total stored weight and price per gram for conversion to rupiah." else "Masukkan berat total yang disimpan dan nilai harga per gram untuk konversi rupiah.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PreciousMetal.entries.forEach { option ->
            if (metal == option) Button(onClick = { metal = option; result = null; error = null }) { Text(metalLabel(option, english)) }
            else OutlinedButton(onClick = { metal = option; result = null; error = null }) { Text(metalLabel(option, english)) }
        }
    }
    NumericField(if (english) "${metalLabel(metal, true)} weight (grams)" else "Berat ${metal.label.lowercase()} (gram)", gramsText) { gramsText = it; result = null; error = null }
    NumericField(if (english) "${metalLabel(metal, true)} price per gram (IDR)" else "Harga ${metal.label.lowercase()} per gram (Rp)", priceText) { priceText = it; result = null; error = null }
    OutlinedTextField(
        value = priceReference,
        onValueChange = { priceReference = it; result = null; error = null },
        shape = ModernTextFieldShape,
        colors = modernTextFieldColors(),
        label = { Text(if (english) "Price source and date" else "Sumber dan tanggal harga") },
        placeholder = { Text(if (english) "Example: Antam buyback price · 2026-10-15" else "Contoh: harga buyback Antam · 2026-10-15") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    HaulSwitch(haul) { haul = it; result = null; error = null }
    error?.let { FormError(it) }
    Button(
        onClick = {
            val grams = gramsText.toBigDecimalOrNull()
            val price = priceText.toBigDecimalOrNull()
            if (grams == null || grams.signum() < 0 || price == null || price.signum() <= 0 || priceReference.isBlank()) {
                error = if (english) "Enter a valid weight, price per gram, and price source/date." else "Lengkapi berat, harga per gram, dan sumber/tanggal harga dengan nilai yang valid."
                result = null
            } else {
                error = null
                result = ZakatMetalCalculator.calculate(metal, grams, price, haul)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (english) "Calculate zakat" else "Hitung zakat") }
    result?.let {
        val status = when {
            it.grams < it.nisabGrams -> if (english) "Below nisab" else "Belum mencapai nisab"
            !haul -> if (english) "Nisab met; one Hijri year (haul) not yet completed" else "Nisab tercapai; haul satu tahun Hijriah belum terpenuhi"
            else -> if (english) "Nisab and haul requirements met" else "Memenuhi nisab dan haul"
        }
        val summary = if (english) "Weight: ${it.grams.stripTrailingZeros().toPlainString()} g · Nisab: ${it.nisabGrams.stripTrailingZeros().toPlainString()} g · Zakat: ${currency.format(it.dueRupiah)} · Price: $priceReference" else "Berat: ${it.grams.stripTrailingZeros().toPlainString()} g · Nisab: ${it.nisabGrams.stripTrailingZeros().toPlainString()} g · Zakat: ${currency.format(it.dueRupiah)} · Harga: $priceReference"
        ResultCard {
            Text(status, fontWeight = FontWeight.SemiBold)
            Text(if (english) "${metalLabel(it.metal, true)} nisab: ${it.nisabGrams.stripTrailingZeros().toPlainString()} grams" else "Nisab ${it.metal.label.lowercase()}: ${it.nisabGrams.stripTrailingZeros().toPlainString()} gram")
            Text(if (english) "Price reference: $priceReference" else "Harga acuan: $priceReference")
            if (it.due) Text(if (english) "Zakat 2.5%: ${currency.format(it.dueRupiah)}" else "Zakat 2,5%: ${currency.format(it.dueRupiah)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        SaveAndShareActions(if (english) "Gold/Silver" else "Emas/Perak", status, summary, onSaveRecord)
    }
    Text(if (english) "BAZNAS reference: gold nisab is 85 grams and silver 595 grams; 2.5% is due after haul. Use the buyback price for gold. Scholars differ on jewelry worn regularly." else "Acuan BAZNAS: nisab emas 85 gram dan perak 595 gram; kadar 2,5% setelah haul. Untuk emas, gunakan harga buyback. Perhiasan yang dipakai sehari-hari memiliki perbedaan pendapat.", style = MaterialTheme.typography.bodySmall)
    SourceLink("https://www.baznas.go.id/zakatemas", if (english) "Source: BAZNAS Indonesia · Gold and silver zakat" else "Sumber: BAZNAS RI · Zakat Emas dan Perak")
    Text("${if (english) "Profile" else "Profil"}: ${ZakatMetalCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun TradeZakatForm(onSaveRecord: (String, String, String) -> Unit) {
    val english = LocalAppEnglish.current
    var cashText by rememberSaveable { mutableStateOf("") }
    var inventoryText by rememberSaveable { mutableStateOf("") }
    var receivablesText by rememberSaveable { mutableStateOf("") }
    var debtText by rememberSaveable { mutableStateOf("") }
    var goldPriceText by rememberSaveable { mutableStateOf("") }
    var goldPriceReference by rememberSaveable { mutableStateOf("") }
    var haul by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<ZakatTradeResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val currency = rupiahFormatter()

    FormTitle(if (english) "Trade zakat" else "Zakat perdagangan", if (english) "Calculate business assets at the end of haul: cash, saleable inventory, collectible receivables, minus short-term debt." else "Hitung harta usaha pada akhir haul: kas, stok siap jual, piutang yang dapat ditagih, dikurangi utang jangka pendek.")
    NumericField(if (english) "Business cash and bank balance (IDR)" else "Kas dan saldo rekening usaha (Rp)", cashText) { cashText = it; result = null; error = null }
    NumericField(if (english) "Saleable inventory value (IDR)" else "Nilai persediaan barang siap jual (Rp)", inventoryText) { inventoryText = it; result = null; error = null }
    NumericField(if (english) "Collectible receivables (IDR)" else "Piutang yang kemungkinan tertagih (Rp)", receivablesText) { receivablesText = it; result = null; error = null }
    NumericField(if (english) "Short-term debt (IDR)" else "Utang jangka pendek (Rp)", debtText) { debtText = it; result = null; error = null }
    NumericField(if (english) "Gold price per gram for nisab (IDR)" else "Harga emas per gram untuk nisab (Rp)", goldPriceText) { goldPriceText = it; result = null; error = null }
    OutlinedTextField(
        value = goldPriceReference,
        onValueChange = { goldPriceReference = it; result = null; error = null },
        shape = ModernTextFieldShape,
        colors = modernTextFieldColors(),
        label = { Text(if (english) "Gold price source and date" else "Sumber dan tanggal harga emas") },
        placeholder = { Text(if (english) "Example: Antam buyback price · 2026-10-15" else "Contoh: harga buyback Antam · 2026-10-15") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    HaulSwitch(haul) { haul = it; result = null; error = null }
    error?.let { FormError(it) }
    Button(
        onClick = {
            val values = listOf(cashText, inventoryText, receivablesText, debtText).map { it.toBigDecimalOrNull() }
            val goldPrice = goldPriceText.toBigDecimalOrNull()
            if (values.any { it == null || it.signum() < 0 } || goldPrice == null || goldPrice.signum() <= 0 || goldPriceReference.isBlank()) {
                error = if (english) "Enter valid amounts for every component, the gold price, and its source/date." else "Isi semua komponen, harga emas, dan sumber/tanggal harga dengan nilai yang valid."
                result = null
            } else {
                error = null
                result = ZakatTradeCalculator.calculate(
                    cashAndBankRupiah = values[0]!!,
                    inventoryRupiah = values[1]!!,
                    collectibleReceivablesRupiah = values[2]!!,
                    shortTermDebtRupiah = values[3]!!,
                    goldPricePerGramRupiah = goldPrice,
                    haulCompleted = haul,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (english) "Calculate zakat" else "Hitung zakat") }
    result?.let {
        val status = tradeStatusLabel(it.status, english)
        val summary = if (english) "Net assets: ${currency.format(it.netAssetsRupiah)} · Nisab: ${currency.format(it.nisabRupiah)} · Zakat: ${currency.format(it.dueRupiah)} · Price: $goldPriceReference" else "Harta bersih: ${currency.format(it.netAssetsRupiah)} · Nisab: ${currency.format(it.nisabRupiah)} · Zakat: ${currency.format(it.dueRupiah)} · Harga: $goldPriceReference"
        ResultCard {
            Text(status, fontWeight = FontWeight.SemiBold)
            Text(if (english) "Net business assets: ${currency.format(it.netAssetsRupiah)}" else "Harta bersih usaha: ${currency.format(it.netAssetsRupiah)}")
            Text(if (english) "Nisab: 85 grams of gold = ${currency.format(it.nisabRupiah)}" else "Nisab 85 gram emas: ${currency.format(it.nisabRupiah)}")
            Text(if (english) "Gold price reference: $goldPriceReference" else "Acuan harga emas: $goldPriceReference")
            Text(if (english) "Formula: (cash + inventory + collectible receivables − short-term debt) × 2.5%" else "Rumus: (kas + stok + piutang tertagih − utang jangka pendek) × 2,5%")
            if (it.status == ZakatTradeStatus.DUE) Text(if (english) "Zakat: ${currency.format(it.dueRupiah)}" else "Zakat: ${currency.format(it.dueRupiah)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        SaveAndShareActions(if (english) "Trade" else "Perdagangan", status, summary, onSaveRecord)
    }
    SourceLink("https://baznas.go.id/zakatperdagangan", if (english) "Source: BAZNAS Indonesia · Trade zakat" else "Sumber: BAZNAS RI · Zakat Perdagangan")
    Text("${if (english) "Profile" else "Profil"}: ${ZakatTradeCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun AgricultureZakatForm(onSaveRecord: (String, String, String) -> Unit) {
    val english = LocalAppEnglish.current
    var harvestText by rememberSaveable { mutableStateOf("") }
    var basis by rememberSaveable { mutableStateOf(AgricultureHarvestBasis.DRY_GRAIN) }
    var watering by rememberSaveable { mutableStateOf(AgricultureWatering.NATURAL) }
    var result by rememberSaveable { mutableStateOf<ZakatAgricultureResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    FormTitle(if (english) "Agricultural zakat" else "Zakat pertanian", if (english) "Zakat is calculated at harvest for qualifying food crops; it does not wait for haul." else "Zakat dihitung saat panen untuk komoditas pangan yang memenuhi ketentuan; tidak menunggu haul.")
    Text(if (english) "Harvest form" else "Bentuk hasil panen", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AgricultureHarvestBasis.entries.forEach { option ->
            if (basis == option) Button(onClick = { basis = option; result = null; error = null }) { Text(harvestBasisLabel(option, english)) }
            else OutlinedButton(onClick = { basis = option; result = null; error = null }) { Text(harvestBasisLabel(option, english)) }
        }
    }
    NumericField(if (english) "Harvest amount (${harvestBasisLabel(basis, true).lowercase()}, kg)" else "Jumlah hasil panen (${basis.label.lowercase()}, kg)", harvestText) { harvestText = it; result = null; error = null }
    Text(if (english) "Water source" else "Cara pengairan", fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AgricultureWatering.entries.forEach { option ->
            if (watering == option) Button(onClick = { watering = option; result = null; error = null }, modifier = Modifier.fillMaxWidth()) { Text(wateringLabel(option, english)) }
            else OutlinedButton(onClick = { watering = option; result = null; error = null }, modifier = Modifier.fillMaxWidth()) { Text(wateringLabel(option, english)) }
        }
    }
    error?.let { FormError(it) }
    Button(
        onClick = {
            val harvest = harvestText.toBigDecimalOrNull()
            if (harvest == null || harvest.signum() < 0) {
                error = if (english) "Enter a harvest amount in kilograms of zero or more." else "Masukkan hasil panen dalam kilogram dengan nilai nol atau lebih."
                result = null
            } else {
                error = null
                result = ZakatAgricultureCalculator.calculate(harvest, basis, watering)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (english) "Calculate harvest zakat" else "Hitung zakat panen") }
    result?.let {
        val status = if (english) { if (it.isDue) "Harvest meets nisab" else "Harvest is below nisab" } else { if (it.isDue) "Hasil panen mencapai nisab" else "Hasil panen belum mencapai nisab" }
        val summary = if (english) "Harvest: ${it.harvestKg.stripTrailingZeros().toPlainString()} kg · Nisab: ${it.nisabKg.stripTrailingZeros().toPlainString()} kg · Rate: ${it.watering.rate.multiply(BigDecimal("100")).stripTrailingZeros().toPlainString()}% · Zakat: ${it.dueKg.stripTrailingZeros().toPlainString()} kg" else "Panen: ${it.harvestKg.stripTrailingZeros().toPlainString()} kg · Nisab: ${it.nisabKg.stripTrailingZeros().toPlainString()} kg · Kadar: ${it.watering.rate.multiply(BigDecimal("100")).stripTrailingZeros().toPlainString()}% · Zakat: ${it.dueKg.stripTrailingZeros().toPlainString()} kg"
        ResultCard {
            Text(status, fontWeight = FontWeight.SemiBold)
            Text(if (english) "${harvestBasisLabel(it.basis, true)} nisab: ${it.nisabKg.stripTrailingZeros().toPlainString()} kg" else "Nisab ${it.basis.label.lowercase()}: ${it.nisabKg.stripTrailingZeros().toPlainString()} kg")
            Text(if (english) "Rate ${it.watering.rate.multiply(BigDecimal("100")).stripTrailingZeros().toPlainString()}% · due at harvest" else "Kadar ${it.watering.rate.multiply(BigDecimal("100")).stripTrailingZeros().toPlainString()}% · dibayar saat panen")
            if (it.isDue) Text(if (english) "Harvest zakat: ${it.dueKg.stripTrailingZeros().toPlainString()} kg" else "Zakat hasil panen: ${it.dueKg.stripTrailingZeros().toPlainString()} kg", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        SaveAndShareActions(if (english) "Agriculture" else "Pertanian", status, summary, onSaveRecord)
    }
    Text(if (english) "BAZNAS reference: 653 kg of dry grain or 520 kg of rice; 10% for natural watering and 5% for irrigation or paid watering. Crop types and special cases may differ." else "Acuan BAZNAS: 653 kg gabah kering atau 520 kg beras; kadar 10% untuk pengairan alami dan 5% untuk irigasi/biaya pengairan. Jenis tanaman dan kondisi khusus bisa berbeda.", style = MaterialTheme.typography.bodySmall)
    SourceLink("https://baznas.go.id/assets/images/pustaka/pdf/Pemberdayaan%20ZIS%20Berbasis%20Pesantren.pdf", if (english) "Source: BAZNAS Indonesia · Agricultural zakat" else "Sumber: BAZNAS RI · Zakat Pertanian")
    Text("${if (english) "Profile" else "Profil"}: ${ZakatAgricultureCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun FitrahZakatForm(onSaveRecord: (String, String, String) -> Unit) {
    val english = LocalAppEnglish.current
    var peopleText by rememberSaveable { mutableStateOf("1") }
    var moneyText by rememberSaveable { mutableStateOf("") }
    var regionYear by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<ZakatFitrahResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val currency = rupiahFormatter()

    FormTitle(if (english) "Zakat al-Fitr" else "Zakat fitrah", if (english) "Staple-food amount per person. Cash value follows the local BAZNAS ruling for the selected year." else "Takaran makanan pokok per jiwa. Nominal uang mengikuti ketetapan BAZNAS wilayah dan tahun.")
    NumericField(if (english) "Number of people" else "Jumlah jiwa", peopleText, allowDecimal = false) { peopleText = it; result = null; error = null }
    NumericField(if (english) "Cash value per person (IDR, optional)" else "Nilai uang per jiwa (Rp, opsional)", moneyText) { moneyText = it; result = null; error = null }
    if (moneyText.isNotBlank()) {
        OutlinedTextField(
            value = regionYear,
            onValueChange = { regionYear = it; error = null },
            shape = ModernTextFieldShape,
            colors = modernTextFieldColors(),
            label = { Text(if (english) "Region and ruling year" else "Wilayah dan tahun ketetapan") },
            placeholder = { Text(if (english) "Example: City BAZNAS · Ramadan 1448 AH" else "Contoh: BAZNAS Kota … · Ramadan 1448 H") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    error?.let { FormError(it) }
    Button(
        onClick = {
            val people = peopleText.toIntOrNull()
            val money = moneyText.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
            if (people == null || people !in 1..1000 || (moneyText.isNotBlank() && (money == null || money.signum() < 0))) {
                error = if (english) "Enter 1–1,000 people and a valid cash amount." else "Masukkan jumlah jiwa 1–1.000 dan nominal uang yang valid."
                result = null
            } else if (money != null && regionYear.isBlank()) {
                error = if (english) "Enter the region and ruling year for the cash value." else "Isi wilayah dan tahun keputusan untuk nilai uang."
                result = null
            } else {
                error = null
                result = ZakatFitrahCalculator.calculate(people, money)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (english) "Calculate zakat al-Fitr" else "Hitung zakat fitrah") }
    result?.let {
        val summary = buildString {
            append(if (english) "${it.people} people · ${it.riceKg.stripTrailingZeros().toPlainString()} kg staple food" else "${it.people} jiwa · ${it.riceKg.stripTrailingZeros().toPlainString()} kg makanan pokok")
            it.moneyRupiah?.let { amount -> append(" · ${if (english) "Cash" else "Uang"}: ${currency.format(amount)} · $regionYear") }
        }
        ResultCard {
            Text(if (english) "For ${it.people} people" else "Untuk ${it.people} jiwa", fontWeight = FontWeight.SemiBold)
            Text(if (english) "Staple food: ${it.riceKg.stripTrailingZeros().toPlainString()} kg total (2.5 kg or 3.5 liters per person)" else "Makanan pokok: ${it.riceKg.stripTrailingZeros().toPlainString()} kg total (2,5 kg atau 3,5 liter per jiwa)")
            it.moneyRupiah?.let { amount ->
                Text(if (english) "Cash equivalent · $regionYear: ${currency.format(amount)}" else "Ekuivalen uang · $regionYear: ${currency.format(amount)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        SaveAndShareActions(if (english) "Zakat al-Fitr" else "Fitrah", if (english) "Zakat al-Fitr calculation result" else "Hasil perhitungan zakat fitrah", summary, onSaveRecord)
    }
    Text(if (english) "BAZNAS Indonesia sets 2.5 kg or 3.5 liters of staple food per person. Its 2026 cash reference is IDR 50,000 per person; local values may differ." else "BAZNAS RI menetapkan 2,5 kg atau 3,5 liter makanan pokok per jiwa. Nominal uang tahun 2026 adalah Rp50.000 per jiwa pada acuan BAZNAS RI; nilai daerah dapat berbeda.", style = MaterialTheme.typography.bodySmall)
    SourceLink("https://www.baznas.go.id/zakatfitrah", if (english) "Source: BAZNAS Indonesia · Zakat al-Fitr" else "Sumber: BAZNAS RI · Zakat Fitrah")
    Text("${if (english) "Profile" else "Profil"}: ${ZakatFitrahCalculator.PROFILE_ID}", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun FormTitle(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FormError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HaulSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val english = LocalAppEnglish.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (english) "One Hijri year (haul) completed" else "Haul satu tahun Hijriah terpenuhi", modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    allowDecimal: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val normalized = input.replace(',', '.')
            val filtered = buildString {
                var decimalAdded = false
                normalized.forEach { char ->
                    when {
                        char.isDigit() -> append(char)
                        allowDecimal && char == '.' && !decimalAdded -> {
                            append(char)
                            decimalAdded = true
                        }
                    }
                }
            }.take(18)
            onValueChange(filtered)
        },
        shape = ModernTextFieldShape,
        colors = modernTextFieldColors(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SourceLink(url: String, label: String) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    androidx.compose.material3.TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(label) }
}

@Composable
private fun SaveAndShareActions(
    category: String,
    status: String,
    summary: String,
    onSaveRecord: (String, String, String) -> Unit,
) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    var savedSummary by rememberSaveable { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.TextButton(
            onClick = {
                val text = "${if (english) "Zakat summary" else "Ringkasan zakat"} $category\n$status\n$summary"
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                            if (english) "Share zakat summary" else "Bagikan ringkasan zakat",
                        ),
                    )
                }
            },
        ) { Text(if (english) "Share" else "Bagikan") }
        Button(
            enabled = savedSummary != summary,
            onClick = {
                onSaveRecord(category, status, summary)
                savedSummary = summary
            },
        ) { Text(if (english) { if (savedSummary == summary) "Saved" else "Save locally" } else { if (savedSummary == summary) "Tersimpan" else "Simpan lokal" }) }
    }
}

private fun tradeStatusLabel(status: ZakatTradeStatus, english: Boolean): String = if (english) when (status) {
    ZakatTradeStatus.BELOW_NISAB -> "Net business assets are below nisab"
    ZakatTradeStatus.HAUL_NOT_MET -> "Nisab met; one Hijri year (haul) not yet completed"
    ZakatTradeStatus.DUE -> "Net business assets meet nisab and haul requirements"
} else when (status) {
    ZakatTradeStatus.BELOW_NISAB -> "Harta bersih usaha belum mencapai nisab"
    ZakatTradeStatus.HAUL_NOT_MET -> "Nisab tercapai; haul satu tahun Hijriah belum terpenuhi"
    ZakatTradeStatus.DUE -> "Harta bersih usaha memenuhi nisab dan haul"
}

private fun periodLabel(period: ZakatPeriod, english: Boolean): String = if (english) when (period) {
    ZakatPeriod.MONTHLY -> "Monthly"
    ZakatPeriod.YEARLY -> "Yearly"
} else period.label

private fun metalLabel(metal: PreciousMetal, english: Boolean): String = if (english) when (metal) {
    PreciousMetal.GOLD -> "Gold"
    PreciousMetal.SILVER -> "Silver"
} else metal.label

private fun harvestBasisLabel(basis: AgricultureHarvestBasis, english: Boolean): String = if (english) when (basis) {
    AgricultureHarvestBasis.DRY_GRAIN -> "Dry grain"
    AgricultureHarvestBasis.CLEANED_RICE -> "Rice"
} else basis.label

private fun wateringLabel(watering: AgricultureWatering, english: Boolean): String = if (english) when (watering) {
    AgricultureWatering.NATURAL -> "Rain or natural water source"
    AgricultureWatering.IRRIGATED -> "Irrigation or paid watering"
} else watering.label

private fun rupiahFormatter(): NumberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}
