# Lymph — Status & Next Steps

## Quick Start for Next Agent

1. Set `JAVA_HOME` to a **JDK 17** path before running Gradle (AGP 9.x requires JDK 17 compile target). On Windows:
   ```bash
   export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.x+xx"
   ./gradlew :webApp:jsBrowserDevelopmentRun
   ```
2. Use `./gradlew clean` for cleanup — **never** `rmdir` / `rm -rf`.
3. Reference `example-kmp-app/` as a working template (do not edit it).

---

## Build Status

| Target | Compiles? | Tested in runtime? | Notes |
|--------|-----------|---------------------|-------|
| **Web** (`:webApp`) | ✅ Yes (JS IR + wasmJs) | ✅ JS IR — headless Chromium via CDP (see Priority 1 log); wasmJs compiles but not yet run | Full app verified on JS IR: ComposeViewport, drawer, input modal, reactive panels, real ZIP browser download |
| **Android** (`:androidApp`) | ✅ Yes (debug APK) | ❌ Needs emulator/device test | MainActivity wired to LymphApp, SQLite via SQLiteDatabase |
| **iOS** (`:iosApp`) | ⚠️ Compiles on macOS only | ❌ Requires Mac + Xcode | MainViewController ready; no Xcode host project yet |

---

## Priority 1 — Test the Web App ✅ DONE (headless Chromium, CDP)

Verified end-to-end in headless Chromium via Chrome DevTools Protocol (screenshots + OCR + real download capture through `Browser.setDownloadBehavior`). All items pass:

- [x] Run `./gradlew :webApp:jsBrowserDevelopmentRun` — dev server starts at http://localhost:8080
- [x] App renders "LYMPH" dashboard with 3 panels (Weight/Period/Bowel) — mobile 375×812 and desktop 1280×900
- [x] Press "+" FAB → input modal opens (WEIGHT tab default, dial shows 70.0)
- [x] Add a weight record via SAVE → appears in Weight panel immediately (reactive StateFlow): "[ WEIGHT ] 1 records / <date> 70 kg"
- [x] Delete a record via "x" button — wired to `vm::deleteWeight` (same reactive path as insert; not separately re-tested this session)
- [x] Open drawer menu → click "Export Data" → **real browser download** of `lymph-export.zip`; ZIP verified with Python: 3 CSVs, correct headers, inserted record row present
- [x] Desktop >840dp → master-detail layout: sidebar (nav + ADD RECORD + EXPORT DATA) and three equal-width panels side-by-side; EXPORT DATA button downloads the ZIP and shows "EXPORT OK — zip generated" snackbar

**Bugs found & fixed during this testing pass** (details in Historical Fixes):
1. Canvas collapsed to a ~154px strip — `index.html` had no full-viewport CSS.
2. Desktop layout showed only the Weight panel — panels used `fillMaxWidth()` inside an unweighted `Row`.
3. Drawer never opened — hamburger only toggled `gesturesEnabled`, and the created `DrawerState` was never passed to `ModalNavigationDrawer` (detached state).
4. Export produced no download — bytes were returned but discarded by the UI; web had no download mechanism at all.

**Files involved:**
```
webApp/src/webMain/kotlin/com/lymph/Main.kt       # Entry point (ComposeViewport)
shared/src/commonMain/kotlin/com/lymph/ui/         # All UI: LymphApp, DashboardScreen, panels, InputModal
shared/src/webMain/kotlin/com/lymph/data/dao/      # Memory DAOs (Weight/Period/Bowel)
shared/src/webMain/kotlin/com/lymph/platform/       # WebPlatform, WebDataExporter, BrowserDownload
webApp/src/webMain/resources/                       # index.html + styles.css (full-viewport CSS)
```

---

## Priority 2 — Android Runtime Test

Android compiles clean. Needs runtime verification on emulator or device.

**What to verify:**
- [ ] `./gradlew :androidApp:assembleDebug` produces APK
- [ ] Install via `./gradlew :androidApp:installDebug` (or `adb install`)
- [ ] App launches, shows dashboard with 3 panels
- [ ] Add records → persist across app restart (SQLite on device)
- [ ] Export Data → ZIP file saved / shared

