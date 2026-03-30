package com.example.DrowsyDriver.UserInterface

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.DrowsyDriver.extraction.FaceExtractionEngine
import com.example.DrowsyDriver.extraction.FaceExtractionEngine.NormRect
import com.example.DrowsyDriver.ml.EyeModel
import com.example.DrowsyDriver.ml.MouthModel
import com.example.DrowsyDriver.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs
import com.example.DrowsyDriver.alert.Alert
// ── ML model thresholds ───────────────────────────────────────────────────────
private const val EYE_CLOSED_THRESHOLD   = 0.85f   // model P(closed) per eye
private const val YAWN_THRESHOLD         = 0.85f   // model P(yawn)
private const val DROWSY_FRAMES_REQUIRED = 4        // consecutive frames before status flips

// ── Feature-extraction thresholds (session tracking only, not for status) ────
private const val EAR_CLOSED_TH        = 0.17f
private const val EAR_OPEN_TH          = 0.23f
private const val MAR_YAWN_TH          = 0.50f
private const val HEAD_TILT_TH         = 15f
private const val CLOSED_FRAME_TH      = 5         // frames before eyes count as closed
private const val OPEN_FRAME_TH        = 3         // frames before eyes count as open again

// ── Face-lost guard ───────────────────────────────────────────────────────────
private const val FACE_EXPIRY_MS = 500L

