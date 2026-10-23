package be.tolly.core.model

import be.tolly.core.model.Notices.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticesTest {
    @Test
    fun anUpdateShowsTheNewNoticeOnce() {
        assertEquals(Decision.Show(2), Notices.decide(lastSeen = 1, signedIn = true, demo = false, latest = 2))
        // Once dismissed, the stored number is the newest.
        assertEquals(Decision.None, Notices.decide(lastSeen = 2, signedIn = true, demo = false, latest = 2))
    }

    @Test
    fun anInstallFromBeforeNoticesSeesTheNewest() {
        assertEquals(Decision.Show(3), Notices.decide(lastSeen = null, signedIn = true, demo = false, latest = 3))
    }

    @Test
    fun aNewInstallNeverSeesOne() {
        assertEquals(Decision.MarkSeen, Notices.decide(lastSeen = null, signedIn = false, demo = false, latest = 3))
        assertEquals(Decision.MarkSeen, Notices.decide(lastSeen = null, signedIn = true, demo = true, latest = 3))
    }

    @Test
    fun aSkippedNoticeIsReplacedByTheNewest() {
        assertEquals(Decision.Show(4), Notices.decide(lastSeen = 1, signedIn = false, demo = false, latest = 4))
    }

    @Test
    fun noNoticesShowNothing() {
        assertEquals(Decision.None, Notices.decide(lastSeen = null, signedIn = true, demo = false, latest = 0))
    }
}
