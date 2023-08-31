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
        mavenLocal()
        maven { url = uri("https://gitlab.com/api/v4/projects/48544966/packages/maven") }  // mpthreads
        maven { url = uri("https://gitlab.com/api/v4/projects/38119368/packages/maven") }  // libnexarpc
        maven { url = uri("https://gitlab.com/api/v4/projects/48545045/packages/maven") }  // Libnexakotlin
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}

rootProject.name = "wpw"  // Wally Personal Wallet
include(":shared")
include(":androidApp")