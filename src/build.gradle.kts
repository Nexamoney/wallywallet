import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Wally Wallet version
// On version bump: Run ./gradlew generateVersionFile and commit the updates iosApp/iosApp/info.plist file
val versionNumber = "3.7.10"
val androidVersionCode = versionNumber.replace(".", "").toInt()
val kotlinVersion = "2.1.20"

// Dependency versions
val mpThreadsVersion = "0.4.2"
val nexaRpcVersion = "1.3.0"
val libNexaKotlinVersion = "0.4.28"
val serializationVersion = "1.8.1"  // https://github.com/Kotlin/kotlinx.serialization
val coroutinesVersion = "1.10.2"     // https://github.com/Kotlin/kotlinx.coroutines
val bigNumVersion = "0.3.10"         // https://github.com/ionspin/kotlin-multiplatform-bignum
val composeVersion = "1.8.1"        // https://github.com/JetBrains/compose-multiplatform/releases
val androidTestCoreVersion = "1.6.1" // https://mvnrepository.com/artifact/androidx.test/core
val androidxActivityComposeVersion = "1.10.1"
val uriKmpVersion = "0.0.19"  // https://github.com/eygraber/uri-kmp
val skikoVersion = "0.9.7" // https://github.com/JetBrains/skiko/releases
val workVersion = "2.10.1" // https://developer.android.com/jetpack/androidx/releases/work

val ktorVersion = "3.1.3"     // https://github.com/ktorio/ktor

val secSinceEpoch = Instant.now().epochSecond

plugins {
    //trick: for the same plugin versions in all sub-modulesly
    kotlin("multiplatform")
    id("com.android.application")
    kotlin("plugin.serialization").version("2.1.20")
    //id("org.jetbrains.kotlin.android").version("2.0.0")
    id("org.jetbrains.kotlin.plugin.compose").version("2.1.20")
    id("org.jetbrains.compose")   // https://github.com/JetBrains/compose-multiplatform/releases
    id("org.jetbrains.dokka").version("2.0.0").apply(false)
    id("org.jetbrains.kotlinx.kover")
    // id("org.openjfx.javafxplugin") version "0.1.0"
    idea
    // application  // for JVM executables, but not compatible with android, have to do it by hand
}

kover {
    useJacoco() // Jacoco ogging format for XML
    reports {
        // filters for all report types of all build variants
        filters {
            excludes {
                androidGeneratedClasses()
                packages("info.bitcoinunlimited.www.wally.databinding")
                packages("wpw.src.generated.resources")
            }
        }
    }
}

// Host determination
val LINUX = System.getProperty("os.name").lowercase().contains("linux")
val MAC = System.getProperty("os.name").lowercase().contains("mac")
val MSWIN = System.getProperty("os.name").lowercase().contains("windows")

// NOTE on your primary (first publish) system, you need to specify ALL targets as targets, even if this host does not
// publish them.  If they are not specified, the published .module file will not contain a definition for that target
// and so it will be as if it does not exist from a dependency perspective, even if you later publish the library from
// another host.
val LINUX_TARGETS = LINUX
val LINUX_NATIVE_TARGETS = false // not supported in compose
val MAC_TARGETS = MAC // || LINUX
// ktor network does not support ms windows so we cannot produce MSWIN right now
val MSWIN_TARGETS = MSWIN
val ANDROID_TARGETS = LINUX || MAC

if (MAC) println("Host is a MAC, MacOS and iOS targets are enabled")
if (LINUX) println("Host is LINUX, Android, JVM, and LinuxNative targets are enabled")
else println("Linux target is disabled")

if (MSWIN) println("Host is MS-WINDOWS")

if (!LINUX_TARGETS) println("Linux targets are disabled")
if (!MAC_TARGETS) println("MacOS and iOS targets are disabled")
if (!MSWIN_TARGETS) println("Ms-windows Mingw64 target is disabled")
if (!ANDROID_TARGETS) println("Android target is disabled")

