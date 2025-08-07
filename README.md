# ShipScan Android Application

An Android application that streamlines shipping operations by scanning barcodes, retrieving shipment data from Business Central, creating UPS shipping labels with customer billing, and updating tracking information back to the ERP system.

## Overview

ShipScan is a comprehensive shipping solution that bridges the gap between warehouse operations and digital systems. The app enables warehouse staff to scan shipment barcodes, automatically populate shipping details, generate professional shipping labels via UPS APIs with automated customer billing, and seamlessly update Business Central with tracking information.

**Key Innovation**: Automated customer billing through UPS API - customers are charged directly on their UPS accounts, eliminating upfront shipping costs and improving cash flow.

## Architecture

### Core Components

- **MainActivity**: Barcode scanning interface with camera integration
- **ShipmentDetailsActivity**: Shipment information display and package dimension input
- **LabelActivity**: Label generation, preview, printing, and system updates
- **BCApiService**: Business Central API integration and UPS API management with customer billing
- **PDFPrintAdapter**: Custom print adapter for shipping labels

### Key Features

- **Real-time Barcode Scanning**: Multi-format barcode support using ML Kit
- **Business Central Integration**: Seamless ERP data retrieval and updates
- **UPS API Integration**: Automated shipping label creation with OAuth2 authentication
- **Customer Billing Automation**: Direct billing to customer UPS accounts (BillReceiver)
- **Label Management**: Preview, print, and PDF conversion capabilities
- **Offline-Ready**: Robust error handling and retry mechanisms

## Technology Stack

### Android Framework
- **Target SDK**: Android API level (as configured)
- **Minimum SDK**: Compatible with modern Android devices
- **Architecture**: Activity-based with coroutines for async operations
- **UI Framework**: Native Android Views with Material Design

### Key Libraries
- **CameraX**: Modern camera API for barcode scanning
- **ML Kit**: Google's machine learning for barcode recognition
- **Kotlin Coroutines**: Asynchronous programming and lifecycle management
- **Material Design**: Professional UI components
- **JSONArray/JSONObject**: UPS API request formatting

### External APIs
- **Business Central API**: Custom REST API for shipment data
- **UPS API**: Official UPS shipping and tracking services with customer billing
- **OAuth2**: Secure authentication for UPS services

## Application Flow

### 1. Barcode Scanning (MainActivity)
```
User opens app → Camera initializes → Scan barcode → Navigate to shipment details
```

### 2. Shipment Details (ShipmentDetailsActivity)
```
Fetch shipment data → Display details → Input package dimensions → Create label
```

### 3. Label Generation with Customer Billing (LabelActivity)
```
Generate UPS label with customer billing → Preview label → Print label → Update Business Central
```

### 4. Billing Flow
```
Check customer UPS account → Use BillReceiver if valid → Customer pays directly → $0.00 charges to you
```

## Setup & Configuration

### Prerequisites

1. **Android Development Environment**:
   - Android Studio Arctic Fox or later
   - Kotlin support enabled
   - Minimum SDK 21 (Android 5.0)

2. **API Credentials**:
   - UPS Developer Account and API credentials
   - Business Central API access
   - Valid UPS account number for shipping
   - Customer UPS account numbers for billing

3. **Device Requirements**:
   - Camera permission for barcode scanning
   - Internet connectivity for API calls
   - Print capability (optional)

### Configuration Files

#### gradle.properties
```properties
# UPS API Credentials (add to local gradle.properties & copy gradle.properties.copy > gradle.properties)
UPS_CLIENT_ID=XXX
UPS_CLIENT_SECRET=XXX
UPS_ACCOUNT_NUMBER=XXXXXX
BC_CLIENT_ID=XXX
BC_CLIENT_SECRET=XXX
BC_SCOPE=https://api.businesscentral.dynamics.com/.default
TENANT_ID=XXX
```

### API Configuration

#### Business Central API Setup
```kotlin
// In BCApiService.kt
private const val BC_BASE_URL = "https://api.businesscentral.dynamics.com/v2.0"
private const val ENVIRONMENT = "Lmy0610"
private const val COMPANY_ID = "0d85027a-3432-ef11-8409-002248ab1716"
```

