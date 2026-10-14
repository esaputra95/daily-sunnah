package id.kasirq.dailysunnah.zakat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ZakatSavingsCalculatorTest {
    private val goldPrice = BigDecimal("1000000")

    @Test
    fun savingsBelowNisabAreNotCharged() {
        val result = ZakatSavingsCalculator.calculate(BigDecimal("84999999"), goldPrice, true, true)
        assertEquals(ZakatSavingsStatus.BELOW_NISAB, result.status)
        assertEquals(BigDecimal.ZERO, result.dueRupiah)
    }

    @Test
    fun exactlyNisabIsDueWhenOwnershipAndHaulAreConfirmed() {
        val result = ZakatSavingsCalculator.calculate(BigDecimal("85000000"), goldPrice, true, true)
        assertEquals(ZakatSavingsStatus.DUE, result.status)
        assertEquals(BigDecimal("2125000"), result.dueRupiah)
    }

    @Test
    fun haulOrOwnershipMustBeConfirmed() {
        assertEquals(
            ZakatSavingsStatus.HAUL_NOT_MET,
            ZakatSavingsCalculator.calculate(BigDecimal("100000000"), goldPrice, false, true).status,
        )
        assertEquals(
            ZakatSavingsStatus.OWNERSHIP_UNCONFIRMED,
            ZakatSavingsCalculator.calculate(BigDecimal("100000000"), goldPrice, true, false).status,
        )
    }

    @Test
    fun dueAmountRoundsToWholeRupiah() {
        val result = ZakatSavingsCalculator.calculate(BigDecimal("85000020"), goldPrice, true, true)
        assertEquals(BigDecimal("2125001"), result.dueRupiah)
    }
}
