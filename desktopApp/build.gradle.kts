// Desktop (JVM) application entry point. All shared code lives in :shared.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)  // Compose compiler
    alias(libs.plugins.compose)
}

kotlin {
    jvm {
        mainRun {
            this.mainClass = "info.bitcoinunlimited.www.wally.WallyJvmApp"
        }
        tasks.withType<KotlinCompile>() {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
    }
    jvmToolchain(21)
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.nexa.mpthreads)
                implementation(libs.nexa.libnexakotlin)
            }
        }
    }
}

/* Put all the dependent files into a single big jar */
tasks.register<Jar>("appJar") {
    archiveFileName.set("wpw.jar")
    manifest {
        attributes["Main-Class"] = "info.bitcoinunlimited.www.wally.WallyJvmApp"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    for (c in kotlin.targets.named("jvm").get().compilations)
    {
        // If its a jar blow the jar up and add the class files in that jar
        val tmp = c.runtimeDependencyFiles
        if (tmp != null)
        {
            from(tmp.filter { it.name.endsWith("jar") }.map { zipTree(it) })
        }
        from(c.output)
    }
}

tasks.register<Exec>("runJvmApp") {
    commandLine("java", "-classpath", "build/libs/wpw.jar", "info.bitcoinunlimited.www.wally.WallyJvmApp")
}
