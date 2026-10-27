package be.tolly.core.data

import be.tolly.core.auth.GalleryEntry
import be.tolly.core.auth.KalturaGallery
import be.tolly.core.model.ContentNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A KU Leuven livestream a course links to (`livestream.kuleuven.be/?pin=123456`). */
data class LiveLink(val pin: String, val title: String)

/**
 * The video a course's content holds: the livestreams it links to, and the recordings embedded in
 * it as Kaltura media (in Wiskunde I each week's recording is such an item, "Wis I week 4").
 */
data class ContentVideos(val live: List<LiveLink>, val recordings: List<GalleryEntry>)

/** Where a livestream plays now: an HLS stream, and the room it comes from. */
data class LiveStream(val streamUrl: String, val roomName: String?)

/**
 * A course's video: the KU Leuven livestreams its content links to, and the lesson recordings in
 * its Kaltura gallery or embedded in its content. Both play as HLS without a sign-in of their own:
 * a livestream's PIN is looked up in the livestream site's public API, and recordings are Kaltura
 * streams.
 */
@Singleton
class VideoRepository @Inject constructor(
    private val content: ContentRepository,
    private val gallery: KalturaGallery,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * The livestream links and embedded recordings in the course's content, found by going through
     * its folders (as the offline download does). Both sit in items of their own (a link, a Kaltura
     * tool) or in the text of pages and documents. Each recording is named after its item (or the
     * page it is on) and grouped by the module it is in, the most recently changed module first, so
     * this week's lesson leads.
     */
    suspend fun contentVideos(courseId: String): Result<ContentVideos> = dataCall(inDemo = { ContentVideos(emptyList(), emptyList()) }) {
        findContentVideos(courseId).also { contentCache[courseId] = it }
    }

    /** What [contentVideos] found for the course last, while the app has been running. */
    fun cachedContentVideos(courseId: String): ContentVideos? = contentCache[courseId]

    /** What [galleryRecordings] found for the course last, while the app has been running. */
    fun cachedGalleryRecordings(courseId: String): List<GalleryEntry>? = galleryCache[courseId]

    private val contentCache = ConcurrentHashMap<String, ContentVideos>()
    private val galleryCache = ConcurrentHashMap<String, List<GalleryEntry>>()

    private suspend fun findContentVideos(courseId: String): ContentVideos {
        val live = LinkedHashMap<String, LiveLink>()
        val found = LinkedHashMap<String, Found>()
        val nodes = walk(courseId, parentId = null, path = emptyList(), Semaphore(WALK_CONCURRENCY))
        for ((node, path) in nodes) {
            val text = listOfNotNull(node.url, node.body, node.description).joinToString(" ")
            // A page's text sits in a child named "ultraDocumentBody"; the page's own title is the name.
            val title = if (node.isPageBody) path.lastOrNull() ?: node.title else node.title
            pins(text).forEach { pin -> live.getOrPut(pin) { LiveLink(pin, title) } }
            kalturaEntries(text).forEach { id -> found.getOrPut(id) { Found(id, title, path.firstOrNull(), node.modified ?: 0L) } }
        }
        return ContentVideos(live.values.toList(), arrange(found.values.toList()))
    }

    /** A recording as the walk found it, before [arrange] names and orders it. */
    internal data class Found(val entryId: String, val title: String, val section: String?, val modified: Long)

    /**
     * Every item under [parentId] with the titles of the folders it is in, in course order. Sibling
     * folders load at the same time, at most [permits] requests at once, so a deep course takes
     * about as long as its deepest path rather than the sum of its folders.
     */
    private suspend fun walk(courseId: String, parentId: String?, path: List<String>, permits: Semaphore): List<Pair<ContentNode, List<String>>> {
        if (path.size > MAX_DEPTH) return emptyList()
        permits.withPermit { content.refreshChildren(courseId, parentId) }.onFailure { error ->
            if (error.toDataError() == DataError.SESSION_EXPIRED || error.toDataError() == DataError.OFFLINE) throw error
            return emptyList() // a folder that can't be opened (hidden, a tool) doesn't stop the rest
        }
        val children = content.observeChildren(courseId, parentId).first()
        return coroutineScope {
            children.map { node ->
                async {
                    val below = if (node.type.isContainer || node.isPage) walk(courseId, node.id, path + node.title, permits) else emptyList()
                    listOf(node to path) + below
                }
            }.awaitAll().flatten()
        }
    }

    /** What the livestream with [pin] streams now; fails with NOT_AVAILABLE when it isn't live. */
    suspend fun resolveLive(pin: String): Result<LiveStream> = dataCall {
        val body = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(LIVE_API + pin).build()).execute().use { response ->
                if (response.code == 404) throw DataException(DataError.NOT_AVAILABLE)
                if (!response.isSuccessful) throw IOException("Livestream answered ${response.code}")
                response.body.string()
            }
        }
        parseLive(body) ?: throw DataException(DataError.NOT_AVAILABLE)
    }

    /** The lesson recordings in the course's Kaltura gallery, newest first. */
    suspend fun galleryRecordings(courseId: String): Result<List<GalleryEntry>> = dataCall(inDemo = { emptyList() }) {
        (gallery.entries(courseId) ?: throw DataException(DataError.NOT_AVAILABLE)).also { galleryCache[courseId] = it }
    }

    internal companion object {
        const val MAX_DEPTH = 6
        const val WALK_CONCURRENCY = 8
        const val LIVE_API = "https://icts-p-toledo-streaming-video-live-backend.cloud.icts.kuleuven.be/api/viewers/"

        private val pinLink = Regex("""livestream\.kuleuven\.be/?[^\s"'<>]*?[?&](?:amp;)?pin=(\d{4,10})""", RegexOption.IGNORE_CASE)

        private val kalturaEntry = Regex("""kaltura[^\s"'<>]*?/entryid/([0-9]_[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)

        /** The Kaltura media (entry ids, `1_rd9aw2hw`) that [text] embeds or links to. */
        fun kalturaEntries(text: String): List<String> = kalturaEntry.findAll(text).map { it.groupValues[1] }.distinct().toList()

        /**
         * The recordings found in the content, by module: the module changed most recently first,
         * and in course order within it. Several on one page are numbered ("Oefenzitting 2 · 1").
         */
        internal fun arrange(found: List<Found>): List<GalleryEntry> {
            val sections = found.groupBy { it.section }.values.sortedByDescending { group -> group.maxOf { it.modified } }
            return sections.flatMap { group ->
                val counts = group.groupingBy { it.title }.eachCount()
                val seen = HashMap<String, Int>()
                group.map { item ->
                    val title = if ((counts[item.title] ?: 0) > 1) {
                        "${item.title} · ${seen.merge(item.title, 1, Int::plus)}"
                    } else {
                        item.title
                    }
                    GalleryEntry(
                        entryId = item.entryId,
                        title = title,
                        duration = null,
                        thumbnailUrl = "https://cdnapisec.kaltura.com/p/$PARTNER/thumbnail/entry_id/${item.entryId}/width/320",
                        partnerId = PARTNER,
                        section = item.section,
                    )
                }
            }
        }

        /** KU Leuven's Kaltura account, which the gallery's thumbnails name too. */
        private const val PARTNER = "2375821"

        /** The livestream PINs that [text] links to. */
        fun pins(text: String): List<String> = pinLink.findAll(text).map { it.groupValues[1] }.distinct().toList()

        /** `{"streamUrl": "https://…m3u8", "roomName": "…"}`; null without a stream. */
        fun parseLive(json: String): LiveStream? = runCatching {
            val obj: JsonObject = Json.parseToJsonElement(json).jsonObject
            fun field(name: String) = (obj[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            field("streamUrl")?.let { LiveStream(it, field("roomName")) }
        }.getOrNull()
    }
}
