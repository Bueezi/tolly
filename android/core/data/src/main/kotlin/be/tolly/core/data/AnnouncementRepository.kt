package be.tolly.core.data

import be.tolly.core.database.AnnouncementDao
import be.tolly.core.database.CourseDao
import be.tolly.core.database.SyncStateDao
import be.tolly.core.database.SyncStateEntity
import be.tolly.core.model.Announcement
import be.tolly.core.model.Course
import be.tolly.core.model.Highlight
import be.tolly.core.network.UltraApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementRepository @Inject constructor(
    private val api: UltraApi,
    private val announcementDao: AnnouncementDao,
    private val courseDao: CourseDao,
    private val syncStateDao: SyncStateDao,
    private val courseRepository: CourseRepository,
    private val preferences: PreferencesRepository,
    private val highlightRepository: HighlightRepository,
) {
    /** Customised courses by id, to show announcements with the course's own name, icon and colour. */
    private val courses: Flow<Map<String, Course>> = courseRepository.observeCourses().map { list -> list.associateBy { it.id } }

    private val decoration: Flow<Pair<Map<String, Course>, Highlights>> = combine(courses, highlightRepository.highlights, ::Pair)

    fun observeForCourse(courseId: String): Flow<List<Announcement>> =
        combine(announcementDao.observeForCourse(courseId), decoration) { list, (byId, marks) -> list.map { it.toModel().decorated(byId, marks) } }

    fun observeAll(limit: Int = 300): Flow<List<Announcement>> =
        combine(announcementDao.observeAll(limit), decoration) { list, (byId, marks) -> list.map { it.toModel().decorated(byId, marks) } }

    fun observe(id: String): Flow<Announcement?> =
        combine(announcementDao.observe(id), decoration) { entity, (byId, marks) -> entity?.toModel()?.decorated(byId, marks) }

    /** With the course's own name, icon and colour, and the user's highlight. */
    private fun Announcement.decorated(courses: Map<String, Course>, marks: Highlights): Announcement {
        val highlighted = copy(highlight = marks[HighlightTarget.ANNOUNCEMENT, id])
        val course = courses[courseId] ?: return highlighted
        return highlighted.copy(courseName = course.displayName, courseIcon = course.icon, courseColor = course.color)
    }

    fun observeLastSynced(): Flow<Long?> = syncStateDao.observe(ALL_KEY)

    suspend fun refreshCourse(courseId: String): Result<Unit> = refreshCall { fetchCourse(courseId, newsBaseline()) }

    /** Refreshes the given courses in parallel; fails only if every course failed or the session expired. */
    suspend fun refreshCourses(courseIds: List<String>): Result<Unit> = refreshCall {
        val baseline = newsBaseline()
        val failures = courseIds.mapParallel(CONCURRENCY) { fetchCourse(it, baseline) }.mapNotNull { it.exceptionOrNull() }
        if (courseIds.isNotEmpty() && failures.size == courseIds.size) throw failures.first()
        syncStateDao.upsert(SyncStateEntity(ALL_KEY, System.currentTimeMillis()))
    }

    /** Marks an announcement as read inside the app only; nothing is sent to Toledo. */
    suspend fun markReadLocally(announcement: Announcement) {
        if (announcement.isRead) return
        announcementDao.setReadLocally(listOf(announcement.id), true)
        courseDao.setUnreadAnnouncements(announcement.courseId, announcementDao.unreadCount(announcement.courseId))
    }

    /**
     * Marks every unread announcement of [courseIds] as read inside the app. Returns their ids so
     * the caller can undo with [markUnreadLocally].
     */
    suspend fun markAllReadLocally(courseIds: Collection<String>): List<String> {
        val ids = courseIds.chunked(MAX_SQL_ARGS).flatMap { announcementDao.unreadIds(it) }
        setReadLocally(ids, read = true)
        return ids
    }

    /** Undoes [markAllReadLocally]: the announcements get Toledo's read state back. */
    suspend fun markUnreadLocally(ids: List<String>) = updateReadState(ids) { announcementDao.clearLocalReadState(it) }

    /** Marks one announcement read or unread in the app (a swipe), whatever Toledo says. */
    suspend fun setRead(announcement: Announcement, read: Boolean) =
        updateReadState(listOf(announcement.id)) { announcementDao.setReadLocally(it, read) }

    suspend fun toggleHighlight(announcement: Announcement, highlight: Highlight) =
        highlightRepository.toggle(HighlightTarget.ANNOUNCEMENT, announcement.id, highlight)

    private suspend fun setReadLocally(ids: List<String>, read: Boolean) = updateReadState(ids) { announcementDao.setReadLocally(it, read) }

    private suspend fun updateReadState(ids: List<String>, update: suspend (List<String>) -> Unit) {
        if (ids.isEmpty()) return
        val courses = mutableSetOf<String>()
        ids.chunked(MAX_SQL_ARGS).forEach { chunk ->
            update(chunk)
            courses += announcementDao.courseIdsOf(chunk)
        }
        courses.forEach { courseDao.setUnreadAnnouncements(it, announcementDao.unreadCount(it)) }
    }

    /**
     * Announcements posted before this count as read when they first show up. Set on the first
     * refresh after signing in, so the feed starts out read; an older install that already has
     * announcements keeps their state (baseline 0).
     */
    private suspend fun newsBaseline(): Long =
        preferences.newsBaseline() ?: (if (announcementDao.count() > 0) 0L else System.currentTimeMillis())
            .also { preferences.setNewsBaseline(it) }

    private suspend fun fetchCourse(courseId: String, baseline: Long) {
        val entities = try {
            api.announcementsWithReadStatus(courseId).results
                .filterNot { it.isDraft }
                .map { it.toEntity(courseId, readLocal = false) }
        } catch (e: HttpException) {
            if (e.isSessionError()) throw e
            // The internal endpoint is undocumented; fall back to the public API without read status.
            api.announcements(courseId).results
                .filterNot { it.draft }
                .map { it.toEntity(courseId, readLocal = false) }
        }
        announcementDao.replaceForCourseSettled(courseId) { readHere, unreadHere, known ->
            entities.map {
                when {
                    it.id in unreadHere -> it.copy(unreadLocal = true)
                    it.id in readHere -> it.copy(readLocal = true)
                    it.id !in known && it.created < baseline -> it.copy(readLocal = true)
                    else -> it
                }
            }
        }
        courseDao.setUnreadAnnouncements(courseId, announcementDao.unreadCount(courseId))
    }

    private companion object {
        const val ALL_KEY = "announcements"
        const val CONCURRENCY = 4
        /** Stays well below SQLite's limit on query parameters. */
        const val MAX_SQL_ARGS = 500
    }
}
