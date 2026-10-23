package id.kasirq.dailysunnah.zakat

import java.math.BigDecimal
import java.math.RoundingMode

enum class ZakatPeriod(val label: String, val nisab: BigDecimal) {
    MONTHLY("Bulanan", BigDecimal("7640144")),
    YEARLY("Tahunan", BigDecimal("91681728")),
}

data class ZakatIncomeResult(val income: BigDecimal, val nisab: BigDecimal, val due: BigDecimal, val isDue: Boolean) : java.io.Serializable

object ZakatIncomeCalculator {
    const val PROFILE_ID = "BAZNAS_INCOME_2026_V1"
    val RATE = BigDecimal("0.025")
    fun calculate(income: BigDecimal, period: ZakatPeriod): ZakatIncomeResult {
        require(income.signum() >= 0)
        val due = if (income >= period.nisab) income.multiply(RATE).setScale(0, RoundingMode.HALF_UP) else BigDecimal.ZERO
        return ZakatIncomeResult(income, period.nisab, due, income >= period.nisab)
    }
}

enum class PreciousMetal(val label: String, val nisabGrams: BigDecimal) {
    GOLD("Emas", BigDecimal("85")),
    SILVER("Perak", BigDecimal("595")),
}

data class PreciousMetalResult(
    val metal: PreciousMetal,
    val grams: BigDecimal,
    val pricePerGram: BigDecimal,
    val nisabGrams: BigDecimal,
    val dueRupiah: BigDecimal,
    val due: Boolean,
) : java.io.Serializable

object ZakatMetalCalculator {
    const val PROFILE_ID = "BAZNAS_METALS_2_5_V1"
    val RATE = BigDecimal("0.025")
    fun calculate(metal: PreciousMetal, grams: BigDecimal, pricePerGram: BigDecimal, haulCompleted: Boolean): PreciousMetalResult {
        require(grams.signum() >= 0 && pricePerGram.signum() > 0)
        val due = grams >= metal.nisabGrams && haulCompleted
        val amount = if (due) grams.multiply(pricePerGram).multiply(RATE).setScale(0, RoundingMode.HALF_UP) else BigDecimal.ZERO
        return PreciousMetalResult(metal, grams, pricePerGram, metal.nisabGrams, amount, due)
    }
}

data class ZakatFitrahResult(val people: Int, val riceKg: BigDecimal, val moneyRupiah: BigDecimal?) : java.io.Serializable

object ZakatFitrahCalculator {
    const val PROFILE_ID = "BAZNAS_FITRAH_LOCAL_RULE_V1"
    val KG_PER_PERSON = BigDecimal("2.5")
    fun calculate(people: Int, moneyPerPerson: BigDecimal? = null): ZakatFitrahResult {
        require(people >= 0 && (moneyPerPerson == null || moneyPerPerson.signum() >= 0))
        return ZakatFitrahResult(
            people = people,
            riceKg = KG_PER_PERSON.multiply(BigDecimal(people)),
            moneyRupiah = moneyPerPerson?.multiply(BigDecimal(people)),
        )
    }
}

enum class ZakatTradeStatus { BELOW_NISAB, HAUL_NOT_MET, DUE }

data class ZakatTradeResult(
    val cashAndBankRupiah: BigDecimal,
    val inventoryRupiah: BigDecimal,
    val collectibleReceivablesRupiah: BigDecimal,
    val shortTermDebtRupiah: BigDecimal,
    val netAssetsRupiah: BigDecimal,
    val goldPricePerGramRupiah: BigDecimal,
    val nisabRupiah: BigDecimal,
    val status: ZakatTradeStatus,
    val dueRupiah: BigDecimal,
) : java.io.Serializable

object ZakatTradeCalculator {
    const val PROFILE_ID = "BAZNAS_TRADE_85G_2_5_V1"
    val NISAB_GOLD_GRAMS = BigDecimal("85")
    val RATE = BigDecimal("0.025")

    fun calculate(
        cashAndBankRupiah: BigDecimal,
        inventoryRupiah: BigDecimal,
        collectibleReceivablesRupiah: BigDecimal,
        shortTermDebtRupiah: BigDecimal,
        goldPricePerGramRupiah: BigDecimal,
        haulCompleted: Boolean,
    ): ZakatTradeResult {
        require(
            listOf(
                cashAndBankRupiah,
                inventoryRupiah,
                collectibleReceivablesRupiah,
                shortTermDebtRupiah,
            ).all { it.signum() >= 0 } && goldPricePerGramRupiah.signum() > 0,
        )
        val netAssets = cashAndBankRupiah
            .add(inventoryRupiah)
            .add(collectibleReceivablesRupiah)
            .subtract(shortTermDebtRupiah)
            .max(BigDecimal.ZERO)
        val nisab = NISAB_GOLD_GRAMS.multiply(goldPricePerGramRupiah)
        val status = when {
            netAssets < nisab -> ZakatTradeStatus.BELOW_NISAB
            !haulCompleted -> ZakatTradeStatus.HAUL_NOT_MET
            else -> ZakatTradeStatus.DUE
        }
        val due = if (status == ZakatTradeStatus.DUE) {
            netAssets.multiply(RATE).setScale(0, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        return ZakatTradeResult(
            cashAndBankRupiah = cashAndBankRupiah,
            inventoryRupiah = inventoryRupiah,
            collectibleReceivablesRupiah = collectibleReceivablesRupiah,
            shortTermDebtRupiah = shortTermDebtRupiah,
            netAssetsRupiah = netAssets,
            goldPricePerGramRupiah = goldPricePerGramRupiah,
            nisabRupiah = nisab,
            status = status,
            dueRupiah = due,
        )
    }
}

enum class AgricultureHarvestBasis(val label: String, val nisabKg: BigDecimal) {
    DRY_GRAIN("Gabah kering", BigDecimal("653")),
    CLEANED_RICE("Beras", BigDecimal("520")),
}

enum class AgricultureWatering(val label: String, val rate: BigDecimal) {
    NATURAL("Air hujan/sumber alami", BigDecimal("0.10")),
    IRRIGATED("Irigasi/biaya pengairan", BigDecimal("0.05")),
}

data class ZakatAgricultureResult(
    val harvestKg: BigDecimal,
    val basis: AgricultureHarvestBasis,
    val watering: AgricultureWatering,
    val nisabKg: BigDecimal,
    val dueKg: BigDecimal,
    val isDue: Boolean,
) : java.io.Serializable

object ZakatAgricultureCalculator {
    const val PROFILE_ID = "BAZNAS_AGRICULTURE_5_WASQ_V1"

    fun calculate(
        harvestKg: BigDecimal,
        basis: AgricultureHarvestBasis,
        watering: AgricultureWatering,
    ): ZakatAgricultureResult {
        require(harvestKg.signum() >= 0)
        val isDue = harvestKg >= basis.nisabKg
        val due = if (isDue) harvestKg.multiply(watering.rate) else BigDecimal.ZERO
        return ZakatAgricultureResult(harvestKg, basis, watering, basis.nisabKg, due, isDue)
    }
}
