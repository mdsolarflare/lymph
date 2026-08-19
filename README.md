# lymph

Offline-first biometric data sanctuary. Zero servers. Zero telemetry. Your body's data stays on your device.

```
┌──────────────────────────────────────────────┐
│  LYMPH v0.1.0                                │
│  STATUS: NOMINAL                             │
│  RECORDS: 0 WEIGHT | 0 PERIOD | 0 BOWEL     │
│                                              │
│  > AWAITING INPUT                            │
└──────────────────────────────────────────────┘
```

## Architecture

Lymph is a **100% client-side** Kotlin Multiplatform application. There is no backend, no cloud sync service, and no network calls. All data persists locally via raw SQLite on native targets — hand-written SQL, no ORM. Cross-device portability is achieved through explicit CSV export/import — you own the migration path.

### Targets

| Target | Module | Runtime | Storage |
|--------|--------|---------|---------|
| Android | `:androidApp` | Native APK, minSdk 24 | SQLite via `SQLiteDatabase` (built into SDK) |
| iOS | `:shared` → Xcode host | Native via Kotlin/Native | sqlite3 C API (ships with iOS) |
| Web | `:webApp` | JS (IR) **and** wasmJs, browser-hosted via Compose `ComposeViewport` (Skia canvas in Shadow DOM) | In-memory + ZIP download/export |

### Persistence Strategy

No ORM. No code generation layer. Three hand-written SQL tables, direct query strings in each DAO implementation:

```sql
-- Weight records
CREATE TABLE IF NOT EXISTS weight_records (
    id TEXT PRIMARY KEY, timestamp INTEGER NOT NULL,
    weight_kg REAL NOT NULL, note TEXT);

-- Period records
CREATE TABLE IF NOT EXISTS period_records (
    id TEXT PRIMARY KEY, timestamp INTEGER NOT NULL,
    duration_days INTEGER NOT NULL, flow_intensity INTEGER NOT NULL DEFAULT 2,
    symptoms TEXT);

-- Bowel records
CREATE TABLE IF NOT EXISTS bowel_records (
    id TEXT PRIMARY KEY, timestamp INTEGER NOT NULL,
    bristol_type INTEGER NOT NULL, effort_level INTEGER NOT NULL DEFAULT 3,
    note TEXT);
```

A thin `SqlExecutor` interface abstracts the platform-specific SQLite binding:

| Platform | Binding | Layer |
|----------|---------|-------|
| Android | `android.database.sqlite.SQLiteDatabase` | Built into SDK, zero dependencies |
| iOS | sqlite3 C API via Kotlin/Native interop | Ships with iOS, zero dependencies |
| Web (JS IR + wasmJs) | `MemoryWeightDao`, etc. (`webMain`) | In-memory `MutableStateFlow` lists; ZIP browser download for persistence |

```
lymph/
├── shared/                          # Multiplatform core
│   ├── src/commonMain/kotlin/       # Entities, DAO contracts, ViewModel, Compose UI, theme
│   │   └── com/lymph/
│   │       ├── data/                # Plain entities, DAO interfaces, SqlExecutor contract
│   │       │   ├── db/              # SqlExecutor / SqlRowMapper / SqlRow common types
│   │       │   ├── dao/             # WeightDao, PeriodDao, BowelDao (plain interfaces)
│   │       │   ├── entity/          # WeightRecord, PeriodRecord, BowelRecord (data classes)
│   │       │   └── export/          # CsvEncoder, ZipEncoder, DataExporter
│   │       ├── platform/            # PlatformContext abstraction
│   │       ├── ui/                  # Compose screens, components, theme
│   │       └── util/                # UUID generator, timestamp formatting
│   ├── src/nativeMain/kotlin/       # SqlExecutor impl (NativeSqlExecutor), SQL-backed DAOs
│   ├── src/androidMain/kotlin/      # AndroidSqlHandle (SQLiteDatabase interop)
│   ├── src/iosMain/kotlin/          # IosSqlHandle (sqlite3 C API interop)
│   └── src/webMain/kotlin/          # In-memory DAOs, WebPlatform, browser download helper
│                                    # (shared by both js(IR) and wasmJs targets)
├── androidApp/                      # Android Compose UI shell
│   └── src/main/kotlin/com/lymph/   # MainActivity + platform wiring
├── webApp/                          # Web Compose UI shell (JS IR + wasmJs)
│   └── src/webMain/
│       ├── kotlin/com/lymph/Main.kt # Entry point — ComposeViewport { LymphApp(...) }
│       └── resources/               # index.html + styles.css (full-viewport CSS)
└── gradle/libs.versions.toml        # Version catalog (single source of truth)
```

## Design Language

### Aesthetic

Clinical command-line interface. Think Pi-hole dashboard, not health-wellness app. Sharp corners only — no rounded cards, no soft shadows, no gradients. The UI communicates data density and precision.

