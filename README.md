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

Lymph is a **100% client-side** Kotlin Multiplatform application. There is no backend, no cloud sync service, and no network calls. All data persists locally via Room (SQLite) on native targets. Cross-device portability is achieved through explicit CSV export/import — you own the migration path.

### Targets

| Target | Module | Runtime | Storage |
|--------|--------|---------|---------|
| Android | `:androidApp` | Native APK, minSdk 24 | Room (SQLite) |
| iOS | `:shared` → Xcode host | Native via Kotlin/Native | Room (SQLite) |
| Web | `:webApp` | WASM JS, browser-hosted | In-memory + CSV export/import |

> **Note:** Room does not support WASM. The web target shares all business logic and UI but uses a stub database at runtime. Data portability on web is handled entirely through the CSV export/import pipeline.

### Module Layout

```
lymph/
├── shared/                          # Multiplatform core
│   ├── src/commonMain/kotlin/       # Entities, DAOs, ViewModel, Compose UI, theme
│   │   └── com/lymph/
│   │       ├── data/                # Room entities, DAOs, database factory
│   │       ├── platform/            # PlatformContext abstraction
│   │       ├── ui/                  # Compose screens, components, theme
│   │       └── util/               # UUID generator, timestamp formatting
│   ├── src/androidMain/kotlin/      # AndroidPlatform, AndroidDataExporter
│   ├── src/iosMain/kotlin/          # IosPlatform, IosDataExporter
│   └── schemas/                     # Room schema exports (migration tracking)
├── androidApp/                      # Android Compose UI shell
│   └── src/main/kotlin/com/lymph/   # MainActivity + platform wiring
├── webApp/                          # Web Compose UI shell (WASM JS)
│   └── src/wasmJsMain/kotlin/       # WasmPlatform, WasmDataExporter, main()
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

### Storage: Multi-Table Room Schema

Three distinct tables, each indexed on `timestamp` for efficient range queries and chronological sorting. All entities implement a common `BiometricRecord` interface exposing `id: String` (UUID v4) and `timestamp: Long` (epoch millis).

```kotlin
interface BiometricRecord {
    val id: String       // UUID v4 string
    val timestamp: Long  // epoch milliseconds
}

@Entity(tableName = "weight_records", indices = [Index(value = ["timestamp"])])
data class WeightRecord(
    @PrimaryKey val id: String,
    override val timestamp: Long,
    val weightKg: Double,
    val note: String? = null
) : BiometricRecord

@Entity(tableName = "period_records", indices = [Index(value = ["timestamp"])])
data class PeriodRecord(
    @PrimaryKey val id: String,
    override val timestamp: Long,
    val durationDays: Int,
    val flowIntensity: Int,      // 1-5 scale
    val symptoms: String? = null
) : BiometricRecord

@Entity(tableName = "bowel_records", indices = [Index(value = ["timestamp"])])
data class BowelRecord(
    @PrimaryKey val id: String,
    override val timestamp: Long,
    val bristolType: Int,        // Bristol Stool Scale 1-7
    val effortLevel: Int,        // 1-5 scale
    val note: String? = null
) : BiometricRecord
```

### DAO Layer

Each table has a dedicated DAO with `getAll()` (Flow for reactive UI), `getSnapshot()` (one-shot for export), `insert()`, and `delete(id)`. A `BiometricViewModel` aggregates all three DAOs.

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
- **Period**: Duration counter, flow intensity selector (1–5), symptoms text.
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
| Persistence | Room 2.7.0-alpha12 (SQLite) |
| Dates | kotlinx-datetime 0.6.2 |
| I/O | kotlinx-io-core 0.7.0 |
| Web Runtime | Kotlin/WASM JS |

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
# Build production WASM bundle
./gradlew :webApp:wasmJsBrowserProductionWebpack

# Run development server with hot reload
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

The dev server starts at `http://localhost:8080` by default. Open in any modern browser (Chrome, Firefox, Safari).

**Testing:** Standard browser DevTools apply. The web target uses an in-memory database stub — all data is lost on page refresh. Use the CSV export feature to persist data across sessions during development.

## CI/CD

> **Planned.** GitHub Actions workflow will be added once local builds are stable for all three targets. Initial pipeline will run unit tests and verify clean compilation. Distribution (Play Store, TestFlight, static hosting) is a separate effort.

## Development Conventions

- **No rounded corners anywhere.** `CornerSize.Zero` on all shapes.
- **Monospace font only.** Configure `FontFamily.Monospace` at the theme root.
- **Glacial Cyan on True Black.** No tertiary colors in the UI palette.
- **UUID primary keys.** All records use UUID v4 strings — no auto-increment integers.
- **Timestamps as epoch millis.** Stored as `Long`, formatted via shared `formatTimestamp()` utility.
- **Explicit multi-table design.** Never merge biometric types into a single table. Each domain gets its own entity, DAO, and CSV export file.

## License

See [LICENSE](LICENSE).
