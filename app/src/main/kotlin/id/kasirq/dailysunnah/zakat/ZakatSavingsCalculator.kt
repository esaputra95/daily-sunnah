package id.kasirq.dailysunnah.zakat

import java.math.BigDecimal
import java.math.RoundingMode

enum class ZakatSavingsStatus {
    INCOMPLETE,
    OWNERSHIP_UNCONFIRMED,
    HAUL_NOT_MET,
    BELOW_NISAB,
    DUE,
}

data class ZakatSavingsResult(
    val status: ZakatSavingsStatus,
    val savingsRupiah: BigDecimal,
    val goldPricePerGramRupiah: BigDecimal,
    val nisabRupiah: BigDecimal,
    val dueRupiah: BigDecimal,
) : java.io.Serializable

object ZakatSavingsCalculator {
    const val PROFILE_ID = "BAZNAS_SAVINGS_85G_2_5_V1"
    val NISAB_GOLD_GRAMS: BigDecimal = BigDecimal("85")
    val RATE: BigDecimal = BigDecimal("0.025")

    fun calculate(
        savingsRupiah: BigDecimal?,
        goldPricePerGramRupiah: BigDecimal?,
        haulCompleted: Boolean,
        fullyOwnedAndPermissible: Boolean,
    ): ZakatSavingsResult {
        val savings = savingsRupiah?.takeIf { it.signum() >= 0 } ?: BigDecimal.ZERO
        val goldPrice = goldPricePerGramRupiah?.takeIf { it.signum() > 0 } ?: BigDecimal.ZERO
        val nisab = goldPrice.multiply(NISAB_GOLD_GRAMS)
        val status = when {
            savingsRupiah == null || savingsRupiah.signum() < 0 ||
                goldPricePerGramRupiah == null || goldPricePerGramRupiah.signum() <= 0 -> ZakatSavingsStatus.INCOMPLETE
            savings < nisab -> ZakatSavingsStatus.BELOW_NISAB
            !fullyOwnedAndPermissible -> ZakatSavingsStatus.OWNERSHIP_UNCONFIRMED
            !haulCompleted -> ZakatSavingsStatus.HAUL_NOT_MET
            else -> ZakatSavingsStatus.DUE
        }
        val due = if (status == ZakatSavingsStatus.DUE) {
            savings.multiply(RATE).setScale(0, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        return ZakatSavingsResult(status, savings, goldPrice, nisab, due)
    }
}