### Color Palette

| Token | Value | Usage |
|-------|-------|-------|
| `LymphBlack` | `#111111` (True Black) | All backgrounds |
| `LymphCyan` | `#D6F9FF` (Glacial Cyan) | Body text, chart lines, interactive elements |
| `LymphCyanDim` | `#5A7A82` | Labels, timestamps, secondary data |
| `LymphRed` | `#FF4D4D` | Errors, alerts |
| `LymphSurface` | `#1A1A1A` | Elevated surfaces (cards) |

### Typography

Strict monospace across all surfaces. No variable fonts, no weight hierarchy beyond Bold for headings. Data is presented in tabular form with fixed-width columns.

## Data Model

### Entities — Plain Kotlin data classes

Three entities, one common interface. No annotations, no expect/actual split:

```kotlin
interface BiometricRecord {
    val id: String       // UUID v4 string
    val timestamp: Long  // epoch milliseconds
}

data class WeightRecord(
    override val id: String,
    override val timestamp: Long,
    val weightKg: Double,
    val note: String? = null
) : BiometricRecord

data class PeriodRecord(
    override val id: String,
    override val timestamp: Long,
    val durationDays: Int,
    val flowIntensity: Int = 2,       // 1-4 scale
    val symptoms: String? = null
) : BiometricRecord

data class BowelRecord(
    override val id: String,
    override val timestamp: Long,
    val bristolType: Int,              // Bristol Stool Scale 1-7
    val effortLevel: Int = 3,          // 1-5 scale
    val note: String? = null
) : BiometricRecord
```

### DAO Layer — Plain interfaces + per-platform implementations

Each record type has a plain interface in `commonMain`:

```kotlin
interface WeightDao {
    fun getAll(): Flow<List<WeightRecord>>
    suspend fun getSnapshot(): List<WeightRecord>
    suspend fun insert(record: WeightRecord)
    suspend fun delete(id: String)
}
```

Platform-specific implementations:

| Platform | Concrete class | Backed by |
|----------|---------------|-----------|
| Native (Android/iOS) | `SqlWeightDao` | Real SQLite via `NativeSqlExecutor` |
| Web (JS IR + wasmJs) | `MemoryWeightDao` (`webMain`) | In-memory `MutableStateFlow` list |

A `BiometricViewModel` (expect/actual class) aggregates all three DAOs and exposes reactive state to the UI.

## UI Infrastructure

### View States

Lymph adapts its layout based on available screen real estate:

#### Mobile Dashboard (`<= 840dp`)

Single-column vertical stack used by Android, iOS, and mobile Web viewports.

```
┌─────────────────────────────┐
│  ☰  LYMPH                   │  ← Sandwich bar (settings, export)
├─────────────────────────────┤
│                             │
│  [ WEIGHT ]                 │
│  [ PERIOD ]                 │  ← Metric panels (records or empty state)
│  [ BOWEL  ]                 │
│                             │
└─────────────────────────────┘
              [+]             ← Floating action button (bottom-right)
```

- **Sandwich bar**: Top-left hamburger icon opens a modal drawer with Export Data and Settings.
- **Floating `+` button**: Bottom-right corner. Triggers the input modal.

#### Expanded Web (`> 840dp`)

Master-Detail layout exploiting horizontal space for dense data visualization.

```
┌──────────┬───────────────────────────────────────┐
│          │                                       │
│  MASTER  │           DETAIL                      │
│          │                                       │
│ ─────    │  [ WEIGHT ]  [ PERIOD ]  [ BOWEL  ]   │
│ WEIGHT   │                                       │
│ PERIOD   │  Side-by-side panels with             │
│ BOWEL    │  terminal-style readouts              │
│          │                                       │
│ ─────    │                                       │
│ ADD      │                                       │
│ RECORD   │                                       │
│ EXPORT   │                                       │
│ DATA     │                                       │
└──────────┴───────────────────────────────────────┘
```

- **Sidebar**: section labels (decorative — all panels are always visible), ADD RECORD button, and an EXPORT DATA button that triggers the ZIP download + snackbar.

### Input Modal

The `+` button opens a modal with clean dial/counter controls for manual entry. Each record type presents its own set of controls:

- **Weight**: Numeric dial for kg, optional note field.
- **Period**: Duration counter, flow intensity selector (1–4), symptoms text.
- **Bowel**: Bristol Stool Scale counter (1–7), effort level counter (1–5), optional note.

Timestamp is auto-populated to `now()`.

## Data Portability

### CSV Export

All three tables are bundled into a single ZIP archive containing one CSV per table:

```
lymph-export.zip/
├── weight_records.csv       # id,timestamp,weightKg,note
├── period_records.csv       # id,timestamp,durationDays,flowIntensity,symptoms
└── bowel_records.csv        # id,timestamp,bristolType,effortLevel,note
```

