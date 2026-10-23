package be.tolly.core.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A lesson from the KU Leuven timetable. */
data class TimetableLesson(
    val title: String,
    val start: Long,
    val end: Long?,
    val location: String?,
    /** Every text of the entry, to match it with a course (course codes, names). */
    val text: String,
    /** The calendar's own id for the lesson, stable across refreshes. */
    val uid: String? = null,
    /** The entry's description: in TimeEdit the activity and the group (see [TimetableGroups]). */
    val details: String? = null,
)

/**
 * Reads lessons from the iCalendar feed KU Loket hands out (TimeEdit). A lesson's SUMMARY looks
 * like `G0N43a Wiskunde I: oefeningen, geen docent, 1 ba informatica, reeks 2`: course code and
 * title, then the teacher and the groups. The title shown is the course title without the code.
 */
object TimetableFeedParser {
    private val defaultZone: ZoneId = ZoneId.of("Europe/Brussels")
    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** A KU Leuven course code (`G0N43a`, `H01O9a`) at the start of a summary. */
    private val courseCode = Regex("""^[A-Z][0-9A-Z]{4}[a-z]?\s+""")

    fun isCalendar(body: String): Boolean = body.trimStart().startsWith("BEGIN:VCALENDAR")

    fun parse(body: String): List<TimetableLesson> {
        val lessons = mutableListOf<TimetableLesson>()
        var event: MutableList<Property>? = null
        for (line in unfold(body)) {
            when {
                line == "BEGIN:VEVENT" -> event = mutableListOf()
                line == "END:VEVENT" -> {
                    event?.let { lesson(it) }?.let(lessons::add)
                    event = null
                }
                event != null -> property(line)?.let(event::add)
            }
        }
        return lessons.sortedBy { it.start }
    }

    private class Property(val name: String, val params: Map<String, String>, val value: String)

    /** Content lines with folded continuations (a line break followed by a space or tab) joined. */
    private fun unfold(body: String): List<String> =
        body.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").split('\n').map { it.trimEnd('\r') }

    private fun property(line: String): Property? {
        val colon = line.indexOf(':').takeIf { it > 0 } ?: return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { param ->
            param.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k.uppercase() to v.trim('"') }
        }.toMap()
        return Property(head.first().uppercase(), params, line.substring(colon + 1))
    }

    private fun lesson(properties: List<Property>): TimetableLesson? {
        fun find(name: String) = properties.firstOrNull { it.name == name }
        val start = find("DTSTART")?.let(::instant) ?: return null
        val end = find("DTEND")?.let(::instant)?.takeIf { it > start }
        val summary = find("SUMMARY")?.value?.let(::unescape)?.trim().orEmpty()
        val title = summary.substringBefore(", ").replace(courseCode, "").trim().ifBlank { summary }
        if (title.isBlank()) return null
        val location = find("LOCATION")?.value?.let(::unescape)?.trim()?.takeIf { it.isNotBlank() }
        val description = find("DESCRIPTION")?.value?.let(::unescape).orEmpty()
        return TimetableLesson(
            title = title,
            start = start,
            end = end,
            location = location,
            text = "$summary $description",
            uid = find("UID")?.value?.takeIf { it.isNotBlank() },
            details = description.takeIf { it.isNotBlank() },
        )
    }

    /** `20261005T070000Z` (UTC), a local time in the TZID zone (or Brussels), or an all-day date. */
    private fun instant(property: Property): Long? = runCatching {
        val value = property.value.trim()
        when {
            value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), dateTime).toInstant(ZoneOffset.UTC).toEpochMilli()
            value.length == 8 -> LocalDate.parse(value, date).atStartOfDay(zone(property)).toInstant().toEpochMilli()
            else -> LocalDateTime.parse(value, dateTime).atZone(zone(property)).toInstant().toEpochMilli()
        }
    }.getOrNull()

    private fun zone(property: Property): ZoneId =
        property.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone

    private fun unescape(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                out.append(if (text[i + 1] == 'n' || text[i + 1] == 'N') '\n' else text[i + 1])
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
