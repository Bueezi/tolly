package be.tolly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CourseNamingTest {
    @Test
    fun splitsCodeFromName() {
        val (name, code) = CourseNaming.splitName("Logica voor informatici [G0T42a]", "ULTRA-B-KUL-G0T42a-2627")
        assertEquals("Logica voor informatici", name)
        assertEquals("G0T42a", code)
    }

    @Test
    fun fallsBackToExternalIdForCode() {
        val (name, code) = CourseNaming.splitName("Wiskunde I", "ULTRA-B-KUL-G0N02a-2627")
        assertEquals("Wiskunde I", name)
        assertEquals("G0N02a", code)
    }

    @Test
    fun communitiesHaveNoCodeOrYear() {
        val (_, code) = CourseNaming.splitName("Academic Writing @FWET", "ULTRA-C-17772343-K")
        assertNull(code)
        assertNull(CourseNaming.academicYear("ULTRA-C-17772343-K"))
    }

    @Test
    fun parsesAcademicYear() {
        assertEquals("2627", CourseNaming.academicYear("ULTRA-B-KUL-G0T42a-2627"))
        assertEquals("2627", CourseNaming.academicYear("ULTRA-C-18967146-K-2627"))
        assertNull(CourseNaming.academicYear("ULTRA-C-25108269-1234"))
    }

    @Test
    fun currentAcademicYearSwitchesInSeptember() {
        assertEquals("2526", CourseNaming.currentAcademicYear(LocalDate.of(2026, 8, 31)))
        assertEquals("2627", CourseNaming.currentAcademicYear(LocalDate.of(2026, 9, 1)))
        assertEquals("9900", CourseNaming.currentAcademicYear(LocalDate.of(2099, 10, 1)))
    }

    @Test
    fun formatsAcademicYear() {
        assertEquals("2026–27", CourseNaming.formatAcademicYear("2627"))
    }
}