Export is triggered from the drawer menu (mobile) or the EXPORT DATA button in the master sidebar (expanded web layout). On web, `WebDataExporter.exportZip()` builds the ZIP and hands it to a small JS helper (`BrowserDownload.kt`) that creates a Blob + object URL and clicks a hidden anchor — the archive lands in the browser's Downloads folder as `lymph-export.zip`. The payload is also returned as `ByteArray` via the platform-specific `DataExporter` for future import support.

### CSV Import

Import reads a matching ZIP archive, validates schema compatibility, and upserts records by UUID (skipping duplicates). Planned for v0.2.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4.10 |
| UI | Compose Multiplatform 1.11.1 (material3 1.9.0) |
| Persistence | Raw SQLite — no ORM, hand-written SQL |
| Dates | kotlinx-datetime 0.8.0 |
| I/O | kotlinx-io-core 0.7.0 |
| Web Runtime | Kotlin/JS (IR) + wasmJs — see [Web Rendering Note](#web-rendering-note)

### Web Rendering Note: `ComposeViewport` (Skia canvas), not the HTML renderer

The web app renders through **`ComposeViewport`** — Compose Multiplatform's Skia-based canvas renderer. The canvas lives inside a **Shadow DOM** attached to `<body>`, so:

- Light-DOM queries (`document.querySelector`) will not find Compose nodes; use DevTools' shadow-piercing inspection or CDP with `includeCommandLineAPI`/deep search.
- `webApp/src/webMain/resources/index.html` must link a stylesheet that makes `html, body` fill the viewport (see `styles.css`). Without it the canvas collapses to content height and only a thin strip of UI is visible.

**Dual-target constraint:** the `webMain` source set compiles for **both** `js(IR)` and `wasmJs`, so any web code must stay compatible with both. Notable Kotlin/Wasm interop limits hit during development:

- Raw `js("...")` expressions are allowed, but a top-level property initialized from one may only be typed as external/primitive/string/function (not `Unit`).
- External JS functions cannot take `ByteArray` parameters — pass base64-encoded `String`s instead (see `BrowserDownload.kt`, which installs its helper on `globalThis` and is invoked through a `@JsName` external function).

## Development Environment

### Base Platform

Primary development runs on **macOS (Apple Silicon)** — MacBook Neo or equivalent M-series hardware. This provides native compatibility with all three targets:

- Android builds via Gradle + Android SDK
- iOS framework builds via Kotlin/Native tooling
- Web builds via WASM JS compiler

### Runtime Management (`asdf`)

We use [`asdf`](https://github.com/asdf-vm/asdf) for version-managed runtimes:

**JDK 17 is required.** AGP 9.x compiles against a JDK 17 toolchain. Gradle can *run* on newer JDKs (e.g., JDK 25), but it still needs a JDK 17 installation available to compile the project.

```bash
# Install JVM — must be JDK 17 for AGP compatibility
asdf plugin add java
asdf install java temurin@17.0.13
asdf global java temurin@17.0.13

# Install Node.js (optional, for web dev server tooling)
asdf plugin add nodejs
asdf install nodejs 22.x
asdf global nodejs 22.x
```

Verify:

```bash
java -version   # must show JDK 17.x
node --version  # optional, 20+ recommended
```

> **Windows note:** If your only JDK is not version 17 (e.g., Microsoft JDK 25), you must set `JAVA_HOME` to a JDK 17 path before running Gradle:
> ```bash
> export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.x+xx"
> ./gradlew build
> ```

### Android SDK

Required for Android builds. Install via Homebrew or JetBrains Toolbox:

```bash
# Option A: Command-line tools only (minimal)
brew install --cask android-commandlinetools

# Option B: Full Android Studio (includes emulator)
brew install --cask android-studio
```

After installation, create `local.properties` in the project root:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

Or set `ANDROID_HOME`:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

### iOS Tooling

Xcode (with command-line tools) is required for iOS framework builds:

```bash
xcode-select --install    # Command Line Tools
# Or install full Xcode from the Mac App Store
```

## Building & Running

### Quick Start

```bash
# Web dev server — the fastest way to see the app (JDK 17 via JAVA_HOME required)
./gradlew :webApp:jsBrowserDevelopmentRun   # → http://localhost:8080

# Build shared module for all platforms (requires Android SDK + Xcode CLI tools on macOS)
./gradlew :shared:assemble
```

### Android

```bash
# Assemble debug APK
./gradlew :androidApp:assembleDebug

# Install on connected device or running emulator
./gradlew :androidApp:installDebug

# Run with Gradle (requires a connected target)
./gradlew :androidApp:runDebug
```

**Testing:** Use the Android Emulator (Pixel 6 / API 35 recommended) or a physical device via USB/wireless debugging. Standard `adb` workflow applies.

### iOS

The `:shared` module produces an XCFramework consumable by an Xcode host project:

```bash
# Build framework for simulator (Apple Silicon Mac)
./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pios.target=iosSimulatorArm64

# Build framework for device
./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pios.target=iosArm64
```

**Testing:** Use the iOS Simulator (iPhone 15 / latest iOS recommended) for local development. Deploy to physical iPhones via Xcode for real-device validation. An Xcode host project is planned as a follow-up module.

### Web

```bash
# Run development server (JS IR) — primary, runtime-verified path
./gradlew :webApp:jsBrowserDevelopmentRun   # → http://localhost:8080

# wasmJs variant (compiles; runtime not yet verified)
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# Build production JS bundle
./gradlew :webApp:jsBrowserProductionWebpack
```

The dev server starts at `http://localhost:8080` by default. Open in any modern browser (Chrome, Firefox, Safari). Note: the webpack dev server hot-reloads resource changes, but Kotlin source changes require restarting the Gradle task.

**Testing:** Standard browser DevTools apply — remember Compose renders to a canvas inside Shadow DOM, so inspect via shadow-piercing tools rather than plain DOM queries. The web target uses an in-memory database — all data is lost on page refresh. Use the export feature (ZIP download) to persist data across sessions during development.

## CI/CD

> **Planned.** GitHub Actions workflow will be added once local builds are stable for all three targets. Initial pipeline will run unit tests and verify clean compilation. Distribution (Play Store, TestFlight, static hosting) is a separate effort.

## Development Conventions

- **No rounded corners anywhere.** `CornerSize.Zero` on all shapes.
- **Monospace font only.** Configure `FontFamily.Monospace` at the theme root.
- **Glacial Cyan on True Black.** No tertiary colors in the UI palette.
- **UUID primary keys.** All records use UUID v4 strings — no auto-increment integers.
- **Timestamps as epoch millis.** Stored as `Long`, formatted via shared `formatTimestamp()` utility.
- **Explicit multi-table design.** Never merge biometric types into a single table. Each domain gets its own entity, DAO, and CSV export file.
- **Raw SQL only.** No ORM, no code generation layer. Hand-written queries are clearer than generated ones at this scale.

# Standing Agent Rules
Rules Established:
1. **Identify issues** → List by importance (biggest first) → **Fix only #1** → Test → Loop back to step 1
2. **Avoid overriding defined versions from dependencies** - let version catalog manage compatibility. If you run into a version conflict, you must ask the user how we should resolve it.
3. **Stick with idiomatic Kotlin** - no unnecessary complexity
4. **Use `./gradlew`** for builds (not system gradle), you may need to provide the JAVA_HOME and GRADLE_HOME locations
5. **Reference `example-kmp-app/` as a strong working template** - it is a working template project that you can reference for compatibility and scaffolding.
6. **Do not edit `example-kmp-app`**
7. **Use Gradle tasks for all build and cleanup operations** — never use `rmdir`, `rm -rf`, or manual directory deletion. Use `./gradlew clean` instead of deleting the `build/` directory manually.
8. **Project requires JDK 17 toolchain.** AGP 9.x compiles against JDK 17 specifically. Never change `jvmToolchain(17)` in build files. On Windows, set `JAVA_HOME` to a JDK 17 path before running Gradle if the default JDK is not version 17.
9. **Gradle 9.x API constraints.** Do not use deprecated Gradle APIs (`toolchainManagement.jvm.repositories {}`, `by creating`). When in doubt about an API, check `example-kmp-app/` or test with a clean build first.
10. **Android SDK types.** Use `SQLiteStatement` (from `android.database.sqlite.SQLiteStatement`), not `SQLiteDatabase.Statement`. Android bind methods take `Int` index, not `Long`.
11. **Nullable Boolean comparisons.** Never use `!nullable?.exists()` — Kotlin won't allow negating a nullable Boolean. Use `!= true` instead: `if (dbFile.parentFile?.exists() != true) { ... }`.
12. **String template method calls** must use braces: `${methodCall()}` not `$methodCall()`.
13. **Compose tooling annotations require explicit dependencies.** If a module uses `@Preview`, add `implementation(compose.uiToolingPreview)` and optionally `debugImplementation(compose.uiTooling)`. The Compose Multiplatform plugin doesn't auto-inject these into plain Android application modules — use version catalog refs (`libs.compose.uiToolingPreview`) in those modules.
14. **Cross-compilation awareness.** When writing code for native targets (iOS) that will compile from Windows, avoid JVM-only APIs like `System.currentTimeMillis()`. Use `kotlinx-datetime` or `kotlin.time.Clock.System` instead.
15. **Read TODO.md at session start** — it contains current build status, priority tasks, and a record of all fixes applied in previous sessions.
