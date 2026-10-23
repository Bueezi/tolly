package be.tolly.feature.courses.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.ClassScheduleRepository
import be.tolly.core.data.CourseCustomizationRepository
import be.tolly.core.data.CourseRepository
import be.tolly.core.data.DataError
import be.tolly.core.data.HighlightRepository
import be.tolly.core.data.HighlightTarget
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.SyncRepository
import be.tolly.core.data.isActive
import be.tolly.core.data.onCourses
import be.tolly.core.data.visibleWith
import be.tolly.core.model.ClassNow
import be.tolly.core.model.Course
import be.tolly.core.model.CourseKind
import be.tolly.core.model.CourseNaming
import be.tolly.core.model.Highlight
import be.tolly.core.model.UserPreferences
import be.tolly.core.ui.Refresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseListUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val current: List<Course> = emptyList(),
    val communities: List<Course> = emptyList(),
    val other: List<Course> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val query: String = "",
    val error: DataError? = null,
    val lastSynced: Long? = null,
    /** Courses the user has class in now (or very soon), by course id, from the agenda. */
    val classNow: Map<String, ClassNow> = emptyMap(),
) {
    val isEmpty: Boolean get() = current.isEmpty() && communities.isEmpty() && other.isEmpty()
}

@HiltViewModel
class CourseListViewModel @Inject constructor(
    courseRepository: CourseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val sync: SyncRepository,
    private val customization: CourseCustomizationRepository,
    private val highlights: HighlightRepository,
    schedule: ClassScheduleRepository,
) : ViewModel() {
    private val refresher = Refresher(viewModelScope)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<CourseListUiState> = combine(
        courseRepository.observeCourses(),
        preferencesRepository.preferences,
        combine(refresher.state, query, ::Pair),
        courseRepository.observeLastSynced(),
        schedule.today,
    ) { courses, prefs, (refresh, q), synced, now ->
        val year = CourseNaming.currentAcademicYear()
        val visible = courses.visibleWith(prefs, year).filter { it.matches(q) }
        val (active, inactive) = visible.partition { it.isActive(year) }
        CourseListUiState(
            loading = synced == null && refresh.refreshing,
            refreshing = refresh.refreshing,
            current = active.filter { it.kind == CourseKind.COURSE }.sortedWith(userOrder),
            communities = active.filter { it.kind == CourseKind.COMMUNITY }.sortedWith(userOrder),
            other = inactive.sortedWith(compareByDescending<Course> { it.academicYear.orEmpty() }.then(userOrder)),
            preferences = prefs,
            query = q,
            error = refresh.error,
            lastSynced = synced,
            classNow = now.onCourses(active),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseListUiState())

    init {
        refresher.refresh { sync.refreshIfStale() }
    }

    fun refresh() = refresher.refresh { sync.refreshAll() }

    fun setQuery(value: String) = query.update { value }

    /** Saves the order of one section after the user rearranged it. */
    fun saveOrder(courseIds: List<String>) {
        viewModelScope.launch { customization.setOrder(courseIds) }
    }

    /** A swipe: marks [course] with [highlight], or takes that mark off again. */
    fun toggleHighlight(course: Course, highlight: Highlight) {
        viewModelScope.launch { highlights.toggle(HighlightTarget.COURSE, course.id, highlight) }
    }

    fun setShowCommunities(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowCommunities(show) }
    }

    fun setShowPastCourses(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowPastCourses(show) }
    }

    private fun Course.matches(q: String): Boolean =
        q.isBlank() ||
            name.contains(q, ignoreCase = true) ||
            nickname?.contains(q, ignoreCase = true) == true ||
            code?.contains(q, ignoreCase = true) == true

    private companion object {
        /** The user's own order first, then everything they didn't place, by name. */
        val userOrder: Comparator<Course> =
            compareBy<Course, Int?>(nullsLast()) { it.position }.thenBy { it.displayName.lowercase() }
    }
}
