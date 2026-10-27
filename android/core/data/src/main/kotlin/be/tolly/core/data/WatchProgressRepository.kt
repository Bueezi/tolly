package be.tolly.core.data

import be.tolly.core.auth.ApplicationScope
import be.tolly.core.database.WatchProgressDao
import be.tolly.core.database.WatchProgressEntity
import be.tolly.core.model.WatchProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the user left each lesson recording, so the player continues there and the Video tab
 * shows how much is watched. Recordings are known by their Kaltura entry id.
 */
@Singleton
class WatchProgressRepository @Inject constructor(
    private val dao: WatchProgressDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun observe(): Flow<Map<String, WatchProgress>> =
        dao.observeAll().map { all -> all.associate { it.key to WatchProgress(it.position, it.duration) } }

    suspend fun get(key: String): WatchProgress? = dao.get(key)?.let { WatchProgress(it.position, it.duration) }

    /**
     * Keeps [position] of [duration] for [key]. It is written after the caller is gone too (the
     * player closing). A position in the first seconds isn't worth continuing from: it clears what
     * was kept instead.
     */
    fun save(key: String, position: Long, duration: Long) {
        if (duration <= 0) return
        scope.launch {
            if (position < MIN_POSITION_MS) {
                dao.delete(key)
            } else {
                dao.upsert(WatchProgressEntity(key, position.coerceAtMost(duration), duration, System.currentTimeMillis()))
            }
        }
    }

    fun clear(key: String) {
        scope.launch { dao.delete(key) }
    }

    /** Someone else signed in: their recordings start unwatched. */
    suspend fun clearAll() = dao.deleteAll()

    private companion object {
        const val MIN_POSITION_MS = 10_000L
    }
}
