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
| Web | `:webApp` | JS (IR), browser-hosted via Compose HTML renderer | In-memory + CSV export/import |

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
| Web (JS IR) | `MemoryWeightDao`, etc. | In-memory `MutableStateFlow` lists; CSV export/import for persistence |

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
│   └── src/wasmJsMain/kotlin/       # In-memory DAOs, WasmPlatform
├── androidApp/                      # Android Compose UI shell
│   └── src/main/kotlin/com/lymph/   # MainActivity + platform wiring
├── webApp/                          # Web Compose UI shell (JS IR)
│   └── src/jsMain/kotlin/           # Main.kt — browser entry point via renderComposable
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
| Web (WASM JS) | `MemoryWeightDao` | In-memory `MutableStateFlow` list |

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
│  [ PERIOD ]                 │  ← Collapsible metric panels
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
└──────────┴───────────────────────────────────────┘
```

### Input Modal

The `+` button opens a modal with clean dial/counter controls for manual entry. Each record type presents its own set of controls:

- **Weight**: Numeric dial for kg, optional note field.
- **Period**: Duration counter, flow intensity selector (1–4), symptoms text.
- **Bowel**: Bristol Stool Scale picker (1–7), effort level (1–5), optional note.

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

Export is triggered from the drawer menu (mobile) or master sidebar (web). The ZIP payload is returned as `ByteArray` via the platform-specific `DataExporter`.

### CSV Import

Import reads a matching ZIP archive, validates schema compatibility, and upserts records by UUID (skipping duplicates). Planned for v0.2.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3.21 |
| UI | Compose Multiplatform 1.7.3 |
| Persistence | Raw SQLite — no ORM, hand-written SQL |
| Dates | kotlinx-datetime 0.6.2 |
| I/O | kotlinx-io-core 0.7.0 |
| Web Runtime | Kotlin/JS (IR) — see [Web Rendering Note](#web-rendering-note)

### Web Rendering Note: `compose.html.core` vs `compose.web.core`

Compose Multiplatform provides two web rendering paths:

| Dependency | Target | Status in Compose MP 1.7.3 |
|---|---|---|
| `compose.html.core` (HTML renderer) | JS IR only | **Working** — this is what we use for webApp |
| `compose.web.core` (Wasm renderer) | wasmJs | Deprecated in 1.7.3, points back to html-core internally |

**Key constraint:** `compose.html.core` produces `.klib` artifacts for JS IR **only**. It has no wasmJs artifacts. If placed in a source set shared between JS and wasmJs (like `commonMain`), the compiler fails with:

```
Couldn't resolve dependency 'org.jetbrains.compose.html:html-core' in 'commonMain' for all target platforms.
The dependency should target platforms: [js, wasmJs]
Unresolved platforms: [wasmJs]
```

**Resolution:** webApp targets JS IR only. `compose.html.core` goes in `jsMain.dependencies`, never in `commonMain`. The wasmJs target exists in `shared` for future-proofing but is not used by webApp.

Also, Compose MP 1.7.x klibs were built against Kotlin 1.9.x ABI. When using Kotlin 2.3.x with the K2 compiler, ensure:
- Use `compose.runtime`, `compose.foundation`, `compose.material3`, `compose.ui` from `commonMain.dependencies`
- These resolve correctly across JS IR and native targets under K2
- Do not manually add `compose.html.core` to shared modules — it is auto-injected by the Compose plugin when a `js { browser() }` target is declared in a module that applies `org.jetbrains.compose`

## Development Environment

### Base Platform

Primary development runs on **macOS (Apple Silicon)** — MacBook Neo or equivalent M-series hardware. This provides native compatibility with all three targets:

- Android builds via Gradle + Android SDK
- iOS framework builds via Kotlin/Native tooling
- Web builds via WASM JS compiler

### Runtime Management (`asdf`)

We use [`asdf`](https://github.com/asdf-vm/asdf) for version-managed runtimes:

```bash
# Install JVM (required for Gradle)
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
java -version   # must be JDK 17+
node --version  # optional, 20+ recommended
```

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
# Configure all targets (requires Android SDK + Xcode CLI tools)
./gradlew configure

# Build shared module for all platforms
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
# Build production JS bundle
./gradlew :webApp:jsBrowserProductionWebpack

# Run development server with hot reload
./gradlew :webApp:jsBrowserDevelopmentRun
```

The dev server starts at `http://localhost:8080` by default. Open in any modern browser (Chrome, Firefox, Safari).

**Testing:** Standard browser DevTools apply. The web target uses an in-memory database — all data is lost on page refresh. Use the CSV export feature to persist data across sessions during development.

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
