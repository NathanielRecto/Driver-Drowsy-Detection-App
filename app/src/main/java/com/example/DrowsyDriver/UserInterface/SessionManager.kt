package com.example.DrowsyDriver.session

import java.text.SimpleDateFormat
import java.util.*

object SessionManager {

    var sessionStartTime: Long = System.currentTimeMillis()

    var totalEyeClosedTime = 0f
    var totalHeadTiltTime = 0f
    var yawnCount = 0
    var drowsyEvents = 0
    var framesProcessed = 0

    private val _events = mutableListOf<String>()
    val events: List<String> get() = _events

    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun logEvent(message: String) {
        val time = formatter.format(Date())
        _events.add("$time - $message")
    }

    fun incrementFrame() {
        framesProcessed++
    }

    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
        totalEyeClosedTime = 0f
        totalHeadTiltTime = 0f
        yawnCount = 0
        drowsyEvents = 0
        framesProcessed = 0
        _events.clear()
    }

    fun getSessionDurationMinutes(): Int {
        val diff = System.currentTimeMillis() - sessionStartTime
        return (diff / 1000 / 60).toInt()
    }
}