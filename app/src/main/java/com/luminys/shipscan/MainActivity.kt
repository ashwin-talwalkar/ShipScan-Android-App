package com.luminys.shipscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.FrameLayout

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val PROCESSING_COOLDOWN_MS = 2000L
    }

    private lateinit var previewView: PreviewView
    private lateinit var etBarcodeInput: EditText
    private lateinit var btnProcess: Button
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner

    private var isProcessing = false
    private var lastProcessedTime = 0L
    private var lastScannedBarcode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeCamera()
        setupClickListeners()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermissions()
        }
    }

    private fun initializeViews() {
        val cameraFrame = findViewById<FrameLayout>(R.id.cameraFrame)

        // Create PreviewView programmatically to avoid layout preview errors
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Clear any existing views and add the PreviewView
        cameraFrame.removeAllViews()
        cameraFrame.addView(previewView)

        etBarcodeInput = findViewById(R.id.etBarcodeInput)
        btnProcess = findViewById(R.id.btnProcess)
    }

    private fun initializeCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun setupClickListeners() {
        btnProcess.setOnClickListener {
            val barcodeText = etBarcodeInput.text.toString().trim()
            if (barcodeText.isNotEmpty()) {
                processBarcode(barcodeText)
            } else {
                showToast("Please enter a barcode or scan one")
            }
        }
    }

    private fun requestCameraPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (exc: Exception) {
                showToast("Camera initialization failed: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        try {
            cameraProvider.unbindAll()

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setImageQueueDepth(1)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer())

            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

        } catch (exc: Exception) {
            showToast("Camera binding failed: ${exc.message}")
        }
    }

    private inner class BarcodeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val currentTime = System.currentTimeMillis()

            // Skip if processing or in cooldown
            if (isProcessing || (currentTime - lastProcessedTime) < PROCESSING_COOLDOWN_MS) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes -> handleBarcodeSuccess(barcodes, currentTime) }
                    .addOnFailureListener { e -> Log.e(TAG, "Barcode scanning failed", e) }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun handleBarcodeSuccess(barcodes: List<Barcode>, currentTime: Long) {
        if (barcodes.isEmpty()) return

        // Take first valid barcode
        val barcode = barcodes.firstOrNull { it.displayValue != null || it.rawValue != null }

        if (barcode != null && !isProcessing) {
            val barcodeValue = barcode.displayValue ?: barcode.rawValue

            if (!barcodeValue.isNullOrEmpty() && barcodeValue != lastScannedBarcode) {
                isProcessing = true
                lastProcessedTime = currentTime
                lastScannedBarcode = barcodeValue

                runOnUiThread {
                    etBarcodeInput.setText(barcodeValue)
                    processBarcode(barcodeValue)

                    previewView.postDelayed({
                        isProcessing = false
                    }, PROCESSING_COOLDOWN_MS)
                }
            }
        }
    }

    // Update your processBarcode function in MainActivity.kt to navigate to ShipmentDetailsActivity

    private fun processBarcode(barcodeValue: String) {
        showToast("Barcode scanned: $barcodeValue")

        // Simple vibration feedback
        try {
            val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } catch (e: Exception) {
            // Vibration not available
        }

        // Navigate to ShipmentDetailsActivity
        val intent = Intent(this, ShipmentDetailsActivity::class.java).apply {
            putExtra(ShipmentDetailsActivity.EXTRA_BARCODE, barcodeValue)
        }
        startActivity(intent)
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                showToast("Camera permission is required to scan barcodes. Please grant permission in Settings.")
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isProcessing = false
        lastScannedBarcode = "" // Allow re-scanning same barcode
    }

    override fun onPause() {
        super.onPause()
        isProcessing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}