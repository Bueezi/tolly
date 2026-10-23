package be.tolly.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Runs [block] for every item, at most [concurrency] at a time, and returns the outcomes in order.
 * Cancellation and an expired session end the whole run; any other failure is kept as that item's
 * outcome, so one course or calendar that fails doesn't sink the rest.
 */
internal suspend fun <T, R> List<T>.mapParallel(concurrency: Int, block: suspend (T) -> R): List<Result<R>> =
    coroutineScope {
        val permits = Semaphore(concurrency)
        map { item ->
            async {
                permits.withPermit {
                    try {
                        Result.success(block(item))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (e.isSessionError()) throw e
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()
    }
