package be.tolly.feature.agenda

import be.tolly.core.model.AgendaItem
import be.tolly.core.model.AgendaItemKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadlinesTest {
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    private fun item(title: String, kind: AgendaItemKind = AgendaItemKind.EVENT, start: Long = now + hour, end: Long? = null) =
        AgendaItem(id = title, kind = kind, title = title, description = null, location = null, start = start, end = end, courseId = "c", calendarName = "Course")

    /** Courses put their deadlines in the calendar as plain events, like the activity stream shows. */
    @Test
    fun courseEventsToComeAreDue() {
        assertTrue(item("IW - Deliverable/deadline groepswerk: Voorlopige tekst").isDue(now))
        assertTrue(item("Wis I: proefexamen").isDue(now))
    }

    @Test
    fun anEventThatIsOverIsNot() {
        assertFalse(item("Wis I: proefexamen", start = now - 2 * hour, end = now - hour).isDue(now))
        assertTrue(item("IW - Presentatie groepswerk", start = now - hour, end = now + hour).isDue(now))
    }

    @Test
    fun classesAreNotDue() {
        assertFalse(item("Hoorcollege 5: Predikaatlogica").isDue(now))
        assertFalse(item("Oefenzitting reeks 4").isDue(now))
    }

    @Test
    fun overdueGradableItemsStay() {
        assertTrue(item("Opdracht SQL", kind = AgendaItemKind.DEADLINE, start = now - hour).isDue(now))
        assertFalse(item("Kerstvakantie", kind = AgendaItemKind.INSTITUTION).isDue(now))
    }
}
