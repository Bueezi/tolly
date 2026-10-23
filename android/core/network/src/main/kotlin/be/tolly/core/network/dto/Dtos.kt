package be.tolly.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PagedDto<T>(
    val results: List<T> = emptyList(),
    val paging: PagingDto? = null,
) {
    /** Relative URL of the next page, or null on the last page (Ultra uses both null and ""). */
    val nextPage: String? get() = paging?.nextPage?.takeIf { it.isNotBlank() }
}

@Serializable
data class PagingDto(val nextPage: String? = null)

@Serializable
data class UserDto(
    val id: String,
    val userName: String = "",
    /** Blackboard's student id field; whether KU Leuven fills it in is unknown. */
    val studentId: String? = null,
    val name: NameDto? = null,
    val contact: ContactDto? = null,
)

@Serializable
data class NameDto(
    val given: String? = null,
    val family: String? = null,
    val preferredDisplayName: String? = null,
    val preferred: String? = null,
)

@Serializable
data class ContactDto(val email: String? = null)

@Serializable
data class AvailabilityDto(val available: String? = null)

@Serializable
data class MembershipDto(
    val id: String? = null,
    val courseId: String,
    val availability: AvailabilityDto? = null,
    val courseRoleId: String? = null,
    val lastAccessed: String? = null,
    val course: CourseDto? = null,
)

@Serializable
data class CourseDto(
    val id: String,
    val courseId: String = "",
    val name: String = "",
    val description: String? = null,
    val organization: Boolean = false,
    val availability: AvailabilityDto? = null,
    val externalAccessUrl: String? = null,
)

@Serializable
data class ContentDto(
    val id: String,
    val parentId: String? = null,
    val title: String = "",
    val description: String? = null,
    val body: String? = null,
    val created: String? = null,
    val modified: String? = null,
    val position: Int = 0,
    val hasChildren: Boolean? = null,
    val availability: AvailabilityDto? = null,
    val contentHandler: ContentHandlerDto? = null,
    val links: List<LinkDto> = emptyList(),
)

@Serializable
data class ContentHandlerDto(
    val id: String = "",
    val url: String? = null,
    val file: FileDto? = null,
    val isBbPage: Boolean? = null,
)

@Serializable
data class FileDto(
    val fileName: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class LinkDto(
    val href: String? = null,
    val rel: String? = null,
)

@Serializable
data class AttachmentDto(
    val id: String,
    val fileName: String = "",
    val mimeType: String? = null,
)

/** Public REST announcement. */
@Serializable
data class AnnouncementDto(
    val id: String,
    val title: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val created: String? = null,
    val modified: String? = null,
)

/** Ultra-internal announcement (`/learn/api/v1`), which also carries the read status. */
@Serializable
data class InternalAnnouncementDto(
    val id: String,
    val title: String = "",
    val body: InternalBodyDto? = null,
    val createdDate: String? = null,
    val modifiedDate: String? = null,
    val startDateRestriction: String? = null,
    val isDraft: Boolean = false,
    val readStatus: ReadStatusDto? = null,
)

@Serializable
data class InternalBodyDto(
    val rawText: String? = null,
    val displayText: String? = null,
)

@Serializable
data class ReadStatusDto(val isRead: Boolean = false)

@Serializable
data class AnnouncementCountsDto(
    val unreadCount: Int = 0,
    val postedCount: Int = 0,
)

/** `GET /learn/api/public/v1/calendars/items`: course events, due dates and personal items. */
@Serializable
data class CalendarItemDto(
    val id: String,
    /** Course, GradebookColumn, Institution, OfficeHours or Personal. */
    val type: String? = null,
    val calendarId: String? = null,
    val calendarName: String? = null,
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val start: String? = null,
    val end: String? = null,
    val dynamicCalendarItemProps: DynamicCalendarItemPropsDto? = null,
)

@Serializable
data class DynamicCalendarItemPropsDto(
    val gradable: Boolean? = null,
    val attemptable: Boolean? = null,
    val eventType: String? = null,
)

/** One of the signed-in user's grades: `GET /learn/api/public/v1/courses/{id}/gradebook/users/{userId}`. */
@Serializable
data class GradeDto(
    val userId: String? = null,
    val columnId: String,
    val status: String? = null,
    val displayGrade: DisplayGradeDto? = null,
    val text: String? = null,
    val score: Double? = null,
    val feedback: String? = null,
    val exempt: Boolean? = null,
)

@Serializable
data class DisplayGradeDto(
    val scaleType: String? = null,
    val score: Double? = null,
    val possible: Double? = null,
    val text: String? = null,
)

/** A gradebook column: `GET /learn/api/public/v2/courses/{id}/gradebook/columns`. */
@Serializable
data class GradeColumnDto(
    val id: String,
    val name: String? = null,
    val displayName: String? = null,
    val score: ColumnScoreDto? = null,
    val availability: AvailabilityDto? = null,
    val grading: ColumnGradingDto? = null,
)

@Serializable
data class ColumnScoreDto(val possible: Double? = null)

@Serializable
data class ColumnGradingDto(val due: String? = null, val type: String? = null)
