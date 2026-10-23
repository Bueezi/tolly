package be.tolly.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LogExporterTest {
    @Test
    fun stripsQueryStringsFromUrls() {
        val line = "GET https://ultra.edu.kuleuven.cloud/bbcswebdav/xid-1_1?sig=abc&exp=1 failed"
        assertEquals("GET https://ultra.edu.kuleuven.cloud/bbcswebdav/xid-1_1?… failed", LogExporter.redact(line))
    }

    @Test
    fun keepsUrlsWithoutQuery() {
        val line = "page started https://idp.kuleuven.be/idp/profile/SAML2/Redirect/SSO"
        assertEquals(line, LogExporter.redact(line))
    }
}
