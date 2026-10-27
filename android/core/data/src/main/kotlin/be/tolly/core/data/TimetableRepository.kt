package be.tolly.core.data

import android.util.Log
import be.tolly.core.auth.KuLoket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's lessons from the KU Leuven timetable. KU Loket gives each student a personal
 * iCalendar feed; its URL is looked up once (through a silent KU Loket sign-in, see [KuLoket])
 * and kept, after which the feed itself needs no session.
 */
@Singleton
class TimetableRepository @Inject constructor(
    private val kuLoket: KuLoket,
    private val preferences: PreferencesRepository,
) {
    // A plain client: the feed is public and must not go through the Ultra cookie jar or re-login.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** When looking the feed up last failed; looking again right away would fail the same way. */
    @Volatile private var lookupFailedAt = 0L

    /**
     * One lookup at a time: right after signing in the prefetch and the agenda's first refresh both
     * want the feed, and the agenda should wait for the prefetch's answer rather than look again.
     */
    private val lookup = Mutex()

    /** The student number is looked up (with the feed) at most once per run when it's missing. */
    @Volatile private var askedStudentNumber = false

    /** All lessons in the feed, or null when it can't be read now (callers keep what they had). */
    suspend fun lessons(): List<TimetableLesson>? {
        // Installs that found the feed before the student number was kept get it once now.
        if (!askedStudentNumber && preferences.studentNumber() == null && preferences.timetableFeed() != null) {
            askedStudentNumber = true
            kuLoket.student()?.studentNumber?.let { preferences.setStudentNumber(it) }
        }
        val saved = preferences.timetableFeed()
        val url = saved ?: findFeed() ?: return null
        return when (val feed = fetch(url)) {
            is Feed.Read -> TimetableFeedParser.parse(feed.body).also { Log.i(TAG, "Timetable: ${it.size} lessons") }
            Feed.Gone -> {
                // The subscription was removed or renewed in KU Loket: look the new link up once.
                preferences.setTimetableFeed(null)
                if (saved == null) return null
                val fresh = findFeed() ?: return null
                (fetch(fresh) as? Feed.Read)?.body?.let(TimetableFeedParser::parse)
            }
            Feed.Failed -> null
        }
    }

    /**
     * Looks the feed and student number up while the KU Leuven sign-in is fresh (right after
     * signing in), so the agenda and Settings have them later: KU Loket signs in silently only
     * while that sign-in lasts.
     */
    suspend fun prefetchFeed() {
        if (preferences.timetableFeed() == null || preferences.studentNumber() == null) {
            lookupFailedAt = 0
            findFeed(reuse = false)
        }
    }

    /**
     * Asks KU Loket for the feed (and the student number). With [reuse], a feed found while this
     * waited for another lookup is taken instead. A failed lookup isn't repeated for a while.
     */
    private suspend fun findFeed(reuse: Boolean = true): String? = lookup.withLock {
        if (reuse) preferences.timetableFeed()?.let { return@withLock it }
        if (System.currentTimeMillis() - lookupFailedAt < RETRY_LOOKUP_MS) return@withLock null
        val student = kuLoket.student()
        student?.studentNumber?.let { preferences.setStudentNumber(it) }
        val url = student?.feedUrl
        if (url == null) {
            lookupFailedAt = System.currentTimeMillis()
            return@withLock null
        }
        lookupFailedAt = 0
        preferences.setTimetableFeed(url)
        url
    }

    private sealed interface Feed {
        class Read(val body: String) : Feed

        /** The server no longer knows this feed. */
        data object Gone : Feed

        /** No answer this time (offline, a server error). */
        data object Failed : Feed
    }

    private suspend fun fetch(url: String): Feed = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "text/calendar").build()
        try {
            runInterruptible { client.newCall(request).execute() }.use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful && TimetableFeedParser.isCalendar(body) -> Feed.Read(body)
                    response.code in 400..499 || response.isSuccessful -> {
                        Log.w(TAG, "Timetable feed: HTTP ${response.code}, ${if (response.isSuccessful) "not a calendar" else "gone"}")
                        Feed.Gone
                    }
                    else -> {
                        Log.w(TAG, "Timetable feed: HTTP ${response.code}")
                        Feed.Failed
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Timetable feed request failed", e)
            Feed.Failed
        }
    }

    private companion object {
        const val TAG = "TollyTimetable"
        const val RETRY_LOOKUP_MS = 10 * 60 * 1000L
    }
}
