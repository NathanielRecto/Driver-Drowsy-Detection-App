package com.example.DrowsyDriver.alert
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.DrowsyDriver.R

object Alert {
    private var lastAlertTime: Long = 0
    private const val ALERT_COOLDOWN_MS = 5000  // 10 second cooldown between alerts
    private const val EYE_CLOSED_ALERT_MS = 2000
    private const val YAWN_ALERT_MS = 2000
    private const val HEAD_TILT_DEG_MS = 3000
    private var mediaPlayer: MediaPlayer? = null
    fun checkAlert(eyeClosedDuration: Long, isDrowsy: Boolean, yawnDuration: Long,headTiltDuration: Long): Boolean {
        if (eyeClosedDuration >= EYE_CLOSED_ALERT_MS) return true   // Eyes closed longer than trigger
        if (isDrowsy) return true                                   // Output from ML module
        if (yawnDuration > YAWN_ALERT_MS) return true                 // Yawning
        if (kotlin.math.abs(headTiltDuration) >= HEAD_TILT_DEG_MS) return true    // Head tilt beyond trigger point for longer than trigger
        return false
    }
    fun triggerAlert(context: Context){
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < ALERT_COOLDOWN_MS) return

        lastAlertTime = now
        try {
            // Stop any existing sound before starting a new one
            mediaPlayer?.stop()
            mediaPlayer?.release()

            // Create MediaPlayer with custom sound.
            mediaPlayer = MediaPlayer.create(context, R.raw.alarm).apply {

                // Set to ALARM usage so it plays loud even if media volume is low
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                isLooping = false // Change to true if you want it to repeat
                start()
            }
            Log.d("ALERT", "[ALERT TRIGGERED] - Drowsiness Detected")
        } catch (e: Exception) {
                Log.e("ALERT", "Error playing sound file", e)
            }
    }
}