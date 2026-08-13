plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false  // https://kotlinlang.org/docs/multiplatform-dsl-reference.html#top-level-blocks
    alias(libs.plugins.task.tree)
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover)
    // Versioned Nexa AI skills, creates / mirrors into .claude/skills/ by syncNexaSkills.
    // bump the version here and run ./gradlew syncNexaSkills to update.
    id("org.nexa.aiskills") version "0.1.28"
}


tasks.register("clean", Delete::class) {
    delete(rootProject.getLayout().getBuildDirectory())
}

// If you have additional files in .claude/skills besides the NEXA skills, include those below so they are not deleted upon re-sync
// for an additional file at the root of that directory - include("<file_name>")
// for an additional skill directory contained in .claude/skills - include("<skill-name>/**")
// for an additional skill directory with a "local-" prefix - include("local-*/**") is sufficient
tasks.named<Sync>("syncNexaSkills") {
    preserve { include("local-*/**") }
}
