package be.tolly.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileUserAgentTest {
    private val tablet = "Mozilla/5.0 (Linux; Android 14; SM-X710 Build/UP1A; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/141.0.0.0 Safari/537.36"

    @Test
    fun tabletUserAgentGetsTheMobileToken() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; SM-X710 Build/UP1A; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36",
            mobileUserAgent(tablet),
        )
    }

    @Test
    fun phoneUserAgentStaysTheSame() {
        val phone = tablet.replace(" Safari/", " Mobile Safari/")
        assertEquals(phone, mobileUserAgent(phone))
    }

    @Test
    fun unusualUserAgentStillEndsUpMobile() {
        assertEquals("SomeBrowser/1.0 Mobile", mobileUserAgent("SomeBrowser/1.0"))
    }
}
