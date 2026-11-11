plugins {
    alias(libs.plugins.android.application) apply false
    // Pin AGP's built-in Kotlin without applying the separate Android Kotlin plugin.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}
