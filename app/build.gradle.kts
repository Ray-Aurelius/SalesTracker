plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.salestracker.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.salestracker.app"
        minSdk = 26
        targetSdk = 36
        // Each GitHub build gets a higher number, so phones accept it as an update.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.2.$build"
    }

    // A permanent signing key lets every new build install over the last one, keeping your data.
    // GitHub writes it from the SIGNING_KEYSTORE secret; it is never stored in the repository.
    val stableKey = file("stable.keystore")
    signingConfigs {
        create("stable") {
            if (stableKey.exists()) {
                storeFile = stableKey
                storePassword = "salestracker"
                keyAlias = "salestracker"
                keyPassword = "salestracker"
            }
        }
    }

    buildTypes {
        debug {
            if (stableKey.exists()) signingConfig = signingConfigs.getByName("stable")
        }
        // What users install: not debuggable, so no computer can attach to the app or copy its files.
        // Code shrinking stays off on purpose; it adds little security for an offline app and could
        // introduce behavior differences that can't be tested on a phone before release.
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (stableKey.exists()) signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Store screenshots render only when asked: ./gradlew testDebugUnitTest -PstoreShots
            it.systemProperty("storeShots", project.hasProperty("storeShots").toString())
            it.systemProperty("storeShotsDir", layout.buildDirectory.dir("store-screenshots").get().asFile.absolutePath)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0") // per-app language switching
    implementation("androidx.biometric:biometric:1.1.0") // fingerprint / face / PIN app lock
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") // password-protecting report PDFs (works offline)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1") // store screenshots, rendered off-device
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.json:json:20240303") // real JSON for tests (Android's is a stub off-device)
}
