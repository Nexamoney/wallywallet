plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false  // https://kotlinlang.org/docs/multiplatform-dsl-reference.html#top-level-blocks
    alias(libs.plugins.task.tree)
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover)
}


tasks.register("clean", Delete::class) {
    delete(rootProject.getLayout().getBuildDirectory())
}
