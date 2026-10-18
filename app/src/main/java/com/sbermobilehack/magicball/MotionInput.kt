package com.sbermobilehack.magicball

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun MotionInput(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestShake by rememberUpdatedState(onShake)
    DisposableEffect(context, owner, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var previous: Float? = null
        var lastShake = 0L
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val magnitude = sqrt(event.values.take(3).sumOf { (it * it).toDouble() }).toFloat()
                val old = previous
                previous = magnitude
                val now = SystemClock.elapsedRealtime()
                if (old != null && abs(magnitude - old) > 13 && now - lastShake > 1800) {
                    lastShake = now
                    latestShake()
                }
            }
        }
        fun update() {
            manager.unregisterListener(listener)
            previous = null
            if (enabled && sensor != null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
}
