package id.kasirq.dailysunnah.quran.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranVerseKeyTest {
    @Test
    fun acceptsThreeDigitSurahReferences() {
        assertTrue(QuranVerseKey.isValid("100:1"))
        assertTrue(QuranVerseKey.isValid("114:6"))
    }

    @Test
    fun rejectsReferencesOutsideQuranBoundsAndMalformedKeys() {
        assertFalse(QuranVerseKey.isValid("0:1"))
        assertFalse(QuranVerseKey.isValid("115:1"))
        assertFalse(QuranVerseKey.isValid("100:0"))
        assertFalse(QuranVerseKey.isValid("100:287"))
        assertFalse(QuranVerseKey.isValid("01:1"))
        assertFalse(QuranVerseKey.isValid("114:"))
    }

    @Test
    fun advancesWithinAndAcrossSurahs() {
        org.junit.Assert.assertEquals("2:5", QuranVerseKey.next("2:4"))
        org.junit.Assert.assertEquals("3:1", QuranVerseKey.next("2:286"))
        org.junit.Assert.assertNull(QuranVerseKey.next("114:6"))
        org.junit.Assert.assertNull(QuranVerseKey.next("115:1"))
    }
}
