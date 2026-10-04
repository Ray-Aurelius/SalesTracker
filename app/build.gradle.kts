plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.salestracker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salestracker.app"
        minSdk = 26
        targetSdk = 35
        // Each GitHub build gets a higher number, so phones accept it as an update.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.1.$build"
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0") // per-app language switching
    implementation("androidx.biometric:biometric:1.1.0") // fingerprint / face / PIN app lock
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real JSON for tests (Android's is a stub off-device)
}
