package be.tolly.core.auth

import be.tolly.core.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState

    /** [sessionValid] is false when the Ultra session expired and silent re-login failed. */
    data class SignedIn(val user: User, val sessionValid: Boolean = true, val demo: Boolean = false) : AuthState
}

/** Thrown by the network layer when the session is gone and the user has to sign in again. */
class SessionExpiredException : IOException("Toledo session expired")

@Singleton
class SessionManager @Inject constructor(
    private val store: SessionStore,
    private val cookieJar: SessionCookieJar,
    private val webCookies: WebSessionCookies,
    private val reauthenticator: SilentReauthenticator,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val reauthMutex = Mutex()

    /** Cookie version and time of the last failed silent login, so requests queued behind it fail with it. */
    private var failedReauth: Pair<Long, Long>? = null

    init {
        scope.launch {
            val session = store.read()
            val user = session.user
            _state.value = if (user != null && session.cookies.isNotEmpty()) {
                AuthState.SignedIn(user.toModel())
            } else {
                AuthState.SignedOut
            }
        }
    }

    /**
     * Moves the cookies of a finished WebView login into the encrypted jar.
     * Returns false if the WebView didn't receive any Ultra cookies. Main thread only.
     */
    fun captureWebLogin(): Boolean {
        val cookies = webCookies.captureUltraCookies()
        if (cookies.isEmpty()) return false
        cookieJar.replaceAll(cookies)
        webCookies.removeUltraCookies(cookies.map { it.name })
        return true
    }

    /** Lends the session to the in-app browser's WebView. Main thread only. */
    fun shareSessionWithWebView() {
        webCookies.setUltraCookies(cookieJar.loadForRequest(UltraEndpoints.BASE_URL.toHttpUrl()))
    }

    /**
     * Takes the session back from the WebView store once the in-app browser closes. Ultra may have
     * renewed the cookies meanwhile (or the browser signed in again through the IdP), so the
     * WebView's copy wins. Main thread only.
     */
    fun reclaimSessionFromWebView() {
        val cookies = webCookies.captureUltraCookies()
        if (cookies.isEmpty()) return
        cookieJar.saveFromResponse(UltraEndpoints.BASE_URL.toHttpUrl(), cookies)
        webCookies.removeUltraCookies(cookies.map { it.name })
    }

    /** Called once the captured session has been verified against the API. */
    suspend fun completeSignIn(user: User) {
        store.update {
            it.copy(user = StoredUser(user.id, user.userName, user.displayName, user.email, user.studentId))
        }
        _state.value = AuthState.SignedIn(user)
    }

    /**
     * Shows [user] as signed in without any session: nothing is stored, so the next start is signed
     * out again, and [DemoMode] keeps refreshes off the network.
     */
    fun enterDemo(user: User) {
        DemoMode.active = true
        _state.value = AuthState.SignedIn(user, demo = true)
    }

    /**
     * Tries to renew the session without UI. [observedVersion] is the cookie version the failing
     * request was sent with; if another caller already renewed the session we just report success.
     */
    suspend fun refreshSessionSilently(observedVersion: Long): Boolean = reauthMutex.withLock {
        if (cookieJar.version != observedVersion && cookieJar.hasCookies) return@withLock true
        // A refresh sends many requests at once; after one failed attempt the others shouldn't each
        // spend up to half a minute in the hidden WebView. Later on (a flaky network) we try again.
        failedReauth?.let { (version, at) ->
            if (version == cookieJar.version && System.currentTimeMillis() - at < REAUTH_RETRY_AFTER) return@withLock false
        }
        val cookies = reauthenticator.reauthenticate()
        if (cookies.isNullOrEmpty()) {
            failedReauth = cookieJar.version to System.currentTimeMillis()
            markExpired()
            false
        } else {
            failedReauth = null
            cookieJar.replaceAll(cookies)
            _state.update { if (it is AuthState.SignedIn) it.copy(sessionValid = true) else it }
            true
        }
    }

    fun markExpired() {
        _state.update { if (it is AuthState.SignedIn) it.copy(sessionValid = false) else it }
    }

    /** Forgets the session everywhere: encrypted store, cookie jar and the WebView (incl. SSO). */
    suspend fun signOut() {
        DemoMode.active = false
        cookieJar.clear()
        store.clear()
        webCookies.clearAll()
        _state.value = AuthState.SignedOut
    }

    /** Drops a half-finished login (cookies captured but the API rejected them). */
    fun abandonLogin() {
        cookieJar.clear()
    }

    private fun StoredUser.toModel() = User(id = id, userName = userName, displayName = displayName, email = email, studentId = studentId)

    private companion object {
        const val REAUTH_RETRY_AFTER = 60_000L
    }
}
