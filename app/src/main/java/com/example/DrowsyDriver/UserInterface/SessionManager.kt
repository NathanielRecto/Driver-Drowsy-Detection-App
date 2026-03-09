package com.example.DrowsyDriver.session

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton session tracker.
 *
 * Thread-safety fixes:
 *  - framesProcessed, yawnCount, drowsyEvents use AtomicInteger so concurrent
 *    increments from the engine callback thread never corrupt the count.
 *  - totalEyeClosedTime and totalHeadTiltTime use AtomicLong (storing bits of
 *    a Float) because there is no AtomicFloat in Java.
 *  - events uses CopyOnWriteArrayList so the engine callback can add entries
 *    on any thread while resetSession() clears it on the main thread without
 *    causing a ConcurrentModificationException or SIGSEGV.
 *  - sessionStartMs is @Volatile so the main thread always sees the latest
 *    value written by whichever thread called resetSession().
 */
object SessionManager {

    // ── Atomic counters ───────────────────────────────────────────────────────
    private val _frames       = AtomicInteger(0)
    private val _yawns        = AtomicInteger(0)
    private val _drowsy       = AtomicInteger(0)
    private val _eyeTimeBits  = AtomicLong(0L)   // Float bits
    private val _tiltTimeBits = AtomicLong(0L)   // Float bits

    val framesProcessed: Int  get() = _frames.get()
    val yawnCount:       Int  get() = _yawns.get()
    val drowsyEvents:    Int  get() = _drowsy.get()

    var totalEyeClosedTime: Float
        get()      = java.lang.Float.intBitsToFloat(_eyeTimeBits.get().toInt())
        set(value) { _eyeTimeBits.set(java.lang.Float.floatToRawIntBits(value).toLong()) }

    var totalHeadTiltTime: Float
        get()      = java.lang.Float.intBitsToFloat(_tiltTimeBits.get().toInt())
        set(value) { _tiltTimeBits.set(java.lang.Float.floatToRawIntBits(value).toLong()) }

    // ── Thread-safe event log ─────────────────────────────────────────────────
    // CopyOnWriteArrayList allows concurrent reads and writes from different
    // threads without locking. resetSession() replaces it with a new empty
    // instance rather than calling clear(), which is safe even if a writer is
    // mid-way through adding an entry to the old list.
    @Volatile private var _events = CopyOnWriteArrayList<String>()
    val events: List<String> get() = _events

    // ── Session start time ────────────────────────────────────────────────────
    @Volatile private var sessionStartMs = System.currentTimeMillis()

    // ── Public API ────────────────────────────────────────────────────────────

    fun incrementFrame()  { _frames.incrementAndGet() }

    fun logEvent(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _events.add("[$time] $msg")
    }

    fun getSessionDurationMinutes(): Long {
        return (System.currentTimeMillis() - sessionStartMs) / 60_000L
    }

    /**
     * Reset all counters and the event log.
     * Safe to call from any thread.
     */
    fun incrementYawn()   { _yawns.incrementAndGet() }
    fun incrementDrowsy() { _drowsy.incrementAndGet() }

    fun resetSession() {
        _frames.set(0)
        _yawns.set(0)
        _drowsy.set(0)
        _eyeTimeBits.set(0L)
        _tiltTimeBits.set(0L)
        _events = CopyOnWriteArrayList()   // atomic reference swap
        sessionStartMs = System.currentTimeMillis()
    }
}