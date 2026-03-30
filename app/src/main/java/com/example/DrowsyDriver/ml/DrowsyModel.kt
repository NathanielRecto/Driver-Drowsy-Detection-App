package com.example.DrowsyDriver.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ---------------------------------------------------------------------------
// Eye model
//   Training label order (Keras alphabetical folder sort):
//     index 0 → "eyes_closed"
//     index 1 → "eyes_open"
// ---------------------------------------------------------------------------
class EyeModel(context: Context) {

    private val tflite: Interpreter
    private val imgSize = 224

    private val inputBuffer = ByteBuffer
        .allocateDirect(1 * imgSize * imgSize * 3 * 4)
        .also { it.order(ByteOrder.nativeOrder()) }
    private val pixels = IntArray(imgSize * imgSize)
    private val output = Array(1) { FloatArray(2) }

    // Guards tflite.run() and tflite.close() so they never overlap.
    //
    // Root cause of SIGSEGV at 0x140: onDispose calls eyeModel.close() which
    // frees the native TFLite interpreter while the ML loop's
    // withContext(Dispatchers.Default) is still inside tflite.run() in native
    // code. tflite.run() then dereferences the freed native struct at offset
    // 0x140 → SIGSEGV. This is a native crash — try-catch cannot intercept it.
    //
    // synchronized(lock) ensures close() blocks until any in-progress run()
    // finishes, and run() returns immediately (returns 0f) if already closed.
    private val lock = Any()
    private var closed = false

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, "mnv2_eyes.tflite")
        tflite = Interpreter(modelBuffer)
        Log.d("TFLITE", "Eye model loaded")
    }

    private fun bitmapToInputBuffer(bmp: Bitmap): ByteBuffer {
        inputBuffer.rewind()
        bmp.getPixels(pixels, 0, imgSize, 0, 0, imgSize, imgSize)
        for (p in pixels) {
            inputBuffer.putFloat(((p shr 16) and 0xFF).toFloat()) // R
            inputBuffer.putFloat(((p shr 8)  and 0xFF).toFloat()) // G
            inputBuffer.putFloat( (p         and 0xFF).toFloat()) // B
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    /**
     * Returns P(eyes_closed). Returns 0f immediately if the model is closed.
     * Thread-safe: synchronized so close() cannot free the interpreter
     * while run() is executing in native code.
     */
    fun predictClosedProb(bitmap: Bitmap): Float {
        synchronized(lock) {
            if (closed) return 0f
            tflite.run(bitmapToInputBuffer(bitmap), output)
            Log.d("EYE_DEBUG", "closed=${output[0][0]}  open=${output[0][1]}")
            return output[0][0]
        }
    }

    /**
     * Closes the interpreter. Blocks until any in-progress run() finishes
     * so we never free native memory while inference is running.
     */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            tflite.close()
        }
    }
}

// ---------------------------------------------------------------------------
// Mouth model
//   index 0 → "mouth_normal"
//   index 1 → "mouth_yawn"
// ---------------------------------------------------------------------------
class MouthModel(context: Context) {

    private val tflite: Interpreter
    private val imgSize = 224

    private val inputBuffer = ByteBuffer
        .allocateDirect(1 * imgSize * imgSize * 3 * 4)
        .also { it.order(ByteOrder.nativeOrder()) }
    private val pixels = IntArray(imgSize * imgSize)
    private val output = Array(1) { FloatArray(2) }

    private val lock = Any()
    private var closed = false

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, "mnv2_mouth.tflite")
        tflite = Interpreter(modelBuffer)
        Log.d("TFLITE", "Mouth model loaded")
    }

    private fun bitmapToInputBuffer(bmp: Bitmap): ByteBuffer {
        inputBuffer.rewind()
        bmp.getPixels(pixels, 0, imgSize, 0, 0, imgSize, imgSize)
        for (p in pixels) {
            inputBuffer.putFloat(((p shr 16) and 0xFF).toFloat())
            inputBuffer.putFloat(((p shr 8)  and 0xFF).toFloat())
            inputBuffer.putFloat( (p         and 0xFF).toFloat())
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    /**
     * Returns P(yawn). Returns 0f immediately if the model is closed.
     * Thread-safe: synchronized so close() cannot free the interpreter
     * while run() is executing in native code.
     */
    fun predictYawnProb(bitmap: Bitmap): Float {
        synchronized(lock) {
            if (closed) return 0f
            tflite.run(bitmapToInputBuffer(bitmap), output)
            Log.d("MOUTH_MODEL", "normal=${output[0][0]}  yawn=${output[0][1]}")
            return output[0][1]
        }
    }

    /**
     * Closes the interpreter. Blocks until any in-progress run() finishes.
     */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            tflite.close()
        }
    }
}