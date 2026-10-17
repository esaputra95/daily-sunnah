package id.kasirq.dailysunnah.quran.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranSurahOpeningPolicyTest {
    @Test
    fun providesArabicNamesForAllSurahs() {
        for (number in 1..114) assertNotNull(QuranSurahArabicNames.get(number))
        assertEquals("الفاتحة", QuranSurahArabicNames.get(1))
        assertEquals("البقرة", QuranSurahArabicNames.get(2))
        assertEquals("الناس", QuranSurahArabicNames.get(114))
    }

    @Test
    fun addsStandaloneBasmalaExceptForAlFatihahAndAtTawbah() {
        assertFalse(QuranSurahOpeningPolicy.needsStandaloneBasmala(1))
        assertTrue(QuranSurahOpeningPolicy.needsStandaloneBasmala(2))
        assertFalse(QuranSurahOpeningPolicy.needsStandaloneBasmala(9))
        assertTrue(QuranSurahOpeningPolicy.needsStandaloneBasmala(114))
        assertFalse(QuranSurahOpeningPolicy.needsStandaloneBasmala(0))
        assertFalse(QuranSurahOpeningPolicy.needsStandaloneBasmala(115))
    }
}
