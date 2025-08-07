package com.luminys.shipscan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.widget.ImageButton

class ShipmentDetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShipmentDetails"
        const val EXTRA_BARCODE = "extra_barcode"
    }

    // Loading views
    private lateinit var layoutLoading: LinearLayout

    // Content views
    private lateinit var layoutContent: ScrollView
    private lateinit var tvShipmentNumber: TextView
    private lateinit var tvCustomerName: TextView
    private lateinit var tvSalesOrder: TextView
    private lateinit var tvShipToAddress: TextView

    // Package dimension views
    private lateinit var etPackageHeight: TextInputEditText
    private lateinit var etPackageWidth: TextInputEditText
    private lateinit var etPackageDepth: TextInputEditText
    private lateinit var etPackageWeight: TextInputEditText

    private lateinit var btnCreateLabel: Button
    private lateinit var btnBack: ImageButton

    // Error views
    private lateinit var layoutError: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var btnBackToScan: Button

    private var scannedBarcode: String = ""
    private var shipmentData: ShipmentData? = null
    private val dateFormatter = SimpleDateFormat("M/d/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_shipment_details)

        scannedBarcode = intent.getStringExtra(EXTRA_BARCODE) ?: ""

        initializeViews()
        setupClickListeners()

        if (scannedBarcode.isNotEmpty()) {
            loadShipmentData(scannedBarcode)
        } else {
            showError("No barcode provided")
        }
    }

    private fun initializeViews() {
        // Loading views
        layoutLoading = findViewById(R.id.layoutLoading)

        // Content views
        layoutContent = findViewById(R.id.layoutContent)
        tvShipmentNumber = findViewById(R.id.tvShipmentNumber)
        tvCustomerName = findViewById(R.id.tvCustomerName)
        tvSalesOrder = findViewById(R.id.tvSalesOrder)
        tvShipToAddress = findViewById(R.id.tvShipToAddress)

        // Package dimension views
        etPackageHeight = findViewById(R.id.etPackageHeight)
        etPackageWidth = findViewById(R.id.etPackageWidth)
        etPackageDepth = findViewById(R.id.etPackageDepth)
        etPackageWeight = findViewById(R.id.etPackageWeight)

        btnCreateLabel = findViewById(R.id.btnCreateLabel)
        btnBack = findViewById(R.id.btnBack)

        // Error views
        layoutError = findViewById(R.id.layoutError)
        btnRetry = findViewById(R.id.btnRetry)
        btnBackToScan = findViewById(R.id.btnBackToScan)


    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnRetry.setOnClickListener {
            loadShipmentData(scannedBarcode)
        }

        btnCreateLabel.setOnClickListener {
            createShippingLabel()
        }

        btnBackToScan.setOnClickListener {
            finish() // Go back to the previous activity (scanner)
        }
    }

    private fun loadShipmentData(barcode: String) {
        showLoading("Loading shipment details...")

        lifecycleScope.launch {
            try {
                val shipment = withContext(Dispatchers.IO) {
                    BCApiService.getShipment(barcode)
                }

                shipmentData = shipment
                showContent()
                populateShipmentData(shipment)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading shipment data", e)
                showError("Failed to load shipment: ${e.message}")
            }
        }
    }

    private fun populateShipmentData(shipment: ShipmentData) {
        tvShipmentNumber.text = "Shipment: ${shipment.shipmentNo}"
        tvCustomerName.text = shipment.shipToName

        if (shipment.externalDocumentNo.isNotEmpty()) {
            tvSalesOrder.text = "Ext Doc: ${shipment.externalDocumentNo}"
        } else {
            tvSalesOrder.text = "Document: N/A"
        }

        // Use the extension function for formatted address
        tvShipToAddress.text = shipment.getFormattedAddress()

        // Pre-populate package dimensions if available
        shipment.getPackageDimensionsAsStrings()?.let { dimensions ->
            etPackageHeight.setText(dimensions.height)
            etPackageWidth.setText(dimensions.width)
            etPackageDepth.setText(dimensions.depth)
            etPackageWeight.setText(dimensions.weight)
        }
    }

    private fun createShippingLabel() {
        val shipment = shipmentData ?: return

        // Validate package dimensions
        val packageDimensions = validateAndGetPackageDimensions() ?: return

        shipmentData = shipment.copy(
            packageLength = packageDimensions.depth.toDouble(),
            packageWidth = packageDimensions.width.toDouble(),
            packageHeight = packageDimensions.height.toDouble(),
            packageWeight = packageDimensions.weight.toDouble()
        )

        btnCreateLabel.isEnabled = false
        btnCreateLabel.text = "Creating Label..."

        lifecycleScope.launch {
            try {
                val labelResponse = withContext(Dispatchers.IO) {
                    BCApiService.createShippingLabel(
                        shipmentData!!,
                        packageDimensions
                    )
                }

                val intent = Intent(this@ShipmentDetailsActivity, LabelActivity::class.java).apply {
                    putExtra(LabelActivity.EXTRA_SHIPMENT_DATA, shipmentData!!)
                    putExtra(LabelActivity.EXTRA_LABEL_RESPONSE, labelResponse)
                    putExtra(LabelActivity.EXTRA_SHIP_DATE, getCurrentDateString())
                }
                startActivity(intent)

            } catch (e: Exception) {
                Log.e(TAG, "Error creating label", e)
                showToast("Failed to create label: ${e.message}")
            } finally {
                btnCreateLabel.isEnabled = true
                btnCreateLabel.text = "Create Shipping Label"
            }
        }
    }

    private fun validateAndGetPackageDimensions(): PackageDimensions? {
        val height = etPackageHeight.text.toString().trim()
        val width = etPackageWidth.text.toString().trim()
        val depth = etPackageDepth.text.toString().trim()
        val weight = etPackageWeight.text.toString().trim()

        // Validate all fields are filled
        if (height.isEmpty()) {
            etPackageHeight.error = "Height is required"
            etPackageHeight.requestFocus()
            return null
        }
        if (width.isEmpty()) {
            etPackageWidth.error = "Width is required"
            etPackageWidth.requestFocus()
            return null
        }
        if (depth.isEmpty()) {
            etPackageDepth.error = "Depth is required"
            etPackageDepth.requestFocus()
            return null
        }
        if (weight.isEmpty()) {
            etPackageWeight.error = "Weight is required"
            etPackageWeight.requestFocus()
            return null
        }

        // Validate numeric values
        try {
            val heightValue = height.toDouble()
            val widthValue = width.toDouble()
            val depthValue = depth.toDouble()
            val weightValue = weight.toDouble()

            if (heightValue <= 0) {
                etPackageHeight.error = "Height must be greater than 0"
                etPackageHeight.requestFocus()
                return null
            }
            if (widthValue <= 0) {
                etPackageWidth.error = "Width must be greater than 0"
                etPackageWidth.requestFocus()
                return null
            }
            if (depthValue <= 0) {
                etPackageDepth.error = "Depth must be greater than 0"
                etPackageDepth.requestFocus()
                return null
            }
            if (weightValue <= 0) {
                etPackageWeight.error = "Weight must be greater than 0"
                etPackageWeight.requestFocus()
                return null
            }

            return PackageDimensions(
                height = height,
                width = width,
                depth = depth,
                weight = weight
            )

        } catch (e: NumberFormatException) {
            showToast("Please enter valid numeric values for package dimensions")
            return null
        }
    }

    private fun getCurrentDateString(): String {
        return dateFormatter.format(Date())
    }

    private fun showLoading(message: String) {
        layoutLoading.visibility = View.VISIBLE
        layoutContent.visibility = View.GONE
        layoutError.visibility = View.GONE
    }

    private fun showContent() {
        layoutLoading.visibility = View.GONE
        layoutContent.visibility = View.VISIBLE
        layoutError.visibility = View.GONE
    }

    private fun showError(message: String) {
        layoutLoading.visibility = View.GONE
        layoutContent.visibility = View.GONE
        layoutError.visibility = View.VISIBLE
        showToast(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}