val NATIVE_BUILD_CHOICE: NativeBuildType = NativeBuildType.DEBUG


fun org.jetbrains.kotlin.gradle.dsl.KotlinNativeBinaryContainer.libnexaBinCfg()
{
    /*
    executable {
        NATIVE_BUILD_CHOICE
    }
     */
    //sharedLib { NATIVE_BUILD_CHOICE }
    //staticLib { NATIVE_BUILD_CHOICE }
}

val prop = Properties().apply {
    try {
        load(FileInputStream(File(rootProject.rootDir, "local.properties")))
    } catch(e: java.io.FileNotFoundException)
    {
        File(rootProject.rootDir, "local.properties").writeText("### This file must NOT be checked into version control, since it contains local configuration.")
        load(FileInputStream(File(rootProject.rootDir, "local.properties")))
    }
}

configurations.all {
    // Check for updates every build
    resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.MILLISECONDS)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate()

    jvm {
        //withJava()
        mainRun {
            this.mainClass = "info.bitcoinunlimited.www.wally.WallyJvmApp"

        }
        tasks.withType<KotlinCompile>() {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
        compilations.getByName("main") {
        }

        //from { configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
        //from { configurations.jvmRuntimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
    }

    if (ANDROID_TARGETS)
    {
        androidTarget {
            tasks.withType<KotlinCompile>() {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }

            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            instrumentedTestVariant {
                sourceSetTree.set(KotlinSourceSetTree.test)

                dependencies {
                    testImplementation("androidx.compose.ui:ui-test-junit4-android:1.8.0")
                    androidTestImplementation("androidx.compose.ui:ui-test-junit4-android:1.8.0")
                    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.0")
                    // debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
                }
            }
        }
    }

    if (MAC_TARGETS)
    {
        /*
        cocoapods {
            summary = "Wally Personal Wallet shared logic"
            homepage = "www.wallywallet.org"
            version = "1.0"
            ios.deploymentTarget = "14.1"
            podfile = project.file("../iosApp/Podfile")
            framework {
                baseName = "shared"
            }
        }

         */

        val iosX64def = iosX64 {
            compilations.getByName("main") {
                //compilerOptions.options.freeCompilerArgs.add("-verbose")
                //compilerOptions.options.freeCompilerArgs.add("-opt-in=kotlin.experimental.ExperimentalNativeApi")
                compileTaskProvider {
                    compilerOptions {
                        freeCompilerArgs.addAll("-verbose", "-opt-in=kotlin.experimental.ExperimentalNativeApi")
                    }
                }
            }
        }
        val iosArm64def = iosArm64 {
            compilations.getByName("main") {
                compileTaskProvider {
                    compilerOptions {
                        freeCompilerArgs.addAll("-verbose", "-opt-in=kotlin.experimental.ExperimentalNativeApi")
                    }
                }
            }
        }

        /* commented out because in libnexalight.def doesn't know how to point to the .a file */
        val iosSimArm64def = iosSimulatorArm64 {
            compilations.getByName("main") {
                compileTaskProvider {
                    compilerOptions {
                        freeCompilerArgs.addAll("-verbose", "-opt-in=kotlin.experimental.ExperimentalNativeApi")
                    }
                }
            }
        }

        val iosSdkVersion: String by lazy {
            val process = ProcessBuilder("xcrun", "--show-sdk-version")
              .redirectErrorStream(true)
              .start()
            process.inputStream.bufferedReader().readText().trim()
        }
        println("iOS SDK version is: $iosSdkVersion")

        listOf(iosX64def, iosArm64def, iosSimArm64def).forEach {
            it.binaries.framework {
                baseName = "src" // Needs to be "src" so we can import the same module name in swift
                //linkerOpts("-platform_version ios 15.0 $iosSdkVersion")
                // -miphoneos-version-min=15.0
                // linkerOpts("""-compiler-option "-miphoneos-version-min=15.0"""")
            }
        }
    }

    /* Linux native targets are not supported in compose -- desktop is available via jvm
    if (LINUX_TARGETS)
    {
        linuxX64 {
            compilations.getByName("main") {
                compilerOptions.options.freeCompilerArgs.add("-verbose")
                //binaries.libnexaBinCfg()
            }
        }
    }

     */

    if (MSWIN_TARGETS)
    {
        // MS windows
        mingwX64 {
            compilations.getByName("main") {
                //compilerOptions.options.freeCompilerArgs.add("-verbose")
                compileTaskProvider {
                    compilerOptions {
                        freeCompilerArgs.addAll("-verbose")
                    }
                }
                target.binaries.libnexaBinCfg()
            }
        }
    }


    sourceSets {
        // All these "variable" definitions need corresponding directories (that's what "by getting" does)

        val commonMain by getting {
            dependencies {
                // core language features
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                // Compose
                implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")

                implementation("org.jetbrains.compose.material3:material3:$composeVersion")
                // implementation(compose.materialIconsExtended)
                //@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                api(compose.materialIconsExtended)
                // multiplatform replacements

                // for bigintegers
                implementation("com.ionspin.kotlin:bignum:$bigNumVersion")
                implementation("com.ionspin.kotlin:bignum-serialization-kotlinx:$bigNumVersion")

                // for network
                implementation("com.eygraber:uri-kmp:$uriKmpVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                // implementation("io.ktor:ktor-client-websockets:$ktorVersion")
                // implementation("io.ktor:ktor-client-logging:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                // implementation("io.ktor:ktor-utils:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

                // These deps don't exist on the mingw64 native target, which is why its disabled right now
                implementation("io.ktor:ktor-network:$ktorVersion")
                // implementation("io.ktor:ktor-network-tls:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")

                // IO
                implementation("com.squareup.okio:okio:3.11.0")
                implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")

                // nexa
                implementation("org.nexa:mpthreads:$mpThreadsVersion") {
                    isChanging = true
                }
                implementation("org.nexa:libnexakotlin:$libNexaKotlinVersion") { setChanging(true) }
                //implementation("org.nexa:walletoperations:0.0.1")

                // Generate and display Compose Multiplaform QR code
                implementation("io.github.alexzhirkevich:qrose:1.0.1")  // https://github.com/alexzhirkevich/qrose/releases

                // Animation library binding
                implementation("org.jetbrains.skiko:skiko:$skikoVersion")

                // Common ViewModel for all targets
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

                // Icons
                implementation(compose.materialIconsExtended)

                // Parse HTML from a string
                implementation("com.fleeksoft.ksoup:ksoup:0.2.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                //implementation(kotlin("LibNexaTests"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation("org.nexa:nexarpc:$nexaRpcVersion")
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }

        val osName = System.getProperty("os.name")
        val targetOs = when {
            osName == "Mac OS X" -> "macos"
            osName.startsWith("Win") -> "windows"
            osName.startsWith("Linux") -> "linux"
            else -> error("Unsupported OS: $osName")
        }

        val targetArch = when (val osArch = System.getProperty("os.arch")) {
            "x86_64", "amd64" -> "x64"
            "aarch64" -> "arm64"
            else -> error("Unsupported arch: $osArch")
        }

        val skikoTarget = "${targetOs}-${targetArch}"

        val jvmMain by getting {
            dependencies {
                // Strangely this appears to work on multiple platforms (win, macos) if the linux-built jar is copied to them
                implementation("org.jetbrains.skiko:skiko-awt-runtime-$skikoTarget:$skikoVersion")
                // These compose platform libs are necessary so that you can take the wpw.jar file and copy it to another
                // platform and run it.  DO NOT remove without running this manual test!
                // Note (when manually testing) that you also need the correct libnexa shared lib copied over.
                implementation(compose.desktop.common)
                implementation(compose.desktop.currentOs)
                implementation(compose.desktop.linux_x64)
                implementation(compose.desktop.linux_arm64)
                implementation(compose.desktop.windows_x64)
                implementation(compose.desktop.macos_x64)
                implementation(compose.desktop.macos_arm64)
                implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
                // Required for Dispatchers.Main
                // https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-main.html
                // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
                // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")

                // SVG rendering
                implementation("com.github.weisj:jsvg:2.0.0")

                // https://mvnrepository.com/artifact/org.openjfx/javafx-media
                //implementation("org.openjfx:javafx-media:17.0.10")
                //implementation("org.openjfx:javafx-graphics:17.0.10")
                //implementation("org.jetbrains.compose.ui:ui-compose-javafx:$composeVersion")
                //implementation("com.github.almasb:fxgl:21")
            }
        }


        if (MAC_TARGETS || MSWIN_TARGETS || LINUX_NATIVE_TARGETS)
        {
            // Common to all "native" targets
            val nativeMain by getting {
                // dependsOn(sourceSets.named("commonMain").get())
                dependencies {
                    // Compose
                    implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                    implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
                    implementation("org.jetbrains.compose.material3:material3:$composeVersion")
                }
            }
        }

        /*  don't know if I need this yet
        // Common to all JVM targets
        create("commonJvm") {
            kotlin.srcDir(mustExist("src/commonJvm/kotlin"))
            dependsOn(sourceSets.named("commonMain").get())
            dependencies {
            }
        }

         */

        if (ANDROID_TARGETS)
        {
            val androidMain by getting {
                //dependsOn(sourceSets.named("commonJvm").get())

                dependencies {
                    //implementation(project(":shared"))

                    // CameraX core library using the camera2 implementation
                    val camerax_version = "1.4.2"  // https://developer.android.com/jetpack/androidx/releases/camera
                    val lottieVersion = "6.6.6"

                    implementation(kotlin("stdlib-jdk8"))
                    implementation("androidx.activity:activity-compose:$androidxActivityComposeVersion")
                    implementation("androidx.tracing:tracing:1.3.0")
                    implementation("androidx.compose.ui:ui:1.8.1")
                    implementation("androidx.compose.ui:ui-tooling:1.8.1")
                    implementation("androidx.compose.ui:ui-tooling-preview:1.8.1")
                    implementation("androidx.compose.foundation:foundation:1.8.1")
                    implementation("androidx.compose.material:material:1.8.1")
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.8.1")
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")

                    // android layout dependencies
                    //implementation("com.google.android.flexbox:flexbox:3.0.0")  // https://github.com/google/flexbox-layout/tags
                    implementation("androidx.activity:activity:1.10.1")
                    //implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")  // https://developer.android.com/jetpack/androidx/releases/navigation
                    //implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
                    implementation("androidx.wear:wear:1.3.0")
                    //implementation("com.android.support.constraint:constraint-layout:2.1.4") // https://developer.android.com/jetpack/androidx/releases/constraintlayout
                    implementation("com.google.android.material:material:1.12.0")
                    //implementation("androidx.preference:preference:1.2.1")  // https://developer.android.com/jetpack/androidx/releases/preference

                    //implementation("org.jetbrains.skiko:skiko-android:$skikoVersion")
                    //implementation("org.jetbrains.skiko:skiko-android-runtime-x64:$skikoVersion")
                    //implementation("org.jetbrains.skiko:skiko-android-runtime-arm64:$skikoVersion")

                    // network access
                    implementation("io.ktor:ktor-client-core:$ktorVersion")
                    implementation("io.ktor:ktor-client-cio:$ktorVersion")
                    implementation("io.ktor:ktor-client-android:$ktorVersion")
                    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
                    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

                    // for bigintegers
                    implementation("com.ionspin.kotlin:bignum:0.3.10")
                    implementation("com.ionspin.kotlin:bignum-serialization-kotlinx:0.3.10")

                    // Background syncing
                    // java: implementation("androidx.work:work-runtime:$workVersion")
                    implementation("androidx.work:work-runtime-ktx:$workVersion")
                    // QR scanning (and read from gallery)
                    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
                    // Image file conversion
                    implementation("com.caverock:androidsvg-aar:1.4")

                    // This calls your own startup code with the app context (see AndroidManifest.xml)
                    //implementation("androidx.startup:startup-runtime:1.1.1")

                    // Camera
                    implementation("androidx.camera:camera-camera2:${camerax_version}")
                    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
                    implementation("androidx.camera:camera-view:${camerax_version}")
                    implementation("com.google.mlkit:barcode-scanning:17.3.0")

                    implementation("androidx.media3:media3-exoplayer:1.6.1")
                    // Dynamic Adaptive Streaming over HTTP: implementation("androidx.media3:media3-exoplayer-dash:1.X.X")
                    implementation("androidx.media3:media3-ui:1.6.1")

                    // Animation
                    implementation("com.airbnb.android:lottie-compose:$lottieVersion")

                    implementation("androidx.compose.material:material-icons-extended:1.7.8")
                }
            }
        }


        if (MAC_TARGETS)
        {
            val iosX64Main by getting {
                //dependsOn(sourceSets.named("commonNative").get())
                dependencies {
                }
            }

            val iosMain by getting {
                dependencies {
                }
            }

            val iosTest by getting {
                dependencies {

                }
            }
        }


        /* Linux native targets are not supported in compose -- desktop is available via jvm
        if (LINUX_TARGETS)
        {
            val linuxMain by getting {
                dependencies {
                }
            }
            val linuxX64Main by getting {
                dependsOn(sourceSets.named("linuxMain").get())
                dependencies {
                }
            }
        }
         */


        if (MSWIN_TARGETS)
        {
            val mingwMain by getting {
                dependencies {
                    implementation("app.cash.sqldelight:native-driver:2.0.2")
                }
            }
        }


        if (ANDROID_TARGETS)
        {
            val androidInstrumentedTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                    implementation("org.nexa:nexarpc:$nexaRpcVersion")
                    implementation("org.nexa:nexarpc:$nexaRpcVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
                    implementation("androidx.test:core-ktx:1.6.1")
                    implementation("androidx.test.ext:junit-ktx:1.2.1")
                }
            }
        }

        all {
            nexaLangSettings()
            languageSettings {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.ExperimentalUnsignedTypes")
            }
        }
    }


/*
    // Stop publication duplication
    val publicationsFromLinuxOnly:MutableList<String> =
      mutableListOf(jvm(), androidTarget()).map { it.name }.toMutableList()
    // if (LINUX_TARGETS) publicationsFromLinuxOnly.add(linuxX64().name)
    publicationsFromLinuxOnly.add("kotlinMultiplatform")
    publicationsFromLinuxOnly.add("androidDebug")
    publicationsFromLinuxOnly.add("androidRelease")
    if (MSWIN_TARGETS) publicationsFromLinuxOnly.add(mingwX64().name)

    publishing {
        publications {
            matching { val name = it.name; publicationsFromLinuxOnly.filter { it in name }.size > 0 }.all {
                tasks.withType<AbstractPublishToMaven>()
                  .matching {
                      val pub = it.publication
                      if (pub != null) {
                          pub.name in publicationsFromLinuxOnly
                      } else false
                  }
                  .configureEach { onlyIf { LINUX } }
            }
        }
    }
*/
}

val gitCommitHash: String by lazy {
    ProcessBuilder("git", "rev-parse", "--short", "HEAD")
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .start()
      .inputStream
      .bufferedReader()
      .readText()
      .trim()
}

val nowDateTime: String by lazy {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    LocalDateTime.now().format(formatter)
}

version = "$versionNumber-$gitCommitHash"

android {
    namespace = "info.bitcoinunlimited.www.wally"
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")
    compileSdk = 35
    defaultConfig {
        applicationId = "info.bitcoinunlimited.www.wally"
        minSdk = 29
        targetSdk = 35
        versionCode = androidVersionCode
        versionName = versionNumber
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices.devices {
            maybeCreate<ManagedVirtualDevice>("pixel5").apply {
                device = "Pixel 5"
                apiLevel = 34
                systemImageSource = "aosp"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "BUILD_TIME", "\"${secSinceEpoch}\"")
            ndk.debugSymbolLevel = "FULL"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "BUILD_TIME", "\"${secSinceEpoch}\"")
            ndk.debugSymbolLevel = "FULL"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    /*
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
    }
     */
    lint {
        abortOnError = false // Prevents Lint from failing the build
        warningsAsErrors = false // Ensures warnings don't fail the build
    }
}


if (MAC_TARGETS)
{
    task("iosTest") {
        val device = project.findProperty("iosDevice")?.toString() ?: "iPhone 14 Pro Max"
        dependsOn(kotlin.iosX64().binaries.getTest("DEBUG").linkTaskName)
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Runs tests for target 'ios' on an iOS simulator"

        doLast {
            val binary = kotlin.iosX64().binaries.getTest("DEBUG").outputFile
            exec {
                commandLine = listOf("xcrun", "simctl", "spawn", device, binary.absolutePath)
            }
        }
    }
}

fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.nexaLangSettings()
{
    languageSettings {
        languageVersion = "1.9"
    }
}

tasks.register("generateVersionFile") {
    doLast {
        println("Generating... src/commonMain/kotlin/Version.kt")
        val file = file("src/commonMain/kotlin/Version.kt")
        file.writeText("""
            package info.bitcoinunlimited.www.wally
            
            import info.bitcoinunlimited.www.wally.ui.VersionI
            
            object Version: VersionI
            {
                override val VERSION = "${project.version}"
                override val VERSION_NUMBER = "$versionNumber"
                override val GIT_COMMIT_HASH = "$gitCommitHash"
                override val GITLAB_URL = "https://gitlab.com/wallywallet/wallet/-/commit/$gitCommitHash"
                override val BUILD_DATE = "${nowDateTime}"
            }
        """.trimIndent())
    }

    if(MAC)
        dependsOn("updateCFBundleShortVersionString")
}

tasks.named("preBuild").configure {
    dependsOn("generateVersionFile")
}

// Task to update the iOS version based on versionNumber
tasks.register<Exec>("updateCFBundleShortVersionString") {
    val plistFile = file("../iosApp/iosApp/Info.plist")

    // Use PlistBuddy to set the CFBundleShortVersionString
    commandLine("/usr/libexec/PlistBuddy", plistFile.absolutePath, "-c", "Set :CFBundleShortVersionString $versionNumber")
}

tasks.named("compileKotlinMetadata").configure {
    dependsOn("generateVersionFile")
}

/*
// PUBLISHING
// Deployment constants
group = "org.nexa"

publishing {
    repositories {
        maven {
            // Project ID number is shown just below the project name in the project's home screen
            url = uri("https://gitlab.com/api/v4/projects/48545045/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Deploy-Token"
                value = prop.getProperty("WallyPersonalWalletDeployTokenValue")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

 */

// Stop android studio from indexing the contrib folder
idea {
    module {
    }
}

/*
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
*/


println("JAR Tasks:")
for (t in project.tasks.withType<Jar>())
{
    println("  ${t.name}")
}
println("Kotlin Sourcesets:")
for (s in kotlin.sourceSets)
{
    println("    ${s.name}")
}
println("Kotlin targets:")

for (s in kotlin.targets)
{
    println("    ${s.name}")
}


/* Put all the dependent files into a single big jar */
tasks.register<Jar>("appJar") {
    //archiveClassifier.set("app")
    //archiveBaseName.set("wpw")
    archiveFileName.set("wpw.jar")
    manifest {
        attributes["Main-Class"] = "info.bitcoinunlimited.www.wally.WallyJvmApp"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    for (c in kotlin.targets.named("jvm").get().compilations)
    {
        //println("  ${c.output}")
        // if (c.compileDependencyFiles != null) from(c.compileDependencyFiles)
        val tmp = c.runtimeDependencyFiles
        if (tmp != null)
        {
            //for (f in tmp.files)
            //    println("    $f")
            // If its a jar blow the jar up and add the class files in that jar
            from(tmp.filter { it.name.endsWith("jar") }.map { zipTree(it)})
        }
        from(c.output)
    }
    from(kotlin.sourceSets.named("commonMain").get().resources)
}

tasks {
    register<Exec>("runJvmApp") {
        commandLine("java", "-classpath", "build/libs/wpw.jar", "info.bitcoinunlimited.www.wally.WallyJvmApp")
    }

    /* TODO attempt to clean up file attributes before signing */
    /*
    named("embedAndSignAppleFrameworkForXcode") {
            doFirst {
                exec {
                    workingDir("/Users/stone/Desktop/git.nosync/wpw/build")
                    commandLine("xattr", "-rl", ".")
                }
                exec {
                    workingDir("/Users/stone/Desktop/git.nosync/wpw/build")
                    commandLine("echo", "Cleaning file attributes")
                }
                exec {
                    workingDir("/Users/stone/Desktop/git.nosync/wpw/build")
                    commandLine("xattr", "-rc", ".")
                }
                exec {
                    workingDir("/Users/stone/Desktop/git.nosync/wpw/build")
                    commandLine("xattr", "-rl", ".")
                }
            }

        doLast {
            exec {
                workingDir("/Users/stone/Desktop/git.nosync/wpw/build")
                commandLine("echo", "POST SIGN")
            }
            exec {
                workingDir("/Users/stone/Desktop/git.nosync/wpw/build")
                commandLine("xattr", "-rl", ".")
            }
        }
    }
     */
}

/* same as the above but uses some plugin
tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("appJar") {
    archiveClassifier.set("app")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    for (c in kotlin.targets.named("jvm").get().compilations)
    {
        if (c.runtimeDependencyFiles != null) from(c.runtimeDependencyFiles)
        from(c.output)
    }
}
 */

fun mustExist(dir:String):File
{
    val f = file(dir)
    if (!f.exists()) throw Exception("missing $f")
    return f
}

fun prjFileMustExist(path:String):File
{
    val f = project.file(path)
    if (!f.exists()) throw Exception("missing $f")
    return f
}

kotlin {
    sourceSets {
        getByName("commonTest") {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
    }
    jvmToolchain(17)
}

// Restrict test coverage reporting using the Kover library to Android and JVM targets
kover {
    sourceSets {
        listOf("androidMain", "jvmMain")
    }
}

// Configure some tests for easy access

// Always rerun this task (but it won't exist on android systems)
kotlin.runCatching {
    tasks.named("iosSimulatorArm64Test") {
        group = "wallyTest"
        description = "Run the UI tests on iOS simulator"
        outputs.upToDateWhen { false }
    }
}

// To see these tasks in Android Studio, go to settings->experimental and check "Configure All Gradle Tasks During Gradle Sync"
tasks.register("testJvmUI") {
    group = "wallyTest"
    description = "Run the UI tests on the JVM"
    dependsOn("jvmTest")
    outputs.upToDateWhen { false }
}

// you cannot modify these tasks so I create a wrapper I can modify
tasks.register("testAndroidUI") {
    group = "wallyTest"
    description = "Run the UI tests on a real android phone"
    dependsOn("connectedAndroidTest")
    outputs.upToDateWhen { false }
    // systemProperty("testSlowdown", "4000")
}

// you cannot modify these tasks so I create a wrapper I can modify
tasks.register("compileIos") {
    group = "gitlab CI"
    description = "Run the gitlab CI equivalent"
    dependsOn("compileKotlinIosArm64","compileKotlinIosSimulatorArm64", "compileKotlinIosX64", "iosArm64MetadataElements", "iosSimulatorArm64MetadataElements", "iosX64MetadataElements")
}

// makes the standard streams (err and out) visible at console when running tests
tasks.withType<Test> {
    testLogging {
        events("started", "passed", "failed", "skipped")
        showStandardStreams = true
    }
    outputs.upToDateWhen { false }  // Always rerun test tasks
}