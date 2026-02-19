package com.example.DrowsyDriver.extraction

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.round

class FaceExtractionEngine(
    private val context: Context,
    private val modelAssetPath: String = "face_landmarker.task",
    private val onResult: (ExtractionResult) -> Unit
) {

    data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    data class ExtractionResult(
        val ear: Float,
        val mar: Float,
        val headTiltDeg: Float,
        val imageWidth: Int,
        val imageHeight: Int,
        val faceBox: NormRect,
        val leftEyeBox: NormRect,
        val rightEyeBox: NormRect,
        val mouthBox: NormRect,
        val frameBitmap: Bitmap?
    )
    private var lastFrameBitmap: Bitmap? = null
    private var landmarker: FaceLandmarker? = null
    private var lastW = 1
    private var lastH = 1

    fun start() {
        if (landmarker != null) return

        val baseOptions = com.google.mediapipe.tasks.core.BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(false)
            .setOutputFacialTransformationMatrixes(false)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { e -> e.printStackTrace() }
            .build()

        landmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun stop() {
        landmarker?.close()
        landmarker = null
    }

    /**
     * Reliable alignment strategy:
     * - Convert ImageProxy -> Bitmap
     * - Rotate bitmap to upright using rotationDegrees
     * - Mirror horizontally for front camera (selfie preview)
     * - Feed MediaPipe upright bitmap (no extra rotation options)
     */
    fun analyze(imageProxy: ImageProxy, isFrontCamera: Boolean = true) {
        val lm = landmarker ?: run { imageProxy.close(); return }

        try {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val raw = imageProxy.toBitmapNv21()

            val upright = raw.rotate(rotation)
            val finalBmp = if (isFrontCamera) upright.mirrorX() else upright
            lastFrameBitmap = finalBmp
            lastW = finalBmp.width
            lastH = finalBmp.height

            val mpImage = BitmapImageBuilder(finalBmp).build()
            lm.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun handleResult(result: FaceLandmarkerResult) {
        if (result.faceLandmarks().isEmpty()) return

        val pts = result.faceLandmarks()[0].map { it.x() to it.y() } // normalized

        val ear = round2(computeAvgEar(pts))
        val mar = round2(computeMar(pts))
        val tilt = round2(computeHeadTiltRollDeg(pts))

        // tighter boxes
        val faceBox = boxFromIndices(pts, FACE_OVAL).padRelative(0.06f)
        val leftEyeBox = boxFromIndices(pts, LEFT_EYE_TIGHT).padRelative(0.15f)
        val rightEyeBox = boxFromIndices(pts, RIGHT_EYE_TIGHT).padRelative(0.15f)
        val mouthBox = boxFromIndices(pts, MOUTH_TIGHT)
            .padRelative(0.08f)
            .shiftY(-0.02f)   // ✅ move box slightly up


        onResult(
            ExtractionResult(
                ear = ear,
                mar = mar,
                headTiltDeg = tilt,
                imageWidth = lastW,
                imageHeight = lastH,
                faceBox = faceBox,
                leftEyeBox = leftEyeBox,
                rightEyeBox = rightEyeBox,
                mouthBox = mouthBox,
                frameBitmap = lastFrameBitmap
            )
        )
    }

    // ---------- features ----------
    private fun computeAvgEar(pts: List<Pair<Float, Float>>): Float {
        fun earFor(p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: Int): Float {
            val (x1, y1) = pts[p1]
            val (x2, y2) = pts[p2]
            val (x3, y3) = pts[p3]
            val (x4, y4) = pts[p4]
            val (x5, y5) = pts[p5]
            val (x6, y6) = pts[p6]
            val a = dist(x2, y2, x6, y6)
            val b = dist(x3, y3, x5, y5)
            val c = dist(x1, y1, x4, y4)
            return if (c > 1e-6f) (a + b) / (2f * c) else 0f
        }
        val left = earFor(33, 160, 158, 133, 153, 144)
        val right = earFor(362, 385, 387, 263, 373, 380)
        return (left + right) / 2f
    }

    private fun computeMar(pts: List<Pair<Float, Float>>): Float {
        val (xU, yU) = pts[13]
        val (xL, yL) = pts[14]
        val (xLeft, yLeft) = pts[78]
        val (xRight, yRight) = pts[308]
        val vertical = dist(xU, yU, xL, yL)
        val horizontal = dist(xLeft, yLeft, xRight, yRight)
        return if (horizontal > 1e-6f) vertical / horizontal else 0f
    }

    private fun computeHeadTiltRollDeg(pts: List<Pair<Float, Float>>): Float {
        val (xL, yL) = pts[33]
        val (xR, yR) = pts[263]
        val angleRad = atan2((yR - yL), (xR - xL))
        return (angleRad * 180f / Math.PI).toFloat()
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

    private fun round2(v: Float): Float = round(v * 100f) / 100f

    // ---------- box helpers ----------
    private fun boxFromIndices(pts: List<Pair<Float, Float>>, indices: IntArray): NormRect {
        var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
        for (i in indices) {
            val (x, y) = pts[i]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        return NormRect(
            minX.coerceIn(0f, 1f),
            minY.coerceIn(0f, 1f),
            maxX.coerceIn(0f, 1f),
            maxY.coerceIn(0f, 1f)
        )
    }

    // padding based on box size (keeps it tight for different faces)
    private fun NormRect.padRelative(frac: Float): NormRect {
        val w = (right - left).coerceAtLeast(1e-6f)
        val h = (bottom - top).coerceAtLeast(1e-6f)
        val px = w * frac
        val py = h * frac
        return NormRect(
            (left - px).coerceIn(0f, 1f),
            (top - py).coerceIn(0f, 1f),
            (right + px).coerceIn(0f, 1f),
            (bottom + py).coerceIn(0f, 1f)
        )
    }
    private fun NormRect.shiftY(delta: Float): NormRect {
        val t = (top + delta).coerceIn(0f, 1f)
        val b = (bottom + delta).coerceIn(0f, 1f)
        // Keep height if we hit clamp
        val h = bottom - top
        return if (b - t < h * 0.5f) {
            // fallback: preserve height best effort
            val newTop = (b - h).coerceIn(0f, 1f)
            NormRect(left, newTop, right, b)
        } else {
            NormRect(left, t, right, b)
        }
    }
    companion object {
        // face oval
        private val FACE_OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323,
            361, 288, 397, 365, 379, 378, 400, 377, 152, 148,
            176, 149, 150, 136, 172, 58, 132, 93, 234, 127,
            162, 21, 54, 103, 67, 109
        )

        // tighter eye sets (corners + upper/lower lid points)
        private val LEFT_EYE_TIGHT = intArrayOf(33, 133, 160, 158, 153, 144)
        private val RIGHT_EYE_TIGHT = intArrayOf(263, 362, 385, 387, 373, 380)

        // tighter mouth set (corners + upper/lower lip)
        // Outer lips (covers full lips better)
        private val MOUTH_TIGHT = intArrayOf(
            61, 291,   // corners
            0,         // upper lip top
            17,        // lower lip bottom
            82, 312,   // upper lip curve
            87, 317    // lower lip curve
        )
    }
}

// ---------- ImageProxy helpers ----------
private fun ImageProxy.toBitmapNv21(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun Bitmap.rotate(deg: Int): Bitmap {
    if (deg == 0) return this
    val m = Matrix().apply { postRotate(deg.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

private fun Bitmap.mirrorX(): Bitmap {
    val m = Matrix().apply {
        postScale(-1f, 1f)
        postTranslate(width.toFloat(), 0f)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}
