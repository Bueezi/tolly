package be.tolly.core.designsystem.component

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.shapes.UnevenRoundedRectangle
import be.tolly.core.designsystem.theme.LocalGlassSpec

private val BlurBehindRadius = 28.dp

/** iOS 26 sheets have deep, continuous corners. */
private val GlassSheetShape = UnevenRoundedRectangle(topStart = 34.dp, topEnd = 34.dp)

/**
 * A modal bottom sheet. With the glass look it is an iOS sheet: deep corners and translucent
 * over the app, which is blurred behind it on Android 12 and up (unless the system has
 * cross-window blur off, e.g. in battery saver); otherwise the sheet stays almost opaque so its
 * text keeps its contrast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TollySheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    if (spec == null) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState, content = content)
        return
    }
    var blurred by remember { mutableStateOf(false) }
    val base = if (spec.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = GlassSheetShape,
        containerColor = base.copy(alpha = if (blurred) 0.82f else 0.97f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (blurred) 0.18f else 0.32f),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BlurWindowBehind(hiding = sheetState.targetValue == SheetValue.Hidden, onBlurred = { blurred = true })
        }
        content()
    }
}

/**
 * Blurs whatever is behind the dialog window this is composed in, easing in as it opens and out
 * as it closes. Calls [onBlurred] once the blur is on; does nothing if it can't be.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun BlurWindowBehind(hiding: Boolean, onBlurred: () -> Unit) {
    val view = LocalView.current
    val window = remember(view) { view.dialogWindow()?.takeIf { it.windowManager.isCrossWindowBlurEnabled } } ?: return
    val target = with(LocalDensity.current) { BlurBehindRadius.toPx() }
    val radius = remember { Animatable(0f) }
    LaunchedEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        onBlurred()
    }
    LaunchedEffect(window, hiding) {
        radius.animateTo(if (hiding) 0f else target, tween(if (hiding) 150 else 250)) {
            window.attributes = window.attributes.apply { blurBehindRadius = value.toInt() }
        }
    }
}

private fun View.dialogWindow(): Window? {
    var current: Any? = this
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = (current as? View)?.parent
    }
    return null
}
