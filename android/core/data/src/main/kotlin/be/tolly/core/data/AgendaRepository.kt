package be.tolly.core.data

import android.util.Log
import be.tolly.core.database.CalendarDao
import be.tolly.core.database.CalendarItemEntity
import be.tolly.core.database.SyncStateDao
import be.tolly.core.database.SyncStateEntity
import be.tolly.core.model.AgendaItem
import be.tolly.core.model.AgendaItemKind
import be.tolly.core.model.Course
import be.tolly.core.model.CourseIcon
import be.tolly.core.network.UltraApi
import be.tolly.core.network.collectAllPages
import be.tolly.core.network.dto.CalendarItemDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The agenda for a window of days, cached for offline use: the Toledo calendar (course events,
 * due dates, institution and personal items) plus the lessons of the KU Leuven timetable, which
 * come from KU Loket rather than Toledo (see [TimetableRepository]).
 */
@Singleton
class AgendaRepository @Inject constructor(
    private val api: UltraApi,
    private val calendarDao: CalendarDao,
    private val syncStateDao: SyncStateDao,
    private val courses: CourseRepository,
    private val timetable: TimetableRepository,
    private val timeEdit: TimeEditRepository,
    private val preferences: PreferencesRepository,
) {
    fun observe(from: Long, until: Long): Flow<List<AgendaItem>> =
        combine(calendarDao.observeBetween(from, until), courses.observeCourses()) { items, courseList ->
            val byId = courseList.associateBy { it.id }
            items.map { it.toModel(byId) }
        }

    fun observeLastSynced(): Flow<Long?> = syncStateDao.observe(SYNC_KEY)

    /**
     * Emits when the timetable link is found (or renewed) after the agenda was shown: the lookup
     * after signing in can take a while on a slow phone, and the agenda's first refresh may have
     * gone without it.
     */
    val timetableFound: Flow<Unit> = preferences.timetableFeedChanges.drop(1).filterNotNull().map { }

    /**
     * Refreshes both sources. They don't depend on each other: lessons still update when the
     * Toledo session has expired (the refresh then fails afterwards), and without the timetable
     * this time the lessons we had stay.
     */
    suspend fun refresh(from: Long, until: Long): Result<Unit> = refreshCall {
        coroutineScope {
            // The user's own lessons, and those of other reeksen they also show (see TimeEditRepository).
            val lessons = async {
                timetable.lessons()?.let { own ->
                    val extra = preferences.preferences.first().extraSeries
                    if (extra.isEmpty()) own else own + timeEdit.lessonsOf(extra, own)
                }
            }
            val calendar = try {
                Result.success(toledoCalendar(from, until))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val courseList = courses.observeCourses().first()
            val lessonItems = lessons.await()
                ?.filter { it.start in from until until }
                ?.map { it.toEntity(courseList) }
                ?: calendarDao.getBetween(from, until, LESSON_TYPE)
            val calendarItems = calendar.getOrNull() ?: calendarDao.getBetweenExcept(from, until, LESSON_TYPE)
            calendarDao.replaceBetween(from, until, calendarItems + lessonItems)
            calendar.getOrThrow()
        }
        syncStateDao.upsert(SyncStateEntity(SYNC_KEY, System.currentTimeMillis()))
    }

    /**
     * Toledo's calendar items. Asked for everything and per item type, merged; only when that has
     * no course events, also per calendar. Which of these lists course events differs per setup.
     */
    private suspend fun toledoCalendar(from: Long, until: Long): List<CalendarItemEntity> {
        val since = Instant.ofEpochMilli(from).toString()
        val untilText = Instant.ofEpochMilli(until).toString()
        var items = query(since, untilText, listOf<Pair<String?, String?>>(null to null) + TYPES.map { it to null })
        var calendars = 0
        if (items.none { it.type == "Course" }) {
            val calendarIds = runCatching { calendarIds(api.calendars()) }.getOrElse { error ->
                if (error.isSessionError()) throw error
                emptyList()
            }
            calendars = calendarIds.size
            items = (items + query(since, untilText, calendarIds.map { null to it }, required = false)).distinctBy { it.id }
        }
        Log.i(TAG, "Calendar: ${items.size} items, $calendars calendars asked separately; " + items.groupingBy { it.type ?: "?" }.eachCount())
        return items.mapNotNull { it.toEntity() }
    }

    /** Runs the (type, calendarId) [queries]; fails only on an expired session or, when [required], if all fail. */
    private suspend fun query(since: String, until: String, queries: List<Pair<String?, String?>>, required: Boolean = true): List<CalendarItemDto> {
        val results = queries.mapParallel(CONCURRENCY) { (type, calendarId) ->
            collectAllPages(api.calendarItems(since, until, type, calendarId)) { api.calendarItemsPage(it) }
        }
        if (required && results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        return results.flatMap { it.getOrNull().orEmpty() }.distinctBy { it.id }
    }

    private companion object {
        const val TAG = "TollyAgenda"
        const val SYNC_KEY = "calendar"
        const val CONCURRENCY = 3
        val TYPES = listOf("Course", "GradebookColumn", "Institution", "OfficeHours", "Personal")
    }
}

internal const val LESSON_TYPE = "Lesson"

/** Calendar ids from `GET /calendars`, whether it answers with a list or a page of results. */
internal fun calendarIds(json: JsonElement): List<String> {
    val list = when (json) {
        is JsonArray -> json
        is JsonObject -> json["results"] as? JsonArray ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }
    return list.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.content }.distinct()
}

private fun CalendarItemDto.toEntity(): CalendarItemEntity? {
    val startMillis = parseInstant(start) ?: return null
    return CalendarItemEntity(
        id = id,
        type = type.orEmpty(),
        calendarId = calendarId,
        calendarName = calendarName,
        title = title?.takeIf { it.isNotBlank() } ?: calendarName.orEmpty(),
        description = description?.takeIf { it.isNotBlank() },
        location = location?.takeIf { it.isNotBlank() },
        start = startMillis,
        end = parseInstant(end),
        gradable = dynamicCalendarItemProps?.gradable == true || type == "GradebookColumn",
    )
}

/**
 * A timetable lesson, linked to the course whose code or name it mentions. Toledo also lists
 * earlier years' runs of a course, with the same code and name, so this year's go first.
 */
internal fun TimetableLesson.toEntity(courses: List<Course>): CalendarItemEntity {
    val haystack = (title + " " + text).lowercase()
    val candidates = courses.sortedByDescending { it.isActive() }
    val course = candidates.firstOrNull { c -> c.code?.lowercase()?.let { it in haystack } == true }
        ?: candidates.firstOrNull { c -> c.name.lowercase().let { it.length > 3 && (it in haystack || title.lowercase() in it) } }
    return CalendarItemEntity(
        id = "lesson:" + (uid ?: (title + start).hashCode().toString()),
        type = LESSON_TYPE,
        calendarId = course?.id,
        calendarName = null,
        title = title,
        description = null,
        location = location,
        start = start,
        end = end,
        gradable = false,
        timetableDetails = details,
    )
}

private fun CalendarItemEntity.toModel(courses: Map<String, Course>): AgendaItem {
    val linked = calendarId?.let(courses::get)
    // A lesson can name a course one can't open: an earlier year's run, or the separate Toledo
    // course of its exercise sessions ("Wiskunde I: oefeningen"), which stays unavailable while
    // everything happens in "Wiskunde I". Such lessons go to the course one does work in.
    val course = if (type == LESSON_TYPE && linked != null && !linked.isActive()) {
        val active = courses.values.filter { it.isActive() }
        active.firstOrNull { it.code != null && it.code == linked.code || it.name == linked.name }
            ?: active.namedAtStartOf(listOf(linked.name, title))
            ?: linked
    } else {
        linked
    }
    val extra = id.startsWith("lesson:" + TimeEditRepository.EXTRA_UID)
    return AgendaItem(
        id = id,
        kind = when {
            type == LESSON_TYPE -> AgendaItemKind.LESSON
            gradable -> AgendaItemKind.DEADLINE
            type == "Institution" -> AgendaItemKind.INSTITUTION
            type == "OfficeHours" -> AgendaItemKind.OFFICE_HOURS
            type == "Personal" -> AgendaItemKind.PERSONAL
            else -> AgendaItemKind.EVENT
        },
        title = title,
        description = description,
        location = location,
        start = start,
        end = end,
        courseId = course?.id,
        calendarName = course?.displayName ?: calendarName,
        courseIcon = course?.icon ?: CourseIcon.INITIALS,
        courseColor = course?.color,
        // Another reeks's session names its groups instead: it isn't the user's own group.
        timetableGroup = timetableDetails?.takeUnless { extra }?.let(TimetableGroups::group),
        timetableActivity = timetableDetails?.let(TimetableGroups::activity),
        extraSeries = if (extra) timetableDetails?.let(TimetableGroups::groups) else null,
    )
}
