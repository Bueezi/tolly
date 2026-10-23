package be.tolly.core.auth

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import org.json.JSONArray
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** A recorded lesson in a course's Kaltura media gallery. */
data class GalleryEntry(
    /** Kaltura's entry id, `1_eb824w2k`. */
    val entryId: String,
    val title: String,
    /** As the gallery shows it, `01:48:57`. */
    val duration: String?,
    val thumbnailUrl: String?,
    /** Kaltura's partner (KU Leuven's account), which the stream's address needs. */
    val partnerId: String?,
    /** For a recording found in the course's content, the module it is in ("Week 5: Hoorcollege"). */
    val section: String? = null,
)

/**
 * A course's lesson recordings ("Lesopnames"), from its Kaltura media gallery: an LTI tool in
 * Toledo with no API of its own. An invisible WebView launches the tool with the app's session
 * (the launch signs into Kaltura on its own) and reads the gallery page it lands on. The videos
 * themselves stream without a sign-in (see [playbackUrl]).
 */
@Singleton
class KalturaGallery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
) {
    private val mutex = Mutex()

    /** The recordings of the course [courseId], newest first, or null when the gallery can't be read. */
    suspend fun entries(courseId: String): List<GalleryEntry>? = mutex.withLock {
        withContext(Dispatchers.Main) { sessionManager.shareSessionWithWebView() }
        try {
            runHiddenWebView<List<GalleryEntry>>(context, launchUrl(courseId), TIMEOUT) { view, url, finish ->
                val uri = url?.let { runCatching { URI(it) }.getOrNull() }
                val isGallery = uri?.host == HOST && uri.path.orEmpty().startsWith("/channel/")
                if (isGallery) view.evaluateJavascript(READ_GALLERY) { finish(parse(decodeJsString(it))) }
                isGallery
            }.also { if (it == null) Log.w(TAG, "No recordings gallery for $courseId") }
        } finally {
            // The launch may have renewed the Ultra cookies; the app keeps them.
            withContext(Dispatchers.Main) { sessionManager.reclaimSessionFromWebView() }
        }
    }

    companion object {
        private const val TAG = "TollyKaltura"
        private const val HOST = "kaltura-kaf.edu.kuleuven.cloud"
        private val TIMEOUT = 40.seconds

        /** Toledo's placement of the Kaltura media gallery, the same for every course. */
        private const val GALLERY_PLACEMENT = "_83_1"

        /** Opens the course's media gallery; in the in-app browser too, for "open in browser". */
        fun launchUrl(courseId: String): String =
            "${UltraEndpoints.BASE_URL}webapps/blackboard/execute/blti/launchPlacement" +
                "?blti_placement_id=$GALLERY_PLACEMENT&course_id=$courseId&from_ultra=true"

        /** The HLS stream of a recording, which Kaltura serves without a session. */
        fun playbackUrl(entry: GalleryEntry): String {
            val partner = entry.partnerId ?: DEFAULT_PARTNER
            return "https://cdnapisec.kaltura.com/p/$partner/sp/${partner}00/playManifest/entryId/${entry.entryId}" +
                "/format/applehttp/protocol/https/a.m3u8"
        }

        private const val DEFAULT_PARTNER = "2375821"

        /** The gallery's items as JSON: the page lists every recording with its link, thumbnail and length. */
        private val READ_GALLERY = """
            JSON.stringify([...document.querySelectorAll('li.galleryItem')].map(function (item) {
              var link = item.querySelector('a.item_link[href*="/media/t/"]');
              var img = item.querySelector('img.thumb_img');
              var name = item.querySelector('.thumb_name_content_link a') || link;
              var time = item.querySelector('.duration');
              return {
                href: link ? link.getAttribute('href') : '',
                title: name ? (name.getAttribute('data-original-title') || name.textContent).trim() : '',
                thumb: img ? img.src : '',
                duration: time ? time.textContent.trim() : ''
              };
            }))
        """.trimIndent()

        private fun decodeJsString(value: String?): String =
            runCatching { JSONArray("[$value]").optString(0) }.getOrNull().orEmpty()

        private val entryInLink = Regex("""/media/t/([0-9]_[A-Za-z0-9]+)""")
        private val partnerInThumb = Regex("""/p/(\d+)/""")

        internal fun parse(json: String): List<GalleryEntry>? = runCatching {
            Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                fun field(name: String) = (item[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val id = field("href")?.let { entryInLink.find(it)?.groupValues?.get(1) } ?: return@mapNotNull null
                val thumb = field("thumb")
                GalleryEntry(
                    entryId = id,
                    title = field("title") ?: id,
                    duration = field("duration"),
                    thumbnailUrl = thumb,
                    partnerId = thumb?.let { partnerInThumb.find(it)?.groupValues?.get(1) },
                )
            }.distinctBy { it.entryId }
        }.getOrNull()
    }
}
