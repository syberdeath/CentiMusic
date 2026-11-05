plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Same permanent signing key as CentiMusic, provided by CI from GitHub secrets. Without it (local
// builds) Gradle falls back to the machine's own debug key.
val ciKeystore: File? = System.getenv("CENTIMUSIC_KEYSTORE_FILE")?.let { File(it) }?.takeIf { it.exists() }
val ciKeyPassword: String? = System.getenv("CENTIMUSIC_KEY_PASSWORD")
// Every CI run gets a higher versionCode, so Android always treats a new APK as an update.
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.hkm.carlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hkm.carlauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRunNumber ?: 1
        versionName = "1.0.${ciRunNumber ?: 0}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (ciKeystore != null && ciKeyPassword != null) {
            create("hkm") {
                storeFile = ciKeystore
                storePassword = ciKeyPassword
                keyAlias = "centimusic"
                keyPassword = ciKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("hkm")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("hkm") ?: signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.maplibre.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.org.json)
}
