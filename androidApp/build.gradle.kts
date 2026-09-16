import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

// MAPS_API_KEY=... in local.properties (gitignored) — never commit a real Maps key. Falls back to
// an empty string so the build still succeeds without one; the map picker just won't render tiles.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "")

// RELEASE_KEYSTORE_PASSWORD=... in local.properties (gitignored) — the keystore itself
// (androidApp/keystore/release.keystore) is also gitignored; never commit either. This is just
// the upload key for Play App Signing, not the real signing key Google holds — resettable if lost.
val releaseKeystorePassword: String = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD", "")

android {
    namespace = "com.maxtime.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.maxtime.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("androidApp/keystore/release.keystore")
            storePassword = releaseKeystorePassword
            keyAlias = "maxtime-release"
            keyPassword = releaseKeystorePassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        // Points at the maxtime-app Firebase project. Store release builds.
        create("production") {
            dimension = "environment"
            applicationId = "com.maxtime.android"
            resValue("string", "app_name", "MaxTime")
        }
        // Points at the maxtime-staging Firebase project. Day-to-day development builds —
        // installs alongside production/beta on the same device.
        create("staging") {
            dimension = "environment"
            applicationId = "com.maxtime.android.staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "MaxTime Staging")
        }
        // Points at the maxtime-beta Firebase project. Pre-release testing (e.g. Play internal/closed tracks).
        create("beta") {
            dimension = "environment"
            applicationId = "com.maxtime.android.beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "MaxTime Beta")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        resValues = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
