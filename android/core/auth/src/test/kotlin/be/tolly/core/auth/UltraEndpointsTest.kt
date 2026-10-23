package be.tolly.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraEndpointsTest {
    @Test
    fun detectsLoginCompletion() {
        assertTrue(UltraEndpoints.isLoginComplete("https://ultra.edu.kuleuven.cloud/ultra/course"))
        assertFalse(UltraEndpoints.isLoginComplete("https://ultra.edu.kuleuven.cloud/auth-saml/saml2/authenticate/_16_1"))
        assertFalse(UltraEndpoints.isLoginComplete("https://idp.kuleuven.be/idp/profile/SAML2/POST/SSO"))
    }

    @Test
    fun detectsAuthenticatorAppLinkOnly() {
        assertTrue(UltraEndpoints.isAuthenticatorAppLink("https://icts.kuleuven.be/apps/authenticator/login?x=1"))
        assertFalse(UltraEndpoints.isAuthenticatorAppLink("https://icts.kuleuven.be/other"))
        assertFalse(UltraEndpoints.isAuthenticatorAppLink("http://icts.kuleuven.be/apps/authenticator"))
        assertFalse(UltraEndpoints.isAuthenticatorAppLink("https://idp.kuleuven.be/apps/authenticator"))
    }
}