**Known Android-specific code:**
```
shared/src/androidMain/kotlin/com/lymph/data/db/AndroidSqlHandle.kt     # SQLiteDatabase binding
shared/src/androidMain/kotlin/com/lymph/platform/AndroidPlatform.kt      # Context.filesDir path
shared/src/androidMain/kotlin/com/lymph/platform/AndroidDataExporter.kt  # java.util.zip export
androidApp/src/main/kotlin/com/lymph/MainActivity.kt                    # Activity shell + Preview
```

**Note:** The `@Preview` function in MainActivity uses `DashboardScreen.Preview()` which renders plain text only. This is intentional — a real database can't exist in Android Studio layout preview.

---

## Priority 3 — iOS Integration (macOS only)

iOS code compiles but cannot be tested from Windows. Requires a Mac with Xcode.

**What to verify:**
- [ ] `./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pios.target=iosSimulatorArm64` produces XCFramework
- [ ] Create an Xcode host project that consumes the framework and calls `MainViewController()`
- [ ] Run on iOS Simulator → app renders, SQLite works via sqlite3 C API

**Known iOS-specific code:**
```
shared/src/iosMain/kotlin/com/lymph/data/db/IosSqlHandle.kt           # sqlite3 C API binding
shared/src/iosMain/kotlin/com/lymph/platform/PlatformContext.ios.kt    # NSDocumentDirectory path
shared/src/iosMain/kotlin/com/lymph/platform/IosDataExporter.kt        # kotlinx-io based export
iosApp/src/iosMain/kotlin/com/lymph/MainViewController.kt              # ComposeUIViewController bridge
```

---

## Future Work (v0.2+)

### CSV Import
README says: *"Import reads a matching ZIP archive, validates schema compatibility, and upserts records by UUID."* Currently only export is implemented. Needs `CsvDecoder` + import flow in UI.

### CI/CD
README says: *"GitHub Actions workflow will be added once local builds are stable for all three targets."* Add workflow with:
- Web build (`jsBrowserProductionWebpack`)
- Android assembleDebug
- Unit tests (if any)
- Note: iOS testing requires macOS runner

### Settings Drawer Action
DashboardScreen has a "Settings" drawer item that only closes the drawer. Could wire up theme options or data management (e.g., clear-all, import) in future.

### Expanded Sidebar Nav Items
The `[ WEIGHT ] / [ PERIOD ] / [ BOWEL ]` labels in the desktop sidebar are decorative text — all panels are always visible in expanded mode, so there is nothing to navigate to. Could become scroll-anchors or filters later.

---

## Web Runtime Testing Session (headless Chromium + CDP)

| Fix | Files Changed | Root Cause |
|-----|---------------|------------|
| Full-viewport CSS for web canvas | `webApp/src/webMain/resources/styles.css` (new), `index.html` | ComposeViewport's canvas lives in Shadow DOM; without `html, body { height: 100% }` the page collapsed to content height (~154px) and only a thin UI strip was visible |
| Desktop panels side-by-side | `WeightPanel.kt`, `PeriodPanel.kt`, `BowelPanel.kt` (added `modifier` param), `DashboardScreen.kt` (`ExpandedLayout`) | All three panels used `fillMaxWidth()` inside an unweighted `Row` → Period/Bowel collapsed to zero width; now each gets `Modifier.weight(1f)` and the row has a `LymphBlack` background |
| Drawer actually opens/closes | `DashboardScreen.kt` | Two bugs: (a) hamburger only set `gesturesEnabled = showMenu`, which enables swipe gestures but never opens the sheet — M3's `ModalNavigationDrawer` needs its `drawerState` driven via `open()`/`close()`; (b) the created `rememberDrawerState(...)` was **not passed** to `ModalNavigationDrawer`, so `.open()` acted on a detached state object. Now: state is passed in, hamburger calls `drawerState.open()`, items call `drawerState.close()` |
| Real browser ZIP download | `shared/src/webMain/.../platform/BrowserDownload.kt` (new), `WebPlatform.kt` (`WebDataExporter`) | UI discarded the exported bytes. New helper installs a JS function on `globalThis` (Blob + object URL + anchor click) and is called through a `@JsName` external fun; payload crosses interop as base64 String because Kotlin/Wasm forbids `ByteArray` params on external functions. `WebDataExporter.exportZip()` now triggers the download before returning |
| Export reachable in desktop layout | `DashboardScreen.kt` (`ExpandedLayout`) | README design says export lives in the master sidebar; added EXPORT DATA button + a SnackbarHost (expanded mode had none) wired to the shared `doExport` action |
| GlobalScope deprecation | `webMain/.../BiometricViewModel.kt`, `nativeMain/.../BiometricViewModel.kt` | Replaced deprecated `GlobalScope.launch` with a private `CoroutineScope(SupervisorJob() + Dispatchers.Default)` owned by the ViewModel |
| Docs refreshed to match reality | `README.md`, `TODO.md` | README described an HTML renderer, JS-IR-only webApp, and stale versions (Kotlin 2.3.21 / Compose 1.7.3); actual stack is Kotlin 2.4.10 / Compose MP 1.11.1 with ComposeViewport (Skia) on js(IR)+wasmJs |

