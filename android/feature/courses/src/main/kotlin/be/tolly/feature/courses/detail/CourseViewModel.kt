package be.tolly.feature.courses.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.AnnouncementRepository
import be.tolly.core.data.ClassScheduleRepository
import be.tolly.core.data.CourseRepository
import be.tolly.core.data.GradeRepository
import be.tolly.core.data.onCourses
import be.tolly.core.model.Announcement
import be.tolly.core.model.ClassNow
import be.tolly.core.model.Highlight
import be.tolly.core.model.ContentNode
import be.tolly.core.model.Course
import be.tolly.core.model.Grade
import be.tolly.core.ui.ListUiState
import be.tolly.core.ui.Refresher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A level in the folder path; `id == null` is the course outline root. */
data class Crumb(val id: String?, val title: String)

/** State of one course in the detail pane: header, class now, tab, folder path and its announcements. */
@HiltViewModel(assistedFactory = CourseViewModel.Factory::class)
class CourseViewModel @AssistedInject constructor(
    @Assisted val courseId: String,
    courseRepository: CourseRepository,
    schedule: ClassScheduleRepository,
    private val announcementRepository: AnnouncementRepository,
    private val gradeRepository: GradeRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(courseId: String): CourseViewModel
    }

    val course: StateFlow<Course?> = courseRepository.observeCourse(courseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The class the user is in now for this course, or has next today, as the course list shows it. */
    val classNow: StateFlow<ClassNow?> = combine(schedule.today, course) { sessions, current ->
        current?.let { sessions.onCourses(listOf(it))[it.id] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _path = MutableStateFlow(listOf(Crumb(null, "")))
    val path: StateFlow<List<Crumb>> = _path.asStateFlow()

    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    /** The visit of the course (see `CourseDetailPane`) the tab belongs to. */
    var visit: Long? = null

    private val announcementsRefresh = Refresher(viewModelScope)
    private val gradesRefresh = Refresher(viewModelScope)

    val grades: StateFlow<ListUiState<Grade>> = combine(
        gradeRepository.observeForCourse(courseId),
        gradesRefresh.state,
    ) { items, refresh -> refresh.list(items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())
    private val undoChannel = Channel<List<String>>(Channel.BUFFERED)

    /** Ids marked read by [markAllRead], for an Undo snackbar. */
    val markedRead: Flow<List<String>> = undoChannel.receiveAsFlow()

    val announcements: StateFlow<ListUiState<Announcement>> = combine(
        announcementRepository.observeForCourse(courseId),
        announcementsRefresh.state,
    ) { items, refresh -> refresh.list(items, loading = refresh.refreshing && items.isEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun selectTab(index: Int) {
        _tab.value = index
        if (index == TAB_ANNOUNCEMENTS && !announcementsRefresh.state.value.loaded) refreshAnnouncements()
        if (index == TAB_GRADES && !gradesRefresh.state.value.loaded) refreshGrades()
    }

    fun openFolder(node: ContentNode) {
        _path.update { it + Crumb(node.id, node.title) }
    }

    fun goTo(index: Int) {
        _path.update { it.take(index + 1) }
    }

    /** Pops one folder level; returns false at the outline root. */
    fun goUp(): Boolean {
        if (_path.value.size <= 1) return false
        _path.update { it.dropLast(1) }
        return true
    }

    fun markAllRead() {
        viewModelScope.launch {
            val ids = announcementRepository.markAllReadLocally(listOf(courseId))
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

    fun refreshGrades() = gradesRefresh.refresh { gradeRepository.refreshCourse(courseId) }

    fun refreshAnnouncements() = announcementsRefresh.refresh { announcementRepository.refreshCourse(courseId) }

    companion object {
        const val TAB_CONTENT = 0
        const val TAB_ANNOUNCEMENTS = 1
        const val TAB_GRADES = 2
        const val TAB_VIDEO = 3
    }
}
