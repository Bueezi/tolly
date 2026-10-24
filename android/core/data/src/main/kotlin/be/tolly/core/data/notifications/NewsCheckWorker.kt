package be.tolly.core.data.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.auth.ToledoPortal
import be.tolly.core.data.AnnouncementRepository
import be.tolly.core.data.CourseRepository
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.SyncRepository
import be.tolly.core.data.visibleWith
import be.tolly.core.model.UserPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * As often as the user chose (15 min to 6 h): refreshes the announcements of visible courses and
 * notifies about the new, unread ones, and asks the KU Leuven portal how much mail is unread,
 * notifying when that went up.
 */
@HiltWorker
class NewsCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferences: PreferencesRepository,
    private val sync: SyncRepository,
    private val announcements: AnnouncementRepository,
    private val courses: CourseRepository,
    private val notifier: NewsNotifier,
    private val portal: ToledoPortal,
    private val mailNotifier: MailNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val prefs = preferences.preferences.first()
        // Mail and announcements at the same time: the radio wakes once, and for less long.
        val mail = if (prefs.notifyMail) async { checkMail() } else null
        val result = if (prefs.notifyAnnouncements) checkAnnouncements(prefs) else Result.success()
        mail?.await()
        result
    }

    private suspend fun checkAnnouncements(prefs: UserPreferences): Result {
        sync.refreshAnnouncements().onFailure {
            Log.w(TAG, "Announcement check failed", it)
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        val since = preferences.lastNotified()
        val visible = courses.observeCourses().first().visibleWith(prefs).map { it.id }.toSet()
        val fresh = announcements.observeAll().first()
            .filter { it.created > since && !it.isRead && it.courseId in visible }
            .sortedBy { it.created }
        if (fresh.isNotEmpty()) {
            notifier.notify(fresh.takeLast(MAX_PER_RUN))
            preferences.setLastNotified(fresh.maxOf { it.created })
        }
        return Result.success()
    }

    /**
     * Only the unread count is known, so mail counts as new when it went up since the last look
     * (here or in the app). The first check after turning this on only notes the count.
     */
    private suspend fun checkMail() {
        val unread = portal.unreadMail() ?: run {
            Log.w(TAG, "No unread mail count")
            return
        }
        val last = preferences.lastMailUnread()
        if (last != null && unread > last) mailNotifier.notify(fresh = unread - last, unread = unread)
        if (unread == 0) mailNotifier.clear()
        preferences.setLastMailUnread(unread)
    }

    private companion object {
        const val TAG = "TollyNews"
        const val MAX_PER_RUN = 10
    }
}

/** Starts, stops or reschedules the periodic check for announcements and mail. */
@Singleton
class NewsCheckScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Runs the check every [intervalMinutes] while [enabled]; a new interval replaces the old schedule. */
    fun apply(enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // The check may run anywhere in the last third of each period (at least WorkManager's 5 minutes).
        val flexMinutes = (intervalMinutes / 3L).coerceAtLeast(5)
        val request = PeriodicWorkRequestBuilder<NewsCheckWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES, flexMinutes, TimeUnit.MINUTES)
            // Checks wait while the battery is low (about 15 %) and go on once it is charging or above that.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "news-check"
    }
}
