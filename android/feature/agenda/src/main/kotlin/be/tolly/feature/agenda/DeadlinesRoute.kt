package be.tolly.feature.agenda

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.SectionHeader
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyPullToRefresh
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.relativeTime
import be.tolly.core.designsystem.component.scrollingContentPadding
import be.tolly.core.model.AgendaItem
import be.tolly.core.ui.ListMessageState
import be.tolly.core.ui.RefreshErrorBanner
import be.tolly.core.ui.withHaptic

/** The agenda's Deadlines page: gradable items that are due soon, overdue ones first, by how soon. */
@Composable
fun DeadlinesRoute(
    /** Shows a course in the Courses tab. */
    onOpenCourse: (courseId: String) -> Unit,
    /** The top bar's buttons: the agenda's switch back to its calendar. */
    actions: @Composable () -> Unit = {},
    viewModel: DeadlinesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    TollyScaffold(
        topBar = {
            TollyTopBar(
                title = { Text(stringResource(R.string.nav_deadlines)) },
                subtitle = if (state.count > 0) {
                    { Text(pluralStringResource(R.plurals.deadlines_upcoming, state.count, state.count)) }
                } else {
                    null
                },
                actions = { actions() },
                refreshing = state.refreshing && !state.loading,
                bottom = { RefreshErrorBanner(state.error, hasContent = state.groups.isNotEmpty(), onRetry = viewModel::refresh) },
            )
        },
    ) { padding ->
        // The list scrolls under the top bar.
        TollyPullToRefresh(
            onRefresh = withHaptic(HapticFeedbackType.GestureThresholdActivate, viewModel::refresh),
            topPadding = padding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> LoadingState(Modifier.padding(padding))
                state.groups.isEmpty() -> ListMessageState(
                    icon = Icons.Outlined.TaskAlt,
                    emptyTitle = stringResource(R.string.deadlines_empty),
                    error = state.error,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(padding),
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                        contentPadding = scrollingContentPadding(padding, bottom = 16.dp),
                    ) {
                        state.groups.forEach { (group, items) ->
                            item(key = "group-$group") {
                                SectionHeader(
                                    stringResource(group.title()),
                                    color = if (group == DeadlineGroup.OVERDUE) MaterialTheme.colorScheme.error else Color.Unspecified,
                                )
                            }
                            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                                DeadlineRow(
                                    item = item,
                                    overdue = group == DeadlineGroup.OVERDUE,
                                    position = ItemPosition(index, items.size),
                                    onClick = item.courseId?.let { id -> { onOpenCourse(id) } },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DeadlineGroup.title() = when (this) {
    DeadlineGroup.OVERDUE -> R.string.deadlines_overdue
    DeadlineGroup.TODAY -> R.string.agenda_today
    DeadlineGroup.THIS_WEEK -> R.string.deadlines_this_week
    DeadlineGroup.LATER -> R.string.deadlines_later
}

@Composable
private fun DeadlineRow(item: AgendaItem, overdue: Boolean, position: ItemPosition, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val due = remember(item.start) {
        DateUtils.formatDateTime(
            context,
            item.start,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME,
        )
    }
    TollyListItem(
        modifier = modifier.groupItem(position, onClick = onClick, enabled = onClick != null, separatorStart = GroupDefaults.separatorStart(68.dp)),
        leading = {
            CourseAvatar(
                name = item.calendarName.orEmpty().ifBlank { item.title },
                seed = item.courseId ?: item.calendarName.orEmpty(),
                size = 40.dp,
                icon = item.courseIcon,
                color = item.courseColor,
            )
        },
        headline = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
        supporting = {
            Text(
                listOfNotNull(item.calendarName?.takeIf { it != item.title }, stringResource(R.string.agenda_due) + " " + due).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            Text(
                relativeTime(item.start),
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
    )
}
