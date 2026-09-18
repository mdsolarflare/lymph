// E2E driver for the web app: Playwright (Java client) driving headless Edge + OCR (tesseract/ImageMagick).
// JVM-only, NOT part of the app's dependency graph. Gated behind -Pe2e=true so plain builds never
// spin up a browser; start the dev server first (:webApp:jsBrowserDevelopmentRun → :8080).
plugins {
    // No version: kotlin-gradle-plugin is already on the classpath via the root build (KMP, apply false).
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

val e2eEnabled = providers.gradleProperty("e2e").orNull?.toBoolean() == true

dependencies {
    testImplementation(libs.playwright)
    testImplementation(libs.junit.jupiter)
    // Gradle 9: the JUnit Platform launcher must be on the test runtime classpath explicitly.
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    onlyIf {
        if (!e2eEnabled) {
            logger.lifecycle(":e2e:test skipped — run with -Pe2e=true (dev server must be on :8080)")
            return@onlyIf false
        }
        true
    }
    // Tool locations: Gradle property > env var > Windows defaults.
    systemProperty("e2e.tesseract", providers.gradleProperty("e2e.tesseract").orNull
        ?: System.getenv("E2E_TESSERACT") ?: "C:/Users/dalton/AppData/Local/Programs/Tesseract-OCR/tesseract.exe")
    systemProperty("e2e.magick", providers.gradleProperty("e2e.magick").orNull
        ?: System.getenv("E2E_MAGICK") ?: "C:/Users/dalton/AppData/Local/Microsoft/WindowsApps/magick.exe")
    systemProperty("e2e.workdir", layout.buildDirectory.dir("e2e").get().asFile.absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
