import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Wally Wallet version: see wallyApp in gradle/libs.versions.toml
val versionNumber = libs.versions.wallyApp.get()

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)  // Compose compiler
    alias(libs.plugins.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.mokkery)
    idea
}

mokkery {
    // Allow mocking abstract classes (e.g. libnexakotlin's CnxnMgr) that have final
    // members. The mokkery proxy ignores those final members at the type-checker
    // level; the test path doesn't actually call them.
    ignoreFinalMembers = true
}

// Keep the historical generated-resources package from when this module was named "src";
// renaming it would break every wpw.src.generated.resources import in commonMain
compose.resources {
    packageOfResClass = "wpw.src.generated.resources"
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
        tasks.withType<KotlinCompile>() {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
        compilations.getByName("main") {
        }

        //from { configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
        //from { configurations.jvmRuntimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
    }

    if (ANDROID_TARGETS)
    {
        android {
            namespace = "info.bitcoinunlimited.www.wally"
            compileSdk = libs.versions.androidSdk.get().toInt()
            minSdk = libs.versions.androidMinSdk.get().toInt()

            androidResources.enable = true

            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }

            // Join the common "test" source set tree so both android test compilations compile
            // commonTest (UI tests + LeakAssertions expect), like the old sourceSetTree setting
            withHostTestBuilder {
                sourceSetTreeName = "test"
            }
            withDeviceTestBuilder {
                sourceSetTreeName = "test"
            }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                // LeakCanary heap analysis after each instrumented test is slow (~5-10s/test on the
                // emulator), so it is opt-in. The androidDeviceTest LeakAssertions actual only runs
                // the analysis when this argument is "true". CI's androidPixel5TestLeakDetection job sets
                // LEAK_DETECTION=true; the plain androidPixel5Test job leaves it off for a fast run.
                instrumentationRunnerArguments["leakDetection"] = System.getenv("LEAK_DETECTION") ?: "false"
                managedDevices {
                    localDevices.create("pixel5") {
                        device = "Pixel 5"
                        apiLevel = libs.versions.androidTargetSdk.get().toInt()
                        systemImageSource = "aosp"
                    }
                }
            }

            lint {
                abortOnError = false // Prevents Lint from failing the build
                warningsAsErrors = false // Ensures warnings don't fail the build
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

        /*
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
        }*/
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

        listOf(iosArm64def, iosSimArm64def).forEach {
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
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.datetime)
                // Compose
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                // implementation(compose.materialIconsExtended)
                //@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                api(compose.materialIconsExtended)

                implementation(libs.ui.tooling.preview)
                // multiplatform replacements

                // for bigintegers
                implementation(libs.bignum)
                implementation(libs.bignum.serialization.kotlinx)

                // for network
                implementation(libs.uri.kmp)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.serialization)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                // These deps don't exist on the mingw64 native target, which is why its disabled right now
                implementation(libs.ktor.network)
                implementation(libs.ktor.client.cio)

                // IO
                implementation(libs.okio)
                implementation(libs.atomicfu)

                // nexa
                implementation(libs.nexa.mpthreads)
                implementation(libs.nexa.libnexakotlin)
                // libnexakotlin 0.7.0 split the @cli/Display annotations into their own
                // library, and depends on it at runtime only, so declare it to compile.
                implementation(libs.nexa.nexacli)
                //implementation("org.nexa:walletoperations:0.0.1")

                // Generate and display Compose Multiplaform QR code
                implementation(libs.qrose)  // https://github.com/alexzhirkevich/qrose/releases

                // Animation library binding
                implementation(libs.skiko)

                // Common ViewModel for all targets
                implementation(libs.lifecycle.viewmodel.compose)

                // Icons
                implementation(compose.materialIconsExtended)

                // Parse HTML from a string
                implementation(libs.ksoup)

                implementation(compose.components.resources)

                // Lottie animation library wrapper library
                implementation(libs.compottie)
                implementation(libs.compottie.dot)
                implementation(libs.compottie.network)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.nexa.rpc)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }

        val jvmMain by getting {
            dependencies {
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
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core.jvm)
                // Required for Dispatchers.Main
                // https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-main.html
                // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
                // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:$coroutinesVersion")
                implementation(libs.kotlinx.coroutines.swing)

                // SVG rendering
                implementation(libs.jsvg)

                // https://mvnrepository.com/artifact/org.openjfx/javafx-media
                //implementation("org.openjfx:javafx-media:17.0.10")
                //implementation("org.openjfx:javafx-graphics:17.0.10")
                //implementation("org.jetbrains.compose.ui:ui-compose-javafx:$composeVersion")
                //implementation("com.github.almasb:fxgl:21")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }


        if (MAC_TARGETS || MSWIN_TARGETS || LINUX_NATIVE_TARGETS)
        {
            // Common to all "native" targets
            val nativeMain by getting {
                // dependsOn(sourceSets.named("commonMain").get())
                dependencies {
                    // Compose
                    implementation(libs.compose.runtime)
                    implementation(libs.compose.foundation)
                    implementation(libs.compose.material3)
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
                resources.srcDir("src/commonMain/resources")

                dependencies {
                    //implementation(project(":shared"))

                    implementation(kotlin("stdlib"))
                    implementation(libs.androidx.activity.compose)
                    implementation(libs.androidx.tracing)
                    implementation(libs.kotlinx.serialization.cbor)
                    implementation(libs.kotlinx.serialization.json.jvm)

                    // android layout dependencies
                    implementation(libs.androidx.activity)
                    implementation(libs.androidx.wear)
                    implementation(libs.material)

                    //implementation("org.jetbrains.skiko:skiko-android:$skikoVersion")
                    //implementation("org.jetbrains.skiko:skiko-android-runtime-x64:$skikoVersion")
                    //implementation("org.jetbrains.skiko:skiko-android-runtime-arm64:$skikoVersion")

                    // network access
                    implementation(libs.ktor.client.core)
                    implementation(libs.ktor.client.cio)
                    implementation(libs.ktor.client.android)
                    implementation(libs.ktor.client.serialization)
                    implementation(libs.ktor.serialization.kotlinx.json)
                    implementation(libs.ktor.client.content.negotiation)

                    // for bigintegers
                    implementation(libs.bignum)
                    implementation(libs.bignum.serialization.kotlinx)

                    // Background syncing
                    // java: implementation("androidx.work:work-runtime:$workVersion")
                    implementation(libs.androidx.work.runtime.ktx)
                    // QR scanning (and read from gallery)
                    implementation(libs.zxing.android.embedded)
                    // Image file conversion
                    implementation(libs.androidsvg.aar)

                    // This calls your own startup code with the app context (see AndroidManifest.xml)
                    //implementation("androidx.startup:startup-runtime:1.1.1")

                    // Camera
                    implementation(libs.androidx.camera.camera2)
                    implementation(libs.androidx.camera.lifecycle)
                    implementation(libs.androidx.camera.view)
                    implementation(libs.barcode.scanning)

                    implementation(libs.androidx.media3.exoplayer)
                    // Dynamic Adaptive Streaming over HTTP: implementation("androidx.media3:media3-exoplayer-dash:1.X.X")
                    implementation(libs.androidx.media3.ui)

                    // Animation
                    implementation(libs.lottie.compose)

                    // This is only for pulling in the android photo picker
                    implementation(libs.play.services.base)

                    // Play audio from resources file: https://medium.com/@brucemax/play-sounds-in-kotlin-multiplatform-using-multiplatform-resources-1b23716116d5
                    implementation(libs.androidx.media3.exoplayer)

                    implementation(libs.androidx.core.splashscreen)
                }
            }
        }


        if (MAC_TARGETS)
        {
            /*
            val iosX64Main by getting {
                //dependsOn(sourceSets.named("commonNative").get())
                dependencies {
                }
            }*/

            val iosMain by getting {
                dependencies {
                    implementation(libs.ktor.client.darwin)
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
                    implementation(libs.native.driver)
                }
            }
        }

        if (ANDROID_TARGETS)
        {
            val androidDeviceTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                    implementation(libs.nexa.rpc)
                    implementation(libs.kotlinx.coroutines.test)
                    implementation(libs.kotlinx.coroutines.android)
                    implementation(libs.androidx.core.ktx)
                    implementation(libs.androidx.junit.ktx)
                    implementation(libs.androidx.ui.test.junit4.android)
                    // Host activity for runComposeUiTest in the self-instrumenting test APK
                    implementation(libs.ui.test.manifest)
                    // LeakCanary: LeakAssertions / DetectLeaksAfterTestSuccess for instrumented tests.
                    implementation("com.squareup.leakcanary:leakcanary-android:2.14")
                    implementation("com.squareup.leakcanary:leakcanary-android-instrumentation:2.14")
                }
            }

            val androidHostTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
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


if (MAC_TARGETS)
{
    tasks.register<Exec>("xcrun_simctl") {
        val device = project.findProperty("iosDevice")?.toString() ?: "iPhone 14 Pro Max"
        val binary = kotlin.iosArm64().binaries.getTest("DEBUG").outputFile
        commandLine("xcrun", "simctl", "spawn", device, binary.absolutePath)
    }

    task("iosTest") {
        val device = project.findProperty("iosDevice")?.toString() ?: "iPhone 14 Pro Max"
        dependsOn(kotlin.iosArm64().binaries.getTest("DEBUG").linkTaskName)
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Runs tests for target 'ios' on an iOS simulator"

        finalizedBy("xcrun_simctl")
        /*
        doLast {
            val binary = kotlin.iosX64().binaries.getTest("DEBUG").outputFile
            project.execOperations.exec {
                commandLine = listOf("xcrun", "simctl", "spawn", device, binary.absolutePath)
            }
        }
         */
    }
}

fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.nexaLangSettings()
{
    languageSettings {
        languageVersion = "2.2"
    }
}

tasks.register("generateVersionFile") {
    doLast {
        println("Generating... src/commonMain/kotlin/Version.kt")
        val file = file("src/commonMain/kotlin/Version.kt")
        file.writeText("""
            package info.bitcoinunlimited.www.wally
                        
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

// The KMP android library plugin has no preBuild, so hook the android compilation directly
// (mirrors the old preBuild dependsOn generateVersionFile)
tasks.matching { it.name == "compileAndroidMain" }.configureEach {
    dependsOn("generateVersionFile")
    dependsOn("generateI18nFiles")
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


/**
 *  i18n
 *  Source of truth: ../i18n/res/values/strings.xml  (English, defines all keys)
 *  Translations:   ../i18n/res/values-<lang>/strings.xml
 *
 *  On every build this task:
 *    1. Reads the XML files and sorts keys alphabetically
 *    2. Writes one strings_<lang>.bin per locale (null-terminated UTF-8 strings,
 *       indexed by position, must stay in sync with strings.kt)
 *    3. Writes strings.kt (the S object) so app code references S.someKey
 *       as a stable Int that resolves to the right position in the .bin
 *
 *  Add a locale: add its code to i18nLangs + create values-<lang>/strings.xml
 *  Add a string:  add it to values/strings.xml (+ translations), then rebuild
 *
 */

val i18nLangs   = listOf("ca", "de", "es", "et", "fr", "hi", "in", "it", "ko", "nb", "no", "round", "sl", "sv", "tr")
val i18nPackage = "info.bitcoinunlimited.www.wally"
val i18nXmlDir  = file("../i18n/res")
val i18nRawDir  = layout.buildDirectory.dir("generated/i18n/androidMain/res/raw")
val i18nResDir  = layout.buildDirectory.dir("generated/i18n/commonMain/resources")
val i18nKtDir   = layout.buildDirectory.dir("generated/i18n/commonMain/kotlin")

fun readStringsXml(f: File): Map<String, String> {
    val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().parse(f)
    val nodes = doc.getElementsByTagName("resources").item(0).childNodes
    return buildMap {
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeName == "string") {
                val name = n.attributes.getNamedItem("name").textContent
                val v = n.textContent
                    .replace("\\n", "\n").replace("\\'", "'")
                    .replace("\\\"", "\"").replace("\\r", "\r")
                put(name, v)
            }
        }
    }
}

/** Writes one locale pack; returns how many keys had to fall back to English. */
fun writeBin(outDir: File, langCode: String, keys: List<String>, xlat: Map<String, String>, fallback: Map<String, String>): Int {
    var missing = 0
    outDir.resolve("strings_$langCode.bin").outputStream().use { out ->
        for (key in keys) {
            val v = xlat[key]
                ?: fallback[key].also { missing++ }
                ?: error("[i18n] '$key' is missing from values/strings.xml")
            out.write(v.toByteArray())
            out.write(0)
        }
    }
    return missing
}

val generateI18nFiles = tasks.register("generateI18nFiles") {
    group = "i18n"
    description = "Compiles XML locale files --> .bin packs + strings.kt"

    // Capture everything the action needs up front: touching the Project at execution
    // time is what makes a task configuration-cache incompatible.
    val xmlDir = i18nXmlDir
    val langs  = i18nLangs
    val pkg    = i18nPackage
    val rawOut = i18nRawDir.get().asFile
    val resOut = i18nResDir.get().asFile
    val ktOut  = i18nKtDir.get().asFile

    // Only the locale XML is an input; ../i18n/res also holds drawables and mipmaps
    // that have nothing to do with string generation.
    inputs.files(fileTree(xmlDir) { include("values/strings.xml", "values-*/strings.xml") })
        .withPropertyName("localeXml")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("langs", langs)
    outputs.dir(rawOut)
    outputs.dir(resOut)
    outputs.dir(ktOut)

    doLast {
        rawOut.mkdirs()
        resOut.mkdirs()
        ktOut.mkdirs()

        val base = readStringsXml(xmlDir.resolve("values/strings.xml"))
        val keys = base.keys.sorted()

        writeBin(rawOut, "en", keys, base, base)
        writeBin(resOut, "en", keys, base, base)

        val untranslated = linkedMapOf<String, Int>()
        for (lang in langs) {
            val xlat = readStringsXml(xmlDir.resolve("values-$lang/strings.xml"))
            writeBin(rawOut, lang, keys, xlat, base)
            val missing = writeBin(resOut, lang, keys, xlat, base)
            if (missing > 0) untranslated[lang] = missing
        }

        val kt = buildString {
            appendLine("package $pkg")
            appendLine("object S {")
            keys.forEachIndexed { idx, key -> appendLine("    val $key: Int = $idx") }
            append("}")
        }
        ktOut.resolve("strings.kt").writeText(kt)
        println("[i18n] Generated ${keys.size} keys → strings.kt  |  locales: en, ${langs.joinToString()}")
        if (untranslated.isNotEmpty()) {
            println("[i18n] Falling back to English for untranslated keys: " +
                untranslated.entries.joinToString { "${it.key}=${it.value}" })
        }
    }
}

kotlin.sourceSets.getByName("commonMain").let { cm ->
    cm.kotlin.srcDir(i18nKtDir)
    cm.resources.srcDir(i18nResDir)
}

// The KMP android library plugin has no sourceSets["main"].res srcDir DSL, so the
// generated res dir joins the android variant through the components API instead.
androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory(
            i18nRawDir.get().asFile.parentFile.apply { mkdirs() }.path
        )
    }
}

/*
 * Adding the generated dirs as srcDirs does NOT give Gradle a producer for them -- the
 * Compose resource pipeline sits between the source set and *ProcessResources, so the
 * dependency cannot be inferred from the source set alone. Without these, the resource
 * and metadata tasks consume the generated directory with no ordering guarantee, which
 * fails Gradle's validation on JVM/iOS and can package empty locale data.
 */
tasks.matching { t ->
    // Every Kotlin compile task -- including compileCommonMainKotlinMetadata, which a
    // "compileKotlin*" prefix match misses.
    (t.name.startsWith("compile") && t.name.contains("Kotlin")) ||
    // Every task that consumes the resources. Matching on name rather than type is
    // deliberate: the consumers span unrelated types (ProcessResources for JVM/Android,
    // SyncComposeResourcesForIosTask for the Xcode build, plus the Compose assemble and
    // aggregate steps), and enumerating types means rediscovering each omission as a
    // build failure on a different target.
    t.name.contains("Resources")
}.configureEach {
    dependsOn(generateI18nFiles)
}

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


tasks {
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
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add(
            "-Xannotation-default-target=param-property"
        )
    }
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

