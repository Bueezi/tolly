package be.tolly.core.data

import android.webkit.WebResourceResponse
import be.tolly.core.auth.UltraEndpoints
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads Ultra-hosted resources (images in announcements and documents) for the HTML viewer through
 * the authenticated OkHttp client, so the WebView itself never holds the session cookies.
 * Called on a WebView background thread.
 */
@Singleton
class HtmlResourceLoader @Inject constructor(
    private val client: OkHttpClient,
) {
    fun load(url: String): WebResourceResponse? {
        if (!UltraEndpoints.isUltra(url)) return null
        return try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val contentType = response.body.contentType()
            WebResourceResponse(
                contentType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                contentType?.charset()?.name(),
                response.code,
                response.message.ifBlank { "OK" },
                response.headers.toMap(),
                response.body.byteStream(),
            )
        } catch (_: Exception) {
            null
        }
    }
}
