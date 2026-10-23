package be.tolly.feature.agenda

import android.text.format.DateUtils
import be.tolly.core.model.AgendaItem
import be.tolly.core.model.AgendaItemKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The maths behind the Day and Week grids, kept free of Compose so it can be tested: which hours
 * a day shows, where a time falls on them, and how overlapping items share the width.
 */

/** A stretch of one day, in minutes after midnight. */
internal data class MinuteSpan(val start: Int, val end: Int)

/** The hours a grid shows, from the line of [startHour] to the line of [endHour]. */
internal data class HourWindow(val startHour: Int, val endHour: Int) {
    val hours: Int get() = endHour - startHour

    /** Where [minute] of the day falls on the grid, in hours from its top. */
    fun hoursFromTop(minute: Int): Float = minute / 60f - startHour
}

/** An item that is shorter than this is drawn this tall, so it can still be read and tapped. */
internal const val MIN_DISPLAY_MINUTES = 30

/**
 * The hours to show for [spans]: from the hour the first starts in (a class at 8:30 starts the
 * grid at 8, not 7) to an hour after the last, but at least [minStart] to [minEnd], so a quiet
 * day still looks like a day.
 */
internal fun hourWindow(spans: List<MinuteSpan>, minStart: Int = 8, minEnd: Int = 18): HourWindow {
    if (spans.isEmpty()) return HourWindow(minStart, minEnd)
    val first = spans.minOf { it.start } / 60
    val last = (spans.maxOf { it.end } + 59) / 60 + 1
    return HourWindow(minOf(minStart, first).coerceAtLeast(0), maxOf(minEnd, last).coerceAtMost(24))
}

/**
 * The part of [date] that [item] covers, or null if it doesn't touch it. An item with no length
 * gets [MIN_DISPLAY_MINUTES], and one that runs past midnight is cut at the day's edges.
 */
internal fun spanOn(item: AgendaItem, date: LocalDate, zone: ZoneId): MinuteSpan? {
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = item.end?.takeIf { it > item.start } ?: (item.start + MIN_DISPLAY_MINUTES * DateUtils.MINUTE_IN_MILLIS)
    if (item.start >= dayEnd || end <= dayStart) return null
    val start = ((maxOf(item.start, dayStart) - dayStart) / DateUtils.MINUTE_IN_MILLIS).toInt()
    val stop = ((minOf(end, dayEnd) - dayStart) / DateUtils.MINUTE_IN_MILLIS).toInt()
    return MinuteSpan(start, maxOf(stop, start + 1))
}

/** The minute of its day at which [millis] falls. */
internal fun minuteOfDay(millis: Long, zone: ZoneId): Int {
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    return time.hour * 60 + time.minute
}

/** The minutes covered by [spans] together; where two overlap, the overlap counts once. */
internal fun coveredMinutes(spans: List<MinuteSpan>): Int {
    var total = 0
    var from = 0
    var to = -1
    for (span in spans.sortedBy { it.start }) {
        if (span.start > to) {
            if (to >= 0) total += to - from
            from = span.start
            to = span.end
        } else {
            to = maxOf(to, span.end)
        }
    }
    return if (to >= 0) total + to - from else 0
}

/** Which of [count] side-by-side columns an item takes, so overlapping items don't cover each other. */
internal data class ColumnSlot(val column: Int, val count: Int)

/**
 * Lays out [spans] in columns: each item takes the first column free at its start, and every item
 * of a group of overlapping ones gets that group's column count, so they share the width equally.
 * The result is in the order of [spans].
 */
internal fun layoutColumns(spans: List<MinuteSpan>): List<ColumnSlot> {
    val order = spans.indices.sortedWith(compareBy({ spans[it].start }, { -spans[it].end }))
    val columns = IntArray(spans.size)
    val counts = IntArray(spans.size)
    val group = mutableListOf<Int>()
    val columnEnds = mutableListOf<Int>()
    var groupEnd = -1
    fun closeGroup() {
        group.forEach { counts[it] = columnEnds.size }
        group.clear()
        columnEnds.clear()
    }
    for (index in order) {
        val span = spans[index]
        if (group.isNotEmpty() && span.start >= groupEnd) closeGroup()
        var column = columnEnds.indexOfFirst { it <= span.start }
        if (column < 0) {
            column = columnEnds.size
            columnEnds.add(span.end)
        } else {
            columnEnds[column] = span.end
        }
        columns[index] = column
        groupEnd = if (group.isEmpty()) span.end else maxOf(groupEnd, span.end)
        group.add(index)
    }
    closeGroup()
    return spans.indices.map { ColumnSlot(columns[it], counts[it]) }
}

/** The Monday of the week [this] is in. */
internal fun LocalDate.weekStart(): LocalDate = with(DayOfWeek.MONDAY)

/**
 * A short name for a narrow week column: the initials of the significant words of a long name
 * ("Inleiding Statistiek" gives "IS"), or the one word of a short one.
 */
internal fun abbreviation(name: String): String {
    val words = name.split(' ', '-', ':', '/').filter { it.isNotBlank() }
    val significant = words.filter { it.length > 2 && it.first().isLetter() }
    return if (significant.size >= 2) {
        significant.take(4).joinToString("") { it.first().uppercase() }
    } else {
        (significant.firstOrNull() ?: words.firstOrNull() ?: name).take(9)
    }
}

/** Starts at midnight and lasts (about) a whole day, or has no time of its own. */
internal fun AgendaItem.isAllDay(zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (kind == AgendaItemKind.DEADLINE) return false
    val begin = Instant.ofEpochMilli(start).atZone(zone)
    val startsAtMidnight = begin.hour == 0 && begin.minute == 0
    val length = (end ?: return startsAtMidnight) - start
    return startsAtMidnight && length >= DateUtils.DAY_IN_MILLIS - DateUtils.MINUTE_IN_MILLIS
}
