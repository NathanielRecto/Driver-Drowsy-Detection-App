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
        val bothEyesBox: NormRect,
        val mouthBox: NormRect
    )

    private var landmarker: FaceLandmarker? = null
    private var lastW = 1
    private var lastH = 1

    /**
     * The most recent camera frame as a guaranteed ARGB_8888 software bitmap,
     * exclusively for the ML inference loop in CameraScreen.
     *
     * Thread-safety design (double-buffer):
     *   - analyze() runs on the analysisExecutor (single background thread).
     *     Each call creates a fresh [mlCopy] and atomically swaps [lastFrame],
     *     then recycles the OLD [lastFrame] (the one from TWO frames ago).
     *   - The ML loop snapshots [lastFrame] into a local `val frameBmp` on the
     *     coroutine's main-thread slice BEFORE entering Dispatchers.Default.
     *     That local val keeps the bitmap alive even after [lastFrame] is
     *     overwritten by the next analyze() call.
     *   - MediaPipe receives [finalBmp] directly (a separate object). It keeps
     *     that bitmap alive internally until its async processing completes.
     *
     * Result: each consumer (ML loop, MediaPipe) always holds its own bitmap.
     * No two threads ever share a native pixel buffer.
     */
    @Volatile var lastFrame: Bitmap? = null

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
        lastFrame?.recycle()
        lastFrame = null
    }

    fun analyze(imageProxy: ImageProxy, isFrontCamera: Boolean = true) {
        val lm = landmarker ?: run { imageProxy.close(); return }

        try {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val raw      = imageProxy.toBitmapNv21()

            val upright  = raw.rotate(rotation)
            if (upright !== raw) raw.recycle()

            val finalBmp = if (isFrontCamera) upright.mirrorX() else upright
            if (finalBmp !== upright) upright.recycle()

            lastW = finalBmp.width
            lastH = finalBmp.height

            // Create a fresh ARGB_8888 software copy exclusively for the ML loop.
            // IMPORTANT: do NOT recycle the old lastFrame here. The ML loop snapshots
            // lastFrame into a local `val frameBmp` before withContext(Dispatchers.Default),
            // but analyze() on analysisExecutor can still run concurrently with the
            // Default dispatcher executing cropEye() on that same bitmap.
            // Explicit recycling here = "cannot use a recycled source" crash.
            // Let GC collect old frames naturally once all references drop.
            lastFrame = finalBmp.copy(Bitmap.Config.ARGB_8888, false)

            // finalBmp's lifetime is now owned by MediaPipe's internal queue.
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

        val pts   = result.faceLandmarks()[0].map { it.x() to it.y() }
        val ptsPx = result.faceLandmarks()[0].map { it.x() * lastW to it.y() * lastH }

        val ear  = round2(computeAvgEar(ptsPx))
        val mar  = round2(computeMar(ptsPx))
        val tilt = round2(computeHeadTiltRollDeg(ptsPx))

        val faceBox     = boxFromIndices(pts, FACE_OVAL).padRelative(0.06f)
        val leftEyeBox  = boxFromIndices(pts, LEFT_EYE_TIGHT).padRelative(0.15f)
        val rightEyeBox = boxFromIndices(pts, RIGHT_EYE_TIGHT).padRelative(0.15f)
        val mouthBox    = boxFromIndices(pts, MOUTH_TIGHT).padRelative(0.08f)

        val bothEyesBox = NormRect(
            left   = minOf(leftEyeBox.left,   rightEyeBox.left),
            top    = minOf(leftEyeBox.top,     rightEyeBox.top),
            right  = maxOf(leftEyeBox.right,   rightEyeBox.right),
            bottom = maxOf(leftEyeBox.bottom,  rightEyeBox.bottom)
        )

        onResult(ExtractionResult(ear, mar, tilt, lastW, lastH,
            faceBox, leftEyeBox, rightEyeBox, bothEyesBox, mouthBox))
    }

    // ── Feature computations ──────────────────────────────────────────────────

    private fun computeAvgEar(pts: List<Pair<Float, Float>>): Float {
        fun earFor(p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: Int): Float {
            val (x1,y1)=pts[p1];val (x2,y2)=pts[p2];val (x3,y3)=pts[p3]
            val (x4,y4)=pts[p4];val (x5,y5)=pts[p5];val (x6,y6)=pts[p6]
            val a=dist(x2,y2,x6,y6); val b=dist(x3,y3,x5,y5); val c=dist(x1,y1,x4,y4)
            return if (c>1e-6f) (a+b)/(2f*c) else 0f
        }
        return (earFor(33,160,158,133,153,144) + earFor(362,385,387,263,373,380)) / 2f
    }

    private fun computeMar(pts: List<Pair<Float, Float>>): Float {
        val (xU,yU)=pts[13]; val (xL,yL)=pts[14]
        val (xLt,yLt)=pts[78]; val (xRt,yRt)=pts[308]
        val v=dist(xU,yU,xL,yL); val h=dist(xLt,yLt,xRt,yRt)
        return if (h>1e-6f) v/h else 0f
    }

    private fun computeHeadTiltRollDeg(pts: List<Pair<Float, Float>>): Float {
        val (xL,yL)=pts[33]; val (xR,yR)=pts[263]
        return (atan2((yR-yL),(xR-xL)) * 180f / Math.PI).toFloat()
    }

    private fun dist(x1:Float,y1:Float,x2:Float,y2:Float) =
        hypot((x2-x1).toDouble(),(y2-y1).toDouble()).toFloat()

    private fun round2(v:Float) = round(v*100f)/100f

    // ── Box helpers ───────────────────────────────────────────────────────────

    private fun boxFromIndices(pts: List<Pair<Float,Float>>, indices: IntArray): NormRect {
        var minX=1f; var minY=1f; var maxX=0f; var maxY=0f
        for (i in indices) { val (x,y)=pts[i]
            if(x<minX)minX=x; if(y<minY)minY=y; if(x>maxX)maxX=x; if(y>maxY)maxY=y }
        return NormRect(minX.coerceIn(0f,1f),minY.coerceIn(0f,1f),
            maxX.coerceIn(0f,1f),maxY.coerceIn(0f,1f))
    }

    private fun NormRect.padRelative(frac: Float): NormRect {
        val w=(right-left).coerceAtLeast(1e-6f); val h=(bottom-top).coerceAtLeast(1e-6f)
        val px=w*frac; val py=h*frac
        return NormRect((left-px).coerceIn(0f,1f),(top-py).coerceIn(0f,1f),
            (right+px).coerceIn(0f,1f),(bottom+py).coerceIn(0f,1f))
    }

    private fun NormRect.shiftY(delta: Float): NormRect {
        val t=(top+delta).coerceIn(0f,1f); val b=(bottom+delta).coerceIn(0f,1f)
        val h=bottom-top
        return if (b-t<h*0.5f) NormRect(left,(b-h).coerceIn(0f,1f),right,b)
        else             NormRect(left,t,right,b)
    }

    companion object {
        private val FACE_OVAL = intArrayOf(
            10,338,297,332,284,251,389,356,454,323,361,288,397,365,
            379,378,400,377,152,148,176,149,150,136,172,58,132,93,
            234,127,162,21,54,103,67,109)
        private val LEFT_EYE_TIGHT  = intArrayOf(33,133,160,158,153,144)
        private val RIGHT_EYE_TIGHT = intArrayOf(263,362,385,387,373,380)
        private val MOUTH_TIGHT     = intArrayOf(61,291,0,17,82,312,87,317)
    }
}

// ── ImageProxy helpers ────────────────────────────────────────────────────────

private fun ImageProxy.toBitmapNv21(): Bitmap {
    val yBuffer=planes[0].buffer; val uBuffer=planes[1].buffer; val vBuffer=planes[2].buffer
    val ySize=yBuffer.remaining(); val uSize=uBuffer.remaining(); val vSize=vBuffer.remaining()
    val nv21=ByteArray(ySize+uSize+vSize)
    yBuffer.get(nv21,0,ySize); vBuffer.get(nv21,ySize,vSize); uBuffer.get(nv21,ySize+vSize,uSize)
    val yuvImage=YuvImage(nv21,ImageFormat.NV21,width,height,null)
    val out=java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0,0,width,height),85,out)
    val bytes=out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes,0,bytes.size)
}

private fun Bitmap.rotate(deg: Int): Bitmap {
    if (deg==0) return this
    val m=Matrix().apply { postRotate(deg.toFloat()) }
    return Bitmap.createBitmap(this,0,0,width,height,m,true)
}

private fun Bitmap.mirrorX(): Bitmap {
    val m=Matrix().apply { postScale(-1f,1f); postTranslate(width.toFloat(),0f) }
    return Bitmap.createBitmap(this,0,0,width,height,m,true)
}