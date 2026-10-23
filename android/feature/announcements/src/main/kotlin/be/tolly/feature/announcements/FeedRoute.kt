package be.tolly.feature.announcements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import be.tolly.core.ui.TollyListDetailScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.designsystem.component.TollyPullToRefresh
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollySegmented
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.groupShape
import be.tolly.core.designsystem.component.scrollingContentPadding
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.ui.SwipeableAnnouncementRow
import be.tolly.core.ui.ListMessageState
import be.tolly.core.ui.RefreshErrorBanner
import be.tolly.core.ui.MarkedReadSnackbar
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.position
import be.tolly.core.ui.rememberEnableNotifications
import be.tolly.core.ui.rememberTabFling
import be.tolly.core.ui.rememberTabPager
import be.tolly.core.ui.swipeTabs
import be.tolly.core.model.Announcement
import be.tolly.core.ui.rememberTwoPaneDirective
import be.tolly.core.ui.withHaptic
import kotlinx.coroutines.launch

/** The Announcements destination: all courses' announcements | the selected one. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FeedRoute(
    /** An announcement to open, e.g. from a notification; [onOpenHandled] is called once it is shown. */
    openAnnouncementId: String? = null,
    onOpenHandled: () -> Unit = {},
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = rememberTwoPaneDirective())
    val scope = rememberCoroutineScope()
    LaunchedEffect(openAnnouncementId) {
        if (openAnnouncementId != null) navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, openAnnouncementId)
        onOpenHandled()
    }
    val listHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden

    TollyListDetailScaffold(
        navigator = navigator,
        list = { shown ->
            FeedList(
                selectedId = shown,
                onOpen = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } },
            )
        },
        detail = { shown ->
            if (shown == null) {
                MessageState(icon = Icons.Outlined.Campaign, title = stringResource(R.string.announcements_select))
            } else {
                AnnouncementPane(
                    announcementId = shown,
                    navigation = if (listHidden) PaneNavigation.BACK else PaneNavigation.NONE,
                    onNavigate = { scope.launch { navigator.navigateBack() } },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(
    selectedId: String?,
    onOpen: (String) -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MarkedReadSnackbar(viewModel.markedRead, snackbarHostState, onUndo = viewModel::undoMarkRead)
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pager = rememberTabPager(count = 2, selected = page) { page = it }
    TollyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            TollyTopBar(
                title = { Text(stringResource(R.string.nav_announcements_short)) },
                subtitle = {
                    Text(
                        if (state.unreadCount > 0) {
                            pluralStringResource(R.plurals.feed_unread, state.unreadCount, state.unreadCount)
                        } else {
                            stringResource(R.string.announcements_all_read)
                        },
                    )
                },
                actions = {
                    if (state.unreadCount > 0) {
                        IconButton(onClick = withHaptic(HapticFeedbackType.Confirm, viewModel::markAllRead)) {
                            Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(R.string.action_mark_all_read))
                        }
                    }
                },
                refreshing = state.refreshing && !state.loading,
                bottom = { FeedHeader(state, viewModel, pager, onSelect = { page = it }) },
            )
        },
    ) { padding ->
        // All and Unread side by side: swiping moves between them, like tapping the segments.
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            flingBehavior = rememberTabFling(pager),
        ) { page ->
            FeedPage(
                items = if (page == PAGE_UNREAD) state.unreadItems else state.items,
                unreadOnly = page == PAGE_UNREAD,
                state = state,
                padding = padding,
                selectedId = selectedId,
                onOpen = onOpen,
                viewModel = viewModel,
            )
        }
    }
}

/** One page of the feed: a list of [items], under the top bar, with the loading and empty states. */
@Composable
private fun FeedPage(
    items: List<Announcement>,
    unreadOnly: Boolean,
    state: FeedUiState,
    padding: PaddingValues,
    selectedId: String?,
    onOpen: (String) -> Unit,
    viewModel: FeedViewModel,
) {
    // The list scrolls under the top bar.
    TollyPullToRefresh(
        onRefresh = withHaptic(HapticFeedbackType.GestureThresholdActivate, viewModel::refresh),
        topPadding = padding.calculateTopPadding(),
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            items.isEmpty() && unreadOnly && state.items.isNotEmpty() -> MessageState(
                icon = Icons.Outlined.MarkEmailRead,
                title = stringResource(R.string.announcements_all_read),
                modifier = Modifier.padding(padding),
            )
            items.isEmpty() -> ListMessageState(
                icon = Icons.Outlined.Campaign,
                emptyTitle = stringResource(R.string.announcements_empty),
                error = state.error,
                onRetry = viewModel::refresh,
                retryWhenEmpty = true,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = scrollingContentPadding(padding, top = 8.dp, bottom = 8.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, announcement ->
                    SwipeableAnnouncementRow(
                        announcement = announcement,
                        selected = announcement.id == selectedId,
                        showCourse = true,
                        position = ItemPosition(index, items.size),
                        onClick = { onOpen(announcement.id) },
                        onToggleRead = { viewModel.toggleRead(announcement) },
                        onHighlight = { viewModel.toggleHighlight(announcement, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

private const val PAGE_UNREAD = 1

/** The filters, the notification prompt and any error, under the feed's title. */
@Composable
private fun FeedHeader(state: FeedUiState, viewModel: FeedViewModel, pager: PagerState, onSelect: (Int) -> Unit) {
    val tick = withHaptic(HapticFeedbackType.SegmentTick) {}
    TollySegmented(
        count = 2,
        selectedIndex = pager.targetPage,
        onSelect = { index ->
            tick()
            onSelect(index)
        },
        modifier = Modifier.fillMaxWidth().swipeTabs(pager).padding(horizontal = 16.dp, vertical = 8.dp),
        position = pager::position,
    ) { index ->
        Text(
            when {
                index == 0 -> stringResource(R.string.filter_all)
                state.unreadCount > 0 -> stringResource(R.string.filter_unread_count, state.unreadCount)
                else -> stringResource(R.string.filter_unread)
            },
            maxLines = 1,
        )
    }
    if (state.showNotificationPrompt) {
        val enable = rememberEnableNotifications(viewModel::enableNotifications)
        NotificationPrompt(onEnable = enable, onDismiss = viewModel::dismissNotificationPrompt)
    }
    RefreshErrorBanner(state.error, hasContent = state.items.isNotEmpty(), onRetry = viewModel::refresh)
}

@Composable
private fun NotificationPrompt(onEnable: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = if (Tolly.glass) Tolly.colors.group else MaterialTheme.colorScheme.tertiaryContainer,
        shape = groupShape(ItemPosition.Single),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.notifications_prompt_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.notifications_prompt_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
                TextButton(onClick = onEnable) { Text(stringResource(R.string.action_turn_on)) }
            }
        }
    }
}
