package com.example.DrowsyDriver
import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.DrowsyDriver.extraction.FaceExtractionEngine
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import androidx.core.graphics.scale
import androidx.core.graphics.get

object CNNModel {
    private var interpreter: Interpreter? = null

    fun loadModel(context: Context, modelFileName: String = "model.tflite") {
        val fd = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fd.startOffset
        val declaredLength = fd.declaredLength
        val mappedByteBuffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        interpreter = Interpreter(mappedByteBuffer)
    }
    fun predict(input: Array<Array<Array<FloatArray>>>): Float {
        val output = Array(1) { FloatArray(1) }
        interpreter?.run(input, output)
        return output[0][0]
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

object Detection {
    public var score = 0f
    private var yawnStartTime: Long? = null
    private var eyeClosedStartTime: Long? = null
    private var yawnTriggered = false
    private var eyeClosureTriggered = false
    private var marScore = 0f
    private var earScore = 0f
    private const val MAR_THRESHOLD = 1.0f
    private const val EAR_THRESHOLD = 0.25f
    private const val YAWN_DURATION_MS = 2000L      // 2 seconds
    private const val EYE_CLOSE_DURATION_MS = 2000L // 2 seconds
    fun process(cnnScore: Float, ear: Float, mar: Float): Float {

        val now = System.currentTimeMillis()
        // Yawn detection
        if (mar > MAR_THRESHOLD) {
            if (yawnStartTime == null) {
                yawnStartTime = now
            }
            val elapsed = now - yawnStartTime!!

            // avoid double counting yawn
            if (elapsed >= YAWN_DURATION_MS && !yawnTriggered) {
                marScore = 0.2f
                yawnTriggered = true
            }
        }
        else {
            yawnStartTime = null
            yawnTriggered = false
            marScore = 0f
        }

        // Eye closure detection
        if (ear < EAR_THRESHOLD) {

            if (eyeClosedStartTime == null) {
                eyeClosedStartTime = now
            }

            val elapsed = now - eyeClosedStartTime!!

            if (elapsed >= EYE_CLOSE_DURATION_MS && !eyeClosureTriggered) {
                earScore = 0.3f
                eyeClosureTriggered = true
            }

        } else {
            eyeClosedStartTime = null
            eyeClosureTriggered = false
            earScore = -0.1f
        }

        // drowsiness score
        val newScore = cnnScore + marScore + earScore
        score = 0.8f * score + 0.2f * newScore
        score = score.coerceIn(0f, 1f)

        return score
    }

    fun shouldTriggerAlarm(threshold: Float = 0.6f): Boolean {
        return score > threshold
    }

    fun reset() {
        score = 0f
        yawnStartTime = null
        eyeClosedStartTime = null
        yawnTriggered = false
        eyeClosureTriggered = false
        marScore = 0f
        earScore = 0f
    }
}

class DrowsinessProcessor(private val context: Context) {
    // Create mediapipe face extraction engine
    private val faceEngine = FaceExtractionEngine(context) {
        result -> handleFaceResult(result)
    }
    fun start() {
        //CNNModel.loadModel(context)
        faceEngine.start()
        Detection.reset()
    }
    fun stop() {
        faceEngine.stop()
        CNNModel.close()
    }
    fun analyze(imageProxy: ImageProxy) {
        faceEngine.analyze(imageProxy, true)
    }
    fun handleFaceResult(result: FaceExtractionEngine.ExtractionResult) {
        val ear = result.ear
        val mar = result.mar
        val cnnScore = 0.2f  // fixed value for testing
       // result.frameBitmap?.let { bitmap ->
         //   val cnnInput = prepareCNNInput(bitmap)
         //   val cnnScore = CNNModel.predict(cnnInput)

            val finalScore = Detection.process(cnnScore = cnnScore, ear = result.ear, mar = result.mar)

            if (Detection.shouldTriggerAlarm()) {
                triggerAlarm()
            }
        //}
    }

    private fun prepareCNNInput(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        // Resize to 128x128
        val resized = bitmap.scale(128, 128)

        // Create input tensor [1,128,128,3]
        val input = Array(1) { Array(128) { Array(128) { FloatArray(3) } } }
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                val pixel = resized[x, y]
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()
                input[0][y][x][0] = r / 255f
                input[0][y][x][1] = g / 255f
                input[0][y][x][2] = b / 255f
            }
        }
        return input
    }
    private fun triggerAlarm() {
        android.util.Log.d("DROWSINESS", "ALARM")
    }
}