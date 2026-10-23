package be.tolly.feature.announcements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.HtmlContent
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.dateTime
import be.tolly.core.designsystem.theme.LocalBottomBarInset
import be.tolly.core.ui.HandleFileEvents
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.PaneNavigationIcon
import be.tolly.core.ui.withHaptic

/** Full announcement; used by the feed and by a course's Announcements tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementPane(
    announcementId: String,
    navigation: PaneNavigation,
    onNavigate: () -> Unit,
) {
    val viewModel = hiltViewModel<AnnouncementViewModel, AnnouncementViewModel.Factory>(key = "announcement:$announcementId") {
        it.create(announcementId)
    }
    // Each time it's opened, not once per view model: that lives on while the tab does.
    LaunchedEffect(viewModel) { viewModel.markRead() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val announcement = state.announcement
    val snackbarHostState = remember { SnackbarHostState() }
    HandleFileEvents(viewModel.files.events, snackbarHostState)

    TollyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            TollyTopBar(
                large = false,
                title = { Text(announcement?.courseName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { PaneNavigationIcon(navigation, onNavigate) },
                actions = {
                    announcement?.let { current ->
                        IconButton(onClick = withHaptic(HapticFeedbackType.ToggleOn, viewModel::toggleRead)) {
                            Icon(
                                if (current.isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead,
                                contentDescription = stringResource(if (current.isRead) R.string.action_mark_unread else R.string.action_mark_read),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = announcement
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@TollyScaffold
        }
        if (current == null) {
            MessageState(
                icon = Icons.Outlined.Campaign,
                title = stringResource(R.string.announcement_missing),
                modifier = Modifier.padding(padding),
            )
            return@TollyScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CourseAvatar(
                        name = current.courseName.orEmpty(),
                        seed = current.courseId,
                        size = 32.dp,
                        icon = current.courseIcon,
                        color = current.courseColor,
                    )
                    Text(
                        dateTime(current.created),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            HtmlContent(
                html = current.bodyHtml,
                onLinkClick = viewModel::onLink,
                baseUrl = UltraEndpoints.BASE_URL,
                resourceLoader = viewModel::loadResource,
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalBottomBarInset.current),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
