package com.shinhwa.accesslinktester.ui.face

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors

data class FaceDetectionState(
    val faceCount: Int = 0,
    val message: String = "카메라 준비 중",
    val embedding: FloatArray? = null
)

@Composable
fun FaceCameraView(
    modifier: Modifier = Modifier,
    enableRecognition: Boolean = false,
    onFaceState: (FaceDetectionState) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(context.hasCameraPermission())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            onFaceState(FaceDetectionState(message = "카메라 권한이 필요합니다"))
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasPermission) {
        Surface(
            modifier = modifier,
            color = Color(0xFF0E243C),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("카메라 권한이 필요합니다", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("권한 요청")
                }
            }
        }
        return
    }

    key(enableRecognition) {
        CameraPreviewWithAnalyzer(
            modifier = modifier,
            enableRecognition = enableRecognition,
            onFaceState = onFaceState
        )
    }
}

@Composable
private fun CameraPreviewWithAnalyzer(
    modifier: Modifier,
    enableRecognition: Boolean,
    onFaceState: (FaceDetectionState) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val recognitionEngine = remember(enableRecognition) {
        if (!enableRecognition) {
            null
        } else {
            runCatching { FaceRecognitionEngine(context.applicationContext) }
                .onFailure { Log.e("FaceCameraView", "Face recognition model load failed", it) }
                .getOrNull()
        }
    }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.18f)
                .enableTracking()
                .build()
        )
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            detector.close()
            recognitionEngine?.close()
            cameraExecutor.shutdown()
        }
    }

    if (lifecycleOwner == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("카메라 생명주기를 찾을 수 없습니다", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                analyzeFaceFrame(
                                    imageProxy = imageProxy,
                                    detector = detector,
                                    recognitionEngine = recognitionEngine,
                                    workerExecutor = cameraExecutor,
                                    mainExecutor = mainExecutor,
                                    onFaceState = onFaceState
                                )
                            }
                        }
                    val selector = CameraSelector.DEFAULT_FRONT_CAMERA

                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, analyzer)
                        onFaceState(FaceDetectionState(message = "얼굴 인식 대기 중"))
                    }.onFailure {
                        onFaceState(FaceDetectionState(message = "전면 카메라를 시작할 수 없습니다"))
                    }
                },
                ContextCompat.getMainExecutor(ctx)
            )
            previewView
        }
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeFaceFrame(
    imageProxy: ImageProxy,
    detector: com.google.mlkit.vision.face.FaceDetector,
    recognitionEngine: FaceRecognitionEngine?,
    workerExecutor: Executor,
    mainExecutor: Executor,
    onFaceState: (FaceDetectionState) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    detector.process(inputImage)
        .addOnSuccessListener(workerExecutor) { faces ->
            val message = when (faces.size) {
                0 -> "얼굴을 찾는 중"
                1 -> "얼굴 감지됨"
                else -> "얼굴이 여러 명 감지됨"
            }
            val embedding = if (faces.size == 1 && recognitionEngine != null) {
                runCatching {
                    val frameBitmap = imageProxy.toRotatedBitmap()
                    val faceBitmap = frameBitmap?.cropFace(faces.first().boundingBox)
                    faceBitmap?.let { recognitionEngine.getEmbedding(it) }
                }.onFailure {
                    Log.e("FaceCameraView", "Face embedding failed", it)
                }.getOrNull()
            } else {
                null
            }
            mainExecutor.execute {
                onFaceState(
                    FaceDetectionState(
                        faceCount = faces.size,
                        message = if (faces.size == 1 && recognitionEngine != null && embedding == null) {
                            "얼굴 특징값 생성 실패"
                        } else {
                            message
                        },
                        embedding = embedding
                    )
                )
            }
        }
        .addOnFailureListener(workerExecutor) {
            mainExecutor.execute {
                onFaceState(FaceDetectionState(message = "얼굴 검출 실패"))
            }
        }
        .addOnCompleteListener(workerExecutor) {
            imageProxy.close()
        }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
