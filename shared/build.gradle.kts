import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.maxtime.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "MaxTime shared module"
        homepage = "https://github.com/userAksh/MaxTime"
        version = "1.0"
        ios.deploymentTarget = "15.0"
        podfile = project.file("../iosApp/Podfile")

        // Xcode config names are the environment names (see iosApp/project.yml), not Xcode's
        // default Debug/Release — the cocoapods plugin needs an explicit mapping for each.
        xcodeConfigurationToNativeBuildType["Staging"] = NativeBuildType.DEBUG
        // Beta pinned to DEBUG as a workaround for a Kotlin/Native compiler bug: linking the
        // shared framework in RELEASE mode produces a duplicate-symbol error on a FirebaseAuth
        // cinterop binding (reproduces via a bare `gradlew :shared:linkPodReleaseFrameworkIosArm64`,
        // independent of Xcode/CocoaPods — a real upstream issue, not a project misconfiguration).
        // The app's own Swift/Xcode code still builds fully optimized under the Beta config; only
        // the KMP framework itself skips optimization. Revisit before Production ships.
        xcodeConfigurationToNativeBuildType["Beta"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["Production"] = NativeBuildType.RELEASE

        framework {
            baseName = "shared"
            isStatic = true
        }

        pod("FirebaseAuth")
        pod("FirebaseFirestore")
        pod("FirebaseStorage")
        pod("FirebaseFunctions")
        // The pod's own framework is named RazorpayStandard, not razorpay-pod/razorpay_pod —
        // without this override Kotlin's cocoapods plugin guesses the wrong module name from the
        // pod name and cinterop generation fails with "module 'razorpay_pod' not found". packageName
        // is set explicitly too since it's otherwise snapshotted from the pre-override default
        // (cocoapods.razorpay_pod) rather than following the moduleName override above.
        pod("razorpay-pod") {
            moduleName = "RazorpayStandard"
            packageName = "cocoapods.RazorpayStandard"
        }
    }

    // The Kotlin cocoapods plugin generates its OWN synthetic Pods project (under
    // build/cocoapods/synthetic/ios) to build each pod's xcframework for cinterop — this is
    // separate from, and not affected by, iosApp/Podfile's own post_install hook. The plugin
    // bakes in a post_install of its own (see KT-57741) that only raises a pod's deployment
    // target up to a floor of 12.0, which predates Xcode 27's simulator SDK minimum of 15.0 and
    // isn't configurable from the cocoapods{} DSL. Patch the generated project directly right
    // after `pod install` runs there, before Gradle shells out to xcodebuild against it.
    tasks.matching { it.name == "podInstallSyntheticIos" }.configureEach {
        // Resolved to a plain (configuration-cache-safe) Provider<RegularFile> at configuration
        // time — referencing `file(...)`/the Project itself from inside doLast isn't serializable
        // and breaks the configuration cache.
        val pbxprojFile = layout.buildDirectory.file("cocoapods/synthetic/ios/Pods/Pods.xcodeproj/project.pbxproj")
        doLast {
            val pbxproj = pbxprojFile.get().asFile
            if (pbxproj.exists()) {
                val original = pbxproj.readText()
                val patched = Regex("""IPHONEOS_DEPLOYMENT_TARGET = (\d+(?:\.\d+)?);""").replace(original) { match ->
                    val version = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (version < 15.0) "IPHONEOS_DEPLOYMENT_TARGET = 15.0;" else match.value
                }
                if (patched != original) pbxproj.writeText(patched)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.animation)
            implementation(libs.compottie)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.firebase.common)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.functions)
            implementation(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.androidx.activity.compose)
            implementation(libs.play.services.location)
            implementation(libs.maps.compose)
            api(libs.razorpay.checkout)
        }
    }
}
