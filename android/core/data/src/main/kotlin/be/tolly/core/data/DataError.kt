package be.tolly.core.data

import android.util.Log
import be.tolly.core.auth.DemoMode
import be.tolly.core.auth.SessionExpiredException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** Failure categories the UI can explain to the user. */
enum class DataError {
    SESSION_EXPIRED,
    OFFLINE,
    NOT_AVAILABLE,
    SERVER,
}

class DataException(val error: DataError, cause: Throwable? = null) : Exception(error.name, cause)

fun Throwable.toDataError(): DataError = when (this) {
    is DataException -> error
    is SessionExpiredException -> DataError.SESSION_EXPIRED
    is HttpException -> when (code()) {
        401 -> DataError.SESSION_EXPIRED
        403, 404 -> DataError.NOT_AVAILABLE
        else -> DataError.SERVER
    }
    is SerializationException -> DataError.SERVER
    is IOException -> DataError.OFFLINE
    else -> DataError.SERVER
}

/** An expired Toledo session, however it showed up: it ends a refresh instead of being skipped. */
internal fun Throwable.isSessionError(): Boolean =
    this is SessionExpiredException || (this is HttpException && code() == 401)

/**
 * Runs [block], mapping failures to [DataException] while letting cancellation through. In the
 * demo there is no Toledo to ask, so [block] doesn't run: the call answers with [inDemo], or
 * fails as [DataError.NOT_AVAILABLE] when it has no demo answer (signing in, say).
 */
internal suspend fun <T> dataCall(inDemo: (() -> T)? = null, block: suspend () -> T): Result<T> = try {
    when {
        !DemoMode.active -> Result.success(block())
        inDemo != null -> Result.success(inDemo())
        else -> throw DataException(DataError.NOT_AVAILABLE)
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    val failure = if (e is DataException) e else DataException(e.toDataError(), e)
    Log.w("TollyData", "Request failed: ${failure.error}", e)
    Result.failure(failure)
}

/** A [dataCall] that only refreshes the cache: in the demo it succeeds without changing anything. */
internal suspend fun refreshCall(block: suspend () -> Unit): Result<Unit> = dataCall(inDemo = {}, block = block)
