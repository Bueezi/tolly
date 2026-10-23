package be.tolly.core.auth

import android.webkit.CookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cookie
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Bridges the WebView cookie store. After a WebView login the Ultra cookies are moved into the
 * encrypted [SessionCookieJar] and removed from the WebView store, so only the KU Leuven IdP
 * SSO cookie (needed for silent re-login) stays with the WebView.
 *
 * Must be called on the main thread.
 */
@Singleton
class WebSessionCookies @Inject constructor() {
    private val cookieManager: CookieManager get() = CookieManager.getInstance()

    /** Reads the Ultra cookies (including httpOnly ones) that the WebView received during login. */
    fun captureUltraCookies(): List<Cookie> {
        val byName = LinkedHashMap<String, Cookie>()
        for (path in CAPTURE_PATHS) {
            val header = cookieManager.getCookie("https://${UltraEndpoints.HOST}$path") ?: continue
            for (cookie in parseCookieHeader(header)) byName.putIfAbsent(cookie.name, cookie)
        }
        return byName.values.toList()
    }

    /** Puts the Ultra session into the WebView store, so pages in the in-app browser are signed in. */
    fun setUltraCookies(cookies: List<Cookie>) {
        for (cookie in cookies) {
            cookieManager.setCookie(UltraEndpoints.BASE_URL, "${cookie.name}=${cookie.value}; Path=/; Secure; HttpOnly")
        }
        cookieManager.flush()
    }

    /** Expires the Ultra cookies inside the WebView store; the IdP cookies are left alone. */
    fun removeUltraCookies(names: Collection<String>) {
        for (name in names) {
            for (path in CAPTURE_PATHS) {
                cookieManager.setCookie("https://${UltraEndpoints.HOST}$path", "$name=; Max-Age=0; Path=$path")
            }
            cookieManager.setCookie("https://${UltraEndpoints.HOST}/", "$name=; Max-Age=0")
        }
        cookieManager.flush()
    }

    /** Signs the WebView out of everything, including the KU Leuven SSO session. */
    suspend fun clearAll() {
        suspendCancellableCoroutine { cont ->
            cookieManager.removeAllCookies { if (cont.isActive) cont.resume(Unit) }
        }
        cookieManager.flush()
    }

    companion object {
        private val CAPTURE_PATHS = listOf("/", "/ultra/", "/learn/", "/learn/api/", "/webapps/")

        internal fun parseCookieHeader(header: String): List<Cookie> =
            header.split(';').mapNotNull { part ->
                val name = part.substringBefore('=').trim()
                val value = part.substringAfter('=', "").trim()
                if (name.isEmpty() || !part.contains('=')) return@mapNotNull null
                runCatching {
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .hostOnlyDomain(UltraEndpoints.HOST)
                        .path("/")
                        .secure()
                        .httpOnly()
                        .build()
                }.getOrNull()
            }
    }
}