#### UPS API Setup
```kotlin
// Test Environment
private const val UPS_AUTH_URL = "https://wwwcie.ups.com/security/v1/oauth/token"
private const val UPS_SHIPPING_URL = "https://wwwcie.ups.com/api/shipments/v2409/ship"

// Production Environment (when ready)
// private const val UPS_AUTH_URL = "https://onlinetools.ups.com/security/v1/oauth/token"
// private const val UPS_SHIPPING_URL = "https://onlinetools.ups.com/api/shipments/v2409/ship"
```

## Installation & Deployment

### Development Setup

1. **Clone Repository**:
   ```bash
   git clone <repository-url>
   cd ShipScan
   ```

2. **Configure API Keys**:
   ```properties
   # Fill out in gradle.properties
   UPS_CLIENT_ID=XXX
   UPS_CLIENT_SECRET=XXX
   UPS_ACCOUNT_NUMBER=XXXXXX
   BC_CLIENT_ID=XXX
   BC_CLIENT_SECRET=XXX
   BC_SCOPE=https://api.businesscentral.dynamics.com/.default
   TENANT_ID=XXX
   ```

3. **Build and Run**:
   ```bash
   # Use Android Studio Run button
   ```

### Production Deployment

1. **Update UPS URLs**: Change from `wwwcie.ups.com` to `onlinetools.ups.com`
2. **Generate Signed APK**:
   - Configure signing in Android Studio
   - Build → Generate Signed Bundle/APK
   - Select APK and configure keystore

3. **Distribution Options**:
   - Google Play Store (recommended)
   - Enterprise distribution (MDM)
   - Direct APK installation

## Customer Billing Feature

### How It Works

The app automatically determines billing based on customer data from Business Central:

1. **Customer Has UPS Account**: Uses `BillReceiver` - customer pays directly
2. **No Customer UPS Account**: Uses `BillShipper` - you pay and invoice separately

### Business Central Integration

Customer UPS account numbers are stored in the `agentAccount` field in Business Central and mapped to `upsAccountNumber` in the app:

```kotlin
// In parseShipmentResponse()
upsAccountNumber = json.optString("agentAccount", ""),
```

### UPS API Billing Logic

```kotlin
// Payment Information Logic
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
```

### Charge Verification

- **Test Environment**: Shows $0.00 when the customer is billed and customer is not actually charged
- **Production Environment**: Customer pays real rates, you pay $0.00
- **When you're billed**: Shows actual shipping costs in response

## Usage Instructions

### For End Users

#### 1. Starting the Application
- Open ShipScan app
- Grant camera permission when prompted
- Point camera at barcode or enter barcode manually

#### 2. Processing Shipments
- Scan shipment barcode
- Review shipment details automatically loaded
- Verify customer UPS account is populated (for customer billing)
- Enter package dimensions (height, width, depth, weight)
- Tap "Create Shipping Label"

#### 3. Managing Labels
- Preview generated shipping label
- Verify billing information (customer vs. company)
- Print label using device printer
- Update Business Central system with tracking info
- Complete workflow and return to scanning

#### 4. Billing Verification
- Check label response for $0.00 charges (this indicates that the customer was billed)
- Non-zero charges indicate you're being billed (BC has our account number or is blank)
- Customer receives separate UPS invoice

### For Administrators

#### 1. Device Setup
- Install APK on warehouse devices
- Configure network access to Business Central API
- Set up printer connections if needed
- Test with sample shipments and customer accounts

#### 2. Customer Account Management
- Ensure customer UPS accounts are entered in Business Central `agentAccount` field
- Verify customer accounts are active and allow third-party billing
- Test billing with small shipments before full deployment

#### 3. User Training
- Demonstrate barcode scanning techniques
- Show package dimension measurement
- Explain billing verification process
- Practice complete workflow with customer billing scenarios

## API Integration Details

### Business Central API

This integration uses the Business Central API to retrieve warehouse shipment data including customer UPS account information and update tracking information post-label creation.

**Base URL**:
```
https://api.businesscentral.dynamics.com/v2.0/{TENANT_ID}/{ENVIRONMENT}/api/art/integration/v1.0/companies({COMPANY_ID})
```

**Primary Endpoints**:

