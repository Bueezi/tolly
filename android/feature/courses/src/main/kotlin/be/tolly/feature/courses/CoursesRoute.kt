package be.tolly.feature.courses

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.TollyListDetailScaffold
import be.tolly.core.ui.rememberTwoPaneDirective
import be.tolly.feature.courses.detail.CourseDetailPane
import be.tolly.feature.courses.document.DocumentPane
import be.tolly.feature.courses.list.CourseListPane
import kotlinx.coroutines.launch

/**
 * The Courses destination: course list | course (content + announcements) | opened item.
 * Shows one, two or three panes depending on the window size and keeps state across resizes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CoursesRoute(
    /**
     * A course to show right away, opened from elsewhere (the agenda). This copy of the tab is
     * pushed over where it was opened from, so back (with its preview) returns there through
     * [onClose], rather than to this tab's course list.
     */
    initialCourseId: String? = null,
    onClose: () -> Unit = {},
    announcementPane: @Composable (id: String, navigation: PaneNavigation, onNavigate: () -> Unit) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<CoursePaneKey>(
        scaffoldDirective = rememberTwoPaneDirective(),
        initialDestinationHistory = listOf(
            if (initialCourseId == null) {
                ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List)
            } else {
                ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, CoursePaneKey(initialCourseId))
            },
        ),
    )
    val scope = rememberCoroutineScope()
    val listHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    val detailHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden
    val goBack: () -> Unit = { scope.launch { navigator.navigateBack() } }
    // A course opened from elsewhere has nothing to go back to in this tab but that place.
    val leavesTab = initialCourseId != null && !navigator.canNavigateBack()
    // Which visit of a course this is, new with each pick from the list (and for the course this
    // copy of the tab was opened on): a new visit shows the course from its content again.
    var visit by rememberSaveable { mutableLongStateOf(System.nanoTime()) }

    TollyListDetailScaffold(
        navigator = navigator,
        list = { shown ->
            CourseListPane(
                selectedCourseId = shown?.courseId,
                onCourseClick = { course ->
                    visit = System.nanoTime()
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, CoursePaneKey(course.id)) }
                },
            )
        },
        detail = { shown ->
            if (shown == null) {
                MessageState(icon = Icons.Outlined.School, title = stringResource(R.string.courses_select))
            } else {
                CourseDetailPane(
                    courseId = shown.courseId,
                    navigation = if (listHidden || leavesTab) PaneNavigation.BACK else PaneNavigation.NONE,
                    onNavigate = if (leavesTab) onClose else goBack,
                    visit = visit,
                    itemOpen = shown.item != null,
                    selectedItem = shown.item,
                    onOpenItem = { item ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Extra, CoursePaneKey(shown.courseId, item))
                        }
                    },
                )
            }
        },
        extra = { shown ->
            val item = shown?.item ?: return@TollyListDetailScaffold
            val navigation = if (detailHidden) PaneNavigation.BACK else PaneNavigation.CLOSE
            when (item) {
                is OpenItem.Document -> DocumentPane(
                    courseId = shown.courseId,
                    contentId = item.contentId,
                    isPage = item.isPage,
                    title = item.title,
                    navigation = navigation,
                    onNavigate = goBack,
                )
                is OpenItem.Announcement -> announcementPane(item.id, navigation, goBack)
            }
        },
    )
}
