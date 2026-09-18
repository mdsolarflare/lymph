rootProject.name = "Lymph"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    // Auto-provision the JDK 17 toolchain (shared/build.gradle.kts: jvmToolchain(17)) when no
    // local installation is discoverable — e.g. non-standard install paths or fresh containers.
    // Without this, every task that resolves a Java toolchain fails at configuration time,
    // which also blocks `clean` (so stale build/js state can never be recovered).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":shared")
include(":androidApp")
include(":webApp")
include(":iosApp")
// E2E driver (JVM-only, gated behind -Pe2e=true). Not part of the app's dependency graph.
include(":e2e")
