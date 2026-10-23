package be.tolly.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToledoPortalTest {
    @Test
    fun readsTheUnreadCount() {
        assertEquals(3, ToledoPortal.parseCount("""{"value":3,"origin":"MSCLOUD"}"""))
        assertNull(ToledoPortal.parseCount("<html>sign in</html>"))
    }
}
