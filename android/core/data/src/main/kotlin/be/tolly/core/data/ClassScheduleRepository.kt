package be.tolly.core.data

import be.tolly.core.model.AgendaItem
import be.tolly.core.model.AgendaItemKind
import be.tolly.core.model.ClassNow
import be.tolly.core.model.Course
import be.tolly.core.model.SessionKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which class the user is in right now, or has next today, from the cached agenda. The course
 * list tints the row of that course; the course itself says so above its content.
 */
@Singleton
class ClassScheduleRepository @Inject constructor(
    private val agenda: AgendaRepository,
) {
    /** The current time, once a minute: which lesson is going on changes with it. */
    private val clock: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(MINUTE - now % MINUTE)
        }
    }

    /**
     * Today's lessons (and other course sessions): the ones going on now, and the next one to
     * start today. Which course each belongs to is decided by [onCourses].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val today: Flow<List<Pair<AgendaItem, ClassNow>>> = clock
        .map { LocalDate.now() }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            val zone = ZoneId.systemDefault()
            agenda.observe(day.atStartOfDay(zone).toInstant().toEpochMilli(), day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }
        .combine(clock) { items, now ->
            val sessions = items
                .filter { item ->
                    val end = item.end ?: return@filter false
                    item.courseId != null &&
                        (item.kind == AgendaItemKind.LESSON || item.kind == AgendaItemKind.EVENT) &&
                        end - item.start <= MAX_SESSION &&
                        end > now
                }
                .sortedBy { it.start }
            val ongoing = sessions.filter { it.start <= now }
            // Only the one class that starts next, and only today (the window is today).
            val next = sessions.firstOrNull { it.start > now }
            buildList {
                next?.let {
                    val minutes = ((it.start - now + MINUTE - 1) / MINUTE).toInt()
                    add(it to ClassNow(it.start, it.end!!, it.location, ClassNow.Status.NEXT, minutes, SessionKind.of(it.title)))
                }
                ongoing.forEach { add(it to ClassNow(it.start, it.end!!, it.location, ClassNow.Status.ONGOING, kind = SessionKind.of(it.title))) }
            }
        }
        .distinctUntilChanged()

    private companion object {
        const val MINUTE = 60_000L

        /** Longer calendar items (exam periods, whole days) aren't a class one is in. */
        const val MAX_SESSION = 6 * 60 * MINUTE
    }
}

/**
 * Puts each session on one of [courses], by course id. Exercise sessions and practicals sometimes
 * have their own Toledo course ("Gegevensbanken: oefeningen") that isn't among [courses]; their
 * sessions go on the course whose name theirs starts with ("Gegevensbanken"). An ongoing session
 * wins over the next one on the same course.
 */
fun List<Pair<AgendaItem, ClassNow>>.onCourses(courses: List<Course>): Map<String, ClassNow> = buildMap {
    for ((item, session) in this@onCourses) {
        val course = courseFor(item, courses) ?: continue
        if (session.status == ClassNow.Status.ONGOING || course !in this) put(course, session)
    }
}

private fun courseFor(item: AgendaItem, courses: List<Course>): String? {
    if (courses.any { it.id == item.courseId }) return item.courseId
    return courses.namedAtStartOf(listOfNotNull(item.calendarName, item.title))?.id
}

/**
 * The course among these whose name one of [names] starts with, as "Gegevensbanken: oefeningen"
 * starts with "Gegevensbanken"; the longest such name wins. A name only counts up to a word
 * boundary, so "Wiskunde II" doesn't start with "Wiskunde I".
 */
internal fun List<Course>.namedAtStartOf(names: List<String>): Course? {
    val wanted = names.map { it.lowercase() }
    // "Beginselen van Programmeren - BvP" is "Beginselen van programmeren" in the timetable.
    fun Course.names() = listOf(name, displayName, name.substringBefore(" - "), name.substringBefore(":"))
        .map { it.trim().lowercase() }
        .filter { it.length > 3 }
    fun String.startsWithWord(prefix: String) =
        startsWith(prefix) && getOrNull(prefix.length)?.isLetterOrDigit() != true
    return filter { course -> course.names().any { name -> wanted.any { it.startsWithWord(name) } } }
        .maxByOrNull { it.name.length }
}
