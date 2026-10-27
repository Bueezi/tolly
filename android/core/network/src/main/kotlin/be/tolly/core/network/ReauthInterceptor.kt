package be.tolly.core.network

import be.tolly.core.auth.DemoMode
import be.tolly.core.auth.SessionCookieJar
import be.tolly.core.auth.SessionExpiredException
import be.tolly.core.auth.SessionManager
import be.tolly.core.auth.UltraEndpoints
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider

/**
 * Renews an expired Ultra session transparently: on a 401 from Ultra it runs the silent SSO login
 * once and retries the request. When that fails the request ends with [SessionExpiredException].
 */
class ReauthInterceptor @Inject constructor(
    private val cookieJar: SessionCookieJar,
    private val sessionManager: Provider<SessionManager>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != UltraEndpoints.HOST) return chain.proceed(request)
        if (!cookieJar.hasCookies) {
            // The demo has no session to lose (a note's images ask Toledo all the same).
            if (!DemoMode.active) sessionManager.get().markExpired()
            throw SessionExpiredException()
        }

        val version = cookieJar.version
        val response = chain.proceed(request)
        if (response.code != 401) return response
        response.close()

        val renewed = runBlocking { sessionManager.get().refreshSessionSilently(version) }
        if (!renewed) throw SessionExpiredException()

        val retry = chain.proceed(request)
        if (retry.code == 401) {
            retry.close()
            sessionManager.get().markExpired()
            throw SessionExpiredException()
        }
        return retry
    }
}
