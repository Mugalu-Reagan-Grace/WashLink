plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

val useFirebaseEmulators = providers.gradleProperty("useFirebaseEmulators")
    .orElse("false").get().toBoolean()
val firebaseEmulatorHost = providers.gradleProperty("firebaseEmulatorHost")
    .orElse("10.0.2.2").get()
val washlinkApiBaseUrl = providers.gradleProperty("washlinkApiBaseUrl")
    .orElse("").get().trimEnd('/')
require(Regex("^[A-Za-z0-9.-]+$").matches(firebaseEmulatorHost)) {
    "firebaseEmulatorHost must be a hostname or IP address without a port."
}
require(washlinkApiBaseUrl.isEmpty() || washlinkApiBaseUrl.startsWith("https://")) {
    "washlinkApiBaseUrl must be an HTTPS URL."
}
val escapedWashlinkApiBaseUrl = washlinkApiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.example.washlink"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        manifestPlaceholders["MAPS_API_KEY"] =
            providers.gradleProperty("MAPS_API_KEY").orElse("").get()
        buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useFirebaseEmulators.toString())
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$firebaseEmulatorHost\"")
        buildConfigField("String", "WASHLINK_API_BASE_URL", "\"$escapedWashlinkApiBaseUrl\"")
    }
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.washlink"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-storage")
    // Google Sign-In for Firebase Auth
    implementation("com.google.android.gms:play-services-auth:20.5.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.places:places:4.4.1")

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation("androidx.fragment:fragment:1.8.5")
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
