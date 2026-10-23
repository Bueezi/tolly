package be.tolly.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.RefreshBar
import be.tolly.core.designsystem.component.TollyPullToRefresh
import be.tolly.core.designsystem.theme.LocalBottomBarInset

/**
 * A list under a tab of a detail pane (a course's content, announcements, grades): the refresh
 * error banner and bar above it, pull to refresh, and a loading or empty state until there are
 * rows. [emptyContent] replaces the [ListMessageState] for an empty list that needs its own.
 */
@Composable
fun RefreshableList(
    state: ListUiState<*>,
    onRefresh: () -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    modifier: Modifier = Modifier,
    emptyContent: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        RefreshErrorBanner(state.error, hasContent = state.items.isNotEmpty(), onRetry = onRefresh)
        RefreshBar(state.refreshing && !state.loading)
        TollyPullToRefresh(
            onRefresh = withHaptic(HapticFeedbackType.GestureThresholdActivate, onRefresh),
            topPadding = 0.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> LoadingState()
                state.items.isEmpty() && emptyContent != null -> emptyContent()
                state.items.isEmpty() -> ListMessageState(
                    icon = emptyIcon,
                    emptyTitle = emptyTitle,
                    error = state.error,
                    onRetry = onRefresh,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + LocalBottomBarInset.current),
                    content = content,
                )
            }
        }
    }
}
