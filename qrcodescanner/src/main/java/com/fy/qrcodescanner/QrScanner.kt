package com.fy.qrcodescanner

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview.Builder
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class QrScanner(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor = Executors.newSingleThreadExecutor()
    private var currentListener: QrScannerListener? = null

    fun start(cameraView: PreviewView, listener: QrScannerListener) {
        currentListener = listener
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
                ?: return@addListener
            if (lifecycleOwner.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED) {
                return@addListener
            }

            val preview = Builder().build().also {
                it.setSurfaceProvider(cameraView.surfaceProvider)
            }

            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, { imageProxy ->
                        processImage(scanner, imageProxy)
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let {
                        currentListener?.onQrScanned(it)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Scan failed", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}