**Testing notes for future sessions:**
- Dev server: `cd lymph && export JAVA_HOME=<jdk17> && setsid nohup ./gradlew :webApp:jsBrowserDevelopmentRun --no-daemon > /tmp/webdev.log 2>&1 < /dev/null & disown` — wait for "Loopback" in the log.
- The app needs ~10s after page load before input is reliable (sql.js CDN fetch + first frames); fixed waits of ≥12s work. Screenshot size >5KB indicates the dashboard has rendered.
- Compose renders to a canvas — there is no DOM to query; verify UI via screenshots + OCR (`convert img.png -negate -resize 400%` then `tesseract --psm 6`).
- Capture real downloads with CDP: connect to the browser WS endpoint, call `Browser.setDownloadBehavior {behavior:'allow', downloadPath:...}` (omit `browserContextId` for the default context — passing `""` errors), then click and inspect the directory.
- Mobile coordinates (375×812): hamburger ≈ (28,32); drawer "Export Data" row ≈ (100,57); FAB ≈ (331,768); modal SAVE ≈ (289,533). Desktop (1280×900): sidebar EXPORT DATA ≈ (124,864).
- `Emulation.setDeviceMetricsOverride` is required per page target — without it headless Chrome uses a 500×725 viewport and all coordinates shift.

## Historical Fixes Applied in Earlier Sessions

| Fix | Files Changed | Root Cause |
|-----|---------------|------------|
| JDK 17 requirement docs + agent rules | README.md | AGP 9.x needs JDK 17 toolchain; project uses `jvmToolchain(17)` |
| Windows symlink permission workaround | README.md (docs) | yarn install fails on Windows without Developer Mode |
| AndroidSqlHandle nullable Boolean | shared/...AndroidSqlHandle.kt | `!nullable?.exists()` is not valid Kotlin — use `!= true` |
| SQLiteDatabase.Statement → SQLiteStatement | shared/...AndroidSqlHandle.kt | Wrong import; `SQLiteDatabase.Statement` doesn't exist in Android SDK |
| bind() Long→Int type mismatch | shared/...AndroidSqlHandle.kt | SQLiteStatement.bind* methods take Int index, not Long |
| String template method call syntax | shared/...AndroidPlatform.kt | `$getDataDirectory()` needs braces: `${getDataDirectory()}` |
| Missing @Preview annotation dependency | androidApp/build.gradle.kts + libs.versions.toml | `compose.uiTooling` / `uiToolingPreview` not declared for androidApp module |
| AndroidManifest AppCompat theme missing | androidApp/AndroidManifest.xml | Used `@style/Theme.AppCompat...` without AppCompat dep → switched to built-in `@android:style/Theme.Material.Light.NoActionBar` |
| Clock.kt System.currentTimeMillis() on iOS cross-compile | shared/...nativeMain/...Clock.kt | `System.currentTimeMillis()` not available when cross-compiling iOS from Windows → uses `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()` |

---

## Current State (checkpoint)

The web portal is **ugly but working end-to-end** — that's the agreed v0.1 bar:

- Mobile: dashboard, drawer (Export Data / Settings), FAB → input modal, reactive panels, real ZIP download.
- Desktop (>840dp): sidebar with ADD RECORD + EXPORT DATA, three equal-width panels, snackbar feedback.
- Both web targets compile; JS IR is the runtime-verified path.

Next up when picked back up: **Priority 2 (Android runtime test)**, then v0.2 features (CSV import first — it's the natural pair to the now-working export).
