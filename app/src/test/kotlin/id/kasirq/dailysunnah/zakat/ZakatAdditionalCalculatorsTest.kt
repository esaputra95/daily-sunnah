package id.kasirq.dailysunnah.zakat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ZakatAdditionalCalculatorsTest {
    @Test fun incomeUsesProfileNisabAndRateForPeriod() {
        val due = ZakatIncomeCalculator.calculate(BigDecimal("10000000"), ZakatPeriod.MONTHLY)
        assertTrue(due.isDue)
        assertEquals(BigDecimal("250000"), due.due)
        assertFalse(ZakatIncomeCalculator.calculate(BigDecimal("7640143"), ZakatPeriod.MONTHLY).isDue)
    }

    @Test fun metalsRequireNisabAndHaul() {
        assertFalse(ZakatMetalCalculator.calculate(PreciousMetal.GOLD, BigDecimal("85"), BigDecimal("1000000"), false).due)
        val due = ZakatMetalCalculator.calculate(PreciousMetal.SILVER, BigDecimal("595"), BigDecimal("15000"), true)
        assertTrue(due.due)
        assertEquals(BigDecimal("223125"), due.dueRupiah)
    }

    @Test fun fitrahCalculatesFoodAndEditableMoneyValue() {
        val result = ZakatFitrahCalculator.calculate(4, BigDecimal("40000"))
        assertEquals(BigDecimal("10.0"), result.riceKg)
        assertEquals(BigDecimal("160000"), result.moneyRupiah)
    }
}
