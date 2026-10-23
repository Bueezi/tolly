package be.tolly.core.data

import be.tolly.core.model.Course
import be.tolly.core.model.CourseKind
import be.tolly.core.model.CourseNaming
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonLinkingTest {
    private fun course(id: String, year: String) = Course(
        id = id, externalId = "x", name = "Gegevensbanken", code = "H0X30a", kind = CourseKind.COURSE,
        available = true, academicYear = year, webUrl = "", lastAccessed = null, unreadAnnouncements = 0,
    )

    @Test
    fun linksLessonsToThisYearsRunOfACourse() {
        val thisYear = CourseNaming.currentAcademicYear()
        val lastYear = course("_old", "0000")
        val current = course("_new", thisYear)
        val lesson = TimetableLesson("Gegevensbanken: oefeningen", 0, 1, "200A", text = "H0X30a Gegevensbanken: oefeningen")
        assertEquals("_new", lesson.toEntity(listOf(lastYear, current)).calendarId)
    }

    @Test
    fun exerciseSessionsGoToTheCourseTheirNameStartsWith() {
        fun named(id: String, name: String) = course(id, CourseNaming.currentAcademicYear()).copy(name = name)
        val courses = listOf(
            named("_wi", "Wiskunde I"),
            named("_bvp", "Beginselen van Programmeren - BvP"),
            named("_bvr", "Bewijzen en redeneren voor informatici: Colleges"),
        )
        assertEquals("_wi", courses.namedAtStartOf(listOf("Wiskunde I: oefeningen"))?.id)
        assertEquals("_bvp", courses.namedAtStartOf(listOf("Beginselen van programmeren: oefeningen"))?.id)
        assertEquals("_bvr", courses.namedAtStartOf(listOf("Bewijzen en redeneren voor informatici: Oefenzittingen"))?.id)
        // Only up to a word boundary: Wiskunde II is another course.
        assertEquals(null, courses.namedAtStartOf(listOf("Wiskunde II, deel 1: oefeningen 1")))
    }
}
