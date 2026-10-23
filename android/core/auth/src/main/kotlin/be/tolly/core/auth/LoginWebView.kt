package be.tolly.core.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/** Shared WebView settings for the interactive and the silent KU Leuven login. */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.configureForLogin() {
    with(settings) {
        javaScriptEnabled = true // the IdP and the SAML auto-POST pages need JS
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        cacheMode = WebSettings.LOAD_DEFAULT
        @Suppress("DEPRECATION")
        saveFormData = false
        userAgentString = mobileUserAgent(userAgentString)
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@configureForLogin, false)
    }
}

/**
 * The WebView's user agent, made to look like a phone browser. On tablets Android leaves out the
 * "Mobile" token, and the KU Leuven sign-in then treats the WebView as a desktop browser and shows
 * a QR code to scan with a phone instead of the normal sign-in.
 */
fun mobileUserAgent(default: String): String = when {
    "Mobile" in default -> default
    " Safari/" in default -> default.replace(" Safari/", " Mobile Safari/")
    else -> "$default Mobile"
}
