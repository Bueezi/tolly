package be.tolly.core.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** Obtains fresh Ultra cookies without user interaction, or returns null if the user must sign in. */
fun interface SilentReauthenticator {
    suspend fun reauthenticate(): List<Cookie>?
}

/**
 * Replays the SAML login in an invisible WebView. While the KU Leuven IdP still has an SSO session
 * (kept in the WebView cookie store), the flow completes by itself within a few seconds. If the IdP
 * shows its login form instead, we give up and let the UI ask the user to sign in again.
 */
class WebViewSilentReauthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webCookies: WebSessionCookies,
) : SilentReauthenticator {

    override suspend fun reauthenticate(): List<Cookie>? {
        fun complete(url: String?, finish: (List<Cookie>?) -> Unit): Boolean {
            if (!UltraEndpoints.isLoginComplete(url)) return false
            val cookies = webCookies.captureUltraCookies()
            webCookies.removeUltraCookies(cookies.map { it.name })
            finish(cookies.takeIf { it.isNotEmpty() })
            return true
        }
        return runHiddenWebView(
            context,
            UltraEndpoints.LOGIN_URL,
            TIMEOUT,
            onPageStarted = { url, finish -> complete(url, finish) },
            onPageFinished = { _, url, finish -> complete(url, finish) },
        )
    }

    private companion object {
        val TIMEOUT = 25.seconds
    }
}
