package be.tolly.core.data

import android.util.Log
import be.tolly.core.model.AgendaItem
import be.tolly.core.model.GroupSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a lesson's TimeEdit description: the activity on its first line (`15 Oefenzitting`), the
 * group on its second (`G0T43a_InfR4`, the course code and the group within it).
 */
object TimetableGroups {
    private val groupLine = Regex("""^([A-Z][0-9A-Z]{4}[a-z]?)_\S+$""")

    fun activity(details: String): String? = details.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    fun group(details: String): String? = groups(details).firstOrNull()

    /** Every group a session is for: one, or several when they share it. */
    fun groups(details: String): List<String> = details.lineSequence().map { it.trim() }.filter { groupLine.matches(it) }.toList()

    /**
     * The study programme a group belongs to: `Inf` for `G0N78a_InfR1`. A course shared by
     * programmes numbers each one's reeksen from 1 (`InfR1`, `WisR1`, `FysR1`).
     */
    fun programme(group: String): String? = Regex("""_(.*?)R\d+$""").find(group)?.groupValues?.get(1)

    /** Whether [group] is of the same programme as [own] (any group, when [own] names none). */
    fun sameProgramme(group: String, own: String): Boolean = programme(own).let { it == null || programme(group) == it }

    /** Sorts groups by reeks number, then by name. */
    val order: Comparator<String> = compareBy<String>({ seriesNumber(it) ?: Int.MAX_VALUE }, { it })

    /** The course code a group belongs to: `G0T43a` for `G0T43a_InfR4`. */
    fun courseCode(group: String): String? = groupLine.matchEntire(group)?.groupValues?.get(1)

    /** The reeks number of a group like `G0T43a_InfR4`, or null when it isn't named that way. */
    fun seriesNumber(group: String): Int? = Regex("""R(\d+)$""").find(group)?.groupValues?.get(1)?.toIntOrNull()
}

/**
 * The other groups (reeksen) of a course in KU Leuven's public TimeEdit timetable, which needs no
 * sign-in: a search finds the course (OLA) by its code, and its calendar has the sessions of every
 * group, each naming its group in the description.
 */
