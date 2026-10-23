package be.tolly.core.data

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Refreshes everything the home screens show: the course list, then announcements of visible courses. */
@Singleton
class SyncRepository @Inject constructor(
    private val courses: CourseRepository,
    private val announcements: AnnouncementRepository,
    private val preferences: PreferencesRepository,
) {
    suspend fun refreshAll(): Result<Unit> {
        courses.refresh().onFailure { return Result.failure(it) }
        return refreshAnnouncements()
    }

    suspend fun refreshAnnouncements(): Result<Unit> {
        val prefs = preferences.preferences.first()
        val ids = courses.observeCourses().first()
            .visibleWith(prefs)
            .filter { it.available }
            .map { it.id }
        return announcements.refreshCourses(ids)
    }

    /** Refreshes when the cache is older than [maxAgeMillis]. */
    suspend fun refreshIfStale(maxAgeMillis: Long = STALE_AFTER_MS): Result<Unit> {
        val last = courses.lastSynced() ?: 0L
        return if (System.currentTimeMillis() - last > maxAgeMillis) refreshAll() else Result.success(Unit)
    }

    private companion object {
        const val STALE_AFTER_MS = 15 * 60 * 1000L
    }
}
