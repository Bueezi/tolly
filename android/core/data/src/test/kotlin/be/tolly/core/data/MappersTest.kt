package be.tolly.core.data

import be.tolly.core.model.ContentType
import be.tolly.core.model.Course
import be.tolly.core.model.CourseKind
import be.tolly.core.model.UserPreferences
import be.tolly.core.network.dto.AvailabilityDto
import be.tolly.core.network.dto.ContentDto
import be.tolly.core.network.dto.ContentHandlerDto
import be.tolly.core.network.dto.CourseDto
import be.tolly.core.network.dto.FileDto
import be.tolly.core.network.dto.InternalAnnouncementDto
import be.tolly.core.network.dto.InternalBodyDto
import be.tolly.core.network.dto.LinkDto
import be.tolly.core.network.dto.MembershipDto
import be.tolly.core.network.dto.ReadStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {
    @Test
    fun mapsContentHandlers() {
        assertEquals(ContentType.FOLDER, contentTypeOf("resource/x-bb-folder"))
        assertEquals(ContentType.LESSON, contentTypeOf("resource/x-bb-lesson"))
        assertEquals(ContentType.FILE, contentTypeOf("resource/x-bb-file"))
        assertEquals(ContentType.LTI, contentTypeOf("resource/x-bb-bltiplacement-KalturaMediaGalleryCourseContent"))
        assertEquals(ContentType.LTI, contentTypeOf("resource/x-bb-blti-link"))
        assertEquals(ContentType.TEST, contentTypeOf("resource/x-bb-asmt-test-link"))
        assertEquals(ContentType.OTHER, contentTypeOf(null))
    }

    @Test
    fun rootContentIgnoresHiddenRootParent() {
        val dto = ContentDto(
            id = "_2_1",
            parentId = "_999_1",
            title = "slides.pdf",
            contentHandler = ContentHandlerDto(id = "resource/x-bb-file", file = FileDto("slides.pdf", "application/pdf")),
            links = listOf(LinkDto(href = "/ultra/redirect?x=1", rel = "alternate")),
        )
        val entity = dto.toEntity(courseId = "_9_1", parentId = null)
        assertNull(entity.parentId)
        assertEquals(ContentType.FILE, entity.type)
        assertEquals("slides.pdf", entity.fileName)
        assertEquals("https://ultra.edu.kuleuven.cloud/ultra/redirect?x=1", entity.webUrl)
        assertFalse(entity.hasChildren)
    }

    @Test
    fun pageFoldersAreFlagged() {
        val dto = ContentDto(id = "_1_1", title = "Cursusmateriaal", hasChildren = true, contentHandler = ContentHandlerDto("resource/x-bb-folder", isBbPage = true))
        assertTrue(dto.toEntity("_9_1", null).isPage)
    }

    @Test
    fun mapsMembershipToCourse() {
        val membership = MembershipDto(
            courseId = "_10_1",
            availability = AvailabilityDto("Yes"),
            course = CourseDto(
                id = "_10_1",
                courseId = "ULTRA-B-KUL-G0T42a-2627",
                name = "Logica voor informatici [G0T42a]",
                availability = AvailabilityDto("No"),
            ),
        )
        val entity = membership.toEntity(sortIndex = 3, unreadAnnouncements = 2)!!
        assertEquals("Logica voor informatici", entity.name)
        assertEquals("G0T42a", entity.code)
        assertEquals("2627", entity.academicYear)
        assertFalse(entity.available)
        assertEquals(2, entity.unreadAnnouncements)
    }

    @Test
    fun announcementUsesDisplayTextAndReadStatus() {
        val dto = InternalAnnouncementDto(
            id = "_5_1",
            title = "Exam",
            body = InternalBodyDto(rawText = "<p>raw</p>", displayText = "<p>display</p>"),
            createdDate = "2026-10-16T08:23:45.506Z",
            startDateRestriction = "2026-10-17T08:00:00.000Z",
            readStatus = ReadStatusDto(isRead = true),
        )
        val entity = dto.toEntity("_9_1", readLocal = false)
        assertEquals("<p>display</p>", entity.bodyHtml)
        assertTrue(entity.readRemote)
        assertEquals(java.time.Instant.parse("2026-10-17T08:00:00Z").toEpochMilli(), entity.created)
    }

    @Test
    fun visibilityFiltersPastCoursesAndCommunities() {
        fun course(id: String, year: String?, kind: CourseKind = CourseKind.COURSE, available: Boolean = true) =
            Course(id, "x", id, null, kind, available, year, "", null, 0)
        val courses = listOf(
            course("current", "2627"),
            course("old", "2526"),
            course("community", null, CourseKind.COMMUNITY),
            course("closed", "2627", available = false),
        )
        val defaults = courses.visibleWith(UserPreferences(), currentYear = "2627").map { it.id }
        assertEquals(listOf("current"), defaults)

        val communities = courses.visibleWith(UserPreferences(showCommunities = true), currentYear = "2627").map { it.id }
        assertEquals(listOf("current", "community"), communities)

        val all = courses.visibleWith(UserPreferences(showCommunities = true, showPastCourses = true), currentYear = "2627").map { it.id }
        assertEquals(listOf("current", "old", "community", "closed"), all)
    }
}
