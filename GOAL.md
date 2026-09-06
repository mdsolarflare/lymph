# Lymph — Status & Next Steps

> This file is the canonical session-context document (renamed from `TODO.md` on Aug 27, user-managed rename). Update it at the end of every session; read it at the start.

## Quick Start for Next Agent

1. Set `JAVA_HOME` to a **JDK 17** path before running Gradle (AGP 9.x requires JDK 17 compile target). On Windows:
   ```bash
   export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.x+xx"
   ./gradlew :webApp:jsBrowserDevelopmentRun
   ```
2. Use `./gradlew clean` for cleanup — **never** `rmdir` / `rm -rf`.
3. Reference `example-kmp-app/` as a working template (do not edit it).
4. **Unit tests**: `./gradlew :shared:jsNodeTest` (runs commonTest on Node — the only test runner available in this container; no Android SDK). On a machine with an Android SDK, `:shared:testAndroid` also works (`withHostTest {}` is configured).
5. **Yarn lock**: if a build fails with "Lock file was changed" (happens after any change to JS-target dependencies, e.g. the `nodejs()` test env added Aug 27), run `./gradlew kotlinUpgradeYarnLock` once, then rebuild. Same recovery as a fresh checkout (`kotlin-js-store/` is gitignored by user choice).

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

## Current State (checkpoint)

The web portal is **ugly but working end-to-end** — that's the agreed v0.1 bar:

- Mobile: dashboard, drawer (Export Data / Settings), FAB → input modal, reactive panels, real ZIP download.
- Desktop (>840dp): sidebar with ADD RECORD + EXPORT DATA, single-panel detail area driven by selectable WEIGHT/PERIOD/BOWEL nav (inverted-cyan highlight), snackbar feedback. Detail column scrolls when content exceeds the viewport (pager no longer clips).
- Weight entry: KG/LB unit toggle, integer-hundredths precision (no float drift), ≤2 decimal places; canonical kg storage. Verified end-to-end via CDP: 70 → +4×0.1 → dial "70.4" → CSV `70.4`; LB round-trip 70kg → 154.32lb → +0.1 → 154.42lb → CSV `70.04` kg.
- **All three panels** have a trend chart with `[90D] [1Y] [ALL]` range selector above a paginated table (10/page, `[ n/N ]` pager): WEIGHT single cyan line; PERIOD two lines (duration + flow); BOWEL two lines (Bristol + effort) — cyan/amber pair. Unit-tested (`ChartModelTest`, 14/14 via `:shared:jsNodeTest`) and passed an agent-driven E2E pass, but **marked incomplete & unverified at the user's request** — they will verify these sections themselves.
- Both web targets compile; JS IR is the runtime-verified path.

Next up (in progress): **v0.2 CSV import** — `CsvDecoder` + `ZipDecoder` + `DataImporter` in commonMain, web file-picker interop, IMPORT DATA UI on web only (native pickers deferred). Full format docs land in `docs/DATA-EXCHANGE.md`. Priority 2 Android runtime test still pending on a machine with an SDK/emulator.
