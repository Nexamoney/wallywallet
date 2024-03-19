pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev/") }  // mpthreads
        maven { url = uri("https://gitlab.com/api/v4/projects/48544966/packages/maven") }  // mpthreads
        maven { url = uri("https://gitlab.com/api/v4/projects/38119368/packages/maven") }  // libnexarpc
        maven { url = uri("https://gitlab.com/api/v4/projects/48545045/packages/maven") }  // Libnexakotlin
        maven { url = uri("https://jitpack.io") }
        google()
        gradlePluginPortal()
    }
}



rootProject.name = "wpw"  // Wally Personal Wallet
include(":src")
//include(":androidApp")