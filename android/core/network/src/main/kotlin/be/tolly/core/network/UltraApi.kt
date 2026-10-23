package be.tolly.core.network

import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.network.dto.AnnouncementCountsDto
import be.tolly.core.network.dto.AnnouncementDto
import be.tolly.core.network.dto.AttachmentDto
import be.tolly.core.network.dto.CalendarItemDto
import be.tolly.core.network.dto.ContentDto
import be.tolly.core.network.dto.GradeColumnDto
import be.tolly.core.network.dto.GradeDto
import be.tolly.core.network.dto.InternalAnnouncementDto
import be.tolly.core.network.dto.MembershipDto
import be.tolly.core.network.dto.PagedDto
import be.tolly.core.network.dto.UserDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Blackboard Learn REST API as used by KU Leuven's Ultra. `public/v1` endpoints are documented by
 * Anthology; `learn/api/v1` endpoints are internal to the Ultra UI and only used for extras.
 */
interface UltraApi {
    @GET("learn/api/public/v1/users/me")
    suspend fun me(): UserDto

    @GET("learn/api/public/v1/users/me/courses")
    suspend fun myCourses(
        @Query("expand") expand: String = "course",
        @Query("limit") limit: Int = 100,
        @Query("fields") fields: String = MEMBERSHIP_FIELDS,
    ): PagedDto<MembershipDto>

    @GET
    suspend fun myCoursesPage(@Url url: String): PagedDto<MembershipDto>

    @GET("learn/api/public/v1/courses/{courseId}/contents")
    suspend fun rootContents(
        @Path("courseId") courseId: String,
        @Query("limit") limit: Int = 200,
    ): PagedDto<ContentDto>

    @GET("learn/api/public/v1/courses/{courseId}/contents/{contentId}/children")
    suspend fun children(
        @Path("courseId") courseId: String,
        @Path("contentId") contentId: String,
        @Query("limit") limit: Int = 200,
    ): PagedDto<ContentDto>

    @GET
    suspend fun contentsPage(@Url url: String): PagedDto<ContentDto>

    @GET("learn/api/public/v1/courses/{courseId}/contents/{contentId}")
    suspend fun content(
        @Path("courseId") courseId: String,
        @Path("contentId") contentId: String,
    ): ContentDto

    @GET("learn/api/public/v1/courses/{courseId}/contents/{contentId}/attachments")
    suspend fun attachments(
        @Path("courseId") courseId: String,
        @Path("contentId") contentId: String,
    ): PagedDto<AttachmentDto>

    @GET("learn/api/public/v1/courses/{courseId}/announcements")
    suspend fun announcements(
        @Path("courseId") courseId: String,
        @Query("limit") limit: Int = 100,
    ): PagedDto<AnnouncementDto>

    @GET("learn/api/v1/courses/{courseId}/announcements")
    suspend fun announcementsWithReadStatus(
        @Path("courseId") courseId: String,
        @Query("limit") limit: Int = 100,
    ): PagedDto<InternalAnnouncementDto>

    @GET("learn/api/v1/courses/{courseId}/announcements/counts")
    suspend fun announcementCounts(@Path("courseId") courseId: String): AnnouncementCountsDto

    /** [type] and [calendarId] narrow the search; null leaves them out. */
    @GET("learn/api/public/v1/calendars/items")
    suspend fun calendarItems(
        @Query("since") since: String,
        @Query("until") until: String,
        @Query("type") type: String? = null,
        @Query("calendarId") calendarId: String? = null,
        @Query("limit") limit: Int = 200,
    ): PagedDto<CalendarItemDto>

    /** The user's calendars; returned as raw JSON because its shape isn't documented precisely. */
    @GET("learn/api/public/v1/calendars")
    suspend fun calendars(): JsonElement

    @GET
    suspend fun calendarItemsPage(@Url url: String): PagedDto<CalendarItemDto>

    @GET("learn/api/public/v1/courses/{courseId}/gradebook/users/{userId}")
    suspend fun userGrades(
        @Path("courseId") courseId: String,
        @Path("userId") userId: String,
    ): PagedDto<GradeDto>

    @GET
    suspend fun gradesPage(@Url url: String): PagedDto<GradeDto>

    @GET("learn/api/public/v2/courses/{courseId}/gradebook/columns")
    suspend fun gradeColumns(@Path("courseId") courseId: String): PagedDto<GradeColumnDto>

    @GET
    suspend fun gradeColumnsPage(@Url url: String): PagedDto<GradeColumnDto>

    @GET("learn/api/public/v2/courses/{courseId}/gradebook/columns/{columnId}")
    suspend fun gradeColumn(@Path("courseId") courseId: String, @Path("columnId") columnId: String): GradeColumnDto

    companion object {
        const val MEMBERSHIP_FIELDS =
            "id,courseId,availability,courseRoleId,lastAccessed," +
                "course.id,course.courseId,course.name,course.organization,course.availability,course.externalAccessUrl"

        fun attachmentDownloadUrl(courseId: String, contentId: String, attachmentId: String): String =
            UltraEndpoints.BASE_URL +
                "learn/api/public/v1/courses/$courseId/contents/$contentId/attachments/$attachmentId/download"

        fun courseWebUrl(courseId: String): String = UltraEndpoints.BASE_URL + "ultra/courses/$courseId/outline"
    }
}

/** Follows `paging.nextPage` links until all results are collected. */
suspend fun <T> collectAllPages(
    first: PagedDto<T>,
    maxPages: Int = 20,
    next: suspend (String) -> PagedDto<T>,
): List<T> {
    val results = first.results.toMutableList()
    var page = first
    var count = 1
    while (count < maxPages) {
        val nextUrl = page.nextPage ?: break
        page = next(nextUrl.removePrefix("/"))
        results += page.results
        count++
    }
    return results
}
