package be.tolly.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import be.tolly.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY sortIndex")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    fun observe(id: String): Flow<CourseEntity?>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun get(id: String): CourseEntity?

    @Query("SELECT * FROM courses")
    suspend fun getAll(): List<CourseEntity>

    @Upsert
    suspend fun upsertAll(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE id NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<String>)

    @Query("UPDATE courses SET unreadAnnouncements = :count WHERE id = :courseId")
    suspend fun setUnreadAnnouncements(courseId: String, count: Int)

    @Transaction
    suspend fun replaceAll(courses: List<CourseEntity>) {
        deleteAllExcept(courses.map { it.id })
        upsertAll(courses)
    }
}

@Dao
interface ContentDao {
    @Query("SELECT * FROM contents WHERE courseId = :courseId AND parentId IS NULL ORDER BY position")
    fun observeRoot(courseId: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE courseId = :courseId AND parentId = :parentId ORDER BY position")
    fun observeChildren(courseId: String, parentId: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE id = :id")
    fun observe(id: String): Flow<ContentEntity?>

    @Query("SELECT * FROM contents WHERE id = :id")
    suspend fun get(id: String): ContentEntity?

    @Upsert
    suspend fun upsertAll(items: List<ContentEntity>)

    @Upsert
    suspend fun upsert(item: ContentEntity)

    @Query("DELETE FROM contents WHERE courseId = :courseId AND parentId IS NULL AND id NOT IN (:keep)")
    suspend fun deleteRootExcept(courseId: String, keep: List<String>)

    @Query("DELETE FROM contents WHERE courseId = :courseId AND parentId = :parentId AND id NOT IN (:keep)")
    suspend fun deleteChildrenExcept(courseId: String, parentId: String, keep: List<String>)

    @Transaction
    suspend fun replaceChildren(courseId: String, parentId: String?, items: List<ContentEntity>) {
        val keep = items.map { it.id }
        if (parentId == null) deleteRootExcept(courseId, keep) else deleteChildrenExcept(courseId, parentId, keep)
        upsertAll(items)
    }

    @Query("SELECT * FROM attachments WHERE courseId = :courseId AND contentId = :contentId ORDER BY fileName")
    fun observeAttachments(courseId: String, contentId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE courseId = :courseId AND contentId = :contentId ORDER BY fileName")
    suspend fun getAttachments(courseId: String, contentId: String): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE courseId = :courseId AND contentId = :contentId")
    suspend fun deleteAttachments(courseId: String, contentId: String)

    @Upsert
    suspend fun upsertAttachments(items: List<AttachmentEntity>)

    @Transaction
    suspend fun replaceAttachments(courseId: String, contentId: String, items: List<AttachmentEntity>) {
        deleteAttachments(courseId, contentId)
        upsertAttachments(items)
    }
}

/** SQL condition for an announcement the app shows as unread (see `AnnouncementWithCourse.toModel`). */
private const val UNREAD = "(unreadLocal = 1 OR (readRemote = 0 AND readLocal = 0))"

@Dao
interface AnnouncementDao {
    @Query(
        """
        SELECT a.*, c.name AS courseName
        FROM announcements a LEFT JOIN courses c ON c.id = a.courseId
        WHERE a.courseId = :courseId ORDER BY a.created DESC
        """,
    )
    fun observeForCourse(courseId: String): Flow<List<AnnouncementWithCourse>>

    @Query(
        """
        SELECT a.*, c.name AS courseName
        FROM announcements a INNER JOIN courses c ON c.id = a.courseId
        ORDER BY a.created DESC LIMIT :limit
        """,
    )
    fun observeAll(limit: Int): Flow<List<AnnouncementWithCourse>>

    @Query(
        """
        SELECT a.*, c.name AS courseName
        FROM announcements a LEFT JOIN courses c ON c.id = a.courseId
        WHERE a.id = :id
        """,
    )
    fun observe(id: String): Flow<AnnouncementWithCourse?>

    @Query("SELECT id FROM announcements WHERE courseId = :courseId AND readLocal = 1")
    suspend fun locallyReadIds(courseId: String): List<String>

    @Query("SELECT id FROM announcements WHERE courseId = :courseId")
    suspend fun idsForCourse(courseId: String): List<String>

    @Query("SELECT COUNT(*) FROM announcements")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(items: List<AnnouncementEntity>)

    @Query("DELETE FROM announcements WHERE courseId = :courseId AND id NOT IN (:keep)")
    suspend fun deleteForCourseExcept(courseId: String, keep: List<String>)

    @Transaction
    suspend fun replaceForCourse(courseId: String, items: List<AnnouncementEntity>) {
        deleteForCourseExcept(courseId, items.map { it.id })
        upsertAll(items)
    }

    /**
     * Replaces a course's announcements with what [settle] makes of the app's own read state
     * (read here, unread here, already known ids), read in the same transaction: a snapshot from
     * before a slow refresh would undo what the user marked meanwhile.
     */
    @Transaction
    suspend fun replaceForCourseSettled(
        courseId: String,
        settle: (readHere: Set<String>, unreadHere: Set<String>, known: Set<String>) -> List<AnnouncementEntity>,
    ) {
        val items = settle(locallyReadIds(courseId).toSet(), locallyUnreadIds(courseId).toSet(), idsForCourse(courseId).toSet())
        replaceForCourse(courseId, items)
    }

    /** Read (or unread) in the app, whatever Toledo says. */
    @Query("UPDATE announcements SET readLocal = :read, unreadLocal = NOT :read WHERE id IN (:ids)")
    suspend fun setReadLocally(ids: List<String>, read: Boolean)

    /** Back to Toledo's own read state, e.g. to undo "mark all as read". */
    @Query("UPDATE announcements SET readLocal = 0, unreadLocal = 0 WHERE id IN (:ids)")
    suspend fun clearLocalReadState(ids: List<String>)

    @Query("SELECT id FROM announcements WHERE courseId IN (:courseIds) AND $UNREAD")
    suspend fun unreadIds(courseIds: List<String>): List<String>

    @Query("SELECT DISTINCT courseId FROM announcements WHERE id IN (:ids)")
    suspend fun courseIdsOf(ids: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM announcements WHERE courseId = :courseId AND $UNREAD")
    suspend fun unreadCount(courseId: String): Int

    @Query("SELECT id FROM announcements WHERE courseId = :courseId AND unreadLocal = 1")
    suspend fun locallyUnreadIds(courseId: String): List<String>

    /** How many more (or fewer) unread announcements the app shows for a course than Toledo counts. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN readRemote = 1 AND unreadLocal = 1 THEN 1
            WHEN readRemote = 0 AND readLocal = 1 AND unreadLocal = 0 THEN -1 ELSE 0 END), 0)
        FROM announcements WHERE courseId = :courseId
        """,
    )
    suspend fun localUnreadDifference(courseId: String): Int
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE courseId = :courseId")
    fun observeForCourse(courseId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE `key` = :key")
    suspend fun get(key: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE `key` = :key")
    fun observe(key: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads")
    suspend fun getAll(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(item: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, status = :status, updatedAt = :now WHERE `key` = :key")
    suspend fun updateProgress(key: String, downloaded: Long, total: Long?, status: DownloadStatus, now: Long)

    @Query("UPDATE downloads SET status = :status, localPath = :localPath, error = :error, updatedAt = :now WHERE `key` = :key")
    suspend fun updateStatus(key: String, status: DownloadStatus, localPath: String?, error: String?, now: Long)

    @Query("DELETE FROM downloads WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}

@Dao
interface SyncStateDao {
    @Query("SELECT syncedAt FROM sync_state WHERE `key` = :key")
    fun observe(key: String): Flow<Long?>

    @Query("SELECT syncedAt FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): Long?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_items WHERE start >= :from AND start < :until ORDER BY start")
    fun observeBetween(from: Long, until: Long): Flow<List<CalendarItemEntity>>

    @Query("SELECT * FROM calendar_items WHERE start >= :from AND start < :until AND type = :type")
    suspend fun getBetween(from: Long, until: Long, type: String): List<CalendarItemEntity>

    @Query("SELECT * FROM calendar_items WHERE start >= :from AND start < :until AND type != :type")
    suspend fun getBetweenExcept(from: Long, until: Long, type: String): List<CalendarItemEntity>

    @Query("DELETE FROM calendar_items WHERE start >= :from AND start < :until")
    suspend fun deleteBetween(from: Long, until: Long)

    @Upsert
    suspend fun upsertAll(items: List<CalendarItemEntity>)

    /** Replaces the cached items of a time window with a fresh copy from the server. */
    @Transaction
    suspend fun replaceBetween(from: Long, until: Long, items: List<CalendarItemEntity>) {
        deleteBetween(from, until)
        upsertAll(items)
    }
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades WHERE courseId = :courseId ORDER BY position")
    fun observeForCourse(courseId: String): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades ORDER BY courseId, position")
    fun observeAll(): Flow<List<GradeEntity>>

    @Query("DELETE FROM grades WHERE courseId = :courseId")
    suspend fun deleteForCourse(courseId: String)

    @Upsert
    suspend fun upsertAll(items: List<GradeEntity>)

    @Transaction
    suspend fun replaceForCourse(courseId: String, items: List<GradeEntity>) {
        deleteForCourse(courseId)
        upsertAll(items)
    }
}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM courses") suspend fun deleteCourses()
    @Query("DELETE FROM contents") suspend fun deleteContents()
    @Query("DELETE FROM attachments") suspend fun deleteAttachments()
    @Query("DELETE FROM announcements") suspend fun deleteAnnouncements()
    @Query("DELETE FROM sync_state") suspend fun deleteSyncState()
    @Query("DELETE FROM calendar_items") suspend fun deleteCalendar()
    @Query("DELETE FROM grades") suspend fun deleteGrades()

    /** Clears the cached course data but keeps the download bookkeeping. */
    @Transaction
    suspend fun clearCourseCache() {
        deleteCourses()
        deleteContents()
        deleteAttachments()
        deleteAnnouncements()
        deleteSyncState()
        deleteCalendar()
        deleteGrades()
    }
}
