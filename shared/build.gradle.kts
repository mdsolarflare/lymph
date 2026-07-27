import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("com.android.library")
}

android {
    namespace = "com.lymph.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
}

kotlin {
    jvmToolchain(25)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "lymph.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Compose (available on all targets)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Utilities (pure Kotlin, no platform deps)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.io.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // ── Native (Android + iOS) — ViewModel, native SQLite driver ────

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(nativeMain)
        }

        val iosArm64Main by getting {
            dependsOn(nativeMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(nativeMain)
        }

        // ── WASM JS — web worker SQLite driver ──────────────────────────

        val wasmJsMain by getting {
            dependsOn(commonMain)
        }
    }
}
