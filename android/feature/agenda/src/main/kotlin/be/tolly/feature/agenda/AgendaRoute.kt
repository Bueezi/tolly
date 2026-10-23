package be.tolly.feature.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.semantics.Role
import be.tolly.core.data.TimetableGroups
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material3.TriStateCheckbox
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.SectionHeader
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyPullToRefresh
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollySheet
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.courseColors
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.scrollingContentPadding
import be.tolly.core.designsystem.theme.LocalGlass
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.AgendaItem
import be.tolly.core.model.AgendaItemKind
import be.tolly.core.ui.ListMessageState
import be.tolly.core.ui.RefreshErrorBanner
import be.tolly.core.ui.openInBrowser
import be.tolly.core.ui.withHaptic
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Agenda destination: Toledo's calendar (course events and due dates) as a list of the coming
 * weeks, or as a Day or Week time grid in which the gaps between classes can be seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaRoute(
    /** Shows a course in the Courses tab, from an agenda item's sheet. */
    onOpenCourse: (courseId: String) -> Unit,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    // A wide window has room for both: the deadlines stand beside the agenda, like a course's
    // content beside the course list, and need no switch.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= SideBySideWidth) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { AgendaScreen(onOpenCourse, viewModel, pageToggle = {}) }
                Box(Modifier.width(DeadlinesPaneWidth)) { DeadlinesRoute(onOpenCourse) }
            }
        } else {
            AgendaPages(onOpenCourse, viewModel)
        }
    }
}

/** How wide the window must be to show the deadlines beside the agenda. */
private val SideBySideWidth = 840.dp
private val DeadlinesPaneWidth = 400.dp

@Composable
private fun AgendaPages(onOpenCourse: (courseId: String) -> Unit, viewModel: AgendaViewModel) {
    // The agenda and the deadlines are two pages of one tab. Their switch, and the agenda's view,
    // are buttons in the top bar: rows of segments under the title took too much of the screen.
    var deadlines by rememberSaveable { mutableStateOf(false) }
    // Back from the deadlines returns to the agenda, as from any screen opened on top of it.
    BackHandler(enabled = deadlines) { deadlines = false }
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = withHaptic(HapticFeedbackType.SegmentTick) { deadlines = !deadlines }) {
            Icon(
                if (deadlines) Icons.Outlined.CalendarMonth else Icons.Outlined.Assignment,
                contentDescription = stringResource(if (deadlines) R.string.agenda_show_calendar else R.string.agenda_show_deadlines),
            )
        }
    }
    if (deadlines) DeadlinesRoute(onOpenCourse, actions = toggle) else AgendaScreen(onOpenCourse, viewModel, toggle)
}

