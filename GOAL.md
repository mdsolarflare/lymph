# Lymph — Status & Next Steps

> This file is the canonical session-context document (renamed from `TODO.md` on Aug 27, user-managed rename). Update it at the end of every session; read it at the start.
>
> **⚠️ v2 REBOOT (Sep 29–Oct 1): Gradle + Kotlin are ABANDONED.** The project is now a **Rust core (WASM) + Deno/TypeScript/Preact frontend**. Product contract: [`docs/SPEC.md`](docs/SPEC.md). The Kotlin tree (`shared/`, `androidApp/`, `webApp/`, `e2e/`, Gradle files) still exists ONLY as the behavioral reference for the port — it is dead code pending one-shot deletion at v2 parity. Nothing in it compiles into v2. All v2 verification below is REAL executed output.

## v2 verification status (all executed, Oct 1)

- **Rust core** (`core/crates/lymph-core`): **60/60 tests green** (`cargo test`), clippy clean, `wasm32-unknown-unknown` compiles, wasm-pack artifact builds. Tests = the 48-test Kotlin baseline ported 1:1 + 12 new (CRC vectors, lb↔kg round-trip, Java-Double-toString parity, ZIP truncation fuzz).
- **Frontend** (`frontend/`): `deno check` 0 errors; `deno task build` → `dist/` (app.js ~83KB + wasm 147KB + css + index.html).
- **E2E round trip GREEN** (real browser, DOM queries — no OCR): seed weight+period+bowel via the modal → reload (data survives localStorage) → EXPORT (snackbar exact, real ZIP structurally verified: 3 CSVs, exact headers, 1 data row each, EOCD ok) → wipe → IMPORT (`IMPORT OK — 3 added`) → re-import (`IMPORT OK — 0 added, 3 duplicates`) → data intact. Weight entry verified: defaults 70 kg valid, modal closes on save, CSV stores `70.0`.
- **Chart refresh fix (Oct 1)**: geometry derived at render time in `chart.tsx` (was a `useSignalEffect` that ignored prop changes — chart froze after insert/import). Verified: insert 1→2→3 records moves polyline/markers live; import into wiped DB renders immediately; range toggle re-derives.
- **PWA — M2 GREEN (Oct 1, taskman-consistent)**: manifest.webmanifest (relative paths, standalone, #111111), sw.js (zero-dep cache-first shell, BUILD_ID stamped by gen-revision.ts — the load-bearing Chromium byte-compare rule), gen-revision.ts (SHA-256 over 10 shell files → cache-manifest.js + sw stamp), registration with `updateViaCache: "none"`, `usePWA` port (install prompt + update toast + SKIP_WAITING/controllerchange reload-guard), icons (headless-Edge canvas raster of favicon.svg: 192/512/512-maskable/apple-touch).
  - **Verified live**: SW installs + controls page; precache = 11 entries (10 files + `./`); **true-offline load GREEN** (CDP Network.emulateNetworkConditions offline → full app renders from cache); **full update cycle GREEN on a live controlled page** (rebuild mid-session → updatefound → toast → RELOAD click → new build active → old caches deleted, exactly 1 shell cache remains); content-addressing confirmed (identical source → identical buildId).
- Storage: localStorage CSV mirror, **same keys as Kotlin app** (`lymph.weight_records` etc.) — existing browser data inherits itself, zero migration code.

## v2 build & run (Windows host)

```bash
# Rust core: test + wasm artifact (wasm32-unknown-unknown target must be installed)
cd core && cargo test && cargo clippy --all-targets
wasm-pack build crates/lymph-core --target web --out-dir ../../pkg --release

# Frontend: type-check, bundle, serve (deno at C:\Users\dalton\.deno\bin\deno.exe — NOT on PATH)
cd ../frontend
deno check src/main.tsx src/app.tsx src/chart.tsx src/panel.tsx src/input-modal.tsx src/store.ts src/io.ts src/format.ts src/lymph-core.ts build.ts serve.ts
deno task build
deno run --allow-read --allow-net serve.ts dist 8080   # → http://localhost:8080/
```

## v2 known-shape / debt (honest list)

- The import-export E2E lives only in session history — it is NOT yet a persisted test file (next milestone: `frontend/e2e/` as a Deno test, mirroring taskman's raw-CDP harness).
- `InputModal` "flow intensity" selector is dials 1–4, matching Kotlin, but the UI label just says "flow" — fine, just noting parity.
- The Hermes browser harness (browser_exec) on this host wedges (`WinError 5` on `.port`/`.pid` files) at turn boundaries — new session names work around it; it is an environment issue, not an app issue.
- Mobile layout (`≤840dp`) is implemented (topbar/drawer/FAB/stacked panels) but only the desktop layout was E2E-driven so far.
- ~~PWA (manifest + service worker + offline)~~ ✅ **DONE Oct 1** (see verification section). Android TWA, Apple roadmap: NOT STARTED — milestone M3 per SPEC §2.
- Update-UX nuance: because the harness kills tabs between calls, a fresh tab always loads the newest build directly (no controller → no waiting → no toast); the toast/update path only triggers on a **live controlled page** — verified by rebuilding mid-session. Real users with an installed PWA are always controlled, so they get the toast.

## Historical Kotlin-era content below — reference only for the port, not active goals


## Quick Start for Next Agent

1. Set `JAVA_HOME` to a **JDK 17** path before running Gradle (AGP 9.x requires JDK 17 compile target). On Windows:
   ```bash
   export JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.20.8-hotspot"
   ./gradlew :webApp:jsBrowserDevelopmentRun
   ```
2. Use `./gradlew clean` for cleanup — **never** `rmdir` / `rm -rf`.
3. `example-kmp-app/` was **deleted (Sep 17)** — the lymph repo itself is the working reference now.
4. **Unit tests**: `./gradlew :shared:jsNodeTest` (runs commonTest on Node — works on Windows and in containers; no Android SDK needed). On a machine with an Android SDK, `:shared:testAndroid` also works (`withHostTest {}` is configured). Baseline **48/48** (CsvDecoder 18 + DataImporter 10 + ZipDecoder 6 + ChartModel 14).
5. **Yarn lock**: if a build fails with "Lock file was changed" (happens after any change to JS-target dependencies), run `./gradlew kotlinUpgradeYarnLock` once, then rebuild. Same recovery as a fresh checkout (`kotlin-js-store/` is gitignored by user choice).
6. **Web E2E**: dev server on :8080 first, then `./gradlew :e2e:test -Pe2e=true --no-daemon` (~2min, needs Edge + tesseract + ImageMagick — all present on the Windows host; see AGENTS.md E2E section).

---

## ★ Next Goal — Mobile Apps in Their Native Setups (defined Sep 18, work paused before starting)

The project is paused after the web milestone (`:e2e` round-trip GREEN + localStorage persistence). **When work resumes, the next milestone is making Android and iOS real, runnable native apps.** Verified state at pause — read this before trusting older notes below:

**Android (`:androidApp`) — closest to done, needs a runtime test:**
- Debug APK compiled in earlier sessions (not re-verified this session); needs an Android SDK + emulator/device, which this Windows dev machine lacks.
- Real SQLite path exists and is wired: `AndroidSqlHandle` (`SQLiteDatabase`), SQL-backed DAOs in `nativeMain`, `AndroidDataExporter` (java.util.zip), `MainActivity` → `LymphApp`.
- Import UI is hidden on native (`filePicker = null`) — native pickers are follow-up work after the app actually runs.
- Suggested resume order: install SDK/emulator → `assembleDebug` → runtime-verify dashboard/add/delete + SQLite persistence **across an app restart** (the same regression class as the web refresh bug) → then native export → then import picker.

**iOS (`:iosApp` + shared ios sources) — NOT actually built; do not trust "MainViewController ready" in older notes:**
- Verified Sep 18 from Windows: `:iosApp:compileKotlinIosSimulatorArm64` → **NO-SOURCE**. With `kotlin.mpp.applyDefaultHierarchyTemplate=false` (gradle.properties) and no `iosMain` source set declared in `:iosApp`, the entire `iosApp/src/iosMain/**` tree — including the entry point `MainViewController.kt` — is **dead code, never compiled**.
- Same trap in `:shared`: `iosArm64Main`/`iosSimulatorArm64Main` are wired directly to `nativeMain` (`shared/build.gradle.kts`), so `shared/src/iosMain/**` (`PlatformContext.ios.kt`, `IosDataExporter.kt`, and `IosSqlHandle.kt` — the full sqlite3 C-API implementation) is **also unreachable**. What actually compiles for iOS today is the `TODO("Not yet implemented")` `platformOpen` stubs in `iosArm64Main`/`iosSimulatorArm64Main` — i.e. the framework would crash on first DB open.
- Fix when resuming: wire the `iosMain` source sets in both modules (re-enable the hierarchy template, or declare `iosMain` + `dependsOn` explicitly), delete the TODO stubs in favor of the real `iosMain` implementation, then verify with `:shared:compileKotlinIosSimulatorArm64` + `:iosApp:compileKotlinIosSimulatorArm64`. **Both compile from Windows** (verified) — only framework linking/running needs macOS + Xcode. Then: Xcode host project consuming the `shared` XCFramework → simulator run.
- Kotlin/Native compilation from Windows works (`:shared:compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL in 18s, Sep 18).

**Suggested resume order:** 1) Android (no Mac required) → 2) iOS source-set wiring + Xcode host on a Mac → 3) portable E2E checklist from README "E2E / UI Testing Bases per Platform" for native assertions (semantics-tree based, no OCR needed on mobile).

---

## Build Status

| Target | Compiles? | Tested in runtime? | Notes |
|--------|-----------|---------------------|-------|
| **Web** (`:webApp`) | ✅ Yes (JS IR + wasmJs) | ✅ JS IR — `:e2e` Playwright suite GREEN (Sep 17–18): seed → export → reload (data survives) → import → re-import; wasmJs compiles but not yet run | localStorage persistence shipped Sep 18; E2E asserts reload-survival |
| **Android** (`:androidApp`) | ✅ Yes (debug APK, earlier sessions) | ❌ Needs emulator/device test | MainActivity wired to LymphApp, SQLite via SQLiteDatabase |
| **iOS** (`:iosApp` + shared) | ⚠️ Misleading — `:shared` iOS targets compile, but only TODO stubs; `:iosApp` sources are dead code (NO-SOURCE) | ❌ Requires Mac + Xcode **and** source-set wiring first | See "Next Goal" above — the real implementation in `shared/src/iosMain` is unreachable |

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

### ~~Expanded Sidebar Nav Items~~ ✅ DONE
The `[ WEIGHT ] / [ PERIOD ] / [ BOWEL ]` sidebar items now work as navigation: selecting one shows exactly that panel in the detail area (one data type at a time), with an inverted-cyan highlight on the selected item. This also fixed layout overlaps — the old `Box`-rooted expanded layout drew the detail row and divider over the 240dp sidebar; it is now a proper `Row` (sidebar → divider → weighted detail area).

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
| Expanded layout overlaps + sidebar nav | `DashboardScreen.kt` (`ExpandedLayout`, new `NavItem`/`DataType`) | Root was a `Box`: the detail `Row` and `VerticalDivider` both anchored at x=0, drawing over the 240dp sidebar (Weight panel card covered the WEIGHT/PERIOD nav labels). Re-rooted as a proper `Row`; sidebar items are now selectable (`DataType` enum + `remember` state) with inverted-cyan highlight; detail shows exactly one panel via `when(selected)` |
| Weight float drift: 70.4 stored as 70.499999999997 | `InputModal.kt`, new `util/WeightFormat.kt` | Weight was a `Double` mutated with `+= 0.1` — binary floating-point accumulation error, masked by display truncation but saved raw. Now held as **integer hundredths** of the selected unit (dial step = ±10); display via `formatWeight()` (≤2 decimals, trailing zeros trimmed); save rounds to hundredths. Also added KG/LB unit toggle (converts on switch; lb→kg uses exact factor 0.45359237) — storage stays canonical kg |
| Dial buttons shifted with value width | `InputModal.kt` (`DialCounter`) | `Row(spacedBy)` without weights: wider values (e.g. "154.32" in LB mode) pushed the `+` button right, so fixed-coordinate taps missed it. Value now sits in a `Box(Modifier.weight(1f))` centered between edge-pinned buttons — stable hit targets for all record types |
| FAB rendered M3-default purple | `Theme.kt` | `FloatingActionButton` uses `colorScheme.primaryContainer`, which was never overridden (only `primary` was) → baseline purple #5F4897. Added `primaryContainer = LymphCyan`, `onPrimaryContainer = LymphBlack` |
| Missing `kotlin.math.roundToLong` import | `WeightFormat.kt`, `InputModal.kt` | `Double.roundToLong()` is an extension in `kotlin.math`, not auto-imported in common code — compile error caught on first build of the new files |
| Editable weight field (type instead of dial-spamming) | `InputModal.kt` (`WeightForm`, `DialCounter` slot API, input filter), `WeightFormat.kt` (`parseHundredths`) | Weight display was a read-only `Text`. Now an `OutlinedTextField`: live input filter (digits + ≤1 dot + ≤2 decimals) so the parser only sees well-formed input; value parsed to integer hundredths each composition drives validation — out-of-range renders LymphRed and disables SAVE. `DialCounter` refactored to a slot API (`center: @Composable () -> Unit`) so weight gets the editable field while period/bowel keep static displays. Note: M3's `OutlinedTextField` has **no** `textAlignment` param (M2 did) — compile error if you try |
| Weight validation range | `WeightFormat.kt` (`MIN_WEIGHT_KG=0.1`, `MAX_WEIGHT_KG=1000.0`), `InputModal.kt` (`isCanonicalInRange`) | User-specified valid range: **0.1–1000 kg** (replaced earlier invented 30–300 bounds). Validation checks the *canonical stored value* — in LB mode the typed value is converted to kg and rounded to hundredths first, so exactly what would be stored is validated (e.g. 2204.63 lb → 1000.00 kg → valid; 2204.64 → 1000.01 → invalid). Don't hardcode per-unit bounds or quote exact lb window edges without re-deriving them from this rule |
| Invisible labels on filled buttons ("blank button") | `InputModal.kt` (3× SAVE), `DashboardScreen.kt` (ADD RECORD), `Theme.kt` (warning doc) | Root cause: every typography slot in `Theme.kt` hardcodes its color, and an explicit style color beats M3's LocalContentColor — so a bare `Text()` inside a filled `Button` rendered bodyLarge's LymphCyan on the LymphCyan container = invisible. OutlinedButtons were unaffected (their content color happens to also be cyan). Fix: pass `style = MaterialTheme.typography.bodyLarge.copy(color = LymphBlack)` at each filled-button label site. **Any future filled Button/IconButton-with-contrast needs the same treatment** — see the WARNING in Theme.kt |
| Weight field pre-fill from last record | `InputModal.kt` (`WeightForm`) | On modal open, the weight field starts with `formatWeight(vm.weights.firstOrNull()?.weightKg)` (70 kg when no records). Works because both DAOs keep lists newest-first (SQL: `ORDER BY timestamp DESC`; web memory DAO prepends on insert) and the modal is composed fresh per open (`if (showModal)`), so `remember {}` re-initializes each time. Verified via save-as-is E2E: fresh profile saves 70; after saving 82.5, reopening and saving untouched stores 82.5 again |

**Testing notes for future sessions:**
- Dev server: `cd lymph && export JAVA_HOME=<jdk17> && setsid nohup ./gradlew :webApp:jsBrowserDevelopmentRun --no-daemon > /tmp/webdev.log 2>&1 < /dev/null & disown` — wait for "Loopback" in the log.
- The app needs ~10s after page load before input is reliable (sql.js CDN fetch + first frames); fixed waits of ≥12s work. Screenshot size >5KB indicates the dashboard has rendered.
- Compose renders to a canvas — there is no DOM to query; verify UI via screenshots + OCR (`convert img.png -negate -resize 400%` then `tesseract --psm 6`).
- Capture real downloads with CDP: connect to the browser WS endpoint, call `Browser.setDownloadBehavior {behavior:'allow', downloadPath:...}` (omit `browserContextId` for the default context — passing `""` errors), then click and inspect the directory.
- Mobile coordinates (375×812): hamburger ≈ (28,32); drawer "Export Data" row ≈ (100,57); FAB ≈ (330,767) — **cyan** since the primaryContainer theme fix; modal: KG/LB toggle ≈ (67,345)/(139,346), weight field click target ≈ (190,435), dial −/+ buttons ≈ (67,427)/(303,427) (stable regardless of value width), SAVE ≈ (289,567). **The dialog card spans x≈27–346 — clicks outside it dismiss the modal** (this is how a mis-aimed `+` tap silently closed the dialog during testing; also: close any open modal before tapping the hamburger or you'll just dismiss the dialog instead of opening the drawer). Desktop (1280×900): sidebar nav WEIGHT/PERIOD/BOWEL ≈ (100, 85/137/182); EXPORT DATA ≈ (124,864).
- **Typing into Compose fields via CDP**: click the field to focus, send `Input.dispatchKeyEvent` Ctrl+A (rawKeyDown+keyUp, modifiers:2, key:'a', code:'KeyA') to select existing content, then `Input.insertText {text}` — replaces the selection. OCR of focused-field text is unreliable; verify via saved panel/CSV instead.
- **ImageMagick+awk gotcha**: `"#D6F9FF"` hex substrings do NOT convert to decimal in awk (`"4F"+0 == 4`, not 79) — pixel-luminance math silently misclassifies every color containing A–F. Use `-colorspace Gray -threshold N%` and count `#FFFFFF` instead.
- **Infra (this container, Aug 20)**: `/workspace` is a Windows C: drive mount; five `.gradle/9.6.1/*.lock` files there got corrupted (read → EIO) and cannot be deleted/renamed while the Windows side holds them open — Gradle fails at startup with `Could not create service of type FileHasher ... Input/output error`. Workaround used: mirror the repo to `/root/lymph-build` (healthy overlay fs) and build there; a watcher (`/tmp/watch-locks.sh`) removes the locks automatically once released. If you hit this on a fresh session, check `cat /workspace/lymph/.gradle/9.6.1/fileHashes/fileHashes.lock` first.
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

## Milestone: Weight Chart + Table Pagination — ✅ DONE

**Scope (agreed with user):** WEIGHT panel only. Period/Bowel visualization is **on hold until those sections are verified by the user** — do not touch `PeriodPanel.kt` / `BowelPanel.kt` for this milestone.

What shipped:
1. **Chart above the table** in the weight panel (both layouts get it free — panels are shared composables). Line trend over time, y auto-scaled to [min,max]+padding (never zero-based), dim gridlines + "nice" ticks, full-cyan polyline with square markers, monospace tick labels, date range under the plot.
2. **Table pagination**: 10 records/page, newest first; terminal-style `[ n/N ]` + `<`/`>` OutlinedButtons below the table, rendered only when >1 page. Page state clamps as record count changes.
3. **X-range selector**: exactly three toggles `[90D] [1Y] [ALL]`, default 90D, same selected-state style as the KG/LB modal toggle. Range state is local to the chart composable (resets on panel switch; acceptable v0.1).

Files:
- `shared/src/commonMain/kotlin/com/lymph/ui/chart/ChartModel.kt` — pure Kotlin: `WeightRange`, `Sample`, `mapWeightSeries()`, `cutoffMillis()`, `niceTicks()` (no Compose imports → unit-testable).
- `shared/src/commonMain/kotlin/com/lymph/ui/chart/WeightTrendChart.kt` — composable: range selector + Canvas drawing + Text-overlay labels.
- `shared/src/commonTest/kotlin/com/lymph/ui/chart/ChartModelTest.kt` — 9 unit tests, all passing.
- `shared/src/commonMain/kotlin/com/lymph/ui/component/WeightPanel.kt` — chart + paginated table integration.
- `shared/build.gradle.kts` — **test infra**: added `nodejs()` to the js target (enables `:shared:jsNodeTest`) and `withHostTest {}` to the android target (enables `:shared:testAndroid` on machines with an Android SDK).

**Running unit tests:**
- This container (no Android SDK): **`:shared:jsNodeTest`** — runs commonTest on Node. ✅ 9/9 passing.
- Machine with Android SDK: `:shared:testAndroid`. iOS needs macOS; wasmJs has no test env configured.
- ⚠️ Adding `nodejs()` changed yarn's dependency set → first build after pulling these changes fails with "Lock file was changed" — run **`./gradlew kotlinUpgradeYarnLock`** once, then rebuild (same recovery as any fresh checkout).

**Compose MP 1.11 API gotchas hit while implementing (for future sessions):**
- `drawChars` does not exist in ui-graphics and `nativeCanvas` is deprecated → tick/date labels are positioned **Text overlays**, not canvas text (renders identically on all targets).
- `Stroke` moved to `androidx.compose.ui.graphics.drawscope.Stroke`; the polyline is drawn as `drawLine` segments instead (square markers cover joints) — sidesteps the import entirely.
- There is no `Int.toDp()` (only `TextUnit.toDp()`) → px→dp via `px / LocalDensity.current.density`.
- A local function inside a `Canvas {}` lambda does NOT inherit the DrawScope receiver (no Density for `.toPx()`) — inline conversions instead.

**E2E verified (headless Chromium + CDP, JS IR dev server):**
- Seeded 12 records → chart renders (gridlines, polyline, markers, tick labels, date axis) on mobile AND desktop detail area; no layout overlap.
- Pagination: page 1 shows exactly 10 rows; `>` → page 2 shows the remaining 2 oldest; pager label `[ n/N ]` present.
- Range toggle: default 90D highlight (pixel-verified #363B3C fill); clicking ALL moves the selection (#424547 fill on ALL, 90D back to surface).
- wasmJs compiles clean (`:shared:compileKotlinWasmJs`).

**Known limitation:** after any data exists, Compose-for-Web (JS IR) re-renders the whole UI into a single full-screen `<canvas>` inside an anonymous shadow root — DOM text queries stop working; E2E must use screenshots + OCR/pixel analysis. (Empty state still renders as DOM.)

**Edge cases handled:** 0 records → existing "> no data" state (no chart); records exist but none in selected range → "> no data in range" message instead of chart; 1 record → single centered marker, no line; min==max → symmetric ±padding around the value.

After this milestone: same pagination pattern + charts for PERIOD and BOWEL — done in the next session (see "Milestone: Period + Bowel Charts" below; user reordered priorities so these came before CSV import).

---

## Milestone: Period + Bowel Charts — ⚠️ IMPLEMENTED, INCOMPLETE & UNVERIFIED (user's call, Aug 29)

> **Status note:** the user is not confident in their ability to test this work and has asked that it stay marked **incomplete and unverified** until they verify it themselves. The implementation below builds, passes unit tests, and passed an agent-driven E2E pass — but treat the milestone as **open**, not done. Do not describe these charts as verified/shipped in user-facing docs.

**Scope (agreed with user):** same timeline concept as the weight chart, but each of these types has **two parameters**, so each chart renders **two lines on one shared auto-scaled y-axis** in sufficiently contrasting colors. User explicitly reordered priorities: this milestone before CSV import/export testing.

What shipped:
1. **Shared generic chart**: `MultiTrendChart` composable (range selector + optional legend + gridlines + N polylines/square markers + tick/date labels). `WeightTrendChart`, `PeriodTrendChart`, `BowelTrendChart` are now thin adapters over it.
2. **Model refactor** (`ChartModel.kt`, pure Kotlin): `WeightRange` → shared `TimeRange`; single-series weight model replaced by generic `MultiSeries` / `SeriesLine` + `mapMultiSeries(...)`; `niceTicks(..., minStep)` and `formatIntegerTick(...)` so period/bowel use whole-number ticks while weight keeps decimal formatting.
3. **Colors**: first parameter uses existing `LymphCyan` (#D6F9FF); second uses new **`LymphAmber` (#FFB454)** — strong hue + lightness contrast on the near-black background, cyan/amber is a color-blind-safe pair (documented in `Colors.kt`).
   - PERIOD: line 1 = duration (days), line 2 = flow intensity [1–4]. Legend: `DURATION (days)` / `FLOW [1-4]`.
   - BOWEL: line 1 = Bristol type [1–7], line 2 = effort level [1–5]. Legend: `BRISTOL[1-7]` / `EFFORT [1-5]`.
4. **Panels**: `PeriodPanel` and `BowelPanel` now show the chart above a paginated table (10/page, `[ n/N ]` pager) — same pattern as `WeightPanel`.

**Bug found & fixed in model:** an empty series among non-empty ones crashed `minOf` on an empty list in `mapMultiSeries` — guarded; covered by unit test.

**Bug found & fixed during E2E (desktop layout):** the detail panel lived in a plain `Box(fillMaxHeight)` with no scrolling, so chart + 10 rows + pager (~880px) exceeded the ~868px viewport and **the pager was clipped off-screen**. Fix in `DashboardScreen.kt` (`ExpandedLayout`): wrap the single selected panel in a `Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))` and pass panels `fillMaxWidth()` instead of `fillMaxSize()`. Mobile layout already scrolled; unchanged.

Files:
- `shared/src/commonMain/kotlin/com/lymph/ui/chart/MultiTrendChart.kt` (new) — generic composable.
- `shared/src/commonMain/kotlin/com/lymph/ui/chart/PeriodTrendChart.kt`, `BowelTrendChart.kt` (new) — thin adapters.
- `shared/src/commonMain/kotlin/com/lymph/ui/chart/WeightTrendChart.kt` — now a thin adapter too.
- `shared/src/commonMain/kotlin/com/lymph/ui/chart/ChartModel.kt` — TimeRange / MultiSeries refactor.
- `shared/src/commonMain/kotlin/com/lymph/ui/theme/Colors.kt` — LymphAmber.
- `shared/src/commonMain/kotlin/com/lymph/ui/component/PeriodPanel.kt`, `BowelPanel.kt` — chart + pagination.
- `shared/src/commonMain/kotlin/com/lymph/ui/screen/DashboardScreen.kt` — scrollable detail column (desktop pager clip fix).
- `shared/src/commonTest/kotlin/com/lymph/ui/chart/ChartModelTest.kt` — rewritten for multi-series; **14/14 passing** via `:shared:jsNodeTest`.

**Agent-driven E2E pass (headless Chromium + CDP, JS IR dev server) — NOT a substitute for user verification:**
- Seeded 12 PERIOD records (varied duration/flow) + 6 BOWEL records (varied Bristol/effort) through the real UI modal.
- Mobile PERIOD: two lines (cyan + amber pixel-verified), legend `DURATION (days)` / `FLOW [1-4]`, table rows in seed order.
- Desktop BOWEL: two lines, legend `BRISTOL[1-7]` / `EFFORT [1-5]`. Desktop PERIOD: same + first 10 paginated rows.
- Pagination on desktop: page 1 = exactly 10 rows + `[1/2] < >`; clicking `>` → page 2 shows the remaining 2 oldest records in correct order (newest-first).
- Range toggle: selected fill moves from 90D to ALL after click (pixel bbox verified at the ALL button position; see color note below).
- Weight regression: single record → single centered marker + date label, `70 kg` table row — adapter refactor didn't break weight.

**Range-button pixel colors (for future E2E):** selected-but-unfocused fill = **#363B3C** (= primary #D6F9FF @ 15% alpha over surface #1A1A1A); a button that was just clicked also gets the M3 focus overlay → fill reads **#424547**. Both mean "selected" — don't treat the color difference as a regression.

---

## Session Handoff — Aug 27 (weight chart milestone completed)

**What happened this session:**
- Weight chart + pagination milestone implemented and fully verified (details in the Milestone section above): `ChartModel.kt` (pure Kotlin, unit-tested), `WeightTrendChart.kt` (composable), paginated table in `WeightPanel.kt`, `[90D] [1Y] [ALL]` range selector.
- Test infra: `nodejs()` added to the shared JS target → commonTest now runs via **`:shared:jsNodeTest`** in this container; `withHostTest {}` added to the android target for SDK-equipped machines. 9/9 tests passing.
- E2E verified on headless Chromium (JS IR dev server): chart renders mobile + desktop, pagination 10/page with working pager, range toggle highlight pixel-verified, wasmJs compiles clean.
- `TODO.md` renamed to **`GOAL.md`** by the user; README agent rule #15 updated to match. This file is now the canonical context doc.

**Git state at handoff (branch `prototype`):**
- Latest commit: `e35de30 "a slightly better checkpoint"` (user's — captured partial docs + DashboardScreen changes).
- **Uncommitted working tree** (all verified building + E2E-passing; sources in `/workspace/lymph` are byte-identical to the built mirror):
  ```
  R  TODO.md -> GOAL.md                              # rename + this session's doc updates
  M  README.md                                       # Weight Panel section, agent rule #15 → GOAL.md
  M  shared/build.gradle.kts                         # nodejs() test env + withHostTest {}
  M  shared/.../ui/component/InputModal.kt           # editable weight field, 0.1–1000 kg validation, pre-fill, SAVE label fix (prev session)
  M  shared/.../ui/component/WeightPanel.kt          # chart + pagination integration
  M  shared/.../ui/screen/DashboardScreen.kt         # ADD RECORD label fix (prev session)
  M  shared/.../ui/theme/Theme.kt                    # filled-button color WARNING doc (prev session)
  ?? shared/.../ui/chart/                            # ChartModel.kt + WeightTrendChart.kt (new)
  ?? shared/.../util/WeightFormat.kt                 # weight parsing/formatting/validation (new, prev session)
  ?? shared/src/commonTest/                          # ChartModelTest.kt (new)
  ```
- User manages commits themselves — advise what to commit, don't commit.

**Environment notes for the next session:**
- Dev server was left running from the **`/root/lymph-build` mirror** on port 8080 (`:webApp:jsBrowserDevelopmentRun`). Kotlin source changes require a restart (no hot-reload). If it's dead, rebuild from the mirror — see infra note below.
- `/workspace` is a Windows C: drive mount with **corrupted `.gradle/9.6.1/*.lock` files** that block Gradle in `/workspace/lymph` (`Could not create service of type FileHasher ... Input/output error`). All builds run from the `/root/lymph-build` mirror (healthy overlay fs). A watcher at `/tmp/watch-locks.sh` removes the locks automatically once the Windows side releases them. If you hit this, check `cat /workspace/lymph/.gradle/9.6.1/fileHashes/fileHashes.lock` first.
- CDP E2E scripts live in `/tmp/cdp-*.mjs` (ephemeral — patterns: seed via FAB/modal coordinates, verify via screenshots + OCR/pixel analysis; see Testing notes below).

## Session Handoff — Aug 29 (period + bowel charts completed)

**What happened this session:**
- User reordered priorities: PERIOD + BOWEL charts **before** CSV import. Implemented both as two-line timeline charts on one shared auto-scaled y-axis (cyan = first parameter, new amber #FFB454 = second; color-blind-safe pair), reusing the weight chart's range selector + pagination pattern.
- Refactored `ChartModel.kt` to a generic multi-series model (`TimeRange`, `MultiSeries`, `mapMultiSeries`) and extracted a shared `MultiTrendChart` composable; weight/period/bowel charts are now thin adapters. Whole-number ticks for period/bowel via `niceTicks(minStep)` + `formatIntegerTick`.
- Fixed a real desktop layout bug found during E2E: the detail panel's pager was clipped off-screen (no scroll in `ExpandedLayout`) — wrapped the selected panel in a vertical-scroll Column.
- Unit tests rewritten for multi-series: **14/14 passing** (`:shared:jsNodeTest`). Full E2E pass on headless Chromium: two-line charts + legends mobile & desktop, pagination `[1/2]`→`[2/2]`, range toggle 90D→ALL, weight single-marker regression. Details in the milestone section above.

**Git state at handoff (branch `prototype`):**
- Latest commit: `365aef0 "added timeline"` (user's — captured the weight-chart milestone).
- **Uncommitted working tree** (all verified building + E2E-passing):
  ```
  M  shared/.../ui/chart/ChartModel.kt          # TimeRange / MultiSeries refactor, minStep ticks
  M  shared/.../ui/chart/WeightTrendChart.kt    # now a thin adapter over MultiTrendChart
  M  shared/.../ui/component/BowelPanel.kt      # chart + pagination
  M  shared/.../ui/component/PeriodPanel.kt     # chart + pagination
  M  shared/.../ui/screen/DashboardScreen.kt    # scrollable detail column (desktop pager clip fix)
  M  shared/.../ui/theme/Colors.kt              # LymphAmber (#FFB454)
  M  shared/src/commonTest/.../ChartModelTest.kt  # multi-series tests, 14 total
  ?? shared/.../ui/chart/MultiTrendChart.kt     # generic two-line chart composable (new)
  ?? shared/.../ui/chart/PeriodTrendChart.kt    # adapter (new)
  ?? shared/.../ui/chart/BowelTrendChart.kt     # adapter (new)
  ```
- User manages commits themselves — advise what to commit, don't commit.

**Environment notes for the next session:**
- Fresh container this time: reinstalled JDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64`), Chromium (headless CDP on :9222), Tesseract + ImageMagick. The `/root/lymph-build` mirror from the previous session was gone; **builds now run directly in `/workspace/lymph`** — the corrupted `.gradle` locks no longer block Gradle here (verify with a quick `./gradlew :shared:jsNodeTest` if in doubt).
- Stale Windows-generated paths under `build/js/**/kotlin-js-store` / yarn workspaces can break the first build after a fresh checkout — recovery that worked: fix/remove stale workspace entries then `./gradlew kotlinUpgradeYarnLock`, rebuild.
- The dev server died once mid-session for no obvious reason (no crash in log, memory fine). Recovery: kill any stragglers (`pkill -f jsBrowserDevelopmentRun`), restart with the setsid/nohup pattern below, wait for "Loopback", then **reload the page and re-seed** — all data is in-memory on web.
- Dev server: `cd /workspace/lymph && export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && setsid nohup ./gradlew :webApp:jsBrowserDevelopmentRun --no-daemon > /tmp/webdev.log 2>&1 < /dev/null & disown` — wait for "Loopback".
- CDP driver this session: `/tmp/cdp.mjs` (commands: `nav`, `shot`, `click x y`, `metrics w h`, `wheel x y dy`, `wait ms`) + `/tmp/seed-period.mjs` / `/tmp/seed-bowel.mjs` (mobile 375×812 coordinates). Ephemeral — recreate from the Testing notes if gone.
- **Desktop (1280×900) E2E coordinates**: sidebar nav WEIGHT/PERIOD/BOWEL ≈ (100, 83/127/171); ADD RECORD button ≈ **(128, 791)** (filled cyan block y≈772–811 — clicks at y>815 miss it); EXPORT DATA text ≈ (73–128, 854). Input modal on desktop is centered: SAVE button center ≈ **(872, 607)**. Range buttons top-right of chart: selected-90D fill bbox x≈1075–1130 y≈73–110; ALL button center ≈ (1220, 91).

## Session Handoff — Sep 11 (web E2E: `:e2e` raw-CDP module; per-platform testing decision)

**What happened this session:**
- Built a dedicated **`:e2e` Kotlin/JVM module** to drive the web app end-to-end, replacing the throwaway Node CDP scripts. Rationale (user): keep it in-repo and Gradle-native; avoid adding browser-driver dependencies where possible.
  - `e2e/build.gradle.kts` — `kotlin("jvm")` + kotlinx-serialization plugin, JDK 17 toolchain, JUnit Platform, **gated behind `-Pe2e=true`** (plain builds never spin up a browser). Tool paths (Edge/tesseract/magick) via Gradle property → env var → Windows default.
  - `Cdp.kt` — minimal CDP client over `java.net.http.WebSocket` (JDK 17, zero WebSocket deps); kotlinx.serialization only deserializes the few fields read (`id`, `result.data`, `backendNodeId`, `nodeId`).
  - `Ocr.kt` — tesseract TSV word boxes + ImageMagick; banding (page-segmentation drops sparse lines on tall images), 3 preprocessing passes per band (raw / inverted-threshold-55% / grayscale-threshold-60 for the black-on-cyan SAVE button), fuzzy phrase match (OCR mangles words), snackbar-strip reader.
  - `LymphImportExportTest.kt` — full scenario: hook export → seed weight/period/bowel → verify "70 kg" → capture zip + structural check → reload → import ("3 added") → re-import ("0 added, 3 duplicates").
- Catalog additions (`libs.versions.toml`): `kotlinx-serialization-json 1.9.0`, `junit-jupiter 5.14.4`, `junit-platform-launcher 1.14.4` (Gradle 9 requires it on the test runtime classpath), serialization plugin alias. `settings.gradle.kts`: `include(":e2e")`.

**Current state of `:e2e`:**
- ✅ Compiles green (`:e2e:testClasses`). JUnit wiring works (fixed Gradle 9 launcher + `@TestInstance(PER_CLASS)`). CDP connect + small commands work.
- ✅ **OCR runaway loop fixed & bounded**: the band loop was unbounded and spawned ~12,400 stray PNGs / hung ~40 min; now hard-capped at `y0 < IMG_H` (≤4 iterations) — test finishes in 56s instead of hanging.
- ⚠️ **BLOCKER (half-fixed): fragmented WebSocket text frames.** A screenshot's base64 spans multiple WS frames; `onText` parses each fragment as complete JSON, fails silently, drops it → `Page.captureScreenshot` times out at 30s. Fix = accumulate into a buffer until `last=true`, then parse. The `textBuffer` field is added but **not yet wired** into `onText`/`handle`. ~6 lines to finish.
- Export mechanism confirmed via probes: the app produces a real `lymph-export.zip`; **headless Edge cancels the actual disk write** (`downloadWillBegin` fires with a real blob, then state→canceled). So the test hooks `globalThis.__lymphDownload(name, b64)` in-page (via CDP `Runtime.evaluate`) and captures the base64 directly — sidesteps download persistence entirely.

**Per-platform E2E testing decision (documented in README "E2E / UI Testing Bases per Platform"):**
- **Web**: raw CDP now (`:e2e`), no new deps. Playwright = intended long-term web E2E base, shelved for now (no-new-deps principle + Chrome headless download/security constraints).
- **iOS**: XCUITest (Mac/Xcode only) — when import lands on native.
- **Android**: Espresso / Compose UI Test (`createComposeRule`) via adb/emulator — when import lands on native.
- Shared data logic (CSV/ZIP, insert-only import) is in `commonMain` and already covered by cross-platform `commonTest` unit tests — that's where real cross-target correctness lives; the E2E harnesses are per-platform integration layers on top. Mobile harnesses read the semantics/accessibility tree directly (no OCR).

**Next steps:**
1. Finish the fragment-buffer fix in `Cdp.kt` (`onText`: append to buffer, parse only when `last=true`, clear after). ~6 lines.
2. Re-run `:e2e:test -Pe2e=true` **in background** (notify on BUILD SUCCESSFUL/FAILED), poll the test XML — do NOT block a foreground call (a prior 420s timeout did not kill the detached Gradle process, letting it run ~40 min).
3. Iterate OCR/click-coords if needed until green: seed 3 → "70 kg" visible → zip captured + structurally valid (3 CSVs, headers, rows) → reload → "IMPORT OK — 3 added" → re-import → "0 added, 3 duplicates". Don't call it done before a real green run.
4. Update AGENTS.md E2E section (user to do): replace the Node/CDP line with "`:e2e` Kotlin/JVM module drives headless Edge via `java.net.http.WebSocket`; run `./gradlew :e2e:test -Pe2e=true` after starting the dev server."

---

## Session Handoff — Sep 12 (Playwright migration DONE; raw CDP removed) — SUPERSEDES Sep 11 next-steps

> **Read this, not the Sep 11 "Next steps" — `Cdp.kt` no longer exists.** The Sep 11 fragment-buffer blocker is obsolete.

- **Raw CDP is gone**: `Cdp.kt` deleted, kotlinx-serialization removed from `libs.versions.toml`. `:e2e` is now a **Playwright (Java client) 1.62.0** driver (`com.microsoft.playwright:playwright` — user-approved dependency) launching **headless Edge** via `LaunchOptions().setChannel("msedge")`, context viewport 1280×900. `e2e.edge` sysprop dropped (channel is hardcoded); tesseract/magick/workdir sysprops remain.
- **Proven by throwaway DiagTest (since deleted): Playwright's native download interception WORKS** — clicking EXPORT DATA yielded a real 474-byte `lymph-export.zip` via `page.onDownload` + `saveAs`. This **overturns** the raw-CDP-era "headless downloads cancel" finding. The old `__lymphDownload` in-page hook was fundamentally broken anyway: Kotlin/JS IR top-level vals are module-scoped, so the helper is NOT on `globalThis` at page load (`typeof __lymphDownload === 'undefined'`; globals dump shows only skiko/webpack names). Don't retry the hook.
- Test flow now: OCR-locate (tesseract, unchanged `Ocr.kt`) → `page.mouse().click(x,y)`; export capture = `page.onDownload` latch → `saveAs` → zip structure check; import = `page.onFileChooser` (registered in setUp) + `fc.setFiles(importZipPath)` with the path set before clicking IMPORT DATA.
- **Current blocker — test bug, ~5-line fix**: `seedRecord()`'s "modal closed when SAVE disappears" poll re-OCRs a **stale screenshot** — `lastShot` is never refreshed inside the loop, so `findPhrase` always sees the pre-click frame → `misses` stays 0 → fails "modal did not close after SAVE (weight): expected: 2 but was: 0" **even if the modal actually closed**. Fix: in that loop, take a fresh `page.screenshot()` into `lastShot` each iteration before the `findPhrase` check. (OCR cache is keyed path+size+mtime, so an overwritten `shot.png` re-OCRs correctly.) Clicks aimed correctly per the run log: ADD RECORD (114,724) — shifted up because the sidebar gained IMPORT DATA; SAVE (873,608) ≈ known-good (872,607).
- **Acceptance bar for green**: seed 3 records (weight/period/bowel) → "70 kg" visible → EXPORT DATA → zip captured + 3 CSVs w/ headers + ≥1 row → "EXPORT OK" snackbar → `page.reload()` → IMPORT DATA → "3 added" → re-import → "0 added, 3 duplicates" → "70 kg" still visible.
- **Run**: dev server on :8080 first (`./gradlew :webApp:jsBrowserDevelopmentRun --no-daemon`, background, poll curl), then `./gradlew :e2e:test -Pe2e=true --no-daemon` (~60–90s). Results: `e2e/build/test-results/test/*.xml`; screenshots + captured zip in `e2e/build/e2e/`. A benign console 404 appears on every app load — ignore it.
- **Playwright API quirks hit (Kotlin)**: `setTimeout` takes `Double`; it's `onConsoleMessage` (not `onConsole`); `Response.url()` is a method; `saveAs` wants `java.nio.file.Path`; a bare `fail(...)` statement inside an if-block triggers "Cannot infer type for V" — use `throw AssertionError(...)`. **Terminal tool**: `notify` requires `background: true` in the same call; long gradle runs should be backgrounded (a timed-out foreground call once left the child gradle running ~40 min).
- Playwright's managed browsers are already at `~/.cache/ms-playwright`; the test uses installed Edge via channel, not those. No `playwright install` needed.
- Docs current: README (insert-only import semantics; per-platform E2E table — web=Playwright, iOS=XCUITest, Android=Espresso/Compose UI Test). **AGENTS.md still describes the old Node/CDP E2E — the user updates AGENTS.md themselves** (agent writes to it are blocked); hand them the replacement lines when green.

---

## Session Handoff — Sep 17 (`:e2e` import/export round-trip GREEN)

> The full web E2E scenario is **green, twice** (once alongside the diagnostic, once standalone after deleting it): seed → "70 kg" → EXPORT → real zip + structure check → "EXPORT OK" → reload → IMPORT → "3 added" → re-import → "0 added, 3 duplicates" → "70 kg" still visible. `./gradlew :e2e:test -Pe2e=true --no-daemon` → BUILD SUCCESSFUL (~1m45s).

**Bugs fixed this session (all in the test harness, no app changes):**

1. **Stale-screenshot poll (Sep 12's known blocker)** — `seedRecord()`'s modal-closed loop now refreshes `lastShot` + re-screenshots every iteration.
2. **"70 kg" exact-substring assert too strict** — OCR mangles the row ("70kq70Tokg"); switched `ocrTextContains` to the fuzzy `findPhrase` matcher.
3. **M3 dark snackbar was invisible to OCR** — the snackbar renders as a LIGHT #E6E0E9 inverseSurface floating bottom-center of the detail area; psm-6 page segmentation over a full-width strip drops it entirely. `Ocr.snackbarText` rewritten: exact-color bbox via `magick %@` → tight crop → 2× upscale → fuzz-fill surface to white-on-black → tesseract. Verified to read `EXPORT OK — zip generated` and `IMPORT OK — 0 added, 3 duplicates` perfectly on captured frames.
4. **`tsvText` word order** — a global y-sort interleaved words within one line ("0 3 added, duplicates"); now groups lines by y-proximity THEN x-sorts within each line.
5. **Silent `FileChooser.setFiles` failure on the 2nd chooser** — chooser fired but no change event reached the app (no import, no snackbar). Import delivery now goes through `page.setInputFiles("#__lymph_file_input", …)` (the app's hidden input is light-DOM with a known id); the chooser event is kept as click-landed detection only.
6. **Swallowed first clicks (recurring flake)** — Compose-on-canvas occasionally drops a click at verified-correct coordinates (3× observed, always the 1st click after an idle poll); `clickPhraseUntil` re-locates from a fresh screenshot and re-clicks (3 attempts) until the expected event (download / chooser) fires. In the final green runs every retry WARN was click #1 → click #2 landing.
7. **Regex hygiene** — `EXPORT\s+OK` can never match OCR output with no separator (`EXPORTOK`); snackbars are matched with `\s*` + IGNORE_CASE, and OCR newlines are normalized to spaces.

**`Ocr.kt` preprocessing pass leak plugged** — `wordsForBand` now deletes each generated `pre-*.png` (a run previously leaked ~200 files into `e2e/build/e2e/`).

**Diagnostics playbook that got there (for future sessions):** don't theorize about swallowed clicks/silent snackbars — write a throwaway DiagTest mirroring the failing sequence with (a) event timestamps (`System.nanoTime()`), (b) rapid-fire raw screenshots during the poll window (NO OCR in the timing loop — it eats ~2s/poll and misses the ~4s snackbar), (c) offline OCR of the captured frames afterwards. Pixel-histogram analysis (`magick histogram:info:`) identified the #E6E0E9 snackbar surface when OCR saw nothing; `magick %@` bbox located it at 624×48+449+816 (bottom-center of the detail area, x 449–1073 — NOT in the sidebar strip the old reader assumed).

**AGENTS.md replacement lines (user to apply — agent writes blocked):**
- Web E2E line → **"`:e2e` Kotlin/JVM module drives headless Edge via Playwright (Java client) + OCR (tesseract/ImageMagick). Run `./gradlew :e2e:test -Pe2e=true --no-daemon` after starting the dev server on :8080. Green scenario: seed 3 → export zip → reload → import "3 added" → re-import "0 added, 3 duplicates"."**
- Delete the CDP launch/`Browser.setDownloadBehavior`/file-chooser-via-CDP gotchas; keep the OCR-phrase (not full-line) + expected-snackbar-strings notes; add: "snackbar = light #E6E0E9 box bottom-center of the DETAIL area — read with `Ocr.snackbarText` (bbox+crop+2×), not a bottom strip"; add: "first click after an idle poll can be swallowed — use `clickPhraseUntil`".

---

## Current State (checkpoint)

The web portal is **ugly but working end-to-end** — that's the agreed v0.1 bar:

- Mobile: dashboard, drawer (Export Data / Settings), FAB → input modal, reactive panels, real ZIP download.
- Desktop (>840dp): sidebar with ADD RECORD + EXPORT DATA + IMPORT DATA, single-panel detail area driven by selectable WEIGHT/PERIOD/BOWEL nav (inverted-cyan highlight), snackbar feedback. Detail column scrolls when content exceeds the viewport (pager no longer clips).
- Weight entry: KG/LB unit toggle, integer-hundredths precision (no float drift), ≤2 decimal places; canonical kg storage. Verified end-to-end via CDP: 70 → +4×0.1 → dial "70.4" → CSV `70.4`; LB round-trip 70kg → 154.32lb → +0.1 → 154.42lb → CSV `70.04` kg.
- **All three panels** have a trend chart with `[90D] [1Y] [ALL]` range selector above a paginated table (10/page, `[ n/N ]` pager): WEIGHT single cyan line; PERIOD two lines (duration + flow); BOWEL two lines (Bristol + effort) — cyan/amber pair. Unit-tested (`ChartModelTest`, 14/14 via `:shared:jsNodeTest`) and passed an agent-driven E2E pass, but **marked incomplete & unverified at the user's request** — they will verify these sections themselves.
- **Web E2E (`:e2e`) is GREEN end-to-end** — the seed→export→reload→import→re-import round trip passes as a real Gradle test (Sep 17 section above).
- **Web persistence (Sep 18): localStorage CSV mirror** — the refresh-loses-data regression is FIXED and E2E-guarded. Each `Memory*Dao` hydrates from `localStorage` at construction and re-encodes its table on every insert/delete via `WebTableStore` + `CsvEncoder`/`CsvDecoder` (one format for storage, export, and import). New files: `shared/src/webMain/.../platform/LocalStorage.kt` (raw-`js()` interop, same pattern as `BrowserDownload`, wasmJs-safe) + `shared/src/webMain/.../data/dao/WebTableStore.kt`. Verified: both web targets compile; E2E now asserts data SURVIVES `page.reload()` (seeded "70 kg" still visible), and the import scenario clears storage first (`window.localStorage.clear()` via `page.evaluate`) for its fresh-DB step — full suite green; 48/48 unit tests unchanged.
- Both web targets compile; JS IR is the runtime-verified path.

Remaining backlog: Priority 2 Android runtime test (needs emulator/device), CI/CD pipeline, Settings drawer action, iOS Xcode host project.
