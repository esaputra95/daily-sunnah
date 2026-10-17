package id.kasirq.dailysunnah.zakat

import java.math.BigDecimal
import java.math.RoundingMode

enum class ZakatPeriod(val label: String, val nisab: BigDecimal) {
    MONTHLY("Bulanan", BigDecimal("7640144")),
    YEARLY("Tahunan", BigDecimal("91681728")),
}

data class ZakatIncomeResult(val income: BigDecimal, val nisab: BigDecimal, val due: BigDecimal, val isDue: Boolean)

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

data class PreciousMetalResult(val metal: PreciousMetal, val grams: BigDecimal, val pricePerGram: BigDecimal, val nisabGrams: BigDecimal, val dueRupiah: BigDecimal, val due: Boolean)

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

data class ZakatFitrahResult(val people: Int, val riceKg: BigDecimal, val moneyRupiah: BigDecimal?)

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
