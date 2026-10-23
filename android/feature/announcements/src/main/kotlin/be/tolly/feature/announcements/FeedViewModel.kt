package be.tolly.feature.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.AnnouncementRepository
import be.tolly.core.data.CourseRepository
import be.tolly.core.data.DataError
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.SyncRepository
import be.tolly.core.data.visibleWith
import be.tolly.core.model.Announcement
import be.tolly.core.model.Highlight
import be.tolly.core.ui.Refresher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val items: List<Announcement> = emptyList(),
    /** The unread ones of [items], for the feed's Unread page. */
    val unreadItems: List<Announcement> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: DataError? = null,
    /** Offer to turn on notifications (once, until the user answers). */
    val showNotificationPrompt: Boolean = false,
)

/** Announcements of all visible courses, newest first. */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val announcementRepository: AnnouncementRepository,
    courseRepository: CourseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val sync: SyncRepository,
) : ViewModel() {
    private val refresher = Refresher(viewModelScope)
    private val undoChannel = Channel<List<String>>(Channel.BUFFERED)

    /** Ids marked read by [markAllRead], for an Undo snackbar. */
    val markedRead: Flow<List<String>> = undoChannel.receiveAsFlow()

    private val visibleCourseIds: Flow<Set<String>> =
        combine(courseRepository.observeCourses(), preferencesRepository.preferences) { courses, prefs ->
            courses.visibleWith(prefs).map { it.id }.toSet()
        }

    val uiState: StateFlow<FeedUiState> = combine(
        announcementRepository.observeAll(),
        visibleCourseIds,
        refresher.state,
        combine(announcementRepository.observeLastSynced(), preferencesRepository.preferences, ::Pair),
    ) { all, visibleCourses, refresh, (synced, prefs) ->
        val visible = all.filter { it.courseId in visibleCourses }
        FeedUiState(
            items = visible,
            unreadItems = visible.filterNot { it.isRead },
            unreadCount = visible.count { !it.isRead },
            loading = visible.isEmpty() && synced == null && refresh.error == null,
            refreshing = refresh.refreshing,
            error = refresh.error,
            showNotificationPrompt = !prefs.notificationPromptDone && !prefs.notifyAnnouncements,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    init {
        viewModelScope.launch {
            // The course list refresh also loads announcements; only fill gaps here.
            if (announcementRepository.observeLastSynced().first() == null) refresh()
        }
    }

    fun refresh() = refresher.refresh { sync.refreshAnnouncements() }

    fun markAllRead() {
        viewModelScope.launch {
            val ids = announcementRepository.markAllReadLocally(visibleCourseIds.first())
            if (ids.isNotEmpty()) undoChannel.send(ids)
        }
    }

    /** A swipe: read becomes unread and the other way round. */
    fun toggleRead(announcement: Announcement) {
        viewModelScope.launch { announcementRepository.setRead(announcement, !announcement.isRead) }
    }

    fun toggleHighlight(announcement: Announcement, highlight: Highlight) {
        viewModelScope.launch { announcementRepository.toggleHighlight(announcement, highlight) }
    }

    fun undoMarkRead(ids: List<String>) {
        viewModelScope.launch { announcementRepository.markUnreadLocally(ids) }
    }

    fun enableNotifications(granted: Boolean) {
        viewModelScope.launch {
            if (granted) preferencesRepository.setNotifyAnnouncements(true) else preferencesRepository.dismissNotificationPrompt()
        }
    }

    fun dismissNotificationPrompt() {
        viewModelScope.launch { preferencesRepository.dismissNotificationPrompt() }
    }
}