@Singleton
class TimeEditRepository @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val mutex = Mutex()
    private val cache = HashMap<String, Pair<Long, List<TimetableLesson>>>()

    /**
     * The sessions of the other groups of [lesson]'s activity in the same week, by time; empty when
     * the lesson has no group, or the course has no other groups.
     */
    suspend fun otherGroups(lesson: AgendaItem, zone: ZoneId = ZoneId.systemDefault()): Result<List<GroupSession>> = runCatching {
        val group = lesson.timetableGroup ?: return@runCatching emptyList()
        val code = TimetableGroups.courseCode(group) ?: return@runCatching emptyList()
        val weekStart = Instant.ofEpochMilli(lesson.start).atZone(zone).toLocalDate()
            .let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        val from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        sessions(code)
            .filter { it.start in from until until }
            .mapNotNull { session ->
                val details = session.details ?: return@mapNotNull null
                val other = TimetableGroups.group(details)?.takeIf { it != group && TimetableGroups.sameProgramme(it, group) }
                    ?: return@mapNotNull null
                if (lesson.timetableActivity != null && TimetableGroups.activity(details) != lesson.timetableActivity) return@mapNotNull null
                GroupSession(other, session.start, session.end, session.location)
            }
            .distinct()
            .sortedWith(compareBy({ it.start }, { it.group }))
    }.onFailure { Log.w(TAG, "No other groups for ${lesson.timetableGroup}", it) }

    /** The groups (reeksen) of [own]'s course in [own]'s programme, [own] among them, in order. */
    suspend fun groupsLike(own: String): Result<List<String>> = runCatching {
        val code = TimetableGroups.courseCode(own) ?: return@runCatching listOf(own)
        sessions(code).flatMap { it.details?.let(TimetableGroups::groups).orEmpty() }
            .filter { TimetableGroups.courseCode(it) == code && TimetableGroups.sameProgramme(it, own) }
            .distinct()
            .sortedWith(TimetableGroups.order)
    }

    /**
     * The sessions of [groups] (of any courses) as lessons for the agenda, without those the user
     * has anyway ([own], same time and room). Groups sharing a session become one lesson that
     * names them all.
     */
    suspend fun lessonsOf(groups: Set<String>, own: List<TimetableLesson>): List<TimetableLesson> {
        val ownSlots = own.map { Triple(it.start, it.end, it.location) }.toSet()
        return groups.mapNotNull(TimetableGroups::courseCode).distinct().flatMap { code ->
            runCatching { sessions(code) }.onFailure { Log.w(TAG, "No sessions for $code", it) }.getOrDefault(emptyList())
        }
            .filter { lesson -> lesson.details?.let(TimetableGroups::groups).orEmpty().any { it in groups } }
            .filter { Triple(it.start, it.end, it.location) !in ownSlots }
            .groupBy { Triple(it.start, it.end, it.location) }
            .map { (_, shared) ->
                val first = shared.first()
                val sharedGroups = shared.flatMap { it.details?.let(TimetableGroups::groups).orEmpty() }
                    .filter { it in groups }.distinct().sortedWith(TimetableGroups.order)
                first.copy(
                    uid = EXTRA_UID + (first.uid ?: "${first.start}:${first.location}"),
                    details = listOfNotNull(first.details?.let(TimetableGroups::activity)).plus(sharedGroups).joinToString("\n"),
                )
            }
    }

    /** Every session of the course [code] this year, kept for a while: the timetable rarely changes. */
    private suspend fun sessions(code: String): List<TimetableLesson> = mutex.withLock {
        cache[code]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second }
        val id = findCourse(code) ?: return emptyList()
        val body = get(BASE.toHttpUrl().newBuilder().addPathSegment("ri.ics").addQueryParameter("sid", CALENDAR_VIEW).addQueryParameter("objects", "$id.$OLA_TYPE").build().toString())
        if (!TimetableFeedParser.isCalendar(body)) throw IOException("Not a calendar")
        TimetableFeedParser.parse(body).also { cache[code] = System.currentTimeMillis() to it }
    }

    /** The TimeEdit id of this year's run of the course [code]: the search lists one per year. */
    private suspend fun findCourse(code: String): String? {
        val url = BASE.toHttpUrl().newBuilder().addPathSegment("objects.html")
            .addQueryParameter("max", "20").addQueryParameter("fr", "t").addQueryParameter("partajax", "t")
            .addQueryParameter("im", "f").addQueryParameter("sid", SEARCH_VIEW).addQueryParameter("l", "nl_NL")
            .addQueryParameter("types", OLA_TYPE).addQueryParameter("search_text", code)
            .build().toString()
        return newestRun(code, get(url))
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("TimeEdit answered ${response.code}")
            response.body.string()
        }
    }

    internal companion object {
        const val TAG = "TollyTimeEdit"

        /** Marks the agenda's lessons of other reeksen (their uid, and so their id). */
        const val EXTRA_UID = "extra:"
        const val BASE = "https://cloud.timeedit.net/be_kuleuven/web/public/"

        /** TimeEdit's object type for an OLA (a course's teaching activity). */
        const val OLA_TYPE = "13"

        /** The public view whose search finds OLAs, and the one whose calendars list their sessions. */
        const val SEARCH_VIEW = "3"
        const val CALENDAR_VIEW = "7"
        val CACHE_MS = TimeUnit.MINUTES.toMillis(30)

        private val result = Regex("""data-idonly="(\d+)"[^>]*?data-name="([^"]*)"""")

        /**
         * From a search's answer, the id of the newest run of [code]: results are named
         * `G0T42a_2026, Logica voor informatici, 2026` (the oldest just `G0T42a, …`).
         */
        fun newestRun(code: String, html: String): String? =
            result.findAll(html)
                .map { it.groupValues[1] to it.groupValues[2] }
                .filter { (_, name) -> name.substringBefore(',').substringBefore('_').equals(code, ignoreCase = true) }
                .maxByOrNull { (_, name) -> name.substringAfterLast(',').trim().toIntOrNull() ?: 0 }
                ?.first
    }
}
