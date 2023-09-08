import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.util.Properties
import java.io.FileInputStream
import java.time.Instant
import java.net.URL

// Dependency versions
val mpThreadsVersion = "0.1.7"
val nexaRpcVersion = "1.1.3"
val libNexaKotlinVersion = "0.0.13"

val serializationVersion = "1.6.0"  // https://github.com/Kotlin/kotlinx.serialization
val coroutinesVersion = "1.7.3"     // https://github.com/Kotlin/kotlinx.coroutines
val ktorVersion = "2.3.3"           // https://github.com/ktorio/ktor
val bigNumVersion = "0.3.8"
val composeVersion = "1.5.0"
val androidxActivityComposeVersion = "1.7.2"

val secSinceEpoch = Instant.now().epochSecond

plugins {
    //trick: for the same plugin versions in all sub-modules
    kotlin("multiplatform").version("1.9.0")
    id("com.android.application") // .version("8.1.1")
    //id("com.android.library").version("8.1.1")
    //id("org.jetbrains.kotlin.native.cocoapods") version "1.9.0"
    //kotlin("native.cocoapods")
    kotlin("plugin.serialization").version("1.9.0")
    id("org.jetbrains.compose").version("1.5.0")
    //id("maven-publish")
    id("org.jetbrains.dokka").version("1.8.20").apply(false)
    // id("com.github.johnrengelman.shadow").version("8.1.1")
    idea
    // application  // for JVM executables, but not compatible with android, have to do it by hand
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
var MSWIN_TARGETS = false

if (MAC) println("Host is a MAC, MacOS and iOS targets are enabled")
if (LINUX) println("Host is LINUX, Android, JVM, and LinuxNative targets are enabled")
else println("Linux target is disabled")

if (MSWIN) { println("Host is MS-WINDOWS"); MSWIN_TARGETS = true }

if (!LINUX_TARGETS) println("Linux targets are disabled")
if (!MAC_TARGETS) println("MacOS and iOS targets are disabled")
if (!MSWIN_TARGETS) println("Ms-windows Mingw64 target is disabled")

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

// Define a few local variables
ext {
    var linuxToolchain = prop["linux.toolchain"]
    var linuxTarget = prop["linux.target"]
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()

    jvm {
        //withJava()
        mainRun {
            this.mainClass = "info.bitcoinunlimited.www.wally.WallyJvmApp"

        }
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
        compilations.getByName("main") {
        }


        //from { configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
        //from { configurations.jvmRuntimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
    }


    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
        // publishLibraryVariants("release", "debug")
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
                compilerOptions.options.freeCompilerArgs.add("-verbose")
                //binaries.libnexaBinCfg()
            }
        }
        val iosArm64def = iosArm64 {
            compilations.getByName("main") {
                compilerOptions.options.freeCompilerArgs.add("-verbose")
                //binaries.libnexaBinCfg()
            }
        }

        /* commented out because in libnexalight.def doesn't know how to point to the .a file */
        val iosSimArm64def = iosSimulatorArm64 {
            compilations.getByName("main") {
                compilerOptions.options.freeCompilerArgs.add("-verbose")
                //binaries.libnexaBinCfg()
            }
        }

        listOf(iosX64def, iosArm64def, iosSimArm64def).forEach {
            it.binaries.framework {
                baseName = "src"
            }
        }

        macosX64 {
            /*
            binaries {
                executable {
                    entryPoint = "main"
                }
            }*/
            compilations.getByName("main") {
                compilerOptions.options.freeCompilerArgs.add("-verbose")
                //binaries.libnexaBinCfg()
            }
        }

        macosArm64 {
            compilations.getByName("main") {
                compilerOptions.options.freeCompilerArgs.add("-verbose")
                //binaries.libnexaBinCfg()
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
                compilerOptions.options.freeCompilerArgs.add("-verbose")
                binaries.libnexaBinCfg()
            }
        }
    }


    sourceSets {
        // All these "variable" definitions need corresponding directories (that's what "by getting" does)

        val commonMain by getting {
            dependencies {
                // core language features
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

                // Compose
                implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
                implementation("org.jetbrains.compose.material3:material3:$composeVersion")

                // multiplatform replacements

                // for bigintegers
                implementation("com.ionspin.kotlin:bignum:$bigNumVersion")
                implementation("com.ionspin.kotlin:bignum-serialization-kotlinx:$bigNumVersion")

                // for network
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

                implementation("com.squareup.okio:okio:3.1.0")
                implementation("org.jetbrains.kotlinx:atomicfu:0.21.0")

                // nexa
                implementation("org.nexa:mpthreads:$mpThreadsVersion")
                implementation("org.nexa:libnexakotlin:$libNexaKotlinVersion")
                //implementation("org.nexa:walletoperations:0.0.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                //implementation(kotlin("LibNexaTests"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                // implementation("org.nexa:NexaRpc:1.1.0")
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

        val skikoVersion = "0.7.77" // or any more recent version
        val skikoTarget = "${targetOs}-${targetArch}"

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                // Strangely this appears to work on multiple platforms (win, macos) if the linux-built jar is copied to them
                implementation("org.jetbrains.skiko:skiko-awt-runtime-$skikoTarget:$skikoVersion")
                implementation(compose.desktop.currentOs)
                implementation(compose.desktop.linux_x64)
                implementation(compose.desktop.linux_arm64)
                implementation(compose.desktop.windows_x64)
                implementation(compose.desktop.macos_x64)
                implementation(compose.desktop.macos_arm64)
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
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

        val androidMain by getting {
            //dependsOn(sourceSets.named("commonJvm").get())
            dependencies {
                //implementation(project(":shared"))

                implementation(kotlin("stdlib-jdk8"))
                implementation("androidx.activity:activity-compose:$androidxActivityComposeVersion")

                implementation("androidx.compose.ui:ui:1.5.0")
                implementation("androidx.compose.ui:ui-tooling:1.5.0")
                implementation("androidx.compose.ui:ui-tooling-preview:1.5.0")
                implementation("androidx.compose.foundation:foundation:1.5.0")
                implementation("androidx.compose.material:material:1.5.0")
                implementation("androidx.activity:activity-compose:1.7.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.5.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.0")

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

                // This calls your own startup code with the app context (see AndroidManifest.xml)
                //implementation("androidx.startup:startup-runtime:1.1.1")
            }
        }

        /*
        val jvmMain by getting {
            //dependsOn(sourceSets.named("commonJvm").get())
            dependencies {
            }
        }
         */

        if (MAC_TARGETS)
        {
            val macosX64Main by getting {
                // dependsOn(sourceSets.named("commonNative").get())
                dependencies {
                    // implementation("app.cash.sqldelight:native-driver:2.0.0")
                }
            }

            val iosX64Main by getting {
                //dependsOn(sourceSets.named("commonNative").get())
                dependencies {
                    // implementation("app.cash.sqldelight:native-driver:2.0.0")
                }
            }

            val iosMain by getting {
                dependencies {
                    // implementation("app.cash.sqldelight:native-driver:2.0.0")
                    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-ios:$coroutinesVersion")
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
                    implementation("app.cash.sqldelight:native-driver:2.0.0")
                }
            }
        }


        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.nexa:nexarpc:$nexaRpcVersion")
                implementation("androidx.test:core:1.5.0")
                implementation("androidx.test:core-ktx:1.5.0")
                implementation("androidx.test.ext:junit:1.1.5")
                implementation("androidx.test.ext:junit-ktx:1.1.5")

                 // androidTestImplementation 'androidx.test:support-annotations:24.0.0'
                implementation("androidx.test.espresso:espresso-contrib:3.5.1")
                // for intent mocking
                implementation("androidx.test.espresso:espresso-intents:3.5.1")
                // for network testing to track idle state
                implementation ("androidx.test.espresso.idling:idling-concurrent:3.5.1")
                implementation ("androidx.test.espresso:espresso-idling-resource:3.5.1")
                implementation("androidx.test.espresso:espresso-core:3.5.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
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


android {
    namespace = "info.bitcoinunlimited.www.wally"
    compileSdk = 34
    defaultConfig {
        applicationId = "info.bitcoinunlimited.www.wally"
        minSdk = 29
        targetSdk = 34
        versionCode = 300
        versionName = "3.00"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    /*
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
    }

     */
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


/*
// PUBLISHING
// Deployment constants
group = "org.nexa"
version = "3.0.0"

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
    archiveBaseName.set("wpw")
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
