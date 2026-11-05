plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "net.jdbridges.droidviewfinder"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.jdbridges.droidviewfinder"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // arm64 only: current phones are arm64, and the UVC library ships four ABIs (~25 MB of .so files).
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // UVC (USB video class) camera access over UsbManager, with the camera's own mode list.
    // Apache 2.0, Maven Central; arm64 native libs are 16 KB aligned (checked 2026-10-06).
    implementation("com.herohan:UVCAndroid:1.0.13")

    testImplementation("junit:junit:4.13.2")
}
