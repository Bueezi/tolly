package be.tolly.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TimetableFeedParserTest {
    private fun utc(text: String) = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()

    // Shaped like KU Loket's TimeEdit feed: CRLF lines, folded at odd places, escaped commas.
    private val feed = listOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "X-WR-CALNAME:TimeEdit-EXAMPLE",
        "BEGIN:VEVENT",
        "DTSTART:20261005T070000Z",
        "DTEND:20261005T083000Z",
        "UID:1000001-1-0@timeedit.com",
        "SUMMARY:G0N43a Wiskunde I: oefeningen\\, geen docent\\, 1",
        "  ba informatica\\, reeks 2",
        "LOCATION:aula 200D.05.0011",
        "DESCRIPTION:15 Oefenzitting \\nG0N43a_InfR2\\nID 1000001",
        "END:VEVENT",
        "BEGIN:VEVENT",
        "DTSTART:20261006T063000Z",
        "DTEND:20261006T083000Z",
        "UID:1000002-1-0@timeedit.com",
        "SUMMARY:G0U41a Bewijzen en redeneren voor",
        "  informatici: Colleges\\, A. Docent",
        "LOCATION:Auditorium 200A.00.0225",
        "END:VEVENT",
        "END:VCALENDAR",
    ).joinToString("\r\n")

    @Test
    fun readsLessonsWithFoldedLines() {
        val lessons = TimetableFeedParser.parse(feed)
        assertEquals(2, lessons.size)
        val first = lessons[0]
        assertEquals("Wiskunde I: oefeningen", first.title)
        assertEquals(utc("2026-10-05T07:00:00"), first.start)
        assertEquals(utc("2026-10-05T08:30:00"), first.end)
        assertEquals("aula 200D.05.0011", first.location)
        assertEquals("1000001-1-0@timeedit.com", first.uid)
        // The course code stays in the text, to match the lesson with its course.
        assertTrue("G0N43a" in first.text)
        assertTrue("1 ba informatica, reeks 2" in first.text)
        assertEquals("Bewijzen en redeneren voor informatici: Colleges", lessons[1].title)
    }

    @Test
    fun localTimesUseTheirZoneAndDatesAreAllDay() {
        val body = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;TZID=Europe/Brussels:20261020T103000
            SUMMARY:Logica voor informatici
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20261101
            SUMMARY:Allerheiligen
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val (lesson, holiday) = TimetableFeedParser.parse(body)
        val brussels = ZoneId.of("Europe/Brussels")
        assertEquals(LocalDateTime.parse("2026-10-20T10:30:00").atZone(brussels).toInstant().toEpochMilli(), lesson.start)
        assertNull(lesson.end)
        assertEquals("Logica voor informatici", lesson.title)
        assertEquals(LocalDateTime.parse("2026-11-01T00:00:00").atZone(brussels).toInstant().toEpochMilli(), holiday.start)
    }

    @Test
    fun recognisesCalendarsOnly() {
        assertTrue(TimetableFeedParser.isCalendar(feed))
        assertFalse(TimetableFeedParser.isCalendar("<html>Not found</html>"))
        assertEquals(emptyList<TimetableLesson>(), TimetableFeedParser.parse("<html>Not found</html>"))
    }
}
