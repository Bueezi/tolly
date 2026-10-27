package be.tolly.core.data

import be.tolly.core.auth.ApplicationScope
import be.tolly.core.auth.AuthState
import be.tolly.core.auth.SessionManager
import be.tolly.core.data.download.DownloadRepository
import be.tolly.core.data.notifications.MailNotifier
import be.tolly.core.data.notifications.NewsNotifier
import be.tolly.core.database.MaintenanceDao
import be.tolly.core.model.User
import be.tolly.core.network.UltraApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: UltraApi,
    private val maintenanceDao: MaintenanceDao,
    private val downloads: DownloadRepository,
    private val preferences: PreferencesRepository,
    private val timetable: TimetableRepository,
    private val customizations: CourseCustomizationRepository,
    private val highlights: HighlightRepository,
    private val watchProgress: WatchProgressRepository,
    private val newsNotifier: NewsNotifier,
    private val mailNotifier: MailNotifier,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    val authState: StateFlow<AuthState> = sessionManager.state

    /**
     * Finishes a WebView login: takes over the session cookies and checks them against the API.
     * Call on the main thread right after the WebView reached Ultra.
     */
    fun captureWebLogin(): Boolean = sessionManager.captureWebLogin()

    suspend fun verifyCapturedSession(): Result<User> = dataCall {
        val user = try {
            api.me().toModel()
        } catch (e: Exception) {
            sessionManager.abandonLogin()
            throw e
        }
        val previous = (authState.value as? AuthState.SignedIn)?.user
        if (previous != null && previous.id != user.id) {
            // Someone else signed in: drop the previous account's data.
            maintenanceDao.clearCourseCache()
            downloads.deleteAll()
            preferences.clearAccountData()
        }
        // Names, colours, marks and watched recordings are kept through signing out and in again, not for someone else.
        val lastUser = preferences.lastUserId()
        if (lastUser == DEMO_USER_ID) maintenanceDao.clearCourseCache()
        if (lastUser != null && lastUser != user.id) {
            customizations.clearAll()
            highlights.clearAll()
            watchProgress.clearAll()
        }
        preferences.setLastUserId(user.id)
        sessionManager.completeSignIn(user)
        // The KU Leuven sign-in is fresh now, so KU Loket signs in silently for the timetable link.
        appScope.launch { timetable.prefetchFeed() }
        user
    }

    /**
     * Shows the demo account: the cache is emptied and filled with [seed]'s made-up data, and
     * nothing is kept for the next start. Leaves the demo by signing out, which clears the cache.
     */
    suspend fun startDemo(seed: suspend () -> Unit) {
        maintenanceDao.clearCourseCache()
        downloads.deleteAll()
        seed()
        // So a real sign-in afterwards clears what the demo left, even after a restart.
        preferences.setLastUserId(DEMO_USER_ID)
        sessionManager.enterDemo(User(DEMO_USER_ID, "r0000000", "Demo Student", "demo@student.example"))
    }

    suspend fun signOut(keepDownloads: Boolean = false) {
        sessionManager.signOut()
        maintenanceDao.clearCourseCache()
        preferences.clearAccountData()
        if (!keepDownloads) downloads.deleteAll()
        // They'd open an account that is gone.
        newsNotifier.clearAll()
        mailNotifier.clear()
    }

    suspend fun clearCache() {
        maintenanceDao.clearCourseCache()
    }

    private companion object {
        const val DEMO_USER_ID = "_demo_1"
    }
}
