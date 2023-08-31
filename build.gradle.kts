import java.util.Properties
import java.io.FileInputStream
import java.net.URL
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask


plugins {
    //trick: for the same plugin versions in all sub-modules
    id("com.android.application").version("8.1.1").apply(false)
    id("com.android.library").version("8.1.1").apply(false)
    kotlin("multiplatform").version("1.9.0").apply(false)
    kotlin("plugin.serialization").version("1.9.0").apply(false)
    id("org.jetbrains.compose").version("1.5.0").apply(false)
    id("maven-publish")
    id("org.jetbrains.dokka").version("1.8.20").apply(false)
    idea
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
    var androidNdkDir = ""
    var linuxToolchain = prop["linux.toolchain"]
    var linuxTarget = prop["linux.target"]
}


buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        //classpath("com.android.tools.build:gradle:YOUR_CURRENT_ANDROID_PLUGIN_VERSION")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
        // classpath("com.squareup.sqldelight:gradle-plugin:1.5.5")
        classpath("org.jetbrains.dokka:dokka-base:1.8.20")
    }
}


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