@Composable
private fun AgendaScreen(
    onOpenCourse: (courseId: String) -> Unit,
    viewModel: AgendaViewModel,
    /** The switch to the deadlines, among the top bar's buttons. */
    pageToggle: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }

    val glass = LocalGlass.current
    val grid = state.mode != AgendaMode.LIST
    TollyScaffold(
        topBar = {
            TollyTopBar(
                title = { Text(stringResource(R.string.nav_agenda)) },
                // "Next 8 weeks" is what the list shows; the Day and Week views name their own dates.
                subtitle = when {
                    state.deadlinesThisWeek > 0 -> {
                        { Text(pluralStringResource(R.plurals.agenda_deadlines_this_week, state.deadlinesThisWeek, state.deadlinesThisWeek)) }
                    }
                    !grid -> {
                        { Text(stringResource(R.string.agenda_subtitle)) }
                    }
                    else -> null
                },
                actions = {
                    ModeButton(state.mode, onSelect = viewModel::setMode)
                    pageToggle()
                    SeriesMenu(viewModel)
                    IconButton(onClick = { context.openInBrowser(UltraEndpoints.CALENDAR_URL) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.agenda_open_calendar))
                    }
                },
                refreshing = state.refreshing && !state.loading,
                bottom = {
                    AgendaHeader(state, viewModel)
                    RefreshErrorBanner(
                        state.error,
                        hasContent = state.days.isNotEmpty() || state.gridDays.isNotEmpty(),
                        onRetry = viewModel::refresh,
                    )
                },
            )
        },
    ) { padding ->
        // With glass the content scrolls under the bar. Without, the whole body starts below it:
        // sticky day headers would otherwise stick behind the bar.
        val belowBar = if (glass) 0.dp else padding.calculateTopPadding()
        val listTop = padding.calculateTopPadding() - belowBar
        TollyPullToRefresh(
            onRefresh = withHaptic(HapticFeedbackType.GestureThresholdActivate, viewModel::refresh),
            topPadding = listTop,
            modifier = Modifier.fillMaxSize().padding(top = belowBar),
        ) {
            val contentPadding = scrollingContentPadding(padding, top = -belowBar, bottom = 16.dp)
            when {
                state.loading -> LoadingState(Modifier.padding(top = listTop))
                state.mode == AgendaMode.DAY -> DayView(
                    state = state,
                    contentPadding = contentPadding,
                    onSelect = viewModel::selectDate,
                    onOpen = { openedId = it.id },
                )
                state.mode == AgendaMode.WEEK -> WeekView(state = state, contentPadding = contentPadding, onOpen = { openedId = it.id })
                state.days.isEmpty() -> ListMessageState(
                    icon = Icons.Outlined.EventAvailable,
                    emptyTitle = stringResource(R.string.agenda_empty),
                    error = state.error,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(top = listTop),
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                        contentPadding = contentPadding,
                    ) {
                        state.days.forEach { day ->
                            // Sticky headers need a solid backing; over the glass background plain ones look better.
                            if (glass) {
                                item(key = "day-${day.date}") { DayHeader(day.date, state.today) }
                            } else {
                                stickyHeader(key = "day-${day.date}") { DayHeader(day.date, state.today) }
                            }
                            itemsIndexed(day.items, key = { _, it -> it.id }) { index, item ->
                                AgendaRow(
                                    item = item,
                                    position = ItemPosition(index, day.items.size),
                                    onClick = { openedId = item.id },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val opened = openedId?.let { id ->
        state.days.firstNotNullOfOrNull { day -> day.items.firstOrNull { it.id == id } }
            ?: state.gridDays.values.firstNotNullOfOrNull { items -> items.firstOrNull { it.id == id } }
    }
    if (opened != null) {
        val otherGroups by viewModel.otherGroups.collectAsStateWithLifecycle()
        LaunchedEffect(opened.id) { viewModel.loadOtherGroups(opened) }
        AgendaItemSheet(
            item = opened,
            otherGroups = otherGroups?.takeIf { it.first == opened.id }?.second,
            onDismiss = { openedId = null },
            // The sheet closes: it is a window of its own, so it stayed over the course sliding in and
            // popped up again only after back had slid the course away.
            onOpenCourse = { courseId ->
                openedId = null
                onOpenCourse(courseId)
            },
        )
    }
}

/**
 * The top bar's view button: shows the current view and moves on to the next one (List, Day,
 * Week, and round again), so the views take no row of their own under the title.
 */
@Composable
private fun ModeButton(mode: AgendaMode, onSelect: (AgendaMode) -> Unit) {
    val modes = AgendaMode.entries
    val next = modes[(mode.ordinal + 1) % modes.size]
    IconButton(onClick = withHaptic(HapticFeedbackType.SegmentTick) { onSelect(next) }) {
        Icon(
            when (mode) {
                AgendaMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
                AgendaMode.DAY -> Icons.Outlined.CalendarViewDay
                AgendaMode.WEEK -> Icons.Outlined.CalendarViewWeek
            },
            contentDescription = stringResource(R.string.agenda_view, stringResource(mode.label), stringResource(next.label)),
        )
    }
}

private val AgendaMode.label: Int
    get() = when (this) {
        AgendaMode.LIST -> R.string.agenda_mode_list
        AgendaMode.DAY -> R.string.agenda_mode_day
        AgendaMode.WEEK -> R.string.agenda_mode_week
    }

/**
 * The top bar's menu of reeksen: for each course the user has a reeks in, its reeksen with a check
 * for those the agenda shows. The user's own are always shown; ticking another adds its sessions
 * (see TimeEditRepository.lessonsOf).
 */
@Composable
private fun SeriesMenu(viewModel: AgendaViewModel) {
    var open by remember { mutableStateOf(false) }
    var perCourse by remember { mutableStateOf(false) }
    val choices by viewModel.seriesMenu.collectAsStateWithLifecycle()
    val shown by viewModel.extraSeries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Box {
        IconButton(onClick = {
            viewModel.loadSeriesMenu()
            open = true
        }) {
            Icon(Icons.Outlined.Groups, contentDescription = stringResource(R.string.agenda_series_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val list = choices
            when {
                list == null -> MenuNote(stringResource(R.string.agenda_other_series_loading))
                list.isEmpty() -> MenuNote(stringResource(R.string.agenda_series_menu_none))
                else -> {
                    // Each series number once, for all courses together; the courses one by one are
                    // in the sheet below, for a reeks that isn't numbered or only one course's.
                    seriesNumbers(list, shown).forEach { row ->
                        val label = context.getString(R.string.agenda_series, row.number)
                        DropdownMenuItem(
                            text = { Text(if (row.onlyYours) stringResource(R.string.agenda_series_yours, label) else label) },
                            leadingIcon = { TriStateCheckbox(state = row.state, onClick = null, enabled = !row.onlyYours) },
                            enabled = !row.onlyYours,
                            onClick = { viewModel.setSeriesNumber(row.number, row.state != ToggleableState.On) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.agenda_series_per_course)) },
                        leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                        onClick = {
                            open = false
                            perCourse = true
                        },
                    )
                }
            }
        }
    }
    if (perCourse) {
        SeriesPerCourseSheet(choices.orEmpty(), shown, onToggle = viewModel::setExtraSeries, onDismiss = { perCourse = false })
    }
}

/** One series number in the short menu: shown in all, some or none of the courses that have it besides the user's own. */
private class SeriesNumberRow(val number: Int, val state: ToggleableState, val onlyYours: Boolean)

private fun seriesNumbers(choices: List<SeriesChoice>, shown: Set<String>): List<SeriesNumberRow> {
    val byNumber = choices.flatMap { choice -> choice.groups.map { it to (it in choice.own) } }
        .mapNotNull { (group, own) -> TimetableGroups.seriesNumber(group)?.let { Triple(it, group, own) } }
        .groupBy { it.first }
    return byNumber.toSortedMap().map { (number, groups) ->
        val others = groups.filter { !it.third }.map { it.second }
        val on = others.count { it in shown }
        val state = when {
            others.isEmpty() || on == others.size -> ToggleableState.On
            on == 0 -> ToggleableState.Off
            else -> ToggleableState.Indeterminate
        }
        SeriesNumberRow(number, state, onlyYours = others.isEmpty())
    }
}

/** Every course's reeksen one by one, with the user's own ticked: the series menu's "Per course". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesPerCourseSheet(
    choices: List<SeriesChoice>,
    shown: Set<String>,
    onToggle: (group: String, shown: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    TollySheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.agenda_series_per_course_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            choices.forEach { choice ->
                SectionHeader(choice.title)
                choice.groups.forEachIndexed { index, group ->
                    val own = group in choice.own
                    val checked = own || group in shown
                    val label = seriesLabel(context, listOf(group))
                    TollyListItem(
                        headline = { Text(if (own) stringResource(R.string.agenda_series_yours, label) else label) },
                        leading = { Checkbox(checked = checked, onCheckedChange = null, enabled = !own) },
                        modifier = Modifier.groupItem(
                            ItemPosition(index, choice.groups.size),
                            enabled = !own,
                            role = Role.Checkbox,
                            onClick = { onToggle(group, !checked) },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 280.dp),
    )
}

/** For the Day and Week views, the date they show, under the title. */
@Composable
private fun AgendaHeader(state: AgendaUiState, viewModel: AgendaViewModel) {
    when (state.mode) {
        AgendaMode.LIST -> Unit
        AgendaMode.DAY -> {
            val date = state.selectedDate
            DateNavigator(
                title = dayTitle(date, state.today),
                summary = classSummary(listOf(state.gridDays[date].orEmpty())),
                previousLabel = stringResource(R.string.agenda_previous_day),
                nextLabel = stringResource(R.string.agenda_next_day),
                onPrevious = { viewModel.stepDays(-1) },
                onNext = { viewModel.stepDays(1) },
                onToday = if (date != state.today) ({ viewModel.selectDate(state.today) }) else null,
            )
        }
        AgendaMode.WEEK -> {
            val monday = state.selectedDate.weekStart()
            val week = shownDays(monday, state.gridDays)
            DateNavigator(
                title = weekTitle(monday),
                summary = classSummary(List(7) { state.gridDays[monday.plusDays(it.toLong())].orEmpty() }),
                previousLabel = stringResource(R.string.agenda_previous_week),
                nextLabel = stringResource(R.string.agenda_next_week),
                onPrevious = { viewModel.stepWeeks(-1) },
                onNext = { viewModel.stepWeeks(1) },
                onToday = if (monday != state.today.weekStart()) ({ viewModel.selectDate(state.today) }) else null,
            )
            WeekDayHeaders(week, state.today, onOpenDay = viewModel::openDay)
        }
    }
}

/** Previous and next buttons around the shown day or week, with what it holds under its name. */
@Composable
private fun DateNavigator(
    title: String,
    summary: String,
    previousLabel: String,
    nextLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = withHaptic(HapticFeedbackType.SegmentTick, onPrevious)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previousLabel)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (onToday != null) {
            TextButton(onClick = withHaptic(HapticFeedbackType.SegmentTick, onToday)) { Text(stringResource(R.string.agenda_today)) }
        }
        IconButton(onClick = withHaptic(HapticFeedbackType.SegmentTick, onNext)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextLabel)
        }
    }
}

/** "3 classes · 5 h 30 min" for the lessons of [days] (which are timed), or "No classes". */
@Composable
private fun classSummary(days: List<List<AgendaItem>>): String {
    val zone = remember { ZoneId.systemDefault() }
    val (count, minutes) = remember(days) { classLoad(days, zone) }
    if (count == 0) return stringResource(R.string.agenda_no_classes)
    return pluralStringResource(R.plurals.agenda_classes_summary, count, count, durationText(minutes))
}

@Composable
private fun durationText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.agenda_duration_min, rest)
        rest == 0 -> stringResource(R.string.agenda_duration_h, hours)
        else -> stringResource(R.string.agenda_duration_hm, hours, rest)
    }
}

/** How many lessons the days hold, and for how long in all (per day, overlapping ones counted once). */
private fun classLoad(days: List<List<AgendaItem>>, zone: ZoneId): Pair<Int, Int> {
    var count = 0
    var minutes = 0
    days.forEach { items ->
        val lessons = items.filter { it.kind == AgendaItemKind.LESSON && !it.isAllDay(zone) }
        count += lessons.size
        minutes += coveredMinutes(
            lessons.mapNotNull { item ->
                val date = java.time.Instant.ofEpochMilli(item.start).atZone(zone).toLocalDate()
                spanOn(item, date, zone)
            },
        )
    }
    return count to minutes
}

/** "Today · Thursday 22 October": the day's name and date. */
@Composable
internal fun dayTitle(date: LocalDate, today: LocalDate): String {
    val context = LocalContext.current
    val label = when (date) {
        today -> stringResource(R.string.agenda_today)
        today.plusDays(1) -> stringResource(R.string.agenda_tomorrow)
        else -> null
    }
    val formatted = remember(date) {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)
    }
    return listOfNotNull(label, formatted).joinToString(" · ")
}

