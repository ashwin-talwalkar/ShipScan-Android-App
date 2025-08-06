plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.luminys.shipscan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luminys.shipscan"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "UPS_CLIENT_ID", "\"${project.findProperty("UPS_CLIENT_ID")}\"")
            buildConfigField("String", "UPS_CLIENT_SECRET", "\"${project.findProperty("UPS_CLIENT_SECRET")}\"")
            buildConfigField("String", "BC_CLIENT_ID", "\"${project.findProperty("BC_CLIENT_ID")}\"")
            buildConfigField("String", "BC_CLIENT_SECRET", "\"${project.findProperty("BC_CLIENT_SECRET")}\"")
            buildConfigField("String", "BC_SCOPE", "\"${project.findProperty("BC_SCOPE")}\"")
            buildConfigField("String", "TENANT_ID", "\"${project.findProperty("TENANT_ID")}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "UPS_CLIENT_ID", "\"${project.findProperty("UPS_CLIENT_ID")}\"")
            buildConfigField("String", "UPS_CLIENT_SECRET", "\"${project.findProperty("UPS_CLIENT_SECRET")}\"")
            buildConfigField("String", "BC_CLIENT_ID", "\"${project.findProperty("BC_CLIENT_ID")}\"")
            buildConfigField("String", "BC_CLIENT_SECRET", "\"${project.findProperty("BC_CLIENT_SECRET")}\"")
            buildConfigField("String", "BC_SCOPE", "\"${project.findProperty("BC_SCOPE")}\"")
            buildConfigField("String", "TENANT_ID", "\"${project.findProperty("TENANT_ID")}\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // CameraX dependencies
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Optional: Camera extensions for better quality
    implementation("androidx.camera:camera-extensions:1.3.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}