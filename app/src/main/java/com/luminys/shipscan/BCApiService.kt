package com.luminys.shipscan

import android.util.Base64
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.io.Serializable

data class ShipmentData(
    val shipmentNo: String,
    val sellToCustomer: String,
    val sellToCustomerName: String,
    val salesOrder: String,
    val externalDocumentNo: String,
    val packageTrackingNo: String,
    val ediSentAt: String,
    val locationCode: String,
    val createdBy: String,
    val shipmentDate: String,
    val assignedUserId: String,
    val status: String,
    val upsAccountNumber: String,
    val shipping: ShippingInfo,
    val lines: List<LineItem>
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
    val estimatedDeliveryDate: String
) : Serializable

private data class UPSToken(
    val accessToken: String,
    val expiresAt: Long
)

object BCApiService {

    private const val BASE_URL = "http://192.168.0.25:3000/api"
    private const val UPS_AUTH_URL = "https://wwwcie.ups.com/security/v1/oauth/token"
    private const val UPS_SHIPPING_URL = "https://wwwcie.ups.com/api/shipments/v2409/ship"

    // Token caching
    private var cachedToken: UPSToken? = null

    suspend fun getShipment(shipmentNo: String): ShipmentData {
        return try {
            val url = URL("$BASE_URL/shipments/$shipmentNo")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseShipmentResponse(response)
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP $responseCode"
                throw Exception("Server error: $errorResponse")
            }

        } catch (e: Exception) {
            throw Exception("Network error: ${e.message}")
        }
    }

    suspend fun createShippingLabel(
        shipment: ShipmentData,
        packageDimensions: PackageDimensions,
    ): LabelResponse {
        return try {
            // Step 1: Get UPS Access Token (with caching)
            val accessToken = getUPSAccessToken()

            // Step 2: Create Shipping Label
            val url = URL(UPS_SHIPPING_URL)
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }

            // Create UPS Shipment Request
            val requestBody = JSONObject().apply {
                put("ShipmentRequest", JSONObject().apply {
                    put("Request", JSONObject().apply {
                        put("SubVersion", "1801")
                        put("RequestOption", "nonvalidate")
                        put("TransactionReference", JSONObject().apply {
                            put("CustomerContext", shipment.shipmentNo)
                        })
                    })

                    put("Shipment", JSONObject().apply {
                        put("Description", "Shipment ${shipment.shipmentNo}")

                        // Shipper info
                        put("Shipper", JSONObject().apply {
                            put("Name", "Your Company Name")
                            put("AttentionName", "Shipping Department")
                            put("TaxIdentificationNumber", "123456")
                            put("Phone", JSONObject().apply {
                                put("Number", "1234567890")
                                put("Extension", " ")
                            })
                            put("ShipperNumber", shipment.upsAccountNumber)
                            put("FaxNumber", "1234567890")
                            put("Address", JSONObject().apply {
                                put("AddressLine", listOf("123 Business Street"))
                                put("City", "Your City")
                                put("StateProvinceCode", "CA")
                                put("PostalCode", "90210")
                                put("CountryCode", "US")
                            })
                        })

                        // Ship To info from shipment data
                        put("ShipTo", JSONObject().apply {
                            put("Name", shipment.shipping.name)
                            put("AttentionName", shipment.shipping.contact.ifEmpty { shipment.shipping.name })
                            put("Phone", JSONObject().apply {
                                put("Number", shipment.shipping.phoneNo.ifEmpty { "0000000000" })
                            })
                            put("Address", JSONObject().apply {
                                val addressLines = mutableListOf<String>()
                                addressLines.add(shipment.shipping.address)
                                if (shipment.shipping.address2.isNotEmpty()) {
                                    addressLines.add(shipment.shipping.address2)
                                }
                                put("AddressLine", addressLines)
                                put("City", shipment.shipping.city)
                                put("StateProvinceCode", shipment.shipping.shipToState)
                                put("PostalCode", shipment.shipping.zipCode)
                                put("CountryCode", shipment.shipping.countryRegion)
                            })
                            put("Residential", " ")
                        })

                        // Ship From (same as shipper)
                        put("ShipFrom", JSONObject().apply {
                            put("Name", "Your Company Name")
                            put("AttentionName", "Shipping Department")
                            put("Phone", JSONObject().apply {
                                put("Number", "1234567890")
                            })
                            put("FaxNumber", "1234567890")
                            put("Address", JSONObject().apply {
                                put("AddressLine", listOf("123 Business Street"))
                                put("City", "Your City")
                                put("StateProvinceCode", "CA")
                                put("PostalCode", "90210")
                                put("CountryCode", "US")
                            })
                        })

                        // Payment Information
                        put("PaymentInformation", JSONObject().apply {
                            put("ShipmentCharge", JSONObject().apply {
                                put("Type", "01") // Bill Shipper
                                put("BillShipper", JSONObject().apply {
                                    put("AccountNumber", shipment.upsAccountNumber)
                                })
                            })
                        })

                        // Service
                        put("Service", JSONObject().apply {
                            put("Code", getUPSServiceCode(shipment.shipping.shipmentMethod.code))
                            put("Description", shipment.shipping.shipmentMethod.agentService)
                        })

                        // Package information
                        put("Package", JSONObject().apply {
                            put("Description", " ")
                            put("Packaging", JSONObject().apply {
                                put("Code", "02") // Customer Supplied Package
                                put("Description", "Package")
                            })
                            put("Dimensions", JSONObject().apply {
                                put("UnitOfMeasurement", JSONObject().apply {
                                    put("Code", "IN")
                                    put("Description", "Inches")
                                })
                                put("Length", packageDimensions.depth)
                                put("Width", packageDimensions.width)
                                put("Height", packageDimensions.height)
                            })
                            put("PackageWeight", JSONObject().apply {
                                put("UnitOfMeasurement", JSONObject().apply {
                                    put("Code", "LBS")
                                    put("Description", "Pounds")
                                })
                                put("Weight", packageDimensions.weight)
                            })
                        })
                    })

                    // Label Specification
                    put("LabelSpecification", JSONObject().apply {
                        put("LabelImageFormat", JSONObject().apply {
                            put("Code", "GIF")
                            put("Description", "GIF")
                        })
                        put("HTTPUserAgent", "ShipScan/1.0")
                    })
                })
            }

            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseUPSLabelResponse(response)
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP $responseCode"
                throw Exception("UPS API error: $errorResponse")
            }

        } catch (e: Exception) {
            throw Exception("Label creation error: ${e.message}")
        }
    }

    private suspend fun getUPSAccessToken(): String {
        // Check if we have a valid cached token
        cachedToken?.let { token ->
            if (System.currentTimeMillis() < token.expiresAt) {
                return token.accessToken
            }
        }

        // Get new token
        return try {
            val url = URL(UPS_AUTH_URL)
            val connection = url.openConnection() as HttpURLConnection

            val credentials = "${BuildConfig.UPS_CLIENT_ID}:${BuildConfig.UPS_CLIENT_SECRET}"
            val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Authorization", "Basic $encodedCredentials")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }

            val requestData = "grant_type=client_credentials"

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestData)
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val accessToken = json.getString("access_token")
                val expiresIn = json.optLong("expires_in", 3600) // Default 1 hour

                // Cache the token with expiration (subtract 5 minutes for safety margin)
                val expiresAt = System.currentTimeMillis() + ((expiresIn - 300) * 1000)
                cachedToken = UPSToken(accessToken, expiresAt)

                accessToken
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "HTTP $responseCode"
                throw Exception("UPS authentication failed: $errorResponse")
            }

        } catch (e: Exception) {
            throw Exception("UPS authentication error: ${e.message}")
        }
    }

    suspend fun updateShipmentTracking(shipmentNo: String, trackingNumber: String, shipDate: String): Boolean {
        return try {
            val url = URL("$BASE_URL/shipments/$shipmentNo/tracking")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val requestBody = JSONObject().apply {
                put("trackingNumber", trackingNumber)
                put("actualShipDate", shipDate)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            connection.responseCode == HttpURLConnection.HTTP_OK

        } catch (e: Exception) {
            false
        }
    }

    private fun getUPSServiceCode(shipmentMethodCode: String): String {
        return when (shipmentMethodCode.uppercase()) {
            "GROUND" -> "03"
            "NEXT_DAY_AIR" -> "01"
            "SECOND_DAY_AIR" -> "02"
            "THREE_DAY_SELECT" -> "12"
            "NEXT_DAY_AIR_SAVER" -> "13"
            "NEXT_DAY_AIR_EARLY_AM" -> "14"
            "SECOND_DAY_AIR_AM" -> "59"
            "EXPRESS_PLUS" -> "54"
            else -> "03" // Default to Ground
        }
    }

    private fun parseShipmentResponse(jsonString: String): ShipmentData {
        val json = JSONObject(jsonString)
        val data = json.getJSONObject("data")
        val shipping = data.getJSONObject("shipping")
        val shipmentMethod = shipping.getJSONObject("shipmentMethod")
        val linesArray = data.getJSONArray("lines")

        val lines = mutableListOf<LineItem>()
        for (i in 0 until linesArray.length()) {
            val lineJson = linesArray.getJSONObject(i)
            lines.add(
                LineItem(
                    itemNo = lineJson.getString("itemNo"),
                    modelName = lineJson.getString("modelName"),
                    description = lineJson.getString("description"),
                    quantity = lineJson.getInt("quantity"),
                    unitOfMeasureCode = lineJson.getString("unitOfMeasureCode")
                )
            )
        }

        return ShipmentData(
            shipmentNo = data.getString("shipmentNo"),
            sellToCustomer = data.getString("sellToCustomer"),
            sellToCustomerName = data.getString("sellToCustomerName"),
            salesOrder = data.getString("salesOrder"),
            externalDocumentNo = data.getString("externalDocumentNo"),
            packageTrackingNo = data.optString("packageTrackingNo", ""),
            ediSentAt = data.optString("ediSentAt", ""),
            locationCode = data.getString("locationCode"),
            createdBy = data.getString("createdBy"),
            shipmentDate = data.getString("shipmentDate"),
            assignedUserId = data.optString("assignedUserId", ""),
            status = data.getString("status"),
            upsAccountNumber = data.getString("upsAccountNumber"),
            shipping = ShippingInfo(
                addressCode = shipping.getString("addressCode"),
                name = shipping.getString("name"),
                address = shipping.getString("address"),
                address2 = shipping.optString("address2", ""),
                city = shipping.getString("city"),
                shipToState = shipping.getString("shipToState"),
                zipCode = shipping.getString("zipCode"),
                countryRegion = shipping.getString("countryRegion"),
                phoneNo = shipping.optString("phoneNo", ""),
                contact = shipping.optString("contact", ""),
                locationCode = shipping.optString("locationCode", ""),
                outboundWhseHandlingTime = shipping.optString("outboundWhseHandlingTime", ""),
                shippingTime = shipping.optString("shippingTime", ""),
                shipmentMethod = ShipmentMethod(
                    code = shipmentMethod.getString("code"),
                    agent = shipmentMethod.getString("agent"),
                    agentService = shipmentMethod.getString("agentService")
                )
            ),
            lines = lines
        )
    }

    private fun parseUPSLabelResponse(jsonString: String): LabelResponse {
        val json = JSONObject(jsonString)
        val shipmentResponse = json.getJSONObject("ShipmentResponse")
        val shipmentResults = shipmentResponse.getJSONObject("ShipmentResults")
        val packageResults = shipmentResults.getJSONArray("PackageResults").getJSONObject(0)

        val trackingNumber = packageResults.getString("TrackingNumber")
        val labelData = packageResults.getJSONObject("ShippingLabel").getString("GraphicImage")

        // Calculate estimated delivery date (this would typically come from UPS response)
        val estimatedDeliveryDate = "" // UPS doesn't always provide this in the response

        return LabelResponse(
            trackingNumber = trackingNumber,
            labelData = labelData,
            estimatedDeliveryDate = estimatedDeliveryDate
        )
    }
}