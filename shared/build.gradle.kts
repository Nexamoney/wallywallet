import com.android.ide.common.util.toPathString
import org.gradle.model.internal.core.ModelNodes.withType
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinExecution
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.util.Properties
import java.io.FileInputStream
import java.net.URL

// Dependency versions
val serializationVersion = "1.6.0"  // https://github.com/Kotlin/kotlinx.serialization
val coroutinesVersion = "1.7.3"     // https://github.com/Kotlin/kotlinx.coroutines
val ktorVersion = "2.3.3"
val bigNumVersion = "0.3.8"
val mpThreadsVersion = "0.1.6"
val composeVersion = "1.5.0"
val androidxActivityComposeVersion = "1.7.2"

// Host determination
val LINUX = System.getProperty("os.name").lowercase().contains("linux")
val MAC = System.getProperty("os.name").lowercase().contains("mac")
val MSWIN = System.getProperty("os.name").lowercase().contains("windows")

// NOTE on your primary (first publish) system, you need to specify ALL targets as targets, even if this host does not
// publish them.  If they are not specified, the published .module file will not contain a definition for that target
// and so it will be as if it does not exist from a dependency perspective, even if you later publish the library from
// another host.
val LINUX_TARGETS = LINUX
val MAC_TARGETS = true
// ktor network does not support ms windows so we cannot produce MSWIN right now
var MSWIN_TARGETS = false


if (MAC) println("Host is a MAC, MacOS and iOS targets are enabled")
if (LINUX) println("Host is LINUX, Android, JVM, and LinuxNative targets are enabled")
else println("Linux target is disabled")

if (MSWIN) { println("Host is MS-WINDOWS"); MSWIN_TARGETS = true }


if (!LINUX_TARGETS) println("Linux targets are disabled")
if (!MAC_TARGETS) println("MacOS and iOS targets are disabled")
if (!MSWIN_TARGETS) println("Ms-windows Mingw64 target is disabled")


val NATIVE_BUILD_CHOICE:NativeBuildType = NativeBuildType.DEBUG

fun org.jetbrains.kotlin.gradle.dsl.KotlinNativeBinaryContainer.libnexaBinCfg() {
    /* This is a library only
    executable {
        NATIVE_BUILD_CHOICE
    }
     */
    sharedLib { NATIVE_BUILD_CHOICE }
    staticLib { NATIVE_BUILD_CHOICE }
}

val prop = Properties().apply {
    load(FileInputStream(File(rootProject.rootDir, "local.properties")))
}

// All plugin versions are in the root project
plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    //id("maven-publish")
    //id("org.jetbrains.dokka")
    //idea
}

fun getCompileSdk():Int
{
    return rootProject.extensions.findByType<com.android.build.gradle.BaseExtension>()!!.compileSdkVersion!!.split("-")[1].toInt()
}

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


@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()

    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
        publishLibraryVariants("release", "debug")
    }



    if (MAC_TARGETS)
    {
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
            /* cocoapods now, not "framework"
            it.binaries.framework {
                baseName = "shared"
            }

             */
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


    /*  These targets have no threading
    wasm {
        // browser()
        nodejs()
        //d8()
    }
    js(IR) {
        nodejs()
        binaries.executable()
    }
     */


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

                // Nexa
                implementation("org.nexa:mpthreads:$mpThreadsVersion")
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

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.common)
            }
        }

        // Common to all "native" targets
        val nativeMain by getting {
            dependsOn(sourceSets.named("commonMain").get())
            dependencies {
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
                implementation(kotlin("stdlib-jdk8"))
                implementation("androidx.activity:activity-compose:$androidxActivityComposeVersion")

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
                implementation("org.nexa:NexaRpc:1.1.0")
                implementation("androidx.test:core:1.5.0")
                implementation("androidx.test:core-ktx:1.5.0")
                implementation("androidx.test.ext:junit:1.1.5")
                implementation("androidx.test.ext:junit-ktx:1.1.5")

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

var androidNdkDir:File? = null

android {
    namespace = "info.bitcoinunlimited.www.wallylib"
    // compileSdk = rootProject.extensions.findByType<com.android.build.gradle.BaseExtension>()!!.compileSdkVersion!!.split("-")[1].toInt()
    defaultConfig {
        minSdk = 29
        compileSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
                value = prop.getProperty("LibNexaKotlinDeployTokenValue")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

 */

/*
// DOCUMENTATION

tasks.dokkaHtml {
    outputDirectory.set(rootDir.resolve("public"))
}

tasks.withType<DokkaTask>().configureEach {

    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        customAssets = listOf(file("src/doc/nexa-64x64.png"), file("src/doc/logo-icon.svg"))
        customStyleSheets = listOf(file("src/doc/logo-styles.css"))
        footerMessage = "(c) 2023 Bitcoin Unlimited"
    }

    dokkaSourceSets {
        named("commonMain")
        {
            //displayName.set("libnexakotlin")
            // displayName.set("Nexa Kotlin Library")
            // includes.from("src/doc/Module.md")
            // platform.set(org.jetbrains.dokka.Platform.jvm)
            //sourceRoots.from(kotlin.sourceSets.getByName("jvmMain").kotlin.srcDirs)
            sourceRoots.from(kotlin.sourceSets.getByName("commonMain").kotlin.srcDirs)
            /*
            sourceLink {
                val path = "src/commonMain/kotlin"
                localDirectory.set(file(path))
                val url = "https://nexa.gitlab.io/libnexakotlin"
                remoteUrl.set(URL("$url/tree/${moduleVersion.get()}/lib/$path"))
            }

             */
        }

        forEach {
            it.run {
                //displayName.set("Nexa Kotlin Library")
                includes.from("src/doc/Module.md")
                sourceLink {
                    localDirectory.set(project.file("libnexakotlin/src/commonMain/kotlin"))
                    remoteUrl.set(URL("https://gitlab.com/nexa/libnexakotlin/-/tree/main/"))
                    remoteLineSuffix.set("#L")
                }
                includeNonPublic.set(false)
            }
        }
    }

    dokkaSourceSets.configureEach {
        perPackageOption {
            matchingRegex.set("bitcoinunlimited.*")
            suppress.set(true)
        }
    }
}
*/