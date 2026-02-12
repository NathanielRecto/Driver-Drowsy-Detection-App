package com.example.DrowsyDriver
import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
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
    fun ComputeDrowsinessScore(cnnScore: Float, earScore: Float, marScore: Float, prevScore: Float): Float {

        val newScore = cnnScore + earScore + marScore
        var score = 0.8f*prevScore + 0.2f * newScore
        score = score.coerceIn(0f,1f)
        return score
    }

    fun TriggerAlarm(score: Float, threshold: Float = 0.7f): Boolean {
        return score > threshold
    }
}