pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev/") }  // for skiko
        // maven { url = uri("https://mvnrepository.com/artifact/androidx.test/core") } // Androidx Test Library ALFA builds
        // maven { url = uri("https://mvnrepository.com/artifact/org.jetbrains.compose.ui/ui-test") } // Compose multiplatform ALFA/BETA builds
        maven { url = uri("https://gitlab.com/api/v4/projects/48544966/packages/maven") }  // mpthreads
        maven { url = uri("https://gitlab.com/api/v4/projects/38119368/packages/maven") }  // libnexarpc
        maven { url = uri("https://gitlab.com/api/v4/projects/48545045/packages/maven") }  // Libnexakotlin
        maven { url = uri("https://jitpack.io") }
        mavenLocal()
    }
}



rootProject.name = "wpw"  // Wally Personal Wallet
include(":src")
//include(":androidApp")