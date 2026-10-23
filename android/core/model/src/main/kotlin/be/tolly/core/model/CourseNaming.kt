package be.tolly.core.model

import java.time.LocalDate

/** Helpers for KU Leuven's course naming conventions (`ULTRA-B-KUL-G0T42a-2627`, `Name [G0T42a]`). */
object CourseNaming {
    private val trailingCode = Regex("""\s*\[([A-Za-z0-9]+)]\s*$""")
    private val yearSuffix = Regex("""-(\d{4})$""")
    private val externalCode = Regex("""^ULTRA-[A-Z]-[A-Z]+-([A-Za-z0-9]+)-\d{4}$""")

    /** Splits `"Logica voor informatici [G0T42a]"` into the bare name and the code. */
    fun splitName(name: String, externalId: String): Pair<String, String?> {
        val match = trailingCode.find(name)
        val bare = match?.let { name.removeRange(it.range).trim() } ?: name.trim()
        val code = match?.groupValues?.get(1) ?: externalCode.find(externalId)?.groupValues?.get(1)
        return bare.ifEmpty { name } to code
    }

    /** `ULTRA-B-KUL-G0T42a-2627` → `2627`. Community ids without a year suffix return null. */
    fun academicYear(externalId: String): String? =
        yearSuffix.find(externalId)?.groupValues?.get(1)?.takeIf { isValidYearPair(it) }

    /** The academic year that is running on [date]; a new year starts in September. */
    fun currentAcademicYear(date: LocalDate = LocalDate.now()): String {
        val startYear = if (date.monthValue >= 9) date.year else date.year - 1
        val a = startYear % 100
        val b = (startYear + 1) % 100
        return "%02d%02d".format(a, b)
    }

    /** `2627` → `2026–27`. */
    fun formatAcademicYear(year: String): String {
        if (!isValidYearPair(year)) return year
        return "20${year.substring(0, 2)}–${year.substring(2)}"
    }

    private fun isValidYearPair(year: String): Boolean {
        if (year.length != 4 || !year.all(Char::isDigit)) return false
        val a = year.substring(0, 2).toInt()
        val b = year.substring(2).toInt()
        return b == (a + 1) % 100
    }
}
