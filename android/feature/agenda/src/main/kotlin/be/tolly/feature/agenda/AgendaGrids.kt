package be.tolly.feature.agenda

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.courseColors
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.AgendaItem
import be.tolly.core.model.AgendaItemKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle as DayStyle
import java.util.Locale

/*
 * The Day and Week views: items drawn on a time grid, each placed and sized by its real start and
 * end, so the gaps between classes show. Both use the same hour window logic (see [hourWindow]).
 */

private val DayHourHeight = 64.dp
private val WeekHourHeight = 46.dp
private val DayLabelWidth = 48.dp
private val WeekLabelWidth = 30.dp

/** From this width on, a Week column's items show what the Day's do. */
private val RoomyColumnWidth = 96.dp

/** Pages on each side of today that the Day view can swipe to; about ten years. */
private const val DAY_PAGES = 3660

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun AgendaItem.date(): LocalDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()

/** A day's items on the time grid, swiped between days by a pager. */
@Composable
internal fun DayView(
    state: AgendaUiState,
    contentPadding: PaddingValues,
    onSelect: (LocalDate) -> Unit,
    onOpen: (AgendaItem) -> Unit,
) {
    // The pager's pages count days from the day it was created; a new day starting meanwhile
    // doesn't move them.
    val anchor = remember { state.today }
    fun pageOf(date: LocalDate) = DAY_PAGES + ChronoUnit.DAYS.between(anchor, date).toInt()
    val pager = rememberPagerState(initialPage = pageOf(state.selectedDate)) { DAY_PAGES * 2 + 1 }
    // Only a swipe picks a day here. A day picked with the buttons moves the pager, and a quick
    // second tap cancels that move halfway: reporting the page it stopped on undid the tap.
    val swiped = remember { BooleanArray(1) }
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { if (it is DragInteraction.Start) swiped[0] = true }
    }
    LaunchedEffect(state.selectedDate) {
        val target = pageOf(state.selectedDate)
        if (target != pager.currentPage) {
            swiped[0] = false
            pager.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            if (swiped[0]) onSelect(anchor.plusDays((page - DAY_PAGES).toLong()))
        }
    }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
        val date = anchor.plusDays((page - DAY_PAGES).toLong())
        DayPage(date, state.gridDays[date].orEmpty(), state.today, contentPadding, onOpen)
    }
}

@Composable
private fun DayPage(date: LocalDate, items: List<AgendaItem>, today: LocalDate, contentPadding: PaddingValues, onOpen: (AgendaItem) -> Unit) {
    val timed = remember(items) { items.filter { !it.isAllDay(zone) && it.kind != AgendaItemKind.DEADLINE } }
    val spans = remember(timed, date) { timed.map { spanOn(it, date, zone) ?: MinuteSpan(0, 1) } }
    val window = remember(spans) { hourWindow(spans) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Starts near the first item (or, today, the current time), not at the top of the night.
    LaunchedEffect(date, window) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        val now = System.currentTimeMillis()
        var focus = spans.minOfOrNull { it.start } ?: (8 * 60)
        if (date == today) focus = minOf(focus, minuteOfDay(now, zone)).coerceAtLeast(0)
        val y = (window.hoursFromTop(focus) - 0.5f).coerceAtLeast(0f) * with(density) { DayHourHeight.toPx() }
        scroll.scrollTo(y.toInt())
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(contentPadding)) {
        val strip = items.filter { it.isAllDay(zone) || it.kind == AgendaItemKind.DEADLINE }
        if (strip.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                strip.forEach { StripChip(it, onClick = { onOpen(it) }) }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp)) {
            HourLabels(window, DayHourHeight, DayLabelWidth, fontSize = 11)
            BoxWithConstraints(
                Modifier
                    .padding(start = DayLabelWidth)
                    .fillMaxWidth()
                    .height(DayHourHeight * window.hours)
                    .hourLines(window, DayHourHeight, Tolly.colors.separator),
            ) {
                val slots = remember(spans) { layoutColumns(spans) }
                timed.forEachIndexed { index, item ->
                    val span = spans[index]
                    val slot = slots[index]
                    val columnWidth = maxWidth / slot.count
                    val height = (DayHourHeight * ((span.end - span.start) / 60f) - 2.dp).coerceAtLeast(24.dp)
                    EventBlock(
                        item = item,
                        compact = false,
                        height = height,
                        onClick = { onOpen(item) },
                        modifier = Modifier
                            .offset(x = columnWidth * slot.column, y = DayHourHeight * window.hoursFromTop(span.start))
                            .width(columnWidth - 3.dp)
                            .height(height),
                    )
                }
                if (date == today) NowLine(window, DayHourHeight, withDot = true)
            }
        }
    }
}

