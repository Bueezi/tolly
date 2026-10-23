package be.tolly.core.ui

import be.tolly.core.data.DataError
import be.tolly.core.data.DataException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefresherTest {
    @Test
    fun runsOneRefreshAtATimeAndKeepsItsError() = runTest(UnconfinedTestDispatcher()) {
        val refresher = Refresher(backgroundScope)
        val done = CompletableDeferred<Result<Unit>>()
        var runs = 0
        refresher.refresh { runs++; done.await() }
        refresher.refresh { runs++; Result.success(Unit) }
        assertEquals(1, runs)
        assertTrue(refresher.state.value.refreshing)
        assertFalse(refresher.state.value.loaded)

        done.complete(Result.failure(DataException(DataError.OFFLINE)))
        assertEquals(RefreshState(refreshing = false, error = DataError.OFFLINE, loaded = true), refresher.state.value)

        refresher.refresh { Result.success(Unit) }
        assertEquals(RefreshState(loaded = true), refresher.state.value)
    }

    @Test
    fun keepsAnErrorReportedWhileRefreshing() = runTest(UnconfinedTestDispatcher()) {
        val refresher = Refresher(backgroundScope)
        val done = CompletableDeferred<Result<Unit>>()
        refresher.refresh { done.await() }
        refresher.fail(DataException(DataError.SERVER))
        done.complete(Result.success(Unit))
        assertEquals(DataError.SERVER, refresher.state.value.error)
    }

    @Test
    fun listLoadsUntilTheFirstRefreshEnds() {
        assertTrue(RefreshState().list(emptyList<Int>()).loading)
        assertFalse(RefreshState(loaded = true).list(emptyList<Int>()).loading)
        assertFalse(RefreshState().list(listOf(1)).loading)
    }
}
