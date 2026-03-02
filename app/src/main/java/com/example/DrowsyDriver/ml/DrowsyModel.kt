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
//
//   FIX: previously the code returned  1f - output[0][1]  which equals
//   output[0][0] only when the two probabilities sum to exactly 1.0 (they
//   do after softmax, so that arithmetic was fine).  The real problem was
//   that the caller was passing a *bothEyesBox* crop — a wide landscape
//   rectangle squished to 224×224 — instead of individual square eye crops.
//   That crop mismatch is fixed in CameraScreen; the model itself now just
//   returns P(closed) = output[0][0] directly, which is clearer.
//
//   If you ever see the detector fire with eyes wide open, flip the index:
//   change output[0][0]  →  output[0][1]  (means training folders were
//   named so that "open" sorts before "closed").
// ---------------------------------------------------------------------------
class EyeModel(context: Context) {

    private val tflite: Interpreter
    private val imgSize = 224

    private val inputBuffer = ByteBuffer
        .allocateDirect(1 * imgSize * imgSize * 3 * 4)
        .also { it.order(ByteOrder.nativeOrder()) }
    private val pixels = IntArray(imgSize * imgSize)
    private val output = Array(1) { FloatArray(2) }

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, "mnv2_eyes.tflite")
        tflite = Interpreter(modelBuffer)
        Log.d("TFLITE", "Eye model loaded")
    }

    /**
     * Convert a 224×224 bitmap to a float32 input buffer.
     * Raw 0-255 values are passed in; the model has a built-in
     * (x − 127.5) / 127.5  rescaling layer so no manual normalisation
     * is needed here.
     */
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
     * Returns P(eyes_closed) for a single, square-ish eye crop that has
     * already been scaled to 224×224 by [cropNormRect].
     *
     * output[0][0] = P(eyes_closed)   ← what we return
     * output[0][1] = P(eyes_open)
     */
    fun predictClosedProb(bitmap: Bitmap): Float {
        tflite.run(bitmapToInputBuffer(bitmap), output)
        Log.d("EYE_DEBUG", "closed=${output[0][0]}  open=${output[0][1]}")
        return output[0][0]   // P(eyes_closed) directly
    }

    fun close() = tflite.close()
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

    /** Returns P(yawn). */
    fun predictYawnProb(bitmap: Bitmap): Float {
        tflite.run(bitmapToInputBuffer(bitmap), output)
        Log.d("MOUTH_MODEL", "normal=${output[0][0]}  yawn=${output[0][1]}")
        return output[0][1]
    }

    fun close() = tflite.close()
}