/** A deadline or an all-day item, above the grid: it has no stretch of time to draw. */
@Composable
private fun StripChip(item: AgendaItem, onClick: () -> Unit) {
    val deadline = item.kind == AgendaItemKind.DEADLINE
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (deadline) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (deadline) Icon(Icons.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
            val context = LocalContext.current
            val time = if (deadline) DateUtils.formatDateTime(context, item.start, DateUtils.FORMAT_SHOW_TIME) else stringResource(R.string.agenda_all_day)
            Text(
                "${if (deadline) stringResource(R.string.agenda_due) + " " else ""}$time · ${item.title}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A week's items on one time grid, a column per day. */
@Composable
internal fun WeekView(state: AgendaUiState, contentPadding: PaddingValues, onOpen: (AgendaItem) -> Unit) {
    val monday = state.selectedDate.weekStart()
    val week = shownDays(monday, state.gridDays)
    val perDay = week.map { date -> state.gridDays[date].orEmpty() }
    val timed = remember(perDay) { perDay.map { items -> items.filter { !it.isAllDay(zone) && it.kind != AgendaItemKind.DEADLINE } } }
    val spans = remember(timed, monday) { timed.mapIndexed { i, items -> items.map { spanOn(it, week[i], zone) ?: MinuteSpan(0, 1) } } }
    val window = remember(spans) { hourWindow(spans.flatten()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp)) {
            HourLabels(window, WeekHourHeight, WeekLabelWidth, fontSize = 10)
            week.forEachIndexed { i, date ->
                WeekColumn(
                    date = date,
                    today = state.today,
                    window = window,
                    items = timed[i],
                    spans = spans[i],
                    deadlines = perDay[i].filter { it.kind == AgendaItemKind.DEADLINE },
                    onOpen = onOpen,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekColumn(
    date: LocalDate,
    today: LocalDate,
    window: HourWindow,
    items: List<AgendaItem>,
    spans: List<MinuteSpan>,
    deadlines: List<AgendaItem>,
    onOpen: (AgendaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    val isToday = date == today
    BoxWithConstraints(
        modifier
            .height(WeekHourHeight * window.hours)
            .then(if (isToday) Modifier.background(tint) else Modifier)
            .hourLines(window, WeekHourHeight, Tolly.colors.separator, divider = true),
    ) {
        val slots = remember(spans) { layoutColumns(spans) }
        items.forEachIndexed { index, item ->
            val span = spans[index]
            val slot = slots[index]
            val columnWidth = maxWidth / slot.count
            val height = (WeekHourHeight * ((span.end - span.start) / 60f) - 1.dp).coerceAtLeast(18.dp)
            EventBlock(
                item = item,
                // A tablet's week has columns wide enough for the Day view's title, time and place.
                compact = columnWidth < RoomyColumnWidth,
                height = height,
                onClick = { onOpen(item) },
                modifier = Modifier
                    .offset(x = columnWidth * slot.column + 1.dp, y = WeekHourHeight * window.hoursFromTop(span.start))
                    .width(columnWidth - 2.dp)
                    .height(height),
            )
        }
        // A deadline is a dot at its time, from the right edge in.
        deadlines.forEachIndexed { index, item ->
            val minute = minuteOfDay(item.start, zone).coerceIn(window.startHour * 60, window.endHour * 60)
            Box(
                Modifier
                    .offset(x = maxWidth - 16.dp - 7.dp * index, y = WeekHourHeight * window.hoursFromTop(minute) - 8.dp)
                    .size(16.dp)
                    .clickable { onOpen(item) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
            }
        }
        if (isToday) NowLine(window, WeekHourHeight, withDot = false)
    }
}

/**
 * The days of the week that starts on [monday] the Week view has columns for: Monday to Friday,
 * and Saturday or Sunday only when something is on then, so the weekdays get the room.
 */
internal fun shownDays(monday: LocalDate, items: Map<LocalDate, List<AgendaItem>>): List<LocalDate> =
    List(7) { monday.plusDays(it.toLong()) }.filter { it.dayOfWeek.value <= 5 || !items[it].isNullOrEmpty() }

/** The weekday letters and dates above the Week grid; a tap on one opens that day. */
@Composable
internal fun WeekDayHeaders(week: List<LocalDate>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = WeekLabelWidth, end = 8.dp, bottom = 4.dp)) {
        week.forEach { date ->
            val isToday = date == today
            Column(
                Modifier.weight(1f).clickable { onOpenDay(date) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isToday) FontWeight.Bold else null,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** The hour numbers down the left edge, each centred on its line. */
@Composable
private fun HourLabels(window: HourWindow, hourHeight: Dp, width: Dp, fontSize: Int) {
    Box(Modifier.width(width).height(hourHeight * window.hours)) {
        for (i in 0..window.hours) {
            Text(
                "%02d".format(window.startHour + i),
                fontSize = fontSize.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = hourHeight * i - (fontSize * 0.75f).dp)
                    .padding(end = 6.dp),
            )
        }
    }
}

/** Hairlines at every hour, and with [divider] one down the left edge between the Week's days. */
private fun Modifier.hourLines(window: HourWindow, hourHeight: Dp, color: androidx.compose.ui.graphics.Color, divider: Boolean = false) =
    drawBehind {
        val step = hourHeight.toPx()
        for (i in 0..window.hours) {
            drawLine(color, Offset(0f, i * step), Offset(size.width, i * step), strokeWidth = 1f)
        }
        if (divider) drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
    }

/** A line at the current time, with a dot at its start in the Day view. */
@Composable
private fun NowLine(window: HourWindow, hourHeight: Dp, withDot: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val minute = minuteOfDay(now, zone)
    if (minute < window.startHour * 60 || minute > window.endHour * 60) return
    val color = MaterialTheme.colorScheme.error
    Box(Modifier.offset(y = hourHeight * window.hoursFromTop(minute) - 4.dp).fillMaxWidth().height(8.dp)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(2.dp).background(color))
        if (withDot) Box(Modifier.align(Alignment.CenterStart).offset(x = (-4).dp).size(8.dp).background(color, CircleShape))
    }
}

/**
 * One item on a grid, in its course's colours like the list's avatars. [compact] is the Week's
 * narrow column: a short course name at 10 sp; the Day shows title, time and place as far as the
 * [height] allows.
 */
@Composable
private fun EventBlock(item: AgendaItem, compact: Boolean, height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (container, accent) = courseColors(item.courseId ?: item.calendarName.orEmpty(), item.courseColor)
    val context = LocalContext.current
    Surface(onClick = onClick, shape = RoundedCornerShape(if (compact) 4.dp else 8.dp), color = container, modifier = modifier) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(if (compact) 2.dp else 4.dp).fillMaxHeight().background(accent))
            if (compact) {
                Text(
                    abbreviation(item.calendarName?.takeIf { it.isNotBlank() } ?: item.title),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = ((height.value - 4) / 11).toInt().coerceIn(1, 4),
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                )
            } else {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    val lines = ((height.value - 8) / 16).toInt()
                    Text(
                        item.shownTitle(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = (lines - 1).coerceIn(1, 3),
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (lines >= 2) {
                        val range = remember(item) {
                            val end = item.end
                            if (end != null && end > item.start) {
                                DateUtils.formatDateRange(context, item.start, end, DateUtils.FORMAT_SHOW_TIME)
                            } else {
                                DateUtils.formatDateTime(context, item.start, DateUtils.FORMAT_SHOW_TIME)
                            }
                        }
                        Text(
                            listOfNotNull(range, item.location).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent.copy(alpha = 0.85f),
                            maxLines = if (lines >= 5) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
