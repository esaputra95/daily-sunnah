package id.kasirq.dailysunnah.quran.data

data class JuzStart(
    val number: Int,
    val surah: Int,
    val ayah: Int,
    val page: Int,
)

/** Madinah Mushaf 604-page juz boundaries bundled for offline navigation. */
object QuranJuzIndex {
    val starts = listOf(
        JuzStart(1, 1, 1, 1),
        JuzStart(2, 2, 142, 22),
        JuzStart(3, 2, 253, 42),
        JuzStart(4, 3, 93, 62),
        JuzStart(5, 4, 24, 82),
        JuzStart(6, 4, 148, 102),
        JuzStart(7, 5, 82, 121),
        JuzStart(8, 6, 111, 142),
        JuzStart(9, 7, 88, 162),
        JuzStart(10, 8, 41, 182),
        JuzStart(11, 9, 93, 201),
        JuzStart(12, 11, 6, 222),
        JuzStart(13, 12, 53, 242),
        JuzStart(14, 15, 1, 262),
        JuzStart(15, 17, 1, 282),
        JuzStart(16, 18, 75, 302),
        JuzStart(17, 21, 1, 322),
        JuzStart(18, 23, 1, 342),
        JuzStart(19, 25, 21, 362),
        JuzStart(20, 27, 56, 382),
        JuzStart(21, 29, 46, 402),
        JuzStart(22, 33, 31, 422),
        JuzStart(23, 36, 28, 442),
        JuzStart(24, 39, 32, 462),
        JuzStart(25, 41, 47, 482),
        JuzStart(26, 46, 1, 502),
        JuzStart(27, 51, 31, 522),
        JuzStart(28, 58, 1, 542),
        JuzStart(29, 67, 1, 562),
        JuzStart(30, 78, 1, 582),
    )

    fun forVerse(surah: Int?, ayah: Int?): Int {
        if (surah == null || ayah == null) return 1
        return starts.lastOrNull { start ->
            start.surah < surah || (start.surah == surah && start.ayah <= ayah)
        }?.number ?: 1
    }
}
