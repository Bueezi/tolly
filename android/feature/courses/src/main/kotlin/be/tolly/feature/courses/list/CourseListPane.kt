package be.tolly.feature.courses.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.designsystem.component.SectionHeader
import be.tolly.core.designsystem.component.SwipeActions
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyPullToRefresh
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.highlightColor
import be.tolly.core.designsystem.component.relativeTime
import be.tolly.core.designsystem.component.scrollingContentPadding
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.designsystem.theme.glassSpec
import be.tolly.core.model.ClassNow
import be.tolly.core.model.Course
import be.tolly.core.model.CourseNaming
import be.tolly.core.model.Highlight
import be.tolly.core.ui.ListMessageState
import be.tolly.core.ui.RefreshErrorBanner
import be.tolly.core.ui.highlightAction
import be.tolly.core.ui.openInBrowser
import be.tolly.core.ui.withHaptic
import be.tolly.feature.courses.ClassNowLines
import be.tolly.feature.courses.customize.CustomizeCourseSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListPane(
    selectedCourseId: String?,
    onCourseClick: (Course) -> Unit,
    viewModel: CourseListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var customizingId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // The long click itself gives the long-press haptic.
    val customize: (Course) -> Unit = { course -> customizingId = course.id }
    val openCourse: (Course) -> Unit = { course ->
        focusManager.clearFocus() // hides the keyboard if the search field was focused
        onCourseClick(course)
    }

    TollyScaffold(
        topBar = {
            TollyTopBar(
                title = { Text(stringResource(if (reordering) R.string.reorder_title else R.string.nav_courses)) },
                subtitle = state.lastSynced?.takeIf { !reordering }?.let { synced -> { SyncedText(synced) } },
                actions = {
                    if (reordering) {
                        TextButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            reordering = false
                        }) { Text(stringResource(R.string.action_done)) }
                    } else {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            // Reordering a search's matches would put them ahead of all the rest.
                            viewModel.setQuery("")
                            reordering = true
                        }) {
                            Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.action_reorder))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.courses_filter))
                        }
                        // Toledo's homepage, for whatever Tolly doesn't do, in the in-app browser, which
                        // is still signed in (a Custom Tab asked to sign in again).
                        IconButton(onClick = { context.openInBrowser(UltraEndpoints.TOLEDO_HOME_URL) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.action_open_in_toledo))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.courses_show_communities)) },
                                leadingIcon = { Checkbox(checked = state.preferences.showCommunities, onCheckedChange = null) },
                                onClick = { viewModel.setShowCommunities(!state.preferences.showCommunities) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.courses_show_past)) },
                                leadingIcon = { Checkbox(checked = state.preferences.showPastCourses, onCheckedChange = null) },
                                onClick = { viewModel.setShowPastCourses(!state.preferences.showPastCourses) },
                            )
                        }
                    }
                },
                refreshing = state.refreshing && !state.loading,
                bottom = if (reordering) {
                    null
                } else {
                    {
                        SearchField(query = state.query, onQueryChange = viewModel::setQuery)
                        RefreshErrorBanner(state.error, hasContent = !state.isEmpty, onRetry = viewModel::refresh)
                    }
                },
            )
        },
    ) { padding ->
        if (reordering) {
            BackHandler { reordering = false }
            ReorderCourseList(
                sections = listOf(
                    R.string.courses_section_current to state.current,
                    R.string.courses_section_communities to state.communities,
                ),
                onOrderChanged = viewModel::saveOrder,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@TollyScaffold
        }
        // The list scrolls under the top bar (and the search field in it).
        TollyPullToRefresh(
            onRefresh = withHaptic(HapticFeedbackType.GestureThresholdActivate, viewModel::refresh),
            topPadding = padding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && state.isEmpty -> LoadingState(Modifier.padding(padding))
                state.isEmpty && state.query.isNotBlank() -> MessageState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.courses_no_results),
                    modifier = Modifier.padding(padding),
                )
                state.isEmpty -> ListMessageState(
                    icon = Icons.Outlined.School,
                    emptyTitle = stringResource(R.string.courses_empty),
                    error = state.error,
                    onRetry = viewModel::refresh,
                    retryWhenEmpty = true,
                    modifier = Modifier.padding(padding),
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(remember(focusManager) { clearFocusOnScroll(focusManager) }),
                    contentPadding = scrollingContentPadding(padding, bottom = 16.dp),
                ) {
                    val rows = CourseRowActions(openCourse, customize, viewModel::toggleHighlight)
                    section(R.string.courses_section_current, state.current, selectedCourseId, state.classNow, rows, showYear = false)
                    section(R.string.courses_section_communities, state.communities, selectedCourseId, state.classNow, rows, showYear = false)
                    section(R.string.courses_section_other, state.other, selectedCourseId, state.classNow, rows, showYear = true)
                }
            }
        }
    }

    val customizing = customizingId?.let { id -> (state.current + state.communities + state.other).firstOrNull { it.id == id } }
    if (customizing != null) {
        CustomizeCourseSheet(course = customizing, onDismiss = { customizingId = null })
    }
}

