package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs

class PocketMotionDetector(
    context: Context,
    private val onFlickTriggered: () -> Unit,
    private val onFaceDownTriggered: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isRegistered = false
    private var flickEnabled: Boolean = true
    private var faceDownEnabled: Boolean = true
    private var flickSensitivity: Float = 0.6f
    private var faceDownSensitivity: Float = 0.6f

    private var lastFlickTimeMs: Long = 0L
    private var lastFlipTimeMs: Long = 0L
    private var wasFaceUp: Boolean = true
    private var lastLateralAccel: Float = 0f

    fun updateSensitivity(value: Float) {
        val clamped = value.coerceIn(0.2f, 1.0f)
        flickSensitivity = clamped
        faceDownSensitivity = clamped
    }

    fun configure(
        wristFlickEnabled: Boolean,
        wristFlickSensitivity: Float,
        faceDownEnabled: Boolean,
        faceDownSensitivity: Float
    ) {
        this.flickEnabled = wristFlickEnabled
        this.flickSensitivity = wristFlickSensitivity.coerceIn(0.2f, 1.0f)
        this.faceDownEnabled = faceDownEnabled
        this.faceDownSensitivity = faceDownSensitivity.coerceIn(0.2f, 1.0f)
    }

    fun start() {
        if (isRegistered) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        isRegistered = true
        wasFaceUp = true
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        val flickScale = 1.45f - flickSensitivity // 1.25 (low) -> 0.45 (high)
        val flipThresholdZ = -6.0f - ((1.0f - faceDownSensitivity) * 2.5f) // -6.0 (high) -> -8.0 (low)

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val az = event.values[2]
                lastLateralAccel = abs(ax)

                if (faceDownEnabled) {
                    if (az > 5.0f) {
                        wasFaceUp = true
                    } else if (wasFaceUp && az < flipThresholdZ && (now - lastFlipTimeMs > 1600L)) {
                        wasFaceUp = false
                        lastFlipTimeMs = now
                        onFaceDownTriggered()
                    }
                }

                if (flickEnabled && gyroscope == null) {
                    val accelThreshold = 15.5f * flickScale
                    if (abs(ax) > accelThreshold && (now - lastFlickTimeMs > 1400L)) {
                        lastFlickTimeMs = now
                        onFlickTriggered()
                    }
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                if (flickEnabled) {
                    val gy = abs(event.values[1])
                    val gz = abs(event.values[2])
                    val gyroThreshold = 4.6f * flickScale
                    if ((gz > gyroThreshold || gy > gyroThreshold) &&
                        lastLateralAccel > 3.5f &&
                        (now - lastFlickTimeMs > 1400L)
                    ) {
                        lastFlickTimeMs = now
                        onFlickTriggered()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
