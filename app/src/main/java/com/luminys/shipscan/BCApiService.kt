    package com.luminys.shipscan
    
    import android.util.Base64
    import org.json.JSONObject
    import java.io.OutputStreamWriter
    import java.net.HttpURLConnection
    import java.net.URL
    import org.json.JSONArray

    private data class BCToken(
        val accessToken: String,
        val expiresAt: Long
    )
    
    private data class UPSToken(
        val accessToken: String,
        val expiresAt: Long
    )
    
    object BCApiService {
    
        private const val BC_BASE_URL = "https://api.businesscentral.dynamics.com/v2.0"
        private const val ENVIRONMENT = "Lmy0610"
        private const val COMPANY_ID = "0d85027a-3432-ef11-8409-002248ab1716"
    
        private const val UPS_AUTH_URL = "https://wwwcie.ups.com/security/v1/oauth/token" //testing url
        // prod UPS_AUTH_URL = "https://onlinetools.ups.com/security/v1/oauth/token"
        private const val UPS_SHIPPING_URL = "https://wwwcie.ups.com/api/shipments/v2409/ship" //testing url
        //prod UPS_AUTH_URL = "https://onlinetools.ups.com/api/shipments/v2409/ship


        // Token caching
        private var cachedBCToken: BCToken? = null
        private var cachedUPSToken: UPSToken? = null
    
        suspend fun getShipment(shipmentNo: String): ShipmentData {
            return try {
                val accessToken = getBCAccessToken()
    
                val apiUrl = "$BC_BASE_URL/${BuildConfig.TENANT_ID}/$ENVIRONMENT/api/art/integration/v1.0/companies($COMPANY_ID)/warehousesshipments('$shipmentNo')"
                println("API URL: $apiUrl") // Debug log
    
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
    
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
    
                val responseCode = connection.responseCode
                println("Response Code: $responseCode") // Debug log
    
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    println("Success Response: $response") // Debug log
                    parseShipmentResponse(response)
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error details available"
    
                    println("Error Response Code: $responseCode") // Debug log
                    println("Error Response Body: $errorResponse") // Debug log
    
                    // More specific error messages based on response code
                    val errorMessage = when (responseCode) {
                        HttpURLConnection.HTTP_UNAUTHORIZED -> "Authentication failed - check BC credentials"
                        HttpURLConnection.HTTP_FORBIDDEN -> "Access forbidden - check permissions"
                        HttpURLConnection.HTTP_NOT_FOUND -> "Shipment '$shipmentNo' not found or API endpoint incorrect"
                        HttpURLConnection.HTTP_BAD_REQUEST -> "Bad request - check shipment number format: $errorResponse"
                        HttpURLConnection.HTTP_INTERNAL_ERROR -> "Business Central server error: $errorResponse"
                        else -> "HTTP $responseCode: $errorResponse"
                    }
    
                    throw Exception("Server error: $errorMessage")
                }
    
            } catch (e: Exception) {
                println("Exception caught: ${e.message}") // Debug log
                e.printStackTrace() // Full stack trace
    
                if (e.message?.contains("Server error") == true) {
                    throw e // Re-throw server errors as-is
                } else {
                    throw Exception("Network error: ${e.message}")
                }
            }
        }
    
        private suspend fun getBCAccessToken(): String {
            // Check if we have a valid cached token
            cachedBCToken?.let { token ->
                if (System.currentTimeMillis() < token.expiresAt) {
                    return token.accessToken
                }
            }
    
            // Get new token
            return try {
                val tokenUrl = "https://login.microsoftonline.com/${BuildConfig.TENANT_ID}/oauth2/v2.0/token"
                println("Token URL: $tokenUrl") // Debug log
    
                val url = URL(tokenUrl)
                val connection = url.openConnection() as HttpURLConnection
    
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 30000
                }
    
                val requestData = buildString {
                    append("grant_type=client_credentials")
                    append("&client_id=${BuildConfig.BC_CLIENT_ID}")
                    append("&client_secret=${BuildConfig.BC_CLIENT_SECRET}")
                    append("&scope=${BuildConfig.BC_SCOPE}")
                }
    
                println("Auth request data: grant_type=client_credentials&client_id=${BuildConfig.BC_CLIENT_ID}&client_secret=***&scope=${BuildConfig.BC_SCOPE}") // Debug (hide secret)
    
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestData)
                    writer.flush()
                }
    
                val responseCode = connection.responseCode
                println("Auth Response Code: $responseCode") // Debug log
    
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    println("Auth Success Response: $response") // Debug log
    
                    val json = JSONObject(response)
                    val accessToken = json.getString("access_token")
                    val expiresIn = json.optLong("expires_in", 3600) // Default 1 hour
    
                    // Cache the token with expiration (subtract 5 minutes for safety margin)
                    val expiresAt = System.currentTimeMillis() + ((expiresIn - 300) * 1000)
                    cachedBCToken = BCToken(accessToken, expiresAt)
    
                    accessToken
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error details available"
    
                    println("Auth Error Response: $errorResponse") // Debug log
    
                    val errorMessage = when (responseCode) {
                        HttpURLConnection.HTTP_BAD_REQUEST -> {
                            "Invalid authentication request. Check your BC_CLIENT_ID, BC_CLIENT_SECRET, and TENANT_ID in gradle.properties: $errorResponse"
                        }
                        HttpURLConnection.HTTP_UNAUTHORIZED -> {
                            "Authentication failed. Verify BC_CLIENT_ID and BC_CLIENT_SECRET are correct: $errorResponse"
                        }
                        else -> "HTTP $responseCode: $errorResponse"
                    }
    
                    throw Exception("BC authentication failed: $errorMessage")
                }
    
            } catch (e: Exception) {
                println("Auth Exception: ${e.message}") // Debug log
                e.printStackTrace()
                throw Exception("BC authentication error: ${e.message}")
            }
        }
    
        private fun getAppropriateUPSService(shipment: ShipmentData): Pair<String, String> {
            val destinationState = shipment.shipToCounty.uppercase()
            val destinationCountry = shipment.shipToCountry.uppercase()
    
            return when {
                // Puerto Rico - requires Air services, Ground doesn't work
                destinationState == "PR" -> {
                    Pair("01", "UPS Next Day Air")
                }
    
                // Alaska and Hawaii - require specific services
                destinationState == "AK" -> {
                    Pair("02", "UPS 2nd Day Air") // Ground doesn't work to Alaska
                }
    
                destinationState == "HI" -> {
                    Pair("02", "UPS 2nd Day Air") // Ground doesn't work to Hawaii
                }
    
                // Other US territories
                destinationState in listOf("VI", "GU", "AS", "MP") -> {
                    Pair("02", "UPS 2nd Day Air") // Air service for territories
                }
    
                // International shipments (non-US country)
                destinationCountry != "US" && destinationCountry != "USA" && destinationCountry.isNotEmpty() -> {
                    Pair("11", "UPS Standard") // International service
                }
    
                // Continental US (48 states + DC)
                destinationCountry in listOf("US", "USA", "") -> {
                    // Use Ground for continental US
                    val serviceCode = when {
                        shipment.shipping?.shipmentMethod?.code?.isNotEmpty() == true ->
                            shipment.shipping.shipmentMethod.code
                        else -> "GROUND"
                    }
                    Pair(getUPSServiceCode(serviceCode), getServiceDescription(serviceCode))
                }
    
                // Fallback - use 2nd Day Air as most reliable
                else -> {
                    Pair("02", "UPS 2nd Day Air")
                }
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

                // Add this debugging RIGHT BEFORE creating the requestBody
                println("=== DEBUGGING SHIPMENT DATA ===")
                println("shipToName: '${shipment.shipToName}' (length: ${shipment.shipToName.length})")
                println("shipToContact: '${shipment.shipToContact}' (length: ${shipment.shipToContact.length})")
                println("shipToAddress: '${shipment.shipToAddress}' (length: ${shipment.shipToAddress.length})")
                println("shipToAddress2: '${shipment.shipToAddress2}' (length: ${shipment.shipToAddress2.length})")
                println("shipToCity: '${shipment.shipToCity}' (length: ${shipment.shipToCity.length})")
                println("shipToCounty: '${shipment.shipToCounty}' (length: ${shipment.shipToCounty.length})")
                println("shipToPostCode: '${shipment.shipToPostCode}' (length: ${shipment.shipToPostCode.length})")
                println("shipToCountry: '${shipment.shipToCountry}' (length: ${shipment.shipToCountry.length})")
                println("upsAccountNumber: '${shipment.upsAccountNumber}' (length: ${shipment.upsAccountNumber.length})")
                println("===============================")
    
    
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
                                put("Name", "Luminys Systems Corporation")
                                put("ShipperNumber", BuildConfig.UPS_ACCOUNT_NUMBER)
                                put("Address", JSONObject().apply {
                                    put("AddressLine", listOf("15245 Alton Pkwy"))
                                    put("City", "Irvine")
                                    put("StateProvinceCode", "CA")
                                    put("PostalCode", "92618")
                                    put("CountryCode", "US")
                                })
                            })

                            // Ship To info from shipment data
                            put("ShipTo", JSONObject().apply {
                                put("Name", shipment.shipToName.ifEmpty { "Unknown" })
                                put("AttentionName", shipment.shipToContact.ifEmpty { shipment.shipToName.ifEmpty { "Unknown" } })
                                put("Address", JSONObject().apply {
                                    // Create JSONArray directly
                                    val addressLinesArray = JSONArray().apply {
                                        if (shipment.shipToAddress.isNotEmpty()) {
                                            put(shipment.shipToAddress)
                                        }
                                        if (shipment.shipToAddress2.isNotEmpty()) {
                                            put(shipment.shipToAddress2)
                                        }

                                        // Ensure we have at least one address line
                                        if (length() == 0) {
                                            put("Address Not Provided")
                                        }
                                    }

                                    put("AddressLine", addressLinesArray)
                                    put("City", shipment.shipToCity)
                                    put("StateProvinceCode", shipment.shipToCounty)
                                    put("PostalCode", shipment.shipToPostCode)
                                    put("CountryCode", shipment.shipToCountry)
                                })

                                // Log the complete ShipTo JSON
                                println("Complete ShipTo JSON: ${this.toString()}")
                                println("========================")
                            })

                            // Ship From (same as shipper)
                            put("ShipFrom", JSONObject().apply {
                                put("Name", "Luminys Systems Corporation")
                                put("AttentionName", "Shipping Department")
                                put("Phone", JSONObject().apply {
                                    put("Number", "9496553999")
                                })
                                put("FaxNumber", "9496553999")
                                put("Address", JSONObject().apply {
                                    put("AddressLine", listOf("64 Fairbanks"))
                                    put("City", "Irvine")
                                    put("StateProvinceCode", "CA")
                                    put("PostalCode", "92618")
                                    put("CountryCode", "US")
                                })
                            })

                            // Payment Information - WORKING VERSION
                            put("PaymentInformation", JSONObject().apply {
                                put("ShipmentCharge", JSONObject().apply {
                                    put("Type", "01")

                                    // Check if customer has UPS account and it's different from ours
                                    if (shipment.upsAccountNumber.isNotEmpty() &&
                                        shipment.upsAccountNumber != BuildConfig.UPS_ACCOUNT_NUMBER) {

                                        // Bill the customer (receiver pays)
                                        put("BillReceiver", JSONObject().apply {
                                            put("AccountNumber", shipment.upsAccountNumber)
                                            put("Address", JSONObject().apply {
                                                put("PostalCode", shipment.shipToPostCode)
                                            })
                                        })
                                    } else {
                                        // Bill our own account (shipper pays)
                                        put("BillShipper", JSONObject().apply {
                                            put("AccountNumber", BuildConfig.UPS_ACCOUNT_NUMBER)
                                        })
                                    }
                                })
                            })

                            // Service - Use appropriate service based on destination
                            put("Service", JSONObject().apply {
                                val (serviceCode, serviceDescription) = getAppropriateUPSService(shipment)
                                put("Code", serviceCode)
                                put("Description", serviceDescription)
                            })

                            // Package information
                            put("Package", JSONObject().apply {
                                put("Description", "Package for ${shipment.shipmentNo}")
                                put("Packaging", JSONObject().apply {
                                    put("Code", "02") // Customer Supplied Package
                                    put("Description", "Package")
                                })
                                put("Dimensions", JSONObject().apply {
                                    put("UnitOfMeasurement", JSONObject().apply {
                                        put("Code", "IN")
                                        put("Description", "Inches")
                                    })
                                    put("Length", packageDimensions.depth.toString())
                                    put("Width", packageDimensions.width.toString())
                                    put("Height", packageDimensions.height.toString())
                                })
                                put("PackageWeight", JSONObject().apply {
                                    put("UnitOfMeasurement", JSONObject().apply {
                                        put("Code", "LBS")
                                        put("Description", "Pounds")
                                    })
                                    put("Weight", packageDimensions.weight.toString())
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
                    println("Label creation success: $response")
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
    
        private fun getServiceDescription(serviceCode: String): String {
            return when (serviceCode.uppercase()) {
                "GROUND" -> "UPS Ground"
                "NEXT_DAY_AIR" -> "UPS Next Day Air"
                "SECOND_DAY_AIR" -> "UPS 2nd Day Air"
                "THREE_DAY_SELECT" -> "UPS 3 Day Select"
                else -> "UPS Ground"
            }
        }
    
        private suspend fun getUPSAccessToken(): String {
            // Check if we have a valid cached token
            cachedUPSToken?.let { token ->
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
                    cachedUPSToken = UPSToken(accessToken, expiresAt)
    
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

        suspend fun updateShipmentTracking(
            shipmentData: ShipmentData,
            trackingNumber: String,
            shipDate: String
        ): Boolean {
            return try {
                val accessToken = getBCAccessToken()

                val apiUrl = "$BC_BASE_URL/${BuildConfig.TENANT_ID}/$ENVIRONMENT/api/art/integration/v1.0/companies($COMPANY_ID)/warehousesshipments('${shipmentData.shipmentNo}')"
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection

                // Extract and process ETag - handle OData format W/"value"
                val etag = if (shipmentData.odataEtag.isNotEmpty()) {
                    val rawEtag = shipmentData.odataEtag

                    if (rawEtag.startsWith("W/\"") && rawEtag.endsWith("\"")) {
                        val innerValue = rawEtag.substring(3, rawEtag.length - 1)
                        "W/\"$innerValue\""
                    } else if (rawEtag.startsWith("\"") && rawEtag.endsWith("\"")) {
                        val innerValue = rawEtag.substring(1, rawEtag.length - 1)
                        "W/\"$innerValue\""
                    } else {
                        "W/\"$rawEtag\""
                    }
                } else {
                    "*"
                }

                println("Using ETag: $etag")

                connection.apply {
                    requestMethod = "PATCH"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("If-Match", etag)
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val requestBody = JSONObject().apply {
                    put("packageLength", shipmentData.packageLength)
                    put("packageWidth", shipmentData.packageWidth)
                    put("packageHeight", shipmentData.packageHeight)
                    put("packageWeight", shipmentData.packageWeight)
                    put("PackageTrackingNo", trackingNumber)
                }

                println("PATCH Request Body: $requestBody") // Debug log

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                println("PATCH Response Code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    true
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error details available"

                    println("PATCH Error Response: $errorResponse")

                    // Handle specific error cases
                    when (responseCode) {
                        HttpURLConnection.HTTP_PRECON_FAILED -> {
                            println("ETag mismatch - record may have been modified by another user")
                        }
                        HttpURLConnection.HTTP_NOT_FOUND -> {
                            println("Shipment not found: ${shipmentData.shipmentNo}")
                        }
                        HttpURLConnection.HTTP_BAD_REQUEST -> {
                            println("Bad request - check field names and values: $errorResponse")
                        }
                    }

                    false
                }

            } catch (e: Exception) {
                println("Exception in updateShipmentTracking: ${e.message}")
                e.printStackTrace()
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
                "SECOND_DAY_AIR_AM" -> "59"
                "EXPRESS_PLUS" -> "54"
                else -> "03" // Default to Ground
            }
        }
    
        private fun parseShipmentResponse(jsonString: String): ShipmentData {
            val json = JSONObject(jsonString)
    
            return ShipmentData(
                // OData fields
                odataContext = json.optString("@odata.context", ""),
                odataEtag = json.optString("@odata.etag", ""),
    
                // Core shipment fields from BC
                shipmentNo = json.optString("no", ""),
                locationCode = json.optString("locationCode", ""),
                assignedUserId = json.optString("assignedUserId", ""),
                externalDocumentNo = json.optString("ExternalDocumentNo", ""),
                packageTrackingNo = json.optString("PackageTrackingNo", ""),
                agentAccount = json.optString("agentAccount", ""),
                shippingInstructionsText = json.optString("shippingInstructionsText", ""),
    
                // Package dimensions (directly from BC)
                packageLength = json.optDouble("packageLength", 0.0),
                packageWidth = json.optDouble("packageWidth", 0.0),
                packageHeight = json.optDouble("packageHeight", 0.0),
                packageWeight = json.optDouble("packageWeight", 0.0),
    
                // Ship to address fields (from BC response)
                shipToName = json.optString("shipToName", ""),
                shipToName2 = json.optString("shipToName2", ""),
                shipToAddress = json.optString("shipToAddress", ""),
                shipToAddress2 = json.optString("shipToAddress2", ""),
                shipToCity = json.optString("shipToCity", ""),
                shipToCounty = json.optString("shipToCounty", ""), // BC uses County for state
                shipToPostCode = json.optString("shipToPostCode", ""),
                shipToCountry = json.optString("shipToCountry", ""),
                shipToContact = json.optString("shipToContact", ""),
    
                // Map agentAccount to UPS account for UPS integration
                upsAccountNumber = json.optString("agentAccount", ""),
    
                // Create legacy shipping info for UPS compatibility (if needed)
                shipping = ShippingInfo(
                    addressCode = "",
                    name = json.optString("shipToName", ""),
                    address = json.optString("shipToAddress", ""),
                    address2 = json.optString("shipToAddress2", ""),
                    city = json.optString("shipToCity", ""),
                    shipToState = json.optString("shipToCounty", ""), // Map county to state
                    zipCode = json.optString("shipToPostCode", ""),
                    countryRegion = json.optString("shipToCountry", ""),
                    phoneNo = "",
                    contact = json.optString("shipToContact", ""),
                    locationCode = json.optString("locationCode", ""),
                    outboundWhseHandlingTime = "",
                    shippingTime = "",
                    shipmentMethod = ShipmentMethod(
                        code = "GROUND", // Default - you may need to map this from BC
                        agent = "UPS",
                        agentService = "Ground"
                    )
                )
            )
        }

        private fun parseUPSLabelResponse(jsonString: String): LabelResponse {
            val json = JSONObject(jsonString)
            val shipmentResponse = json.getJSONObject("ShipmentResponse")
            val shipmentResults = shipmentResponse.getJSONObject("ShipmentResults")
            val packageResults = shipmentResults.getJSONArray("PackageResults").getJSONObject(0)

            val trackingNumber = packageResults.getString("TrackingNumber")
            val labelData = packageResults.getJSONObject("ShippingLabel").getString("GraphicImage")
            val imageFormat = packageResults.getJSONObject("ShippingLabel")
                .getJSONObject("ImageFormat").getString("Code")

            val shipmentIdentificationNumber = shipmentResults.getString("ShipmentIdentificationNumber")

            val billingWeightObj = shipmentResults.getJSONObject("BillingWeight")
            val billingWeight = billingWeightObj.getString("Weight")
            val billingWeightUnit = billingWeightObj.getJSONObject("UnitOfMeasurement").getString("Code")

            // Handle potentially missing charge fields gracefully
            val baseServiceCharge = packageResults.optJSONObject("BaseServiceCharge")?.optString("MonetaryValue", "0.00") ?: "0.00"
            val serviceOptionsCharge = packageResults.optJSONObject("ServiceOptionsCharges")?.optString("MonetaryValue", "0.00") ?: "0.00"

            val totalCharge = shipmentResults.getJSONObject("ShipmentCharges")
                .getJSONObject("TotalCharges").getString("MonetaryValue")

            val currencyCode = shipmentResults.getJSONObject("ShipmentCharges")
                .getJSONObject("TotalCharges").getString("CurrencyCode")

            val customerContext = shipmentResponse.getJSONObject("Response")
                .getJSONObject("TransactionReference").getString("CustomerContext")

            return LabelResponse(
                trackingNumber = trackingNumber,
                labelData = labelData,
                imageFormat = imageFormat,
                shipmentIdentificationNumber = shipmentIdentificationNumber,
                billingWeight = billingWeight,
                billingWeightUnit = billingWeightUnit,
                baseServiceCharge = baseServiceCharge,
                serviceOptionsCharge = serviceOptionsCharge,
                totalCharge = totalCharge,
                currencyCode = currencyCode,
                customerContext = customerContext,
            )
        }
    
    }