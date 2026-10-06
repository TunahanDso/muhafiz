// Top-level build file for the Muhafiz Android project.
//
// Plugin sürümleri gradle/libs.versions.toml üzerinden yönetilir.
// Alt modüller ihtiyaç duydukları pluginleri kendi build.gradle.kts
// dosyalarında uygular.

plugins {
    // Android Gradle Plugin
    alias(libs.plugins.android.application) apply false

    // Kotlin Android
    alias(libs.plugins.jetbrains.kotlin.android) apply false

    // Kotlin 2.x Jetpack Compose Compiler Plugin
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
}