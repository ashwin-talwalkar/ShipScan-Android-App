package com.luminys.shipscan

import java.io.Serializable

data class ShipmentData(
    // OData fields
    val odataContext: String = "",
    val odataEtag: String = "",

    // Core shipment fields
    val shipmentNo: String,
    val locationCode: String,
    val assignedUserId: String = "",
    val externalDocumentNo: String = "",
    val packageTrackingNo: String = "",
    val agentAccount: String = "",
    val shippingInstructionsText: String = "",

    // Package dimensions (from BC response)
    val packageLength: Double = 0.0,
    val packageWidth: Double = 0.0,
    val packageHeight: Double = 0.0,
    val packageWeight: Double = 0.0,

    // Ship to address fields (from BC response)
    val shipToName: String = "",
    val shipToName2: String = "",
    val shipToAddress: String = "",
    val shipToAddress2: String = "",
    val shipToCity: String = "",
    val shipToCounty: String = "", // Note: BC uses "County" for state
    val shipToPostCode: String = "",
    val shipToCountry: String = "",
    val shipToContact: String = "",

    // Legacy fields (for backward compatibility or custom API)
    val sellToCustomer: String = "",
    val sellToCustomerName: String = "",
    val salesOrder: String = "",
    val ediSentAt: String = "",
    val createdBy: String = "",
    val shipmentDate: String = "",
    val status: String = "",
    val upsAccountNumber: String = "",
    val shipping: ShippingInfo? = null,
    val lines: List<LineItem> = emptyList()
) : Serializable

data class ShippingInfo(
    val addressCode: String,
    val name: String,
    val address: String,
    val address2: String,
    val city: String,
    val shipToState: String,
    val zipCode: String,
    val countryRegion: String,
    val phoneNo: String,
    val contact: String,
    val locationCode: String,
    val outboundWhseHandlingTime: String,
    val shippingTime: String,
    val shipmentMethod: ShipmentMethod
) : Serializable

data class ShipmentMethod(
    val code: String,
    val agent: String,
    val agentService: String
) : Serializable

data class LineItem(
    val itemNo: String,
    val modelName: String,
    val description: String,
    val quantity: Int,
    val unitOfMeasureCode: String
) : Serializable

data class PackageDimensions(
    val height: String,
    val width: String,
    val depth: String,
    val weight: String
) : Serializable

data class LabelResponse(
    val trackingNumber: String,
    val labelData: String,
    val imageFormat: String,
    val shipmentIdentificationNumber: String,
    val billingWeight: String,
    val billingWeightUnit: String,
    val baseServiceCharge: String,
    val serviceOptionsCharge: String,
    val totalCharge: String,
    val currencyCode: String,
    val customerContext: String
) : Serializable


// Extension functions for ShipmentData
fun ShipmentData.getFormattedAddress(): String {
    return buildString {
        append(shipToName)
        if (shipToName2.isNotEmpty()) {
            append("\n$shipToName2")
        }
        append("\n$shipToAddress")
        if (shipToAddress2.isNotEmpty()) {
            append("\n$shipToAddress2")
        }
        append("\n$shipToCity, $shipToCounty $shipToPostCode")
        if (shipToCountry.isNotEmpty()) {
            append("\n$shipToCountry")
        }
    }
}

fun ShipmentData.hasPackageDimensions(): Boolean {
    return packageLength > 0 && packageWidth > 0 && packageHeight > 0 && packageWeight > 0
}

fun ShipmentData.getPackageDimensionsAsStrings(): PackageDimensions? {
    return if (hasPackageDimensions()) {
        PackageDimensions(
            height = packageHeight.toString(),
            width = packageWidth.toString(),
            depth = packageLength.toString(),
            weight = packageWeight.toString()
        )
    } else {
        null
    }
}