package be.tolly.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.designsystem.component.TollyBackButton
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.scrollingContentPadding
import be.tolly.core.model.ViewerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A downloaded file shown in Tolly: a PDF's pages, a picture or a text file, by its [mimeType] and [name]. */
@Composable
internal fun FileViewerScreen(uri: Uri, mimeType: String?, name: String, onClose: () -> Unit, onOpenExternally: () -> Unit) {
    TollyScaffold(
        topBar = {
            TollyTopBar(
                detail = true,
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TollyBackButton(onClick = onClose, close = true) },
                actions = {
                    IconButton(onClick = onOpenExternally) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.viewer_open_externally))
                    }
                },
            )
        },
    ) { padding ->
        val failed: @Composable () -> Unit = {
            MessageState(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.viewer_failed),
                actionLabel = stringResource(R.string.viewer_open_externally),
                onAction = onOpenExternally,
            )
        }
        when (ViewerKind.of(mimeType, name)) {
            ViewerKind.PDF -> PdfView(uri, scrollingContentPadding(padding, top = 8.dp, bottom = 16.dp), failed)
            ViewerKind.IMAGE -> ImageView(uri, padding, failed)
            ViewerKind.TEXT -> TextView(uri, scrollingContentPadding(padding, bottom = 16.dp), failed)
            null -> failed()
        }
    }
}

/** A PDF's pages one under the other, each drawn when it scrolls into view. */
@Composable
private fun PdfView(uri: Uri, padding: PaddingValues, failed: @Composable () -> Unit) {
    val context = LocalContext.current
    val document by produceState<Result<PdfDocument>?>(null, uri) {
        val opened = withContext(Dispatchers.IO) { runCatching { PdfDocument.open(context, uri) } }
        value = opened
        awaitDispose { opened.getOrNull()?.close() }
    }
    val pdf = document?.getOrNull()
    when {
        document == null -> LoadingState()
        pdf == null || pdf.pageSizes.isEmpty() -> failed()
        else -> Zoomable {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Sharp enough to zoom in a little before the pages get soft.
                val renderWidth = (constraints.maxWidth * RENDER_SCALE).toInt().coerceAtMost(MAX_RENDER_WIDTH)
                LazyColumn(
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(pdf.pageSizes) { index, size ->
                        val label = stringResource(R.string.viewer_page, index + 1, pdf.pageSizes.size)
                        val page by produceState<ImageBitmap?>(null, pdf, index, renderWidth) {
                            value = pdf.render(index, renderWidth)
                        }
                        Box(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .aspectRatio(size.width.toFloat() / size.height)
                                .shadow(2.dp)
                                .background(Color.White)
                                .semantics { contentDescription = label },
                        ) {
                            page?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        }
                    }
                }
            }
        }
    }
}

/** A picture, scaled to fit, to zoom into. */
@Composable
private fun ImageView(uri: Uri, padding: PaddingValues, failed: @Composable () -> Unit) {
    val context = LocalContext.current
    val image by produceState<Result<ImageBitmap>?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(context, uri) } }
    }
    val bitmap = image?.getOrNull()
    when {
        image == null -> LoadingState()
        bitmap == null -> failed()
        else -> Zoomable {
            Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize().padding(padding), contentScale = ContentScale.Fit)
        }
    }
}

/** A text or code file, line by line in a monospace font, as much as [MAX_TEXT_BYTES] of it. */
@Composable
private fun TextView(uri: Uri, padding: PaddingValues, failed: @Composable () -> Unit) {
    val context = LocalContext.current
    val text by produceState<Result<List<String>>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val buffer = ByteArray(MAX_TEXT_BYTES)
                    var read = 0
                    while (read < buffer.size) {
                        val count = input.read(buffer, read, buffer.size - read)
                        if (count < 0) break
                        read += count
                    }
                    buffer.copyOf(read)
                }
                bytes.toString(Charsets.UTF_8).replace("\r\n", "\n").split('\n')
            }
        }
    }
    val lines = text?.getOrNull()
    when {
        text == null -> LoadingState()
        lines == null -> failed()
        else -> SelectionContainer {
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        line.ifEmpty { " " },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Pinch to zoom (up to [MAX_ZOOM]) and move around with two fingers; one finger still scrolls
 * what is inside. A double tap zooms in, or back out.
 */
@Composable
private fun Zoomable(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(value: Offset): Offset {
        val maxX = size.width * (scale - 1) / 2
        val maxY = size.height * (scale - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            offset = clamp(offset + event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                        offset = clamp(Offset.Zero)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        ) { content() }
    }
}

/** An open PDF; [PdfRenderer] draws one page at a time, so rendering takes turns. */
private class PdfDocument(private val file: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    private val lock = Mutex()

    val pageSizes: List<IntSize> = (0 until renderer.pageCount).map { index ->
        renderer.page(index) { IntSize(it.width.coerceAtLeast(1), it.height.coerceAtLeast(1)) }
    }

    suspend fun render(index: Int, width: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                renderer.page(index) { page ->
                    val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    private inline fun <T> PdfRenderer.page(index: Int, block: (PdfRenderer.Page) -> T): T {
        val page = openPage(index)
        try {
            return block(page)
        } finally {
            page.close()
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { file.close() }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfDocument {
            val file = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
            return try {
                PdfDocument(file, PdfRenderer(file))
            } catch (e: Exception) {
                file.close()
                throw e
            }
        }
    }
}

/** Decodes a picture no bigger than [MAX_IMAGE_SIDE] on its longest side, so a camera photo doesn't run out of memory. */
private fun decodeImage(context: Context, uri: Uri): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
    return requireNotNull(bitmap).asImageBitmap()
}

private const val RENDER_SCALE = 1.5f
private const val MAX_RENDER_WIDTH = 2400
private const val MAX_IMAGE_SIDE = 4096
private const val MAX_TEXT_BYTES = 512 * 1024
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