/** "19 – 25 Oct". */
@Composable
private fun weekTitle(monday: LocalDate): String {
    val context = LocalContext.current
    return remember(monday) {
        val zone = ZoneId.systemDefault()
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        // Noon of the Sunday: a range ending at midnight would show the Monday after it.
        val end = monday.plusDays(6).atStartOfDay(zone).toInstant().toEpochMilli() + DateUtils.HOUR_IN_MILLIS * 12
        DateUtils.formatDateRange(context, start, end, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH)
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    // Sticky (Material) headers need a solid backing.
    Surface(color = Tolly.colors.screen, modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            dayTitle(date, today),
            color = if (date == today) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
}

@Composable
private fun AgendaRow(item: AgendaItem, position: ItemPosition, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The avatar's icon colour: strong enough for a thin stripe in both themes.
    val (_, accent) = courseColors(item.courseId ?: item.calendarName.orEmpty(), item.courseColor)
    val past = (item.end ?: item.start) < System.currentTimeMillis()
    val deadline = item.kind == AgendaItemKind.DEADLINE
    TollyListItem(
        modifier = modifier
            .groupItem(position, onClick = onClick, separatorStart = GroupDefaults.separatorStart(80.dp))
            .alpha(if (past) 0.55f else 1f),
        leading = {
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                TimeColumn(item)
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accent, RoundedCornerShape(2.dp)),
                )
            }
        },
        headline = {
            Text(item.shownTitle(), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (deadline) FontWeight.SemiBold else null)
        },
        supporting = {
            val parts = listOfNotNull(item.calendarName?.takeIf { it != item.title }, item.location)
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailing = if (deadline) {
            {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.agenda_due), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun TimeColumn(item: AgendaItem) {
    val context = LocalContext.current
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
        if (item.isAllDay()) {
            Text(stringResource(R.string.agenda_all_day), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        } else {
            Text(
                remember(item.start) { DateUtils.formatDateTime(context, item.start, DateUtils.FORMAT_SHOW_TIME) },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
            val end = item.end
            if (end != null && end > item.start && item.kind != AgendaItemKind.DEADLINE) {
                Text(
                    remember(end) { DateUtils.formatDateTime(context, end, DateUtils.FORMAT_SHOW_TIME) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaItemSheet(
    item: AgendaItem,
    otherGroups: OtherGroups?,
    onDismiss: () -> Unit,
    onOpenCourse: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val description = remember(item.description) {
        item.description?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }?.takeIf { it.isNotBlank() }
    }
    TollySheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val courseId = item.courseId
            if (courseId != null) {
                Text(item.shownTitle(), style = MaterialTheme.typography.titleLarge)
                CourseLink(item, courseId, onClick = { onOpenCourse(courseId) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge)
                        item.calendarName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            DetailLine(Icons.Outlined.Schedule, remember(item) { timeRange(context, item) })
            item.location?.let { DetailLine(Icons.Outlined.Place, it) }
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (item.timetableGroup != null && otherGroups != null) OtherGroupsSection(item, otherGroups)
            Button(
                onClick = { context.openInBrowser(UltraEndpoints.CALENDAR_URL) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_open_in_toledo))
            }
        }
    }
}

/** The item's course, as a row that opens it: a lesson's details are mostly in its course. */
@Composable
private fun CourseLink(item: AgendaItem, courseId: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CourseAvatar(name = item.calendarName.orEmpty(), seed = courseId, size = 40.dp, icon = item.courseIcon, color = item.courseColor)
            Column(Modifier.weight(1f)) {
                Text(item.calendarName.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.agenda_open_course), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun timeRange(context: Context, item: AgendaItem): String {
    val flags = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
    val end = item.end
    return if (end != null && end > item.start && item.kind != AgendaItemKind.DEADLINE) {
        DateUtils.formatDateRange(context, item.start, end, flags)
    } else {
        DateUtils.formatDateTime(context, item.start, flags)
    }
}

/**
 * The same session for the course's other reeksen that week, from the public timetable: when and
 * where each one is, to go to another one now and then (reeksen sharing a session are one row,
 * "Series 1 and 2"). Which reeksen the agenda itself shows is picked in its top bar (SeriesMenu).
 *
 * Folded at first, to a row that says which reeks is the user's: most taps on a lesson are about
 * the lesson itself, and the list made the sheet long. A tap on the row unfolds it.
 */
@Composable
private fun OtherGroupsSection(item: AgendaItem, state: OtherGroups) {
    val context = LocalContext.current
    val own = item.timetableGroup?.let { seriesLabel(context, listOf(it)) }
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .toggleable(value = open, role = Role.Button) {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    open = it
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (own != null) stringResource(R.string.agenda_other_series_yours, own) else stringResource(R.string.agenda_other_series),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = stringResource(if (open) R.string.action_collapse else R.string.action_expand),
                modifier = Modifier.rotate(turn),
            )
        }
        if (open) when (state) {
            OtherGroups.Loading -> SheetNote(stringResource(R.string.agenda_other_series_loading))
            OtherGroups.Failed -> SheetNote(stringResource(R.string.agenda_other_series_failed))
            is OtherGroups.Loaded -> {
                val sessions = remember(state.sessions) {
                    state.sessions.groupBy { Triple(it.start, it.end, it.location) }.values.toList()
                }
                if (sessions.isEmpty()) SheetNote(stringResource(R.string.agenda_other_series_none))
                sessions.forEach { shared ->
                    val session = shared.first()
                    val label = remember(shared) { seriesLabel(context, shared.map { it.group }) }
                    val time = remember(session) {
                        DateUtils.formatDateRange(
                            context,
                            session.start,
                            session.end ?: session.start,
                            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
                                DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
                        Column(Modifier.weight(1f)) {
                            Text(time, style = MaterialTheme.typography.bodyMedium)
                            session.location?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
