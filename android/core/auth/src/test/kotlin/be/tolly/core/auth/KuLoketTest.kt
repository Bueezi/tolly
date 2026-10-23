package be.tolly.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KuLoketTest {
    @Test
    fun readsFeedAndStudentNumber() {
        val json = """{"d":{"results":[{"Studentid":"r0123456","Scheduleid":"1","Schedulelink":"https://cloud.timeedit.net/x.ics"}]}}"""
        val student = KuLoket.studentInfo(json)
        assertEquals("https://cloud.timeedit.net/x.ics", student?.feedUrl)
        assertEquals("r0123456", student?.studentNumber)
    }

    @Test
    fun putsTheRBackOnBareNumbers() {
        assertEquals("r0123456", KuLoket.studentNumber("0123456"))
        assertEquals("r0123456", KuLoket.studentNumber("00123456"))
        assertEquals("r0123456", KuLoket.studentNumber(" R0123456 "))
        assertNull(KuLoket.studentNumber(""))
    }

    @Test
    fun ignoresOtherPages() {
        assertNull(KuLoket.studentInfo("<html>SAML</html>"))
        assertNull(KuLoket.studentInfo("""{"d":{"results":[]}}"""))
    }
}
