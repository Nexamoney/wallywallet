plugins {
    //trick: for the same plugin versions in all sub-modules
    id("com.android.application").version("8.2.0").apply(false)  // https://developer.android.com/build/releases/gradle-plugin
    id("com.android.library").version("8.2.0").apply(false)
    kotlin("android").version("1.9.23").apply(false)
    kotlin("multiplatform").version("1.9.23").apply(false)  // https://kotlinlang.org/docs/multiplatform-dsl-reference.html#top-level-blocks
    id("com.dorongold.task-tree").version("2.1.1")
}


tasks.register("clean", Delete::class) {
    delete(rootProject.getLayout().getBuildDirectory())
}
