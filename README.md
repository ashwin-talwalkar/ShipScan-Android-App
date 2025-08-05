# ShipScan Android Application

A Android application that streamlines shipping operations by scanning barcodes, retrieving shipment data from Business Central, creating UPS shipping labels, and updating tracking information back to the ERP system. 

## Overview

ShipScan is a comprehensive shipping solution that bridges the gap between warehouse operations and digital systems. The app enables warehouse staff to scan shipment barcodes, automatically populate shipping details, generate professional shipping labels via UPS APIs, and seamlessly update Business Central with tracking information.

## Architecture

### Core Components

- **MainActivity**: Barcode scanning interface with camera integration
- **ShipmentDetailsActivity**: Shipment information display and package dimension input
- **LabelActivity**: Label generation, preview, printing, and system updates
- **BCApiService**: Business Central API integration and UPS API management
- **PDFPrintAdapter**: Custom print adapter for shipping labels

### Key Features

- **Real-time Barcode Scanning**: Multi-format barcode support using ML Kit
- **Business Central Integration**: Seamless ERP data retrieval and updates
- **UPS API Integration**: Automated shipping label creation with OAuth2 authentication
- **Label Management**: Preview, print, and PDF conversion capabilities
- **Offline-Ready**: Robust error handling and retry mechanisms
- **Professional UI**: Material Design components with loading states

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

### External APIs
- **Business Central API**: Custom REST API for shipment data
- **UPS API**: Official UPS shipping and tracking services
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

