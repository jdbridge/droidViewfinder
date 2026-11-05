// AGP 9 has built-in Kotlin support, so there is no separate org.jetbrains.kotlin.android plugin. The
// buildscript classpath pins the Kotlin Gradle plugin that built-in support uses. The Compose compiler
// plugin must match the Kotlin version.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
