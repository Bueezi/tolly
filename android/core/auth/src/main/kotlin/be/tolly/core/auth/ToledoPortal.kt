package be.tolly.core.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * The Toledo portal (toledo.kuleuven.be/portal), which counts the user's unread KU Leuven mail
 * (Outlook) for its mail icon. The portal is a SAML service of its own: its session lives in the
 * WebView cookie store. While it holds, the count is a plain request with those cookies; when it
 * has run out, an invisible WebView opens the portal, which signs in again through the KU Leuven
 * SSO session without any UI, and asks there.
 */
@Singleton
class ToledoPortal @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    /** The number of unread mails, or null when the portal can't be asked now. */
    suspend fun unreadMail(): Int? = mutex.withLock {
        withPortalCookies() ?: signedInInWebView().also { if (it == null) Log.w(TAG, "No unread mail count from the portal") }
    }

    /** Asks with the portal session the WebView store holds; null when there is none (any longer). */
    private suspend fun withPortalCookies(): Int? {
        val cookies = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(PORTAL_URL) } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(UNREAD_URL).header("Cookie", cookies).header("Accept", "application/json").build())
                    .execute().use { response -> if (response.isSuccessful) parseCount(response.body.string()) else null }
            }.getOrNull()
        }
    }

    /**
     * Opens the portal in an invisible WebView and asks from within the page, until the portal has
     * signed in (it first passes through the IdP) and answers.
     */
    private suspend fun signedInInWebView(): Int? {
        val handler = Handler(Looper.getMainLooper())
        return try {
            runHiddenWebView<Int>(context, PORTAL_HOME, TIMEOUT) { view, url, finish ->
                val uri = url?.let { runCatching { URI(it) }.getOrNull() }
                val isPortal = uri?.host == HOST && uri.path.orEmpty().startsWith("/portal")
                if (isPortal) {
                    fun ask(attempt: Int) {
                        view.evaluateJavascript(ASK_UNREAD) { value ->
                            val text = runCatching { JSONArray("[$value]").optString(0) }.getOrNull().orEmpty()
                            when {
                                text.toIntOrNull() != null -> finish(text.toInt())
                                attempt < POLLS -> handler.postDelayed({ ask(attempt + 1) }, POLL_MS)
                            }
                        }
                    }
                    ask(0)
                }
                isPortal
            }
        } finally {
            handler.removeCallbacksAndMessages(null)
        }
    }

    companion object {
        private const val TAG = "TollyPortal"
        private const val HOST = "toledo.kuleuven.be"
        private const val PORTAL_URL = "https://$HOST/portal/"
        private const val PORTAL_HOME = "https://$HOST/portal/#/home"
        private const val UNREAD_URL = "https://$HOST/portal/api/emails/unread"

        /** Outlook on the web, where the portal's mail icon leads. */
        const val OUTLOOK_URL = "https://outlook.office.com/mail/"

        private val TIMEOUT = 30.seconds
        private const val POLLS = 40
        private const val POLL_MS = 500L

        /**
         * Asks the portal's API from within the page, once per page (the answer is kept on the
         * page); while the portal is still signing in it answers 401, and the page is asked again.
         */
        private val ASK_UNREAD = """
            (function () {
              if (window.__tollyUnread === undefined || window.__tollyUnread === 'none') {
                window.__tollyUnread = 'asking';
                fetch('/portal/api/emails/unread', { credentials: 'include', headers: { Accept: 'application/json' } })
                  .then(function (r) { return r.ok ? r.json() : null; })
                  .then(function (j) { window.__tollyUnread = j && typeof j.value === 'number' ? String(j.value) : 'none'; })
                  .catch(function () { window.__tollyUnread = 'none'; });
              }
              return window.__tollyUnread;
            })()
        """.trimIndent()

        /** `{"value": 3, "origin": "MSCLOUD"}`. */
        internal fun parseCount(json: String): Int? =
            runCatching { Json.parseToJsonElement(json).jsonObject["value"]?.jsonPrimitive?.int }.getOrNull()
    }
}