@Composable
private fun SyncedText(lastSynced: Long) {
    val justNow = System.currentTimeMillis() - lastSynced < 60_000
    Text(
        if (justNow) {
            stringResource(R.string.courses_synced_just_now)
        } else {
            stringResource(R.string.courses_last_synced, relativeTime(lastSynced))
        },
    )
}

/** What a course row does when tapped, long-pressed or swiped. */
private class CourseRowActions(
    val open: (Course) -> Unit,
    val customize: (Course) -> Unit,
    val highlight: (Course, Highlight) -> Unit,
)

private fun LazyListScope.section(
    title: Int,
    courses: List<Course>,
    selectedCourseId: String?,
    classNow: Map<String, ClassNow>,
    actions: CourseRowActions,
    /** Off for this year's sections, where the header already says which year it is. */
    showYear: Boolean,
) {
    if (courses.isEmpty()) return
    item(key = "header-$title", contentType = "header") {
        SectionHeader(stringResource(title), Modifier.animateItem())
    }
    itemsIndexed(courses, key = { _, course -> course.id }, contentType = { _, _ -> "course" }) { index, course ->
        val position = ItemPosition(index, courses.size)
        SwipeActions(
            position = position,
            startAction = highlightAction(course.highlight, Highlight.ORANGE) { actions.highlight(course, Highlight.ORANGE) },
            endAction = null,
            modifier = Modifier.animateItem(),
        ) {
            CourseRow(
                course = course,
                selected = course.id == selectedCourseId,
                showYear = showYear,
                classNow = classNow[course.id],
                position = position,
                onClick = { actions.open(course) },
                onLongClick = { actions.customize(course) },
            )
        }
    }
}

/**
 * A course: its avatar, name and badge. Its code isn't shown (the name says enough; search still
 * finds it). While the user has class in it (or will within a few minutes) the row is tinted and
 * says until when, and in which room; the next class later today only gets those lines.
 */
@Composable
private fun CourseRow(
    course: Course,
    selected: Boolean,
    showYear: Boolean,
    classNow: ClassNow?,
    position: ItemPosition,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = listOfNotNull(
        course.academicYear?.takeIf { showYear }?.let(CourseNaming::formatAcademicYear),
        if (!course.available) stringResource(R.string.courses_unavailable) else null,
    ).joinToString(" · ")
    TollyListItem(
        modifier = modifier.groupItem(
            position,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.action_customize),
            separatorStart = GroupDefaults.separatorStart(44.dp),
            highlight = when {
                classNow?.prominent == true -> MaterialTheme.colorScheme.primary
                else -> course.highlight?.let(::highlightColor) ?: Color.Unspecified
            },
        ),
        leading = { CourseAvatar(name = course.displayName, seed = course.id, icon = course.icon, color = course.color) },
        headline = { Text(course.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supporting = when {
            classNow != null -> {
                { ClassNowLines(classNow, color = MaterialTheme.colorScheme.primary) }
            }
            details.isNotEmpty() -> {
                { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            else -> null
        },
        trailing = if (course.unreadAnnouncements > 0) {
            {
                val description = pluralStringResource(
                    R.plurals.unread_announcements,
                    course.unreadAnnouncements,
                    course.unreadAnnouncements,
                )
                Badge(modifier = Modifier.semanticsDescription(description)) { Text(course.unreadAnnouncements.toString()) }
            }
        } else {
            null
        },
    )
}

/**
 * A search pill that only turns into a text field once tapped. An always-present text field gets
 * focus back (and pops up the keyboard) when the app returns to the foreground or the user comes
 * back from a course.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { focusManager.clearFocus() }
    BackHandler(enabled = editing || query.isNotEmpty()) {
        onQueryChange("")
        editing = false
        focusManager.clearFocus()
    }

    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
    // iOS's grey search field with the glass look; a pill on the group colour in Material.
    val glass = Tolly.glass
    val pillColor = when {
        !glass -> Tolly.colors.group
        glassSpec?.dark == true -> Color(0x3D767680)
        else -> Color(0x1F767680)
    }
    if (!editing && query.isEmpty()) {
        Surface(
            onClick = { editing = true },
            shape = CircleShape,
            color = pillColor,
            modifier = modifier.heightIn(min = if (glass) 44.dp else TextFieldDefaults.MinHeight),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (glass) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (glass) 8.dp else 16.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.courses_search),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LaunchedEffect(editing) { if (editing) focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                // Back to the pill once the user leaves an empty field.
                if (focused && !it.isFocused && query.isEmpty()) editing = false
                focused = it.isFocused
            },
        placeholder = { Text(stringResource(R.string.courses_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = pillColor,
            unfocusedContainerColor = pillColor,
        ),
    )
}

/** Hides the keyboard as soon as the user starts scrolling the results. */
private fun clearFocusOnScroll(focusManager: FocusManager) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) focusManager.clearFocus()
        return Offset.Zero
    }
}

private fun Modifier.semanticsDescription(text: String): Modifier = semantics { contentDescription = text }
