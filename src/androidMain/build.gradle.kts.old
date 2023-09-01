import java.time.Instant

plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    kotlin("android")
    kotlin("plugin.serialization")
}

val mpThreadsVersion = "0.1.6"
val ktorVersion = "2.3.3" // https://github.com/ktorio/ktor

val secSinceEpoch = Instant.now().epochSecond

android {
    namespace = "info.bitcoinunlimited.www.wally"
    compileSdk = 34
    defaultConfig {
        applicationId = "info.bitcoinunlimited.www.wally"
        minSdk = 29
        targetSdk = 34
        versionCode = 300
        versionName = "3.00"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.2"
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
            buildConfigField("String", "BUILD_TIME", "\"${secSinceEpoch}\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "BUILD_TIME", "\"${secSinceEpoch}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.compose.ui:ui:1.5.0")
    implementation("androidx.compose.ui:ui-tooling:1.5.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.0")
    implementation("androidx.compose.foundation:foundation:1.5.0")
    implementation("androidx.compose.material:material:1.5.0")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.0")

    // nexa
    implementation("org.nexa:mpthreads:$mpThreadsVersion")
    implementation("org.nexa:libnexakotlin:0.0.7e")
    implementation("org.nexa:walletoperations:0.0.1")

    // android layout dependencies
    implementation("com.google.android.flexbox:flexbox:3.0.0")  // https://github.com/google/flexbox-layout/tags
    implementation("androidx.activity:activity:1.7.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.1")  // https://developer.android.com/jetpack/androidx/releases/navigation
    implementation("androidx.navigation:navigation-ui-ktx:2.7.1")
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.android.support.constraint:constraint-layout:2.1.4") // https://developer.android.com/jetpack/androidx/releases/constraintlayout
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.preference:preference:1.2.1")  // https://developer.android.com/jetpack/androidx/releases/preference

    // network access
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // for bigintegers
    implementation("com.ionspin.kotlin:bignum:0.3.8")
    implementation("com.ionspin.kotlin:bignum-serialization-kotlinx:0.3.8")


    // QR scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Image file conversion
    implementation("com.caverock:androidsvg-aar:1.4")
}