import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
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
    jvmToolchain(17)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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

                // SQLDelight runtime + coroutines extension (common to all platforms)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines.extensions)

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
            dependencies {
                // ViewModel / Lifecycle (KMP — JVM + Native only)
                implementation(libs.kotlinx.lifecycle.viewmodel)
                implementation(libs.kotlinx.lifecycle.viewmodel.compose)

                // Native SQLite driver (used by iosMain + androidMain)
                implementation(libs.sqldelight.native.driver)
            }
        }

        val androidMain by getting {
            dependsOn(nativeMain)
            dependencies {
                implementation(libs.sqldelight.android.driver)
            }
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
            dependencies {
                implementation(libs.sqldelight.web.worker.driver)
            }
        }
    }
}

sqldelight {
    databases {
        create("LymphDatabase") {
            packageName.set("com.lymph.data.db")
            // Generate async-style query APIs (QueryResult.AsyncValue) so the same generated
            // code works on WASM (WebWorkerDriver is async) and on native (synchronous drivers
            // also accept the async QueryResult contract). Required for the WASM web-worker driver.
            generateAsync.set(true)
        }
    }
}
