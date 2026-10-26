plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Permanent signing key, provided by CI from GitHub secrets (see README → "Signing"). Without it
// (local builds) Gradle falls back to the machine's own debug key.
val ciKeystore: File? = System.getenv("CENTIMUSIC_KEYSTORE_FILE")?.let { File(it) }?.takeIf { it.exists() }
val ciKeyPassword: String? = System.getenv("CENTIMUSIC_KEY_PASSWORD")
// Every CI run gets a higher versionCode, so Android always treats a new APK as an update.
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.centimusic.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.centimusic.player"
        minSdk = 26
        targetSdk = 35
        versionCode = ciRunNumber ?: 1
        versionName = "1.0.${ciRunNumber ?: 0}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Same permanent key as debug builds when available, so either can update the other.
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.guava)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
