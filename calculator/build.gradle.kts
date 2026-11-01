plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Same permanent key as CentiMusic (see README → "Signing"); local builds fall back to the debug key.
val ciKeystore: File? = System.getenv("CENTIMUSIC_KEYSTORE_FILE")?.let { File(it) }?.takeIf { it.exists() }
val ciKeyPassword: String? = System.getenv("CENTIMUSIC_KEY_PASSWORD")
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.centimusic.calculator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.centimusic.calculator"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRunNumber ?: 1
        versionName = "1.0.${ciRunNumber ?: 0}"
    }

    signingConfigs {
        if (ciKeystore != null && ciKeyPassword != null) {
            create("centimusic") {
                storeFile = ciKeystore
                storePassword = ciKeyPassword
                keyAlias = "centimusic"
                keyPassword = ciKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("centimusic")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("centimusic") ?: signingConfigs.getByName("debug")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)

    testImplementation(libs.junit)
}
