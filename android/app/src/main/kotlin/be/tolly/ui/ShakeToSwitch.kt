package be.tolly.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.sqrt

/**
 * Calls [onShake] when the phone is shaken, while the app is in front and [enabled]. A shake is a
 * few hard jolts within a short time, so walking, a bump or putting the phone down doesn't count.
 */
@Composable
internal fun ShakeEffect(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)
    LifecycleResumeEffect(enabled) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = ShakeDetector { currentOnShake() }
        if (enabled && accelerometer != null) {
            sensors.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onPauseOrDispose { sensors?.unregisterListener(detector) }
    }
}

private class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var firstJolt = 0L
    private var jolts = 0
    private var lastJolt = 0L
    private var lastShake = 0L

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val force = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (force < JOLT_G) return
        val now = event.timestamp / 1_000_000
        if (now - lastJolt < JOLT_GAP_MS) return // the same jolt, still above the threshold
        lastJolt = now
        if (now - firstJolt > WINDOW_MS) {
            firstJolt = now
            jolts = 0
        }
        jolts++
        if (jolts >= JOLTS && now - lastShake > COOLDOWN_MS) {
            lastShake = now
            jolts = 0
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * How hard a jolt is, in g (resting is 1), and how many make a shake. Firm on purpose: a quick
 * flick, a bump in a bag or a run for the bus switched the look by accident, and nothing told
 * the user why the app suddenly looked different. It takes a deliberate few seconds of shaking.
 */
private const val JOLT_G = 3f
private const val JOLTS = 5
private const val JOLT_GAP_MS = 100L
private const val WINDOW_MS = 1_500L

/** A shake switches once; shaking on doesn't flip the look back and forth. */
private const val COOLDOWN_MS = 1_500L
