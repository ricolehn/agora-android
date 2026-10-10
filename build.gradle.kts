plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied: AGP 9 has built-in Kotlin; declared so the newer KGP (and its compiler) is used
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
