package be.tolly.core.auth

import java.net.URI
import java.net.URLEncoder

/** Hosts and URLs involved in signing in to KU Leuven's Blackboard Ultra. */
object UltraEndpoints {
    const val HOST = "ultra.edu.kuleuven.cloud"
    const val BASE_URL = "https://$HOST/"
    const val IDP_HOST = "idp.kuleuven.be"

    /** Package of the KU Leuven Authenticator app (MFA). */
    const val AUTHENTICATOR_PACKAGE = "be.kuleuven.icts.authenticator"

    private const val LANDING_URL = "https://$HOST/ultra/course"

    /** Starts the SAML flow; Ultra redirects back to [LANDING_URL] once the IdP has authenticated the user. */
    val LOGIN_URL: String =
        "https://$HOST/auth-saml/saml2/authenticate/_16_1?redirectUrl=" + URLEncoder.encode(LANDING_URL, "UTF-8")

    /** True once the WebView is back on an Ultra UI page, i.e. the session cookies have been set. */
    fun isLoginComplete(url: String?): Boolean {
        val uri = parse(url) ?: return false
        return uri.host == HOST && (uri.path ?: "").startsWith("/ultra")
    }

    /**
     * The IdP's "Open KU Leuven Authenticator app" button navigates to this App Link. Browsers hand
     * it to the app; a WebView would load the web fallback ("Authenticator cannot be opened").
     */
    fun isAuthenticatorAppLink(url: String?): Boolean {
        val uri = parse(url) ?: return false
        return uri.scheme == "https" && uri.host == "icts.kuleuven.be" && (uri.path ?: "").startsWith("/apps/authenticator")
    }

    fun isIdentityProvider(url: String?): Boolean = parse(url)?.host == IDP_HOST

    fun isUltra(url: String?): Boolean = parse(url)?.host == HOST

    /** Toledo's homepage, the KU Leuven portal around Ultra. A site of its own, signed in through the IdP. */
    const val TOLEDO_HOME_URL = "https://toledo.kuleuven.be/portal/#/home"

    /**
     * Whether [url] is a Toledo page the in-app browser opens signed in: Ultra with the app's
     * session, the homepage through the IdP sign-in the WebView kept. A Custom Tab has neither.
     */
    fun opensSignedIn(url: String?): Boolean = isUltra(url) || parse(url)?.host == TOLEDO_HOST

    private const val TOLEDO_HOST = "toledo.kuleuven.be"

    /** Toledo's calendar page. */
    const val CALENDAR_URL = "${BASE_URL}ultra/calendar"

    /** A course's grades page in Toledo. */
    fun courseGradesUrl(courseId: String): String = "${BASE_URL}ultra/courses/$courseId/grades"

    fun absolute(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http")) pathOrUrl else BASE_URL + pathOrUrl.removePrefix("/")

    private fun parse(url: String?): URI? = url?.let { runCatching { URI(it) }.getOrNull() }
}
