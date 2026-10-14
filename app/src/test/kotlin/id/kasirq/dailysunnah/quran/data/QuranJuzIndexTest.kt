package id.kasirq.dailysunnah.quran.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranJuzIndexTest {
    @Test
    fun includesAllThirtyOfflineJuzStarts() {
        assertEquals(30, QuranJuzIndex.starts.size)
        assertEquals(JuzStart(1, 1, 1, 1), QuranJuzIndex.starts.first())
        assertEquals(JuzStart(30, 78, 1, 582), QuranJuzIndex.starts.last())
        assertEquals(121, QuranJuzIndex.starts[6].page)
        assertEquals(201, QuranJuzIndex.starts[10].page)
    }

    @Test
    fun resolvesVerseOnBothSidesOfKnownJuzBoundaries() {
        assertEquals(1, QuranJuzIndex.forVerse(2, 141))
        assertEquals(2, QuranJuzIndex.forVerse(2, 142))
        assertEquals(10, QuranJuzIndex.forVerse(9, 92))
        assertEquals(11, QuranJuzIndex.forVerse(9, 93))
    }
}