- **Get Shipment by Number**
   ```
   GET /warehousesshipments('{shipmentNo}')
   ```
  Fetches warehouse shipment record including customer UPS account (`agentAccount` field)

- **Update Shipment Tracking**
  ```
  PATCH /warehousesshipments('{shipmentNo}')
  ```
  Updates the `PackageTrackingNo` field with UPS tracking number

**Key Fields Retrieved**:
- `agentAccount`: Customer's UPS account number for billing
- Ship-to address information
- Package dimensions
- Shipment references

### UPS API Integration

#### Authentication Flow
```kotlin
// OAuth 2.0 Client Credentials Flow
private suspend fun getUPSAccessToken(): String {
    val url = URL(UPS_AUTH_URL)
    val credentials = "${BuildConfig.UPS_CLIENT_ID}:${BuildConfig.UPS_CLIENT_SECRET}"
    val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    
    // Request with Basic Auth header
    connection.setRequestProperty("Authorization", "Basic $encodedCredentials")
    
    // Token caching with expiration
    val expiresAt = System.currentTimeMillis() + ((expiresIn - 300) * 1000)
    cachedUPSToken = UPSToken(accessToken, expiresAt)
}
```

#### Shipping Label Creation with Customer Billing

**Key Request Structure**:
```json
{
  "ShipmentRequest": {
    "Shipment": {
      "PaymentInformation": {
        "ShipmentCharge": {
          "Type": "01",
          "BillReceiver": {
            "AccountNumber": "CUSTOMER_UPS_ACCOUNT",
            "Address": {
              "PostalCode": "CUSTOMER_ZIP"
            }
          }
        }
      }
    }
  }
}
```

**Address Line Handling**:
```kotlin
// Proper JSONArray formatting for UPS
val addressLinesArray = JSONArray().apply {
    if (shipment.shipToAddress.isNotEmpty()) {
        put(shipment.shipToAddress)
    }
    if (shipment.shipToAddress2.isNotEmpty()) {
        put(shipment.shipToAddress2)
    }
}
put("AddressLine", addressLinesArray)
```

#### Response Handling

**Successful Customer Billing Response**:
```json
{
  "ShipmentResponse": {
    "ShipmentResults": {
      "ShipmentCharges": {
        "TotalCharges": {"MonetaryValue": "0.00"}
      }
    }
  }
}
```

**Robust Response Parsing**:
```kotlin
private fun parseUPSLabelResponse(jsonString: String): LabelResponse {
    // Handle missing charge fields gracefully
    val baseServiceCharge = packageResults.optJSONObject("BaseServiceCharge")
        ?.optString("MonetaryValue", "0.00") ?: "0.00"
    val serviceOptionsCharge = packageResults.optJSONObject("ServiceOptionsCharges")
        ?.optString("MonetaryValue", "0.00") ?: "0.00"
}
```

## Security Considerations

### API Security
- **OAuth2 Authentication**: Secure token-based UPS API access with caching
- **HTTPS Only**: All API communications encrypted
- **Credential Management**: API keys stored in build config, not source code
- **Token Caching**: Secure in-memory token storage with expiration handling

### Billing Security
- **Account Validation**: UPS validates customer accounts before accepting billing
- **Address Verification**: Postal codes must match UPS account registration
- **Authorization**: Only authorized accounts can be billed through API
- **Audit Trail**: All billing decisions logged for troubleshooting

## Performance Optimization

_### Memory Management
- **Token Caching**: Reduces authentication overhead
- **Efficient JSON Processing**: Uses JSONArray for proper formatting
- **Resource Cleanup**: Proper disposal of HTTP connections
- **Background Processing**: API calls on background threads

### Network Optimization
- **Connection Pooling**: Reuses HTTP connections
- **Timeout Configuration**: Appropriate timeouts for UPS API calls
- **Retry Logic**: Exponential backoff for failed requests
- **Cached Authentication**: Minimizes token refresh calls_

## Customer Billing Requirements

### Customer Setup Requirements

1. **UPS Account**: Customer must have active UPS account
2. **Account Type**: Must be daily pickup, occasional, customer B.I.N, or drop shipper account
3. **Third-Party Billing**: Account must allow third-party billing authorization
4. **Address Match**: Account postal code must match shipping address
5. **Business Central Entry**: UPS account number entered in `agentAccount` field