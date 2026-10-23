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

    @Test fun tradeZakatSubtractsShortTermDebtBeforeNisabAndRate() {
        val result = ZakatTradeCalculator.calculate(
            cashAndBankRupiah = BigDecimal("100000000"),
            inventoryRupiah = BigDecimal("50000000"),
            collectibleReceivablesRupiah = BigDecimal("10000000"),
            shortTermDebtRupiah = BigDecimal("20000000"),
            goldPricePerGramRupiah = BigDecimal("1000000"),
            haulCompleted = true,
        )
        assertEquals(BigDecimal("140000000"), result.netAssetsRupiah)
        assertEquals(ZakatTradeStatus.DUE, result.status)
        assertEquals(BigDecimal("3500000"), result.dueRupiah)
    }

    @Test fun tradeZakatDoesNotChargeBelowNisabOrBeforeHaul() {
        val belowNisab = ZakatTradeCalculator.calculate(
            BigDecimal("80000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal("1000000"), haulCompleted = true,
        )
        assertEquals(ZakatTradeStatus.BELOW_NISAB, belowNisab.status)
        assertEquals(BigDecimal.ZERO, belowNisab.dueRupiah)

        val haulNotMet = ZakatTradeCalculator.calculate(
            BigDecimal("90000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal("1000000"), haulCompleted = false,
        )
        assertEquals(ZakatTradeStatus.HAUL_NOT_MET, haulNotMet.status)
    }

    @Test fun agricultureUsesHarvestThresholdAndWateringRateWithoutHaul() {
        val rainfed = ZakatAgricultureCalculator.calculate(
            BigDecimal("1000"), AgricultureHarvestBasis.DRY_GRAIN, AgricultureWatering.NATURAL,
        )
        assertTrue(rainfed.isDue)
        assertEquals(BigDecimal("100.00"), rainfed.dueKg)

        val irrigated = ZakatAgricultureCalculator.calculate(
            BigDecimal("1000"), AgricultureHarvestBasis.DRY_GRAIN, AgricultureWatering.IRRIGATED,
        )
        assertEquals(BigDecimal("50.00"), irrigated.dueKg)

        val belowNisab = ZakatAgricultureCalculator.calculate(
            BigDecimal("519.99"), AgricultureHarvestBasis.CLEANED_RICE, AgricultureWatering.NATURAL,
        )
        assertFalse(belowNisab.isDue)
        assertEquals(BigDecimal.ZERO, belowNisab.dueKg)
    }
}
