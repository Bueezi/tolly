package be.tolly.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import be.tolly.core.model.ContentType
import be.tolly.core.model.CourseKind
import be.tolly.core.model.DownloadStatus

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val externalId: String,
    val name: String,
    val code: String?,
    val kind: CourseKind,
    val available: Boolean,
    val academicYear: String?,
    val webUrl: String,
    val lastAccessed: Long?,
    val unreadAnnouncements: Int,
    /** Position in the server response, used as a stable tiebreaker. */
    val sortIndex: Int,
)

/** Content items; root items have `parentId = null`. */
@Entity(
    tableName = "contents",
    indices = [Index(value = ["courseId", "parentId"])],
)
data class ContentEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val parentId: String?,
    val title: String,
    val description: String?,
    val type: ContentType,
    val isPage: Boolean,
    val position: Int,
    val hasChildren: Boolean,
    val body: String?,
    val url: String?,
    val fileName: String?,
    val mimeType: String?,
    val webUrl: String,
    val modified: Long?,
    val available: Boolean,
)

@Entity(
    tableName = "attachments",
    indices = [Index(value = ["courseId", "contentId"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val contentId: String,
    val fileName: String,
    val mimeType: String?,
)

@Entity(
    tableName = "announcements",
    indices = [Index(value = ["courseId"]), Index(value = ["created"])],
)
data class AnnouncementEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val bodyHtml: String,
    val created: Long,
    val modified: Long?,
    /** Read on the server (from Ultra's read tracking). */
    val readRemote: Boolean,
    /** Opened in this app; never sent to the server. */
    val readLocal: Boolean,
    /** Marked unread in this app, which wins over [readRemote] and [readLocal]. */
    @ColumnInfo(defaultValue = "0") val unreadLocal: Boolean = false,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val key: String,
    val courseId: String,
    val courseName: String?,
    val contentId: String?,
    val title: String,
    val fileName: String,
    val mimeType: String?,
    val sourceUrl: String,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val localPath: String?,
    val error: String?,
    val updatedAt: Long,
    /** Folders between the course and the file, joined with '/', for the copy in public Downloads. */
    @ColumnInfo(defaultValue = "") val folderPath: String = "",
)

/** When a given part of the cache was last refreshed from the network. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val syncedAt: Long,
)

/** Announcement row joined with its course name. */
data class AnnouncementWithCourse(
    @Embedded val announcement: AnnouncementEntity,
    val courseName: String?,
)

/** An entry of the Toledo calendar: a course event, a due date (gradebook column) or a personal item. */
@Entity(tableName = "calendar_items", indices = [Index("start")])
data class CalendarItemEntity(
    @PrimaryKey val id: String,
    /** Blackboard's type: Course, GradebookColumn, Institution, OfficeHours or Personal. */
    val type: String,
    val calendarId: String?,
    val calendarName: String?,
    val title: String,
    val description: String?,
    val location: String?,
    val start: Long,
    val end: Long?,
    /** Due dates of gradable items (assignments, tests). */
    val gradable: Boolean,
    /**
     * For a timetable lesson, TimeEdit's description: the activity and the group on lines of
     * their own (`15 Oefenzitting`, `G0T43a_InfR4`), to find the same session of other groups.
     */
    @ColumnInfo(defaultValue = "NULL") val timetableDetails: String? = null,
)

/** The signed-in user's grade for one gradebook column of a course. */
@Entity(tableName = "grades", primaryKeys = ["courseId", "columnId"])
data class GradeEntity(
    val courseId: String,
    val columnId: String,
    val name: String?,
    /** What Toledo shows, e.g. "15/20", "A" or "Passed". */
    val displayGrade: String?,
    val score: Double?,
    val possible: Double?,
    /** Blackboard's grade status, e.g. Graded or NeedsGrading. */
    val status: String?,
    val feedback: String?,
    val dueDate: Long?,
    val position: Int,
)
