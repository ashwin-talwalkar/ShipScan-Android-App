package com.luminys.shipscan

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.print.PrintAttributes
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
    private lateinit var btnBack: ImageButton
    private lateinit var tvShipmentNumber: TextView
    private lateinit var tvCustomerName: TextView
    private lateinit var tvTrackingNumber: TextView
    private lateinit var tvShipDate: TextView
    private lateinit var tvBillingWeight: TextView
    private lateinit var tvTotalCharge: TextView
    private lateinit var ivLabelPreview: ImageView
    private lateinit var btnPrintLabel: Button
    private lateinit var btnUpdateBC: Button
    private lateinit var btnDone: Button
    private lateinit var btnRetry: Button
    private lateinit var layoutSuccess: LinearLayout
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutError: LinearLayout
    private lateinit var layoutContent: ScrollView
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
            showErrorState("Missing shipment or label data")
        }
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvShipmentNumber = findViewById(R.id.tvShipmentNumber)
        tvCustomerName = findViewById(R.id.tvCustomerName)
        tvTrackingNumber = findViewById(R.id.tvTrackingNumber)
        tvShipDate = findViewById(R.id.tvShipDate)
        tvBillingWeight = findViewById(R.id.tvBillingWeight)
        tvTotalCharge = findViewById(R.id.tvTotalCharge)
        ivLabelPreview = findViewById(R.id.ivLabelPreview)
        btnPrintLabel = findViewById(R.id.btnPrintLabel)
        btnUpdateBC = findViewById(R.id.btnUpdateBC)
        btnDone = findViewById(R.id.btnDone)
        btnRetry = findViewById(R.id.btnRetry)
        layoutSuccess = findViewById(R.id.layoutSuccess)
        layoutLoading = findViewById(R.id.layoutLoading)
        layoutError = findViewById(R.id.layoutError)
        layoutContent = findViewById(R.id.layoutContent)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        btnPrintLabel.setOnClickListener { printLabel() }

        btnUpdateBC.setOnClickListener { updateBCSystem() }

        btnDone.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        btnRetry.setOnClickListener {
            showLoading()
            generateLabelPreview()
        }
    }

    private fun populateData() {
        val shipment = shipmentData ?: return
        val label = labelResponse ?: return

        tvShipmentNumber.text = "Shipment: ${shipment.shipmentNo}"
        tvCustomerName.text = "Ship To: ${shipment.shipToName}"
        tvTrackingNumber.text = "Tracking: ${label.trackingNumber}"
        tvShipDate.text = "Ship Date: $shipDate"


        tvBillingWeight.text = "Billing Weight: ${label.billingWeight} LBS"
        tvTotalCharge.text = "Total Charge: $${label.totalCharge} USD"
    }

    private fun generateLabelPreview() {
        val label = labelResponse ?: return

        lifecycleScope.launch {
            try {
                showStatus("Loading label preview...")

                withContext(Dispatchers.IO) {
                    val imageBytes = Base64.decode(label.labelData, Base64.DEFAULT)

                    val isPdf = imageBytes.size > 4 &&
                            imageBytes.sliceArray(0..3).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))

                    labelFile = if (isPdf) {
                        val tempFile = File(cacheDir, "temp_label.pdf")
                        FileOutputStream(tempFile).use { it.write(imageBytes) }
                        tempFile
                    } else {
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            convertBitmapToPdf(bitmap, "temp_label.pdf")
                        } else throw Exception("Could not decode image data")
                    }
                }

                val bitmap = BitmapFactory.decodeByteArray(
                    Base64.decode(label.labelData, Base64.DEFAULT),
                    0,
                    Base64.decode(label.labelData, Base64.DEFAULT).size
                )

                if (bitmap != null) {
                    ivLabelPreview.setImageBitmap(bitmap)
                    ivLabelPreview.visibility = View.VISIBLE
                    showContent()
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
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val file = File(cacheDir, fileName)
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file
    }

    private fun showPlaceholderLabel() {
        ivLabelPreview.visibility = View.VISIBLE
        ivLabelPreview.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
        showToast("Label generated successfully - ${labelResponse?.trackingNumber}")
        showContent()
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

                printManager.print("Shipping Label ${label.trackingNumber}", printAdapter, printAttributes)
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

    private fun showErrorState(message: String) {
        layoutError.visibility = View.VISIBLE
        layoutContent.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        showToast(message)
    }

    private fun showLoading() {
        layoutLoading.visibility = View.VISIBLE
        layoutContent.visibility = View.GONE
        layoutError.visibility = View.GONE
    }

    private fun showContent() {
        layoutContent.visibility = View.VISIBLE
        layoutLoading.visibility = View.GONE
        layoutError.visibility = View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        labelFile?.let { if (it.exists()) it.delete() }
    }
}
