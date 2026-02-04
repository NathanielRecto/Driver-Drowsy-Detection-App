package com.example.DrowsyDriver.UserInterface

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import java.util.concurrent.Executors

import com.example.DrowsyDriver.extraction.FaceExtractionEngine
import com.example.DrowsyDriver.extraction.FaceExtractionEngine.NormRect

// 👇 ADD THESE HERE (file-level constants)
private const val EAR_CLOSED_TH = 0.10f
private const val MAR_YAWN_TH = 0.50f

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val uiState = remember {
        mutableStateOf(
            ExtractionUiState(
                headTiltDeg = 0f,
                yawnDeg = 0f,
                eyesClosedSec = 0f,
                mar = 0f,
                ear = 0f,
                status = "Normal"
            )
        )
    }

    // For correct mapping
    var imageW by remember { mutableStateOf(1) }
    var imageH by remember { mutableStateOf(1) }

    // Boxes from extraction
    var faceBox by remember { mutableStateOf<NormRect?>(null) }
    var leftEyeBox by remember { mutableStateOf<NormRect?>(null) }
    var rightEyeBox by remember { mutableStateOf<NormRect?>(null) }
    var mouthBox by remember { mutableStateOf<NormRect?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val engine = remember(context) {
        FaceExtractionEngine(
            context = context,
            modelAssetPath = "face_landmarker.task",
            onResult = { res ->
                imageW = res.imageWidth
                imageH = res.imageHeight

                faceBox = res.faceBox
                leftEyeBox = res.leftEyeBox
                rightEyeBox = res.rightEyeBox
                mouthBox = res.mouthBox

                val statusNow = if (res.ear < EAR_CLOSED_TH || res.mar > MAR_YAWN_TH)
                    "Drowsy"
                else
                    "Normal"

                uiState.value = uiState.value.copy(
                    ear = res.ear,
                    mar = res.mar,
                    headTiltDeg = res.headTiltDeg,
                    status = statusNow
                )
            }
        )
    }

    DisposableEffect(permissionGranted) {
        if (permissionGranted) {
            try { engine.start() } catch (t: Throwable) { t.printStackTrace() }
        }
        onDispose {
            engine.stop()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (permissionGranted) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
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
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // ✅ Green boxes overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (imageW <= 1 || imageH <= 1) return@Canvas

                val imgWf = imageW.toFloat()
                val imgHf = imageH.toFloat()

                // Center-crop mapping to match PreviewView.FILL_CENTER
                val scale = maxOf(size.width / imgWf, size.height / imgHf)
                val offsetX = (size.width - imgWf * scale) / 2f
                val offsetY = (size.height - imgHf * scale) / 2f

                val mirrorOverlay = true

                fun drawNormRect(r: NormRect?, stroke: Float) {
                    if (r == null) return

                    val lN = if (mirrorOverlay) (1f - r.right) else r.left
                    val rN = if (mirrorOverlay) (1f - r.left) else r.right
                    val tN = r.top
                    val bN = r.bottom

                    val left = r.left * imgWf * scale + offsetX
                    val top = r.top * imgHf * scale + offsetY
                    val right = r.right * imgWf * scale + offsetX
                    val bottom = r.bottom * imgHf * scale + offsetY

                    drawRect(
                        color = Color(0xFF00FF00),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = stroke)
                    )
                }

                // Face big box, eyes + mouth smaller boxes
                drawNormRect(faceBox, stroke = 4f)
                drawNormRect(leftEyeBox, stroke = 3f)
                drawNormRect(rightEyeBox, stroke = 3f)
                drawNormRect(mouthBox, stroke = 3f)
            }

        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission is needed to show preview.")
            }
        }

        Button(
            onClick = { navController.navigate("data_collection") },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .systemBarsPadding(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Session Data")
        }

        StatusPanel(
            state = uiState.value,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .systemBarsPadding()
        )
    }
}
