package be.tolly.feature.courses.detail

import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.data.FileTarget
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.SegmentLabel
import be.tolly.core.designsystem.component.SwipeActions
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollySegmented
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.Announcement
import be.tolly.core.model.ContentType
import be.tolly.core.model.Course
import be.tolly.core.model.CourseNaming
import be.tolly.core.model.Highlight
import be.tolly.core.ui.FileOptionsSheet
import be.tolly.core.ui.HandleFileEvents
import be.tolly.core.ui.ListUiState
import be.tolly.core.ui.MarkedReadSnackbar
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.PaneNavigationIcon
import be.tolly.core.ui.RefreshableList
import be.tolly.core.ui.ScreenTransitions
import be.tolly.core.ui.SwipeableAnnouncementRow
import be.tolly.core.ui.followBack
import be.tolly.core.ui.position
import be.tolly.core.ui.pageSwipe
import be.tolly.core.designsystem.component.LocalRowSwipes
import be.tolly.core.designsystem.component.RowSwipes
import be.tolly.core.ui.rememberTabPager
import be.tolly.core.ui.swipeTabs
import be.tolly.core.ui.highlightAction
import be.tolly.core.ui.openInBrowser
import be.tolly.core.ui.openOfflineDownload
import be.tolly.core.ui.withHaptic
import be.tolly.feature.courses.video.CourseVideo
import be.tolly.feature.courses.ClassNowBanner
import be.tolly.feature.courses.OpenItem
import be.tolly.feature.courses.customize.CustomizeCourseSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailPane(
    courseId: String,
    navigation: PaneNavigation,
    onNavigate: () -> Unit,
    /** True while an item of this course is shown in the extra pane; back then closes that first. */
    itemOpen: Boolean,
    selectedItem: OpenItem?,
    onOpenItem: (OpenItem) -> Unit,
    /**
     * Which visit of the course this is, new each time the user picks it from the list. A new visit
     * shows the course from its content again, while coming back from an opened item, from another
     * tab or a rotation keeps the tab it was on.
     */
    visit: Long,
) {
    val viewModel = hiltViewModel<CourseViewModel, CourseViewModel.Factory>(key = "course:$courseId") { it.create(courseId) }
    // Kept by the view model, which outlives this pane: saved UI state is restored per course and
    // would carry an old visit's tab along with it.
    if (viewModel.visit != visit) {
        viewModel.visit = visit
        viewModel.selectTab(CourseViewModel.TAB_CONTENT)
    }
    val course by viewModel.course.collectAsStateWithLifecycle()
    val path by viewModel.path.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val classNow by viewModel.classNow.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    MarkedReadSnackbar(viewModel.markedRead, snackbarHostState, onUndo = viewModel::undoMarkRead)
    var customizing by rememberSaveable { mutableStateOf(false) }
    // A tapped segment scrolls the pager there; a swipe selects the tab it settles on.
    val pager = rememberTabPager(TAB_COUNT, tab, viewModel::selectTab)

    // Back leaves a subfolder first. The gesture slides the folder away with the finger and shows
    // the parent under it, so the user sees that back goes up a level rather than out of the course.
    val folders = remember { SeekableTransitionState(path) }
    LaunchedEffect(path) { if (folders.targetState != path || folders.currentState != path) folders.animateTo(path) }
    PredictiveBackHandler(enabled = !itemOpen && tab == CourseViewModel.TAB_CONTENT && path.size > 1) { events ->
        folders.followBack(events, from = path, to = path.dropLast(1)) { viewModel.goUp() }
    }

    TollyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            TollyTopBar(
                detail = true,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        course?.let { c ->
                            CourseAvatar(name = c.displayName, seed = c.id, size = 36.dp, icon = c.icon, color = c.color)
                        }
                        CourseTitle(course)
                    }
                },
                navigationIcon = { PaneNavigationIcon(navigation, onNavigate) },
                actions = {
                    if (tab == CourseViewModel.TAB_ANNOUNCEMENTS && (course?.unreadAnnouncements ?: 0) > 0) {
                        IconButton(onClick = withHaptic(HapticFeedbackType.Confirm, viewModel::markAllRead)) {
                            Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(R.string.action_mark_all_read))
                        }
                    }
                    course?.let { c ->
                        IconButton(onClick = { context.openOfflineDownload(listOf(c.id)) }) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = stringResource(R.string.action_download_offline))
                        }
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_customize)) },
                                    leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        customizing = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_open_in_toledo)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        context.openInBrowser(c.webUrl)
                                    },
                                )
                            }
                        }
                    }
                },
                bottom = {
                    classNow?.let { ClassNowBanner(it, Modifier.swipeTabs(pager).padding(start = 16.dp, end = 16.dp, top = 4.dp)) }
                    val select = withHaptic(HapticFeedbackType.SegmentTick) {}
                    TollySegmented(
                        count = TAB_COUNT,
                        selectedIndex = pager.targetPage,
                        onSelect = { index ->
                            select()
                            viewModel.selectTab(index)
                        },
                        modifier = Modifier.fillMaxWidth().swipeTabs(pager).padding(horizontal = 16.dp, vertical = 8.dp),
                        position = pager::position,
                    ) { index ->
                        when (index) {
                            CourseViewModel.TAB_CONTENT -> SegmentLabel(stringResource(R.string.course_tab_content))
                            CourseViewModel.TAB_ANNOUNCEMENTS -> {
                                val unread = course?.unreadAnnouncements ?: 0
                                SegmentLabel(
                                    stringResource(R.string.course_tab_announcements),
                                    badge = if (unread > 0) ({ Badge { Text(unread.toString()) } }) else null,
                                )
                            }
                            CourseViewModel.TAB_GRADES -> SegmentLabel(stringResource(R.string.course_tab_grades))
                            else -> SegmentLabel(stringResource(R.string.course_tab_video))
                        }
                    }
                },
            )
        },
    ) { padding ->
        // The tabs side by side: swiping moves between them, like tapping the segments above.
        // Rows that swipe (highlight, mark read) keep their swipe; other sideways swipes turn the page.
        val rowSwipes = remember { RowSwipes() }
        CompositionLocalProvider(LocalRowSwipes provides rowSwipes) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(padding).pageSwipe(pager, rowSwipes),
                verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 1,
                // pageSwipe turns the pages, ahead of the lists and rows on them.
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    CourseViewModel.TAB_CONTENT -> Column(Modifier.fillMaxSize()) {
                        if (path.size > 1) {
                            Breadcrumbs(path = path, onSelect = viewModel::goTo)
                        }
                        rememberTransition(folders, label = "folder").AnimatedContent(
                            contentKey = { it.last().id },
                            transitionSpec = { ScreenTransitions.between(deeper = targetState.size > initialState.size) },
                        ) { folderPath ->
                            // Opaque, so the folder underneath doesn't show between the rows on top.
                            Box(Modifier.fillMaxSize().background(Tolly.colors.screen)) {
                                FolderList(
                                    courseId = courseId,
                                    folderId = folderPath.last().id,
                                    selectedItem = selectedItem,
                                    snackbarHostState = snackbarHostState,
                                    onOpenFolder = viewModel::openFolder,
                                    onOpenItem = onOpenItem,
                                )
                            }
                        }
                    }
                    CourseViewModel.TAB_GRADES -> {
                        val grades by viewModel.grades.collectAsStateWithLifecycle()
                        CourseGrades(
                            state = grades,
                            onRefresh = viewModel::refreshGrades,
                            onOpenInToledo = { context.openInBrowser(UltraEndpoints.courseGradesUrl(courseId)) },
                        )
                    }
                    CourseViewModel.TAB_VIDEO -> CourseVideo(courseId, active = tab == CourseViewModel.TAB_VIDEO, snackbarHostState = snackbarHostState)
                    else -> CourseAnnouncements(
                        state = announcements,
                        selectedItem = selectedItem,
                        onRefresh = viewModel::refreshAnnouncements,
                        onOpen = { onOpenItem(OpenItem.Announcement(it)) },
                        onToggleRead = viewModel::toggleRead,
                        onHighlight = viewModel::toggleHighlight,
                    )
                }
            }
        }
    }

    val current = course
    if (customizing && current != null) {
        CustomizeCourseSheet(course = current, onDismiss = { customizing = false })
    }
}

