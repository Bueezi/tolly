package be.tolly.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** [action] with a haptic tick of [type] in front of it, for buttons and gestures that change state. */
@Composable
fun withHaptic(type: HapticFeedbackType, action: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(action)
    return remember(haptics, type) {
        {
            haptics.performHapticFeedback(type)
            current()
        }
    }
}
