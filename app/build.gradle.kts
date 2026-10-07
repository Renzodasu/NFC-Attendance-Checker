plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Single source of truth for the version shown in the APK file name and the manifest.
val androidVersionName = "0.0.4"

android {
    namespace = "com.nezzar.nfcattendance"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nezzar.nfcattendance"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = androidVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // The About screen reads the version from BuildConfig, so the number it shows
        // can never drift away from the build. No new dependency.
        buildConfig = true
    }
}

// Ship a named artefact instead of the default app-debug.apk. No dependency,
// no version change - just the output file name.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("Presencia-NFC-$androidVersionName-${variant.name}.apk")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // QR fallback: CameraX for the preview and frames (Jetpack), ZXing core for the
    // decode and the encode. Both work entirely on the device - no Play Services, no
    // model download, no network - so the app keeps its single promise: NFC and a
    // camera, and no INTERNET permission.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}