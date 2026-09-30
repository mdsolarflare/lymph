# Environment
Windows host + git-bash (MSYS); repo at C:\Users\dalton\StudioProjects\lymph (/c/Users/dalton/... in bash).
Use Windows where appropriate, prefer to use git bash everywhere you are able
Backend can flip mid-session between git-bash and the Docker sandbox — after any "backend degraded" error, re-verify uname -a/pwd/repo location before running commands (paths and tools differ completely).
JDK 17 required: export JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.20.8-hotspot" before any gradle call. Verify JDK is present before proceeding. If it's missing ask for JDK location and have this note updated.
E2E tooling on the Windows host: Edge at C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe, tesseract at C:/Users/dalton/AppData/Local/Programs/Tesseract-OCR/tesseract.exe, ImageMagick via the `magick` shim on PATH. The :e2e module finds them via defaults in e2e/build.gradle.kts (Gradle property > env var > default).
Any usage of Node and test scripts or assets need to be defined in this project with the Node dependency called out in the app.
example-kmp-app/ was deleted (Sep 17) — the lymph repo itself is the reference now.

# Build & test
Dev server: ./gradlew :webApp:jsBrowserDevelopmentRun --no-daemon → localhost:8080; first webpack compile takes minutes, poll with curl. Kotlin source changes require a dev-server restart (resource-only changes hot-reload).
Unit tests: ./gradlew :shared:jsNodeTest, XMLs at shared/build/test-results/jsNodeTest/*.xml; baseline 48/48 green (18+10+6+14).
Wasm gate: ./gradlew :shared:compileKotlinWasmJs.
Kotlin/Native gate (works from Windows): ./gradlew :shared:compileKotlinIosSimulatorArm64.
Yarn lock failures → ./gradlew clean kotlinUpgradeYarnLock --no-daemon then rebuild; cleanup via ./gradlew clean, never rm -rf.
Gradle can linger after jsNodeTest — poll test XMLs, don't block on process exit.
Web E2E (`:e2e` — Playwright Java client driving headless Edge + OCR)
Run: dev server on :8080 first, then ./gradlew :e2e:test -Pe2e=true --no-daemon (~2min; plain builds never launch a browser — the -Pe2e flag gates it). Results XML at e2e/build/test-results/test/*.xml; screenshots + captured zip in e2e/build/e2e/.
UI renders to <canvas> via Skiko → no DOM text; all targeting/assertions are OCR-based (tesseract TSV word boxes → line grouping by y-proximity → fuzzy phrase match → span center). Clicks are coordinate-based via page.mouse().
Desktop layout needs >840dp → 1280×900 viewport; sidebar 240dp with bracketed nav labels + ADD RECORD / EXPORT DATA / IMPORT DATA (import renders only when a picker exists).
Web persistence: tables are mirrored to localStorage as CSVs (keys lymph.weight_records etc., schema-identical to the export ZIP) — data SURVIVES page.reload(). To reset state in a test: page.evaluate("() => window.localStorage.clear()") BEFORE reloading.
Seeding needs no typing — modal defaults are valid; click ADD RECORD → rightmost bare PERIOD/BOWEL match (sidebar labels are bracketed) → SAVE; modal closed when SAVE disappears from OCR (refresh the screenshot every poll iteration — re-OCR-ing a stale frame never sees the close).
OCR gotchas: run tesseract on raw + inverted + gray-threshold passes and merge word boxes; icons read as garbage — match phrases, never full lines; em-dashes mangle → assert on words (/3\s*added/), not exact strings; a global y-sort interleaves words within a line — group by y-proximity first, then x-sort within each line.
Snackbars: M3 dark theme renders them as a LIGHT #E6E0E9 box bottom-center of the DETAIL area (x≈449–1073, y≈816–864 at 1280×900) — a bottom-strip OCR pass sees nothing (psm-6 drops the sparse line). Ocr.snackbarText finds the surface by exact color bbox → tight crop → 2× upscale → fuzz-fill → tesseract; that combo reads "EXPORT OK — zip generated" / "IMPORT OK — 0 added, 3 duplicates" perfectly.
Expected snackbars: EXPORT OK — zip generated, IMPORT OK — 3 added, IMPORT OK — 0 added, 3 duplicates; auto-dismiss ~4s → poll OCR every ~700ms starting immediately after the action. Match with \s* + IGNORE_CASE (OCR text has no guaranteed separators).
Compose-on-canvas swallows the FIRST click after an idle poll at correct coordinates (~1 in 3) — wrap action clicks in a retry that re-locates the phrase from a fresh screenshot until an observable event fires (download event, file-chooser event); retries must key on app-visible events, not coordinate math.
Don't OCR inside a timing-sensitive poll loop (~2s/poll can miss a 4s transient) — rapid-fire raw screenshots during the window, OCR the frames offline afterwards.
Export capture: page.onDownload + saveAs works in headless Edge under Playwright (the old raw-CDP "headless downloads cancel" finding is obsolete — don't retry in-page download hooks; Kotlin/JS IR top-level vals are module-scoped and never land on globalThis at page load, verified twice).
Import delivery: the app's BrowserFilePicker owns a persistent hidden <input type=file id="__lymph_file_input"> (light DOM). FileChooser.setFiles can silently fail on the second chooser in a session — deliver via page.setInputFiles("#__lymph_file_input", path) instead and keep the chooser event as click-landed detection only.
App render takes 15–30s → poll OCR for "EXPORT DATA" up to 90s before failing.
Code conventions & pitfalls
No runBlocking in commonTest — use kotlinx.coroutines.test.runTest.
No String(ByteArray) in common JS/wasm — use .decodeToString().
Icons: no AutoMirrored FileDownload in this Compose version — plain Icons.Default.FileDownload.
BiometricRecord is in package com.lymph.data, not .entity.
Schema contract = public CsvEncoder.*_HEADER constants; entry names = DataImporter.WEIGHT_CSV/PERIOD_CSV/BOWEL_CSV; full spec in docs/DATA-EXCHANGE.md.
Import semantics: insert-only (skip existing UUIDs, first occurrence wins), best-effort snackbar summary, only structural failures abort; web-only UI (filePicker = null on native → no import affordance).
Web interop pattern: raw js() IIFE val at module load installs helpers on globalThis, invoked via @JsName external funs; only String/Boolean cross the boundary (wasmJs forbids ByteArray params on external functions). See BrowserDownload.kt / BrowserFilePicker.kt / LocalStorage.kt.
iOS sources are UNWIRED (Sep 18 finding): applyDefaultHierarchyTemplate=false and no iosMain declared in :iosApp (NO-SOURCE) or bridged in :shared → shared/src/iosMain + iosApp/src/iosMain are dead code; the TODO platformOpen stubs are what compiles. See GOAL.md "★ Next Goal" before any iOS work.

# Commits
You manage all commits — never commit/push without an explicit request; advise only.
Plus a header note: GOAL.md is the canonical session-status doc (read at start, update when done); AGENTS.md holds environment/gotchas only, not project state.
