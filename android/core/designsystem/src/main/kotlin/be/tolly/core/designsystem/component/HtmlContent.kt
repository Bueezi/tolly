package be.tolly.core.designsystem.component

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import be.tolly.core.designsystem.theme.LocalGlass

/**
 * Renders Blackboard HTML (announcements, Ultra documents) in a locked-down WebView: no JavaScript,
 * no file access, colours taken from the Material theme and every link handed to [onLinkClick].
 * Ultra-hosted images are fetched through [resourceLoader] so the WebView needs no session cookies.
 *
 * With the glass look the screen's content is recorded for the glass bars and drawn again,
 * blurred, in an offscreen layer. WebView's GPU drawing can't go into such a layer (on Vulkan the
 * renderer aborts, crashing the app), so there the WebView draws in software, into a bitmap the
 * glass can blur like any other content.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlContent(
    html: String,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    baseUrl: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    resourceLoader: ((String) -> WebResourceResponse?)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentLoader by rememberUpdatedState(resourceLoader)
    val layerType = if (LocalGlass.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_NONE

    val document = remember(html, colors, contentPadding, layoutDirection, density.fontScale) {
        wrapHtml(
            body = html,
            text = colors.onSurface,
            muted = colors.onSurfaceVariant,
            link = colors.primary,
            border = colors.outlineVariant,
            codeBackground = colors.surfaceContainerHighest,
            padding = contentPadding,
            layoutDirection = layoutDirection,
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                with(settings) {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = false
                    loadWithOverviewMode = true
                    useWideViewPort = false
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        currentOnLinkClick(request.url.toString())
                        return true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (request.method == "GET") currentLoader?.invoke(request.url.toString()) else null
                }
            }
        },
        update = { webView ->
            if (webView.layerType != layerType) webView.setLayerType(layerType, null)
            webView.settings.textZoom = (density.fontScale * 100).toInt()
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(baseUrl, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun Color.css(): String {
    val argb = toArgb()
    return "rgba(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF}, ${"%.3f".format(java.util.Locale.ROOT, alpha)})"
}

private fun wrapHtml(
    body: String,
    text: Color,
    muted: Color,
    link: Color,
    border: Color,
    codeBackground: Color,
    padding: PaddingValues,
    layoutDirection: LayoutDirection,
): String {
    val top = padding.calculateTopPadding().value
    val bottom = padding.calculateBottomPadding().value
    val start = padding.calculateLeftPadding(layoutDirection).value
    val end = padding.calculateRightPadding(layoutDirection).value
    return """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html, body { margin: 0; background: transparent; }
          body {
            padding: ${top}px ${end}px ${bottom}px ${start}px;
            color: ${text.css()};
            font-family: sans-serif; font-size: 16px; line-height: 1.55;
            overflow-wrap: anywhere; word-break: break-word;
          }
          /* Authors often hard-code dark text or white backgrounds; keep everything readable in both themes. */
          [style*="color"] { color: inherit !important; }
          [style*="background"] { background: transparent !important; }
          a { color: ${link.css()}; }
          img, video, iframe { max-width: 100%; height: auto; border-radius: 8px; }
          table { display: block; overflow-x: auto; border-collapse: collapse; max-width: 100%; }
          td, th { border: 1px solid ${border.css()}; padding: 6px 8px; vertical-align: top; overflow-wrap: break-word; word-break: normal; }
          pre, code { background: ${codeBackground.css()}; border-radius: 6px; font-size: 14px; }
          pre { padding: 12px; overflow-x: auto; white-space: pre-wrap; }
          blockquote { margin: 0; padding-left: 12px; border-left: 3px solid ${border.css()}; color: ${muted.css()}; }
          hr { border: none; border-top: 1px solid ${border.css()}; }
          p:empty { display: none; }
          h1, h2, h3 { line-height: 1.3; }
        </style>
        </head><body>$body</body></html>
    """.trimIndent()
}
