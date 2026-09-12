// Top-level build file: plugin versions for all modules.
// Keep Kotlin, KSP and the Compose plugin on the SAME Kotlin version — see docs/TECH-NOTES.md 3.6.
plugins {
    id("com.android.application") version "8.12.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    id("io.ktor.plugin") version "2.3.12" apply false
}
