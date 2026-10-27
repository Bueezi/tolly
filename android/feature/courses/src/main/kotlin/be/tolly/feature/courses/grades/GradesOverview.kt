package be.tolly.feature.courses.grades

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Grading
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.CourseRepository
import be.tolly.core.data.GradeRepository
import be.tolly.core.data.isActive
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.model.Course
import be.tolly.core.model.CourseKind
import be.tolly.core.model.Grade
import be.tolly.core.ui.ListUiState
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.PaneNavigationIcon
import be.tolly.core.ui.RefreshableList
import be.tolly.core.ui.Refresher
import be.tolly.feature.courses.detail.GradeRow
import be.tolly.feature.courses.detail.average
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One course on the overview: the items that have a result or wait for one, how many more aren't
 * graded yet (the course's own Grades tab lists them), and the average when the scores allow one.
 */
data class CourseGradesUi(val course: Course, val grades: List<Grade>, val notGraded: Int, val average: Int?)

/** The grades of all of this year's courses, for the overview in the You tab. */
@HiltViewModel
class GradesOverviewViewModel @Inject constructor(
    courseRepository: CourseRepository,
    private val gradeRepository: GradeRepository,
) : ViewModel() {
    private val refresher = Refresher(viewModelScope)
    private val courses = courseRepository.observeCourses()
        .map { list ->
            // In the user's order, as on the course list.
            list.filter { it.kind == CourseKind.COURSE && it.isActive() }
                .sortedWith(compareBy<Course, Int?>(nullsLast()) { it.position }.thenBy { it.displayName.lowercase() })
        }

    val uiState: StateFlow<ListUiState<CourseGradesUi>> = combine(
        courses,
        gradeRepository.observeAll(),
        refresher.state,
    ) { courseList, byCourse, refresh ->
        // Courses without any grade item yet would only be empty headers.
        refresh.list(
            courseList.mapNotNull { course ->
                byCourse[course.id]?.takeIf { it.isNotEmpty() }?.let { all ->
                    val graded = all.filter { it.displayGrade != null || it.needsGrading }
                    CourseGradesUi(course, graded, all.size - graded.size, average(all)?.first)
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    /** The overview opened (again: this view model lives as long as the tab): fetch what's new. */
    fun onOpened() = refresh()

    fun refresh() = refresher.refresh { gradeRepository.refreshAll(courses.first().map { it.id }) }
}

/**
 * Every grade of this year's courses in one place: a group per course, headed by its name and
 * average, with the same rows as a course's Grades tab.
 */
@Composable
fun GradesOverviewPane(navigation: PaneNavigation, onNavigate: () -> Unit) {
    val viewModel = hiltViewModel<GradesOverviewViewModel>()
    LaunchedEffect(viewModel) { viewModel.onOpened() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    // Courses the user folded away, to find the others quicker; they stay folded until unfolded.
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val haptics = LocalHapticFeedback.current
    // Courses come in as their grades load, some above others already shown; until the user
    // scrolls, the list stays at its top rather than with whichever course came first.
    val listState = rememberLazyListState()
    var scrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) scrolled = true }
    val courseIds = state.items.map { it.course.id }
    LaunchedEffect(courseIds) { if (!scrolled) listState.scrollToItem(0) }

    TollyScaffold(
        topBar = {
            TollyTopBar(
                detail = true,
                title = { Text(stringResource(R.string.course_tab_grades)) },
                navigationIcon = { PaneNavigationIcon(navigation, onNavigate) },
                actions = {
                    // Folds every course away, to scan the averages, or unfolds them all again.
                    val allFolded = courseIds.isNotEmpty() && courseIds.all { it in collapsed }
                    if (courseIds.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(if (allFolded) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                collapsed = if (allFolded) emptyList() else courseIds
                            },
                        ) {
                            Icon(
                                if (allFolded) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                                contentDescription = stringResource(if (allFolded) R.string.action_expand_all else R.string.action_collapse_all),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        RefreshableList(
            state = state,
            onRefresh = viewModel::refresh,
            emptyIcon = Icons.Outlined.Grading,
            emptyTitle = stringResource(R.string.grades_empty),
            modifier = Modifier.fillMaxSize().padding(padding),
            listState = listState,
        ) {
            state.items.forEach { (course, grades, notGraded, average) ->
                val rows = grades.size + if (notGraded > 0) 1 else 0
                val folded = course.id in collapsed
                item(key = "course:${course.id}") {
                    CourseHeader(
                        course = course,
                        average = average,
                        collapsed = folded,
                        onClick = {
                            haptics.performHapticFeedback(if (folded) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            collapsed = if (folded) collapsed - course.id else collapsed + course.id
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (folded) return@forEach
                itemsIndexed(grades, key = { _, it -> "${course.id}/${it.columnId}" }) { index, grade ->
                    val key = "${course.id}/${grade.columnId}"
                    GradeRow(
                        grade = grade,
                        position = ItemPosition(index, rows),
                        expanded = expanded == key,
                        onClick = { expanded = if (expanded == key) null else key },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (notGraded > 0) {
                    item(key = "more:${course.id}") {
                        TollyListItem(
                            modifier = Modifier.animateItem().groupItem(ItemPosition(rows - 1, rows)),
                            headline = {
                                Text(
                                    pluralStringResource(R.plurals.grades_more_not_graded, notGraded, notGraded),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseHeader(course: Course, average: Int?, collapsed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val chevron by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    Row(
        modifier
            .clickable(
                onClickLabel = stringResource(if (collapsed) R.string.action_expand else R.string.action_collapse),
                onClick = onClick,
            )
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CourseAvatar(name = course.displayName, seed = course.id, size = 32.dp, icon = course.icon, color = course.color)
        Text(
            course.displayName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (average != null) {
            Text(
                "$average%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(chevron),
        )
    }
}
