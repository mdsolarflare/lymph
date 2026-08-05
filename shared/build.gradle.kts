import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)

    // ── iOS targets (native SQLite via sqlite3 C API) ─────────────
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    // ── Web targets (JS IR + WASM, Skia/Canvas via ComposeViewport) ──
    js(IR) {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // ── Android target ───────────────────────────────────────────
    android {
        namespace = "com.lymph.shared"
        compileSdk = 35
        minSdk = 24

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // ── Native (Android + iOS) — raw SQLite via platform bindings ──
        val nativeMain = create("nativeMain") {
            dependsOn(commonMain.get())
        }

        androidMain.get().dependsOn(nativeMain)
        iosArm64Main.get().dependsOn(nativeMain)
        iosSimulatorArm64Main.get().dependsOn(nativeMain)

        // ── Web (JS IR + WASM, in-memory DAOs via ComposeViewport) ──
        val webMain = create("webMain") {
            dependsOn(commonMain.get())
        }

        jsMain.get().dependsOn(webMain)
        wasmJsMain.get().dependsOn(webMain)
    }
}
