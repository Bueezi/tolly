package be.tolly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.auth.AuthState
import be.tolly.core.auth.ToledoPortal
import be.tolly.core.data.AuthRepository
import be.tolly.core.data.CourseRepository
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.SyncRepository
import be.tolly.core.data.notifications.MailNotifier
import be.tolly.core.data.notifications.NewsCheckScheduler
import be.tolly.core.data.visibleWith
import be.tolly.core.model.Notices
import be.tolly.core.model.UserPreferences
import be.tolly.demo.DemoSeeder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A request (from a notification) to show the feed, optionally on one announcement. */
data class OpenNewsRequest(val announcementId: String?)

data class MainUiState(
    val auth: AuthState = AuthState.Loading,
    val preferences: UserPreferences = UserPreferences(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    courseRepository: CourseRepository,
    private val sync: SyncRepository,
    private val demoSeeder: DemoSeeder,
    private val authRepository: AuthRepository,
    private val portal: ToledoPortal,
    newsCheckScheduler: NewsCheckScheduler,
    private val mailNotifier: MailNotifier,
) : ViewModel() {
    private val _unreadMail = MutableStateFlow<Int?>(null)

    /** Unread KU Leuven mail, as the Toledo portal counts it; null until known. */
    val unreadMail: StateFlow<Int?> = _unreadMail.asStateFlow()
    private var mailAskedAt = 0L

    /**
     * Asks the portal for the unread mail count: when the app comes to the front (at most every
     * few minutes) and, [force]d, after the user leaves the Mail tab, where they may have read some.
     */
    fun refreshUnreadMail(force: Boolean = false) {
        val auth = authRepository.authState.value
        if (auth !is AuthState.SignedIn || auth.demo) return
        val now = System.currentTimeMillis()
        if (!force && now - mailAskedAt < MAIL_INTERVAL_MS) return
        mailAskedAt = now
        viewModelScope.launch {
            val unread = portal.unreadMail() ?: return@launch
            _unreadMail.value = unread
            // Seen in the app, so the background check won't call it new.
            preferencesRepository.setLastMailUnread(unread)
            if (unread == 0) mailNotifier.clear()
        }
    }

    private val _notice = MutableStateFlow<Int?>(null)

    /** The one-time notice to show now ([Notices]), if any. */
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    /** Puts the notice away for good. */
    fun dismissNotice() {
        val id = _notice.value ?: return
        _notice.value = null
        viewModelScope.launch { preferencesRepository.setLastNotice(id) }
    }

    private val _openMail = MutableStateFlow(false)

    /** Whether a notification asked to show the Mail tab. */
    val openMail: StateFlow<Boolean> = _openMail.asStateFlow()

    fun requestOpenMail() {
        _openMail.value = true
    }

    fun openMailHandled() {
        _openMail.value = false
    }

    private val _openNews = MutableStateFlow<OpenNewsRequest?>(null)
    val openNews: StateFlow<OpenNewsRequest?> = _openNews.asStateFlow()

    fun requestOpenNews(announcementId: String?) {
        _openNews.value = OpenNewsRequest(announcementId)
    }

    /** The welcome screen's Demo button: the app with made-up data, no sign-in. */
    fun startDemo() {
        viewModelScope.launch { authRepository.startDemo(demoSeeder::seed) }
    }

    /** Switches between the two looks (a shake of the phone). */
    fun toggleGlass() {
        viewModelScope.launch { preferencesRepository.setGlass(!uiState.value.preferences.glass) }
    }

    fun openNewsHandled() {
        _openNews.value = null
    }


    val uiState: StateFlow<MainUiState> = combine(authRepository.authState, preferencesRepository.preferences, ::MainUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    /** Unread announcements across visible courses, for the navigation badge. */
    val unreadAnnouncements: StateFlow<Int> = combine(courseRepository.observeCourses(), preferencesRepository.preferences) { courses, prefs ->
        courses.visibleWith(prefs).sumOf { it.unreadAnnouncements }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            val auth = authRepository.authState.first { it != AuthState.Loading }
            val signedIn = auth as? AuthState.SignedIn
            when (val decision = Notices.decide(preferencesRepository.lastNotice(), signedIn != null, signedIn?.demo == true)) {
                is Notices.Decision.Show -> _notice.value = decision.id
                Notices.Decision.MarkSeen -> preferencesRepository.setLastNotice(Notices.LATEST)
                Notices.Decision.None -> Unit
            }
        }
        // Keep the background check in line with the settings, and off while signed out (or in the demo).
        viewModelScope.launch {
            combine(preferencesRepository.preferences, authRepository.authState.filter { it != AuthState.Loading }) { prefs, auth ->
                val wanted = prefs.notifyAnnouncements || prefs.notifyMail
                (wanted && auth is AuthState.SignedIn && !auth.demo) to prefs.newsCheckMinutes
            }
                .distinctUntilChanged()
                .collect { (enabled, minutes) -> newsCheckScheduler.apply(enabled, minutes) }
        }
        // Refresh after the user signed in again following an expired session.
        viewModelScope.launch {
            authRepository.authState
                .map { (it as? AuthState.SignedIn)?.sessionValid }
                .distinctUntilChanged()
                .runningFold(null as Pair<Boolean?, Boolean?>?) { previous, valid -> (previous?.second) to valid }
                .collect { transition ->
                    if (transition != null && transition.first == false && transition.second == true) sync.refreshAll()
                }
        }
    }

    private companion object {
        const val MAIL_INTERVAL_MS = 2 * 60_000L
    }
}
