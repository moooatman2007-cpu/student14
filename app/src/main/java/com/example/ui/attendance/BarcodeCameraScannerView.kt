package com.example.ui.attendance

import android.Manifest
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalGetImage::class)
@ExperimentalPermissionsApi
@Composable
fun BarcodeCameraScannerView(
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        CameraPreviewScanner(
            onBarcodeScanned = onBarcodeScanned,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "نحتاج إلى السماح باستخدام الكاميرا\nلاستخدام الحضور بالباركود.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() }
                ) {
                    Text(text = "السماح بالكاميرا")
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreviewScanner(
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Configure ML Kit once specifically for Code 128 (and standard formats) with optimized speed
    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_CODE_128)
            .build()
    }
    val barcodeScanner: BarcodeScanner = remember { BarcodeScanning.getClient(options) }

    // Use Atomic references for debounce tracking (500ms debounce window) so updates do NOT trigger Compose recomposition
    val lastScannedCodeRef = remember { AtomicReference("") }
    val lastScannedTimeRef = remember { AtomicLong(0L) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraExecutor.shutdown()
                barcodeScanner.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val frameStartTime = System.currentTimeMillis()
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )

                                val mlStartTime = System.currentTimeMillis()
                                barcodeScanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        val mlDuration = System.currentTimeMillis() - mlStartTime
                                        if (barcodes.isNotEmpty() && com.example.BuildConfig.DEBUG) {
                                            android.util.Log.d("BarcodePerf", "Frame processed in ${System.currentTimeMillis() - frameStartTime}ms (ML Kit took ${mlDuration}ms)")
                                        }

                                        for (barcode in barcodes) {
                                            val rawValue = barcode.rawValue ?: continue
                                            if (rawValue.isNotBlank()) {
                                                val currentTime = System.currentTimeMillis()
                                                val lastCode = lastScannedCodeRef.get()
                                                val lastTime = lastScannedTimeRef.get()

                                                // 500ms strict debouncing for rapid scanning
                                                if (rawValue != lastCode || (currentTime - lastTime > 500)) {
                                                    lastScannedCodeRef.set(rawValue)
                                                    lastScannedTimeRef.set(currentTime)

                                                    if (com.example.BuildConfig.DEBUG) {
                                                        android.util.Log.d("BarcodePerf", "Barcode detected: $rawValue")
                                                    }

                                                    // Dispatch callback instantly on Main UI thread
                                                    CoroutineScope(Dispatchers.Main).launch {
                                                        currentOnBarcodeScanned(rawValue)
                                                    }
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        if (com.example.BuildConfig.DEBUG) {
                                            android.util.Log.e("BarcodePerf", "Barcode scan failure", e)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay target box
        Box(
            modifier = Modifier
                .size(240.dp, 100.dp)
                .border(2.dp, Color.Green.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .background(Color.Transparent)
        )
    }
}

