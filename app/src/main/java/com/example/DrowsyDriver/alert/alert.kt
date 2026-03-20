package com.example.DrowsyDriver.alert
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.DrowsyDriver.R

object Alert {
    private const val CRITICAL_THRESHOLD = 100
    private var lastAlertTime: Long = 0
    private const val ALERT_COOLDOWN_MS = 10000  // 10 second cooldown between alerts
    private const val EYE_CLOSED_MS = 2000
    private const val YAWN_MS = 2000L
    private const val TILT_MS = 5000L
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    fun checkAlert(eyeClosedDuration: Long, isDrowsy: Boolean, yawnDuration: Long,headTiltDuration: Long): Boolean {
        var currentScore = 0
        // 1. Eyes Closed
        if (eyeClosedDuration >= EYE_CLOSED_MS) {
            currentScore += 70
            // If eyes are closed for a very long time, force an immediate 100
            if (eyeClosedDuration > 4000) currentScore += 30
        }

        // 2. ML Inference
        if (isDrowsy) {
            currentScore += 40
        }
        // 3. Yawning
        if (yawnDuration >= YAWN_MS) {
            currentScore += 30
        }
        // 4. Head Tilting
        if (headTiltDuration >= TILT_MS) {
            currentScore += 30
        }
        // Return true if the combined score hits the danger zone
        Log.d("ALERT_SYSTEM", "Current Drowsiness Score: $currentScore")
        return currentScore >= CRITICAL_THRESHOLD
    }

    fun triggerAlert(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < ALERT_COOLDOWN_MS) return
        lastAlertTime = now

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, R.raw.alarm).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        }
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                Log.d("ALERT", "[ALERT TRIGGERED] - Drowsiness Detected")
            }
        }

        // Vibration — fires once per alert (same cooldown)
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
            vibrator = v
        } catch (e: Exception) {
            Log.e("ALERT", "Error starting vibration", e)
        }
    }
}