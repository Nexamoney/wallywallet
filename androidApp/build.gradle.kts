// Thin Android application shell (AGP 9 does not allow com.android.application together
// with the Kotlin Multiplatform plugin in one module). All code and resources live in :shared.
val versionNumber = libs.versions.wallyApp.get()
val androidVersionCode = versionNumber.replace(".", "").toInt()

plugins {
    alias(libs.plugins.android.application)
}

base {
    archivesName = "wally"
}

android {
    namespace = "info.bitcoinunlimited.www.wally.app"
    compileSdk = libs.versions.androidSdk.get().toInt()
    defaultConfig {
        applicationId = "info.bitcoinunlimited.www.wally"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = androidVersionCode
        versionName = versionNumber
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            ndk.debugSymbolLevel = "FULL"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            ndk.debugSymbolLevel = "FULL"
        }
    }
    lint {
        abortOnError = false // Prevents Lint from failing the build
        warningsAsErrors = false // Ensures warnings don't fail the build
    }
    testOptions {
        suites {
            create("journeysTest") {
                assets {
                }
                targets {
                    create("default") {
                    }
                }
                useJunitEngine {
                    inputs += listOf(com.android.build.api.dsl.AgpTestSuiteInputParameters.TESTED_APKS)
                    includeEngines += listOf("journeys-test-engine")
                    enginesDependencies(libs.junit.platform.launcher)
                    enginesDependencies(libs.junit.platform.engine)
                    enginesDependencies(libs.journeys.junit.engine)
                }
                targetVariants += listOf("debug")
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    // LeakCanary: auto-watchers active in the debug APK.
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}

// This was added because ./gradlew build taskTree was failing with:
// - In plugin 'com.android.internal.version-check' type 'com.android.build.gradle.internal.tasks.ListingFileRedirectTask' property 'listingFile' specifies file '.../build/outputs/apk/debug/output-metadata.json' which doesn't exist.
// Now the ListingFileRedirectTask only runs if the .apk output file exists:
tasks.withType<com.android.build.gradle.internal.tasks.ListingFileRedirectTask>().configureEach {
    onlyIf { listingFile.get().asFile.exists() }
}
