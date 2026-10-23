package be.tolly.core.data

import be.tolly.core.database.AnnouncementDao
import be.tolly.core.database.CourseDao
import be.tolly.core.database.SyncStateDao
import be.tolly.core.database.SyncStateEntity
import be.tolly.core.model.Course
import be.tolly.core.model.CourseKind
import be.tolly.core.model.CourseNaming
import be.tolly.core.model.UserPreferences
import be.tolly.core.network.UltraApi
import be.tolly.core.network.collectAllPages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val api: UltraApi,
    private val courseDao: CourseDao,
    private val announcementDao: AnnouncementDao,
    private val syncStateDao: SyncStateDao,
    private val customization: CourseCustomizationRepository,
    private val highlights: HighlightRepository,
) {
    /** Courses with the user's names, icons, colours, order and highlights applied. */
    fun observeCourses(): Flow<List<Course>> =
        combine(courseDao.observeAll(), customization.customizations, highlights.highlights) { list, custom, marks ->
            list.map { it.toModel().customizedWith(custom).copy(highlight = marks[HighlightTarget.COURSE, it.id]) }
        }

    fun observeCourse(id: String): Flow<Course?> =
        combine(courseDao.observe(id), customization.customizations, highlights.highlights) { entity, custom, marks ->
            entity?.toModel()?.customizedWith(custom)?.copy(highlight = marks[HighlightTarget.COURSE, id])
        }

    fun observeLastSynced(): Flow<Long?> = syncStateDao.observe(SYNC_KEY)

    suspend fun lastSynced(): Long? = syncStateDao.get(SYNC_KEY)

    /** Reloads the course list and the unread announcement badges of the active courses. */
    suspend fun refresh(): Result<Unit> = refreshCall {
        val memberships = collectAllPages(api.myCourses()) { api.myCoursesPage(it) }
        val existing = courseDao.getAll().associateBy { it.id }
        val entities = memberships.mapIndexedNotNull { index, membership ->
            membership.toEntity(index, existing[membership.course?.id]?.unreadAnnouncements ?: 0)
        }.distinctBy { it.id }
        courseDao.replaceAll(entities)
        syncStateDao.upsert(SyncStateEntity(SYNC_KEY, System.currentTimeMillis()))

        refreshUnreadCounts(entities.map { it.toModel() }.filter { it.isActive() })
    }

    /** Badges are best effort: a course whose count fails keeps its badge (the internal endpoint may disappear). */
    private suspend fun refreshUnreadCounts(courses: List<Course>) {
        courses.mapParallel(CONCURRENCY) { course ->
            val remoteUnread = api.announcementCounts(course.id).unreadCount
            val difference = announcementDao.localUnreadDifference(course.id)
            courseDao.setUnreadAnnouncements(course.id, (remoteUnread + difference).coerceAtLeast(0))
        }
    }

    private companion object {
        const val SYNC_KEY = "courses"
        const val CONCURRENCY = 4
    }
}

/** A course the student currently works with: available and this academic year (or a community). */
fun Course.isActive(currentYear: String = CourseNaming.currentAcademicYear()): Boolean =
    available && (academicYear == null || academicYear == currentYear || kind == CourseKind.COMMUNITY)

/** Applies the user's list filters. */
fun List<Course>.visibleWith(prefs: UserPreferences, currentYear: String = CourseNaming.currentAcademicYear()): List<Course> =
    filter { course ->
        val communityOk = prefs.showCommunities || course.kind != CourseKind.COMMUNITY
        val yearOk = prefs.showPastCourses || course.isActive(currentYear)
        communityOk && yearOk
    }
