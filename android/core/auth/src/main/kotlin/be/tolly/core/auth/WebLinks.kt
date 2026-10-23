package be.tolly.core.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * For a WebView's `shouldOverrideUrlLoading`: whether another app takes the link [uri] instead of
 * the WebView. An Authenticator approval opens in that app (the IdP page keeps waiting and goes on
 * by itself once approved; without the app the WebView shows the web fallback), web pages stay in
 * the WebView, and any other link (`intent://`, `mailto:`) goes to an app.
 */
fun Context.handleWebViewLink(uri: Uri): Boolean = when {
    UltraEndpoints.isAuthenticatorAppLink(uri.toString()) -> openAuthenticatorLink(uri)
    uri.scheme == "http" || uri.scheme == "https" -> false
    else -> {
        launchExternalLink(uri)
        true
    }
}

/** Returns true if the Authenticator app took the link; otherwise the WebView shows the web fallback. */
private fun Context.openAuthenticatorLink(uri: Uri): Boolean = try {
    startActivity(
        Intent(Intent.ACTION_VIEW, uri)
            .setPackage(UltraEndpoints.AUTHENTICATOR_PACKAGE)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Hands a non-web link from a WebView (`intent://`, `mailto:`, an app's scheme) to another app. */
private fun Context.launchExternalLink(uri: Uri) {
    val intent = if (uri.scheme == "intent") {
        runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()?.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
        }
    } else {
        Intent(Intent.ACTION_VIEW, uri)
    }
    intent ?: return
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        intent.getStringExtra("browser_fallback_url")?.let {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
