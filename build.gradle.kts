plugins {
    //trick: for the same plugin versions in all sub-modules
    id("com.android.application").version("8.8.0").apply(false)  // https://developer.android.com/build/releases/gradle-plugin
    id("com.android.library").version("8.8.0").apply(false)
    kotlin("android").version("2.1.20").apply(false)
    kotlin("multiplatform").version("2.1.20").apply(false)  // https://kotlinlang.org/docs/multiplatform-dsl-reference.html#top-level-blocks
    id("com.dorongold.task-tree").version("2.1.1")
    id("org.jetbrains.compose").version("1.7.1").apply(false)
    kotlin("plugin.compose").version("2.1.20").apply(false)
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}


tasks.register("clean", Delete::class) {
    delete(rootProject.getLayout().getBuildDirectory())
}