@Composable
private fun CourseTitle(course: Course?) {
    val name = course?.displayName.orEmpty()
    // A name too long for one line (common on phones: "Beginselen van Programmeren - BvP") wraps
    // onto two in a slightly smaller size, which still fits the bar with the year under it.
    var wraps by remember(name) { mutableStateOf(false) }
    Column {
        Text(
            name,
            style = if (wraps) MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp) else LocalTextStyle.current,
            maxLines = if (wraps) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!wraps && it.hasVisualOverflow) wraps = true },
        )
        val subtitle = course?.academicYear?.let(CourseNaming::formatAcademicYear).orEmpty()
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Where in the course the folder list is. The way home stays at the start; the folders after it
 * scroll to show the deepest ones. Each folder above the current one takes at most
 * [MaxCrumbWidth] and the current one at most the whole row, so long names end in an ellipsis
 * instead of pushing the rest of the path out of sight.
 */
@Composable
private fun Breadcrumbs(path: List<Crumb>, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(path.size, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onSelect(0) }) {
            Icon(Icons.Outlined.Home, contentDescription = stringResource(R.string.course_outline_root))
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val rowWidth = maxWidth
            Row(Modifier.horizontalScroll(scroll).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                path.forEachIndexed { index, crumb ->
                    if (index == 0) return@forEachIndexed
                    val last = index == path.lastIndex
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onSelect(index) },
                        enabled = !last,
                        modifier = Modifier.widthIn(max = if (last) rowWidth - CrumbArrowWidth else MaxCrumbWidth),
                    ) {
                        Text(
                            crumb.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** The widest a folder above the current one gets in the [Breadcrumbs], and the arrow before each. */
private val MaxCrumbWidth = 160.dp
private val CrumbArrowWidth = 32.dp

@Composable
private fun CourseAnnouncements(
    state: ListUiState<Announcement>,
    selectedItem: OpenItem?,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onToggleRead: (Announcement) -> Unit,
    onHighlight: (Announcement, Highlight) -> Unit,
) {
    val selectedId = (selectedItem as? OpenItem.Announcement)?.id
    RefreshableList(
        state = state,
        onRefresh = onRefresh,
        emptyIcon = Icons.Outlined.Campaign,
        emptyTitle = stringResource(R.string.announcements_empty_course),
    ) {
        itemsIndexed(state.items, key = { _, it -> it.id }) { index, announcement ->
            SwipeableAnnouncementRow(
                announcement = announcement,
                selected = announcement.id == selectedId,
                showCourse = false,
                position = ItemPosition(index, state.items.size),
                onClick = { onOpen(announcement.id) },
                onToggleRead = { onToggleRead(announcement) },
                onHighlight = { onHighlight(announcement, it) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun FolderList(
    courseId: String,
    folderId: String?,
    selectedItem: OpenItem?,
    snackbarHostState: SnackbarHostState,
    onOpenFolder: (be.tolly.core.model.ContentNode) -> Unit,
    onOpenItem: (OpenItem) -> Unit,
) {
    val viewModel = hiltViewModel<FolderViewModel, FolderViewModel.Factory>(key = "folder:$courseId:${folderId ?: "root"}") {
        it.create(courseId, folderId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HandleFileEvents(viewModel.files.events, snackbarHostState)

    val selectedContentId = (selectedItem as? OpenItem.Document)?.contentId
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var optionsFor by remember { mutableStateOf<String?>(null) }
    RefreshableList(
        state = state,
        onRefresh = viewModel::refresh,
        emptyIcon = Icons.Outlined.FolderOpen,
        emptyTitle = stringResource(R.string.content_empty),
    ) {
        itemsIndexed(state.items, key = { _, it -> it.key }) { index, row ->
            val position = ItemPosition(index, state.items.size)
            when (row) {
                is FolderRow.Item -> {
                    val node = row.item.node
                    SwipeActions(
                        position = position,
                        startAction = highlightAction(row.item.highlight, Highlight.ORANGE) {
                            viewModel.toggleHighlight(node, Highlight.ORANGE)
                        },
                        endAction = null,
                        modifier = Modifier.animateItem(),
                    ) {
                        ContentRow(
                            item = row.item,
                            selected = node.id == selectedContentId,
                            position = position,
                            onClick = {
                                if (row.item.expandsInline) {
                                    haptics.performHapticFeedback(
                                        if (row.item.expanded) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                    )
                                }
                                handleClick(
                                    item = row.item,
                                    onOpenFolder = onOpenFolder,
                                    onOpenItem = onOpenItem,
                                    viewModel = viewModel,
                                )
                            },
                            onLongClick = if (node.type == ContentType.FILE) {
                                { optionsFor = node.id }
                            } else {
                                null
                            },
                        )
                    }
                }
                is FolderRow.EmptyModule -> EmptyModuleRow(position, Modifier.animateItem())
            }
        }
    }

    val options = optionsFor?.let { id -> state.items.firstNotNullOfOrNull { (it as? FolderRow.Item)?.item?.takeIf { item -> item.node.id == id } } }
    if (options != null) {
        FileOptionsSheet(
            name = options.node.fileName ?: options.node.title,
            mimeType = options.node.mimeType,
            download = options.download,
            onAction = { action -> viewModel.files.act(FileTarget.Item(options.node), action) },
            onDismiss = { optionsFor = null },
            onOpenInToledo = { context.openInBrowser(options.node.webUrl) },
        )
    }
}

private fun handleClick(
    item: ContentItemUi,
    onOpenFolder: (be.tolly.core.model.ContentNode) -> Unit,
    onOpenItem: (OpenItem) -> Unit,
    viewModel: FolderViewModel,
) {
    val node = item.node
    when {
        node.isPage -> onOpenItem(OpenItem.Document(node.id, isPage = true, title = node.title))
        item.expandsInline -> viewModel.toggleModule(node)
        node.type.isContainer -> {
            // A folder inside an expanded module: keep the module in the breadcrumbs.
            item.module?.let(onOpenFolder)
            onOpenFolder(node)
        }
        node.type == ContentType.DOCUMENT -> onOpenItem(OpenItem.Document(node.id, isPage = false, title = node.title))
        node.type == ContentType.FILE -> viewModel.files.open(node)
        node.type == ContentType.LINK -> viewModel.files.browse(node.url ?: node.webUrl)
        else -> viewModel.files.browse(node.webUrl)
    }
}

private const val TAB_COUNT = 4
