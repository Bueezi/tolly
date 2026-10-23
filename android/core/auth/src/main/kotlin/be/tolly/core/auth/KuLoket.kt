package be.tolly.core.auth

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** The user as KU Loket knows them: their timetable feed and student number (`r0123456`). */
data class KuLoketStudent(val feedUrl: String?, val studentNumber: String?)

/**
 * KU Loket (KU Leuven's SAP student portal), which has the lesson timetable: its "Agenda Sync
 * iCal" page gives every student a personal iCalendar link (a TimeEdit feed) that needs no
 * sign-in. KU Loket is a separate SAML service, but the KU Leuven SSO cookie in the WebView store
 * signs it in without any UI, so an invisible WebView opens the service behind that page and
 * reads the link from its answer. Without an SSO session (the IdP shows its form) there is no
 * link this time.
 */
@Singleton
class KuLoket @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    /** What KU Loket's "Agenda Sync iCal" service says about the user, or null when it can't be read. */
    suspend fun student(): KuLoketStudent? = mutex.withLock {
        val student = runHiddenWebView<KuLoketStudent>(context, FEED_SERVICE_URL, TIMEOUT) { view, url, finish ->
            val uri = url?.let { runCatching { URI(it) }.getOrNull() }
            val isService = uri?.host == HOST && uri.path.orEmpty().startsWith(SERVICE_PATH)
            if (isService) {
                // Before signing in this is a SAML form that submits itself; afterwards, the JSON.
                view.evaluateJavascript("document.body ? document.body.innerText : ''") { text ->
                    studentInfo(decodeJsString(text))?.let(finish)
                }
            }
            isService
        }
        if (student == null) Log.w(TAG, "No timetable link from KU Loket (no KU Leuven SSO session?)")
        student
    }

    companion object {
        private const val HOST = "webwsp.aps.kuleuven.be"
        private const val SERVICE_PATH = "/sap/opu/odata/sap/ZC_EP_ICAL_SRV/"

        /** The OData service behind KU Loket's "Agenda Sync iCal" page (read-only). */
        const val FEED_SERVICE_URL = "https://$HOST${SERVICE_PATH}StudentSet?sap-client=200&\$format=json"
        private const val TAG = "TollyKuLoket"
        private val TIMEOUT = 30.seconds

        /** `evaluateJavascript` hands back a JSON literal; this is the string inside it. */
        private fun decodeJsString(value: String?): String =
            runCatching { JSONArray("[$value]").optString(0) }.getOrNull().orEmpty()

        /**
         * The feed URL and student number in the service's answer
         * (`{"d":{"results":[{"Schedulelink":"https://…","Studentid":"r…"}]}}`); null if it isn't that answer.
         */
        internal fun studentInfo(json: String): KuLoketStudent? = runCatching {
            val rows = Json.parseToJsonElement(json).jsonObject["d"]?.jsonObject?.get("results")?.jsonArray.orEmpty()
            fun field(name: String) = rows.mapNotNull { (it.jsonObject[name] as? JsonPrimitive)?.contentOrNull }
            KuLoketStudent(
                feedUrl = field("Schedulelink").firstOrNull { it.startsWith("https://") },
                studentNumber = field("Studentid").firstNotNullOfOrNull(::studentNumber),
            ).takeIf { it.feedUrl != null || it.studentNumber != null }
        }.getOrNull()

        /**
         * A student number as students know it, like `r0123456`: SAP may leave off the `r` (or
         * pad the digits with a zero), which is put back.
         */
        fun studentNumber(raw: String?): String? {
            val value = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return when {
                Regex("""\d{7}""").matches(value) -> "r$value"
                Regex("""0\d{7}""").matches(value) -> "r" + value.drop(1)
                else -> value
            }
        }
    }
}
