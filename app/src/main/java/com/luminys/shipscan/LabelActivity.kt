package com.luminys.shipscan

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintManager
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LabelActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LabelActivity"
        const val EXTRA_SHIPMENT_DATA = "extra_shipment_data"
        const val EXTRA_LABEL_RESPONSE = "extra_label_response"
        const val EXTRA_SHIP_DATE = "extra_ship_date"
    }

    // UI Components
    private lateinit var btnBack: Button
    private lateinit var tvShipmentNumber: TextView
    private lateinit var tvCustomerName: TextView
    private lateinit var tvTrackingNumber: TextView
    private lateinit var tvShipDate: TextView
    private lateinit var tvDeliveryDate: TextView
    private lateinit var ivLabelPreview: ImageView
    private lateinit var btnPrintLabel: Button
    private lateinit var btnUpdateBC: Button
    private lateinit var btnDone: Button
    private lateinit var layoutSuccess: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    private var shipmentData: ShipmentData? = null
    private var labelResponse: LabelResponse? = null
    private var shipDate: String = ""
    private var labelFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_label)

        // Get data from intent
        shipmentData = intent.getSerializableExtra(EXTRA_SHIPMENT_DATA) as? ShipmentData
        labelResponse = intent.getSerializableExtra(EXTRA_LABEL_RESPONSE) as? LabelResponse
        shipDate = intent.getStringExtra(EXTRA_SHIP_DATE) ?: ""

        initializeViews()
        setupClickListeners()

        if (shipmentData != null && labelResponse != null) {
            populateData()
            generateLabelPreview()
        } else {
            showError("Missing shipment or label data")
        }
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvShipmentNumber = findViewById(R.id.tvShipmentNumber)
        tvCustomerName = findViewById(R.id.tvCustomerName)
        tvTrackingNumber = findViewById(R.id.tvTrackingNumber)
        tvShipDate = findViewById(R.id.tvShipDate)
        tvDeliveryDate = findViewById(R.id.tvDeliveryDate)
        ivLabelPreview = findViewById(R.id.ivLabelPreview)
        btnPrintLabel = findViewById(R.id.btnPrintLabel)
        btnUpdateBC = findViewById(R.id.btnUpdateBC)
        btnDone = findViewById(R.id.btnDone)
        layoutSuccess = findViewById(R.id.layoutSuccess)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnPrintLabel.setOnClickListener {
            printLabel()
        }

        btnUpdateBC.setOnClickListener {
            updateBCSystem()
        }

        btnDone.setOnClickListener {
            // Go back to main activity
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun populateData() {
        val shipment = shipmentData ?: return
        val label = labelResponse ?: return

        tvShipmentNumber.text = "Shipment: ${shipment.shipmentNo}"
        tvCustomerName.text = shipment.sellToCustomerName
        tvTrackingNumber.text = "Tracking: ${label.trackingNumber}"
        tvShipDate.text = "Ship Date: $shipDate"

        // Only set delivery date if it's not empty
        if (label.estimatedDeliveryDate.isNotEmpty()) {
            tvDeliveryDate.text = "Est. Delivery: ${label.estimatedDeliveryDate}"
        } else {
            tvDeliveryDate.text = "Est. Delivery: TBD"
        }
    }

    private fun generateLabelPreview() {
        val label = labelResponse ?: return

        lifecycleScope.launch {
            try {
                showStatus("Loading label preview...")

                withContext(Dispatchers.IO) {
                    // Decode the base64 data
                    val imageBytes = Base64.decode(label.labelData, Base64.DEFAULT)

                    // Check if it's a PDF or GIF
                    val isPdf = imageBytes.size > 4 &&
                            imageBytes.sliceArray(0..3).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46)) // %PDF

                    if (isPdf) {
                        // It's already a PDF
                        val tempFile = File(cacheDir, "temp_label.pdf")
                        FileOutputStream(tempFile).use { fos ->
                            fos.write(imageBytes)
                        }
                        labelFile = tempFile
                    } else {
                        // It's a GIF (or other image format), convert to PDF
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            labelFile = convertBitmapToPdf(bitmap, "temp_label.pdf")
                        } else {
                            throw Exception("Could not decode image data")
                        }
                    }
                }

                // Display the image in ImageView
                val imageBytes = Base64.decode(label.labelData, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                if (bitmap != null) {
                    ivLabelPreview.setImageBitmap(bitmap)
                    ivLabelPreview.visibility = View.VISIBLE
                    hideStatus()
                } else {
                    showPlaceholderLabel()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error generating label preview", e)
                showPlaceholderLabel()
            }
        }
    }

    private fun convertBitmapToPdf(bitmap: Bitmap, fileName: String): File {
        val pdfDocument = PdfDocument()

        // Create page info with bitmap dimensions
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()

        // Start a page
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Draw bitmap on the PDF page
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Finish the page
        pdfDocument.finishPage(page)

        // Write the PDF to file
        val file = File(cacheDir, fileName)
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }

        // Close the document
        pdfDocument.close()

        Log.d(TAG, "Converted GIF to PDF: ${file.absolutePath}")
        return file
    }

    private fun showPlaceholderLabel() {
        hideStatus()
        ivLabelPreview.visibility = View.VISIBLE
        // You can set a placeholder image here
        ivLabelPreview.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))

        // Create a simple text overlay for the placeholder
        val label = labelResponse ?: return
        showToast("Label generated successfully - ${label.trackingNumber}")
    }

    private fun printLabel() {
        val label = labelResponse ?: return

        try {
            val printManager = getSystemService(PRINT_SERVICE) as PrintManager

            if (labelFile != null && labelFile!!.exists()) {
                val printAdapter = PDFPrintAdapter(this, labelFile!!, "Shipping_Label_${label.trackingNumber}")

                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                    .setResolution(PrintAttributes.Resolution("pdf", PRINT_SERVICE, 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()

                val printJob: PrintJob = printManager.print(
                    "Shipping Label ${label.trackingNumber}",
                    printAdapter,
                    printAttributes
                )

                showToast("Printing label...")

            } else {
                showToast("Label file not available for printing")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error printing label", e)
            showToast("Print failed: ${e.message}")
        }
    }

    private fun updateBCSystem() {
        val shipment = shipmentData ?: return
        val label = labelResponse ?: return

        btnUpdateBC.isEnabled = false
        showStatus("Updating BC system...")

        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    BCApiService.updateShipmentTracking(
                        shipment.shipmentNo,
                        label.trackingNumber,
                        shipDate
                    )
                }

                if (success) {
                    hideStatus()
                    layoutSuccess.visibility = View.VISIBLE
                    showToast("BC system updated successfully!")
                } else {
                    showToast("Failed to update BC system")
                    btnUpdateBC.isEnabled = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating BC", e)
                showToast("Update failed: ${e.message}")
                btnUpdateBC.isEnabled = true
                hideStatus()
            }
        }
    }

    private fun showStatus(message: String) {
        progressBar.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = message
    }

    private fun hideStatus() {
        progressBar.visibility = View.GONE
        tvStatus.visibility = View.GONE
    }

    private fun showError(message: String) {
        showToast(message)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up temp files
        labelFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
}