@Composable
fun CameraScreen(navController: NavController) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Camera permission ──────────────────────────────────────────────────────
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ── UI state ───────────────────────────────────────────────────────────────
    val uiState = remember { mutableStateOf(ExtractionUiState()) }

    // ── Debug overlay toggle ───────────────────────────────────────────────────
    var showBoxes by remember { mutableStateOf(false) }

    // ── Image dimensions for the canvas overlay ────────────────────────────────
    var imageW by remember { mutableIntStateOf(1) }
    var imageH by remember { mutableIntStateOf(1) }

    // ── Face boxes + tilt (updated by engine callback, read by ML loop) ────────
    var faceBox           by remember { mutableStateOf<NormRect?>(null) }
    var leftEyeBox        by remember { mutableStateOf<NormRect?>(null) }
    var rightEyeBox       by remember { mutableStateOf<NormRect?>(null) }
    var mouthBox          by remember { mutableStateOf<NormRect?>(null) }
    var headTiltDeg by remember { mutableFloatStateOf(0f) }
    var tiltSmoothed by remember { mutableFloatStateOf(0f) }
    var isCalibrated by remember { mutableStateOf(false) }
    var tiltBaseline by remember { mutableFloatStateOf(0f) }
    var baselineSet by remember { mutableStateOf(false) }
    var lastFaceTimestamp by remember { mutableLongStateOf(0L) }

    // ── Session tracking state (EAR-based, feeds SessionManager only) ──────────
    var earSmoothed          by remember { mutableStateOf(0f) }
    var closedFrameCount     by remember { mutableIntStateOf(0) }
    var openFrameCount       by remember { mutableIntStateOf(0) }
    var eyesAreClosed        by remember { mutableStateOf(false) }
    var eyesClosedStartTime  by remember { mutableStateOf<Long?>(null) }
    var headTiltStartTime    by remember { mutableStateOf<Long?>(null) }
    var blinkEarDown         by remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var isDrowsy by remember { mutableStateOf(false) } // used for alert
    var yawnStartTime by remember { mutableStateOf<Long?>(null) } // not being logged
    // ── FaceExtractionEngine ───────────────────────────────────────────────────
    val engine = remember(context) {
        FaceExtractionEngine(
            context        = context,
            modelAssetPath = "face_landmarker.task",
            onResult       = { res ->

                // ── Image / box state for canvas + ML crop ─────────────────────
                imageW            = res.imageWidth
                imageH            = res.imageHeight
                faceBox           = res.faceBox
                leftEyeBox        = res.leftEyeBox
                rightEyeBox       = res.rightEyeBox
                mouthBox          = res.mouthBox
                // Smooth
                tiltSmoothed =
                    if (tiltSmoothed == 0f) res.headTiltDeg
                    else 0.9f * tiltSmoothed + 0.1f * res.headTiltDeg

                // Only calculate tilt AFTER calibration
                if (isCalibrated) {
                    headTiltDeg = tiltSmoothed - tiltBaseline
                } else {
                    headTiltDeg = 0f
                }
                lastFaceTimestamp = System.currentTimeMillis()

                // Feature-extraction values shown in the overlay (status is set
                // by the ML inference loop below, not here).
                uiState.value = uiState.value.copy(
                    headTiltDeg = res.headTiltDeg,
                    ear         = res.ear,
                    mar         = res.mar
                )

                // ── EAR smoothing (exponential moving average) ─────────────────
                earSmoothed =
                    if (earSmoothed == 0f) res.ear
                    else 0.90f * earSmoothed + 0.10f * res.ear

                val now = System.currentTimeMillis()

                // ── Frame-based eye open/close confirmation ────────────────────
                // Using hysteresis (two separate thresholds) avoids rapid toggling
                // at the boundary.
                if (earSmoothed < EAR_CLOSED_TH) {
                    closedFrameCount++
                    openFrameCount = 0
                } else if (earSmoothed > EAR_OPEN_TH) {
                    openFrameCount++
                    closedFrameCount = 0
                }
                if (!eyesAreClosed && closedFrameCount >= CLOSED_FRAME_TH) eyesAreClosed = true
                if ( eyesAreClosed && openFrameCount  >= OPEN_FRAME_TH)   eyesAreClosed = false

                // ── Eye-closed duration → SessionManager ───────────────────────
                if (eyesAreClosed) {
                    if (eyesClosedStartTime == null) eyesClosedStartTime = now
                } else {
                    eyesClosedStartTime?.let { start ->
                        SessionManager.totalEyeClosedTime = SessionManager.totalEyeClosedTime + (now - start) / 1000f
                    }
                    eyesClosedStartTime = null
                }

                // ── Head-tilt duration → SessionManager ────────────────────────
                if (abs(res.headTiltDeg) > HEAD_TILT_TH) {
                    if (headTiltStartTime == null) headTiltStartTime = now
                } else {
                    headTiltStartTime?.let { start ->
                        SessionManager.totalHeadTiltTime = SessionManager.totalHeadTiltTime + (now - start) / 1000f
                    }
                    headTiltStartTime = null
                }

                // ── Frame counter ──────────────────────────────────────────────
                SessionManager.incrementFrame()

                // ── Yawn detection (duration-based) ───────────────────────
                // Only counts after MAR stays above threshold for 1500ms.
                // Rising-edge counting fired on any brief mouth open (talking,
                // coughing). Now the mouth must stay open for a full yawn duration.
                val isYawning = res.mar > MAR_YAWN_TH
                if (isYawning) {
                    if (yawnStartTime == null) yawnStartTime = now

                val isCurrentlyYawning = res.mar > MAR_YAWN_TH

                if (isCurrentlyYawning) {
                    if (yawnStartTime == null) {
                        yawnStartTime = now
                    }
                } else {
                    yawnStartTime?.let { start ->
                        if (now - start >= 1500L) {
                            SessionManager.incrementYawn()
                            SessionManager.logEvent("Yawn Detected")
                        }
                    }
                    yawnStartTime?.let { start ->
                        val duration = (now - start) / 1000f

                        // Only count as yawn if between 1–3 seconds
                        if (duration in 1f..5f) {
                            SessionManager.incrementYawn()
                            SessionManager.logEvent("Yawn Detected (${String.format("%.2f", duration)}s)")
                        }
                    }
                    yawnStartTime = null
                }

                // ── Blink detection ────────────────────────────────────────────
                // Uses raw EAR threshold crossings directly — faster than
                // eyesAreClosed which needs 5+3 confirmation frames and misses
                // most blinks before they complete.
                if (res.ear < EAR_CLOSED_TH) {
                    blinkEarDown = true
                } else if (blinkEarDown) {
                    blinkEarDown = false
                    SessionManager.incrementBlink()
                }

            }
        )
    }

    DisposableEffect(permissionGranted) {
        if (permissionGranted) {
            try { engine.start() } catch (t: Throwable) { t.printStackTrace() }
        }
        onDispose {
            try { engine.stop() } catch (_: Throwable) {}
            analysisExecutor.shutdown()
        }
    }

    // ── Load TFLite models ─────────────────────────────────────────────────────
    val eyeModel = remember {
        runCatching { EyeModel(context) }
            .onFailure { Log.e("TFLITE", "Failed to load eye model", it) }
            .getOrNull()
    }
    val mouthModel = remember {
        runCatching { MouthModel(context) }
            .onFailure { Log.e("TFLITE", "Failed to load mouth model", it) }
            .getOrNull()
    }

    DisposableEffect(Unit) {
        onDispose {
            try { eyeModel?.close()   } catch (_: Throwable) {}
            try { mouthModel?.close() } catch (_: Throwable) {}
        }
    }

    // ── ML inference loop (~5 fps) ─────────────────────────────────────────────
    var drowsyFrames by remember { mutableIntStateOf(0) }
    // Tracks the previous model status so we only log drowsy events on the
    // rising edge (Normal → Drowsy transition), not on every drowsy frame.
    var wasDrowsy    by remember { mutableStateOf(false) }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) return@LaunchedEffect

        while (true) {
            // Snapshot lastFrame before entering Dispatchers.Default — the local
            // val keeps the bitmap alive through the withContext suspend point.
            val frameBmp = engine.lastFrame
            val now      = System.currentTimeMillis()

            // If onResult hasn't fired recently the face has left the frame.
            // Null the boxes so we don't crop stale regions of an empty background.
            val facePresent = (now - lastFaceTimestamp) < FACE_EXPIRY_MS
            val mb   = if (facePresent) mouthBox    else null
            val leb  = if (facePresent) leftEyeBox  else null
            val reb  = if (facePresent) rightEyeBox else null
            val tilt = headTiltDeg

            // Reset immediately when face disappears
            if (!facePresent && drowsyFrames > 0) {
                drowsyFrames  = 0
                wasDrowsy     = false
                uiState.value = uiState.value.copy(
                    status   = "Normal",
                    eyeProb  = 0f,
                    yawnProb = 0f
                )
            }

            withContext(Dispatchers.Default) {
                if (frameBmp != null && facePresent) {

                    // ── Eye model ──────────────────────────────────────────────
                    var leftProb  = 0f
                    var rightProb = 0f

                    if (eyeModel != null) {
                        leb?.let { box ->
                            cropEye(frameBmp, box, tilt)?.also { crop ->
                                leftProb = eyeModel.predictClosedProb(crop)
                                crop.recycle()
                            }
                        }
                        reb?.let { box ->
                            cropEye(frameBmp, box, tilt)?.also { crop ->
                                rightProb = eyeModel.predictClosedProb(crop)
                                crop.recycle()
                            }
                        }
                    }

                    val eyeProb   = (leftProb + rightProb) / 2f
                    val eyeClosed = leftProb  > EYE_CLOSED_THRESHOLD &&
                            rightProb > EYE_CLOSED_THRESHOLD

                    Log.d("ML_EYE", "L=$leftProb R=$rightProb avg=$eyeProb closed=$eyeClosed tilt=$tilt")

                    // ── Mouth model ────────────────────────────────────────────
                    var yawnProb  = 0f
                    var mouthYawn = false

                    if (mouthModel != null) {
                        mb?.let { box ->
                            cropNormRect(frameBmp, box)?.also { crop ->
                                yawnProb  = mouthModel.predictYawnProb(crop)
                                mouthYawn = yawnProb > YAWN_THRESHOLD
                                crop.recycle()
                            }
                        }
                    }

                    // ── Drowsy decision ────────────────────────────────────────
                    drowsyFrames = if (eyeClosed || mouthYawn) drowsyFrames + 1 else 0
                    isDrowsy = drowsyFrames >= DROWSY_FRAMES_REQUIRED
                    val label    = if (isDrowsy) "Drowsy" else "Normal"

                    if (isDrowsy && !wasDrowsy) {
                        SessionManager.incrementDrowsy()
                        SessionManager.logEvent("Drowsiness Detected")
                    }
                    wasDrowsy = isDrowsy

                    uiState.value = uiState.value.copy(
                        status   = label,
                        eyeProb  = eyeProb,
                        yawnProb = yawnProb
                    )

                    Log.d("ML", "eye=$eyeProb closed=$eyeClosed yawn=$yawnProb mouth=$mouthYawn frames=$drowsyFrames → $label")
                }
            }

            delay(200)
        }
    }

    //--- Alert section
    LaunchedEffect(uiState.value, isDrowsy, headTiltStartTime, yawnStartTime, eyesClosedStartTime) {
        val now = System.currentTimeMillis()

        // Calculate how long these states have been active
        val eyeClosedDuration = eyesClosedStartTime?.let { now - it } ?: 0L
        val headTiltDuration  = headTiltStartTime?.let { now - it } ?: 0L
        val yawnDuration      = yawnStartTime?.let { now - it } ?: 0L

        val shouldAlert = Alert.checkAlert(
            eyeClosedDuration = eyeClosedDuration,
            isDrowsy          = (isDrowsy), // ML trigger
            yawnDuration      = yawnDuration,                       // Feature extraction trigger
            headTiltDuration  = headTiltDuration                    // Feature extraction trigger
        )
        if (shouldAlert) {
            Alert.triggerAlert(context)
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────
    // Black background so the status bar and nav bar areas are always dark,
    // even if the camera preview doesn't fully reach the very edges.
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {

        if (permissionGranted) {

            // No systemBarsPadding here — the preview fills edge to edge behind
            // the status bar and navigation bar for a true full-screen look.
            //
            // isCameraBound prevents the update block from calling unbindAll()
            // on every recomposition. Without this flag, every navigation back
            // to CameraScreen triggers unbindAll() which can free the camera
            // HAL's native image buffers while engine.analyze() is mid-read on
            // imageProxy.planes[0].buffer → SIGSEGV at 0x140.
            //
            // The update block still runs here (not DisposableEffect) because
            // the PreviewView must be attached to the window before
            // setSurfaceProvider() has a valid surface — DisposableEffect runs
            // too early and causes a black screen on first launch.
            var isCameraBound by remember { mutableStateOf(false) }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType          = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                update = { previewView ->
                    if (isCameraBound) return@AndroidView   // already bound — skip

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { ia ->
                                ia.setAnalyzer(analysisExecutor) { imageProxy ->
                                    engine.analyze(imageProxy, isFrontCamera = true)
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                analysis
                            )
                            isCameraBound = true
                        } catch (e: Exception) {
                            Log.e("CAMERA", "bindToLifecycle failed", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // Debug box overlay — drawn on top of the preview
            if (showBoxes) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (imageW <= 1 || imageH <= 1) return@Canvas

                    val imgWf   = imageW.toFloat()
                    val imgHf   = imageH.toFloat()
                    val scale   = maxOf(size.width / imgWf, size.height / imgHf)
                    val offsetX = (size.width  - imgWf * scale) / 2f
                    val offsetY = (size.height - imgHf * scale) / 2f

                    fun drawNormRect(r: NormRect?, strokePx: Float) {
                        if (r == null) return
                        val l  = r.left   * imgWf * scale + offsetX
                        val t  = r.top    * imgHf * scale + offsetY
                        val rr = r.right  * imgWf * scale + offsetX
                        val b  = r.bottom * imgHf * scale + offsetY
                        drawRect(
                            color    = Color(0xFF00FF00),
                            topLeft  = Offset(l, t),
                            size     = Size(rr - l, b - t),
                            style    = Stroke(width = strokePx)
                        )
                    }

                    drawNormRect(faceBox,     strokePx = 4f)
                    drawNormRect(leftEyeBox,  strokePx = 3f)
                    drawNormRect(rightEyeBox, strokePx = 3f)
                    drawNormRect(mouthBox,    strokePx = 3f)
                }
            }

        } else {
            Box(
                modifier         = Modifier.fillMaxSize().systemBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission is needed to show preview.")
            }
        }

        // Show / Hide Boxes — top-left
        Button(
            onClick  = { showBoxes = !showBoxes },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .systemBarsPadding(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (showBoxes) "Hide Boxes" else "Show Boxes")
        }

        // Session Data — top-right
        Button(
            onClick  = { navController.navigate("data_collection") },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .systemBarsPadding(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Session Data")
        }
        if (!isCalibrated) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        tiltBaseline = tiltSmoothed
                        isCalibrated = true
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Tap to Calibrate")
                }
            }
        }

        StatusPanel(
            state    = uiState.value,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .systemBarsPadding()
        )
    }
}

// ── Crop helpers ───────────────────────────────────────────────────────────────

/**
 * Square, deskewed 224×224 crop for a single eye.
 *
 * 1. Expands the landmark box to a square (eye boxes are ~2.5:1 landscape;
 *    squishing directly to 224×224 compresses open eyes until they look closed).
 * 2. Rotates by −headTiltDeg so the eye is always horizontal, matching training data.
 */
private fun cropEye(src: Bitmap, r: NormRect, headTiltDeg: Float): Bitmap? {
    val cx      = (r.left + r.right)  / 2f
    val cy      = (r.top  + r.bottom) / 2f
    val tiltAbs = kotlin.math.abs(headTiltDeg)

    // BUG FIX: in-place bitmap rotation fills output corners with black pixels
    // when the source square is too small to cover the rotated output.
    // At 30° tilt, 36% of the output corners become black — the model reads
    // these as closed eyelids and fires 100% drowsy even with eyes open.
    //
    // Required expansion factor for angle θ = |cosθ| + |sinθ|  (≈1.37 at 30°).
    // We use 1.5× as a fixed factor, which covers all tilts up to ~45° safely.
    // The extra background margin around the eye does not hurt model accuracy
    // because the model was trained on similarly padded crops.
    val padFactor = if (tiltAbs > 5f) 1.5f else 1.0f

    // FIX: halfSide must be computed in pixel space, not normalized space.
    // The old code did maxOf(norm_w, norm_h) then multiplied by src.width and
    // src.height separately — on a portrait 1080×1920 frame this made the crop
    // 1.78× taller than wide. Scaled to 224×224, open eyes looked closed to the
    // model → 100% drowsy at all tilt angles, worst at 30° with padFactor=1.5.
    val halfW_px = (r.right - r.left) * src.width  / 2f
    val halfH_px = (r.bottom - r.top) * src.height / 2f
    val halfSide = maxOf(halfW_px, halfH_px) * padFactor

    val cx_px = cx * src.width
    val cy_px = cy * src.height

    val left   = (cx_px - halfSide).toInt().coerceIn(0, src.width  - 1)
    val top    = (cy_px - halfSide).toInt().coerceIn(0, src.height - 1)
    val right  = (cx_px + halfSide).toInt().coerceIn(left + 1, src.width)
    val bottom = (cy_px + halfSide).toInt().coerceIn(top  + 1, src.height)
    val w = right - left
    val h = bottom - top
    if (w <= 2 || h <= 2) return null

    val cropped  = Bitmap.createBitmap(src, left, top, w, h)
    val deskewed = if (tiltAbs > 5f) {
        val m = Matrix().apply {
            postRotate(-headTiltDeg, cropped.width / 2f, cropped.height / 2f)
        }
        Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, m, true)
            .also { cropped.recycle() }
    } else cropped

    val scaled = Bitmap.createScaledBitmap(deskewed, 224, 224, true)
    if (scaled !== deskewed) deskewed.recycle()
    return scaled
}

/**
 * Plain axis-aligned 224×224 crop — used for the mouth box, which is already
 * approximately square and does not need deskewing.
 */
private fun cropNormRect(src: Bitmap, r: NormRect): Bitmap? {
    val left   = (r.left   * src.width ).toInt().coerceIn(0, src.width  - 1)
    val top    = (r.top    * src.height).toInt().coerceIn(0, src.height - 1)
    val right  = (r.right  * src.width ).toInt().coerceIn(left + 1, src.width)
    val bottom = (r.bottom * src.height).toInt().coerceIn(top  + 1, src.height)
    val w = right - left
    val h = bottom - top
    if (w <= 2 || h <= 2) return null

    val cropped = Bitmap.createBitmap(src, left, top, w, h)
    val scaled  = Bitmap.createScaledBitmap(cropped, 224, 224, true)
    if (scaled !== cropped) cropped.recycle()
    return scaled
}