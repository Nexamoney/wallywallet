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
val versionNumber = "3.9.02"
val androidVersionCode = versionNumber.replace(".", "").toInt()

val secSinceEpoch = Instant.now().epochSecond

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)  // Compose compiler
    alias(libs.plugins.compose)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kover)
    idea
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
        androidTarget {
            tasks.withType<KotlinCompile>() {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }

            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            instrumentedTestVariant {
                sourceSetTree.set(KotlinSourceSetTree.test)
            }
            /*
            dependencies {
                androidTestImplementation("androidx.compose.ui:ui-test-junit4-android:1.9.0")
                debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0")
            }
             */
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

                dependencies {
                    //implementation(project(":shared"))

                    implementation(kotlin("stdlib"))
                    implementation(libs.androidx.activity.compose)
                    implementation(libs.androidx.tracing)
                    implementation(libs.androidx.ui)
                    implementation(libs.androidx.ui.tooling)
                    implementation(libs.androidx.ui.tooling.preview)
                    implementation(libs.androidx.foundation)
                    implementation(libs.androidx.material)
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
                    implementation(libs.androidx.material.icons.extended)

                    // This is only for pulling in the android photo picker
                    implementation(libs.play.services.base)
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
                    implementation(libs.native.driver)
                }
            }
        }

        if (ANDROID_TARGETS)
        {
            val androidInstrumentedTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                    implementation(libs.nexa.rpc)
                    implementation(libs.kotlinx.coroutines.test)
                    implementation(libs.kotlinx.coroutines.android)
                    implementation(libs.androidx.core.ktx)
                    implementation(libs.androidx.junit.ktx)
                    implementation(libs.androidx.ui.test.junit4.android)
                    implementation(libs.ui.test.manifest)
                    // debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
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
    compileSdk = libs.versions.androidSdk.get().toInt()
    defaultConfig {
        applicationId = "info.bitcoinunlimited.www.wally"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidSdk.get().toInt()
        versionCode = androidVersionCode
        versionName = versionNumber
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
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
                apiLevel = libs.versions.androidSdk.get().toInt()
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
    jvmToolchain(21)
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