### 3. Label Generation (LabelActivity)
```
Generate UPS label → Preview label → Print label → Update Business Central
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

3. **Device Requirements**:
   - Camera permission for barcode scanning
   - Internet connectivity for API calls
   - Print capability (optional)

### Configuration Files

#### build.gradle (Module: app)
```gradle
android {
    compileSdk 34
    
    defaultConfig {
        applicationId "com.luminys.shipscan"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
    
    buildTypes {
        release {
            buildConfigField "String", "UPS_CLIENT_ID", "\"${ups_client_id}\""
            buildConfigField "String", "UPS_CLIENT_SECRET", "\"${ups_client_secret}\""
        }
        debug {
            buildConfigField "String", "UPS_CLIENT_ID", "\"${ups_client_id}\""
            buildConfigField "String", "UPS_CLIENT_SECRET", "\"${ups_client_secret}\""
        }
    }
}

dependencies {
    implementation 'androidx.camera:camera-camera2:1.3.0'
    implementation 'androidx.camera:camera-lifecycle:1.3.0'
    implementation 'androidx.camera:camera-view:1.3.0'
    implementation 'com.google.mlkit:barcode-scanning:17.2.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'com.google.android.material:material:1.10.0'
    // Add other dependencies as needed
}
```

#### gradle.properties
```properties
# UPS API Credentials (add to local gradle.properties & copy gradle.properties.copy > gradle.properties)
ups_client_id=YOUR_UPS_CLIENT_ID
ups_client_secret=YOUR_UPS_CLIENT_SECRET
```

### API Configuration

#### Business Central API Setup
```kotlin
// In BCApiService.kt
private const val BASE_URL = "http://192.168.0.25:3000/api"
```

## Installation & Deployment

### Development Setup

1. **Clone Repository**:
   ```bash
   git clone <repository-url>
   cd ShipScan
   ```

2. **Configure API Keys**:
   ```bash
   # Add to gradle.properties
   echo "ups_client_id=YOUR_CLIENT_ID" >> gradle.properties
   echo "ups_client_secret=YOUR_CLIENT_SECRET" >> gradle.properties
   ```

3. **Build and Run**:
   ```bash
   ./gradlew assembleDebug
   # Or use Android Studio Run button
   ```

### Production Deployment

1. **Generate Signed APK**:
   - Configure signing in Android Studio
   - Build → Generate Signed Bundle/APK
   - Select APK and configure keystore

2. **Distribution Options**:
   - Google Play Store (recommended)
   - Enterprise distribution (MDM)
   - Direct APK installation

## Usage Instructions

### For End Users

#### 1. Starting the Application
- Open ShipScan app
- Grant camera permission when prompted
- Point camera at barcode or enter barcode manually

#### 2. Processing Shipments
- Scan shipment barcode
- Review shipment details automatically loaded
- Enter package dimensions (height, width, depth, weight)
- Tap "Create Shipping Label"

#### 3. Managing Labels
- Preview generated shipping label
- Print label using device printer
- Update Business Central system with tracking info
- Complete workflow and return to scanning

### For Administrators

#### 1. Device Setup
- Install APK on warehouse devices
- Configure network access to Business Central API
- Set up printer connections if needed
- Test with sample shipments

#### 2. User Training
- Demonstrate barcode scanning techniques
- Show package dimension measurement
- Explain error handling and retry procedures
- Practice complete workflow

## API Integration Details

### Business Central API

CURRENTLY USING A FAKE DATA API. Please connect the properly working BC Api to get it running 
#### Shipment Retrieval

### UPS API Integration

#### Authentication Flow
1. Request OAuth2 token using client credentials
2. Cache token with expiration handling
3. Auto-refresh tokens before expiry

#### Shipping Label Creation
- Constructs comprehensive UPS ShipmentRequest
- Handles address validation and formatting
- Supports multiple service levels
- Returns tracking number and label data

## Error Handling & Troubleshooting

### Common Issues

#### 1. Camera/Scanning Issues
**Problem**: Camera not working or barcodes not scanning
**Solutions**:
- Verify camera permissions granted
- Ensure adequate lighting
- Clean camera lens
- Try manual barcode entry

#### 2. API Connection Issues
**Problem**: "Network error" or "Server error" messages
**Solutions**:
- Check network connectivity
- Verify API endpoints are accessible
- Confirm API credentials are correct
- Check Business Central system status

#### 3. UPS API Issues
**Problem**: Label creation fails
**Solutions**:
- Verify UPS credentials in build config
- Check UPS account number in shipment data
- Validate shipping addresses
- Ensure package dimensions are reasonable

#### 4. Printing Issues
**Problem**: Labels won't print
**Solutions**:
- Verify printer connectivity
- Check PDF generation succeeded
- Ensure print permissions granted
- Try different printer if available

### Debug Features

#### Logging
- Comprehensive logging with tags
- Error details in Android Logcat
- Network request/response logging

#### Test Mode
- Manual barcode entry for testing
- Error simulation capabilities
- Offline mode testing

## Security Considerations

### API Security
- **OAuth2 Authentication**: Secure token-based UPS API access
- **HTTPS Only**: All API communications encrypted
- **Credential Management**: API keys stored in build config, not source code
- **Token Caching**: Secure in-memory token storage with expiration

### Data Protection
- **No Data Persistence**: Shipment data not stored locally
- **Memory Management**: Sensitive data cleared after use
- **Network Security**: Certificate pinning for production APIs
- **Permission Management**: Minimal required permissions

### Best Practices
- Regular security audits of dependencies
- Secure credential rotation procedures
- Network traffic monitoring
- User access logging

## Performance Optimization

### Memory Management
- Efficient bitmap handling for label images
- Proper cleanup of camera resources
- Coroutine lifecycle management
- Background processing for API calls

### Network Optimization
- Connection pooling for HTTP requests
- Request timeout configuration
- Retry logic with exponential backoff
- Token caching to reduce auth requests

### UI Performance
- Async loading with progress indicators
- Image compression for label previews
- Smooth camera preview implementation
- Responsive error state handling

## Customization & Extension

### Adding New Barcode Formats
```kotlin
val options = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(
        Barcode.FORMAT_QR_CODE,
        Barcode.FORMAT_CODE_128,
        // Add new formats here
        Barcode.FORMAT_PDF417
    )
    .build()
```

### Custom Shipping Services
Extend the `getUPSServiceCode` function in `BCApiService`:
```kotlin
private fun getUPSServiceCode(shipmentMethodCode: String): String {
    return when (shipmentMethodCode.uppercase()) {
        "GROUND" -> "03"
        "NEXT_DAY_AIR" -> "01"
        // Add custom mappings
        "CUSTOM_SERVICE" -> "XX"
        else -> "03"
    }
}
```

### UI Customization
- Modify layouts in `res/layout/` directory
- Update colors in `res/values/colors.xml`
- Customize strings in `res/values/strings.xml`
- Add company branding assets
