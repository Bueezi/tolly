package be.tolly.core.auth

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Where the cookie jar keeps its cookies between process restarts. */
interface CookieStorage {
    suspend fun load(): List<Cookie>
    suspend fun save(cookies: List<Cookie>)
}

class SessionStoreCookieStorage @Inject constructor(
    private val store: SessionStore,
) : CookieStorage {
    override suspend fun load(): List<Cookie> = store.read().cookies.map(StoredCookie::toOkHttp)

    override suspend fun save(cookies: List<Cookie>) {
        store.update { it.copy(cookies = cookies.map(StoredCookie::from)) }
    }
}

/**
 * OkHttp cookie jar holding the Ultra session. Cookies are kept in memory and written through to
 * encrypted storage; [version] changes whenever a login replaces the session. Cookies that
 * responses set (load-balancer stickiness, even on a 401) don't count: a failed request would
 * otherwise look as if someone had renewed the session meanwhile, and skip the silent login.
 */
@Singleton
class SessionCookieJar @Inject constructor(
    private val storage: CookieStorage,
    @ApplicationScope private val scope: CoroutineScope,
) : CookieJar {
    private val lock = Any()
    private var cookies: List<Cookie>? = null
    private val versionCounter = AtomicLong(0)
    private val persistMutex = Mutex()

    val version: Long get() = versionCounter.get()

    val hasCookies: Boolean get() = current().isNotEmpty()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return current().filter { it.expiresAt > now && it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val merged = current().filterNot { old -> cookies.any { it.sameIdentity(old) } } + cookies
            val now = System.currentTimeMillis()
            this.cookies = merged.filter { it.expiresAt > now }
        }
        persist()
    }

    /** Replaces the whole session, e.g. after an interactive or silent login. */
    fun replaceAll(newCookies: List<Cookie>) {
        synchronized(lock) { cookies = newCookies }
        versionCounter.incrementAndGet()
        persist()
    }

    fun clear() = replaceAll(emptyList())

    private fun current(): List<Cookie> = synchronized(lock) {
        cookies ?: runBlocking { storage.load() }.also { cookies = it }
    }

    /**
     * Writes the cookies as they are by the time the write runs, one write at a time, so writes
     * that start out of order still leave the newest cookies on disk. A failed write (full disk,
     * Keystore trouble) only costs the session after a restart, so it mustn't crash the app.
     */
    private fun persist() {
        scope.launch {
            persistMutex.withLock {
                try {
                    storage.save(synchronized(lock) { cookies }.orEmpty())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save the session cookies", e)
                }
            }
        }
    }

    private fun Cookie.sameIdentity(other: Cookie) =
        name == other.name && domain == other.domain && path == other.path

    private companion object {
        const val TAG = "TollyCookies"
    }
}
