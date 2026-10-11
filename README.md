# lymph

Offline-first biometric data sanctuary. Zero servers. Zero telemetry. Your body's data stays on your device.

```
┌──────────────────────────────────────────────┐
│  LYMPH                                       │
│  STATUS: NOMINAL                             │
│  RECORDS: 0 WEIGHT | 0 PERIOD | 0 BOWEL      │
│                                              │
│  > AWAITING INPUT                            │
└──────────────────────────────────────────────┘
```

## Architecture

Lymph is a **100% client-side** application: a Rust core compiled to WebAssembly, driven by a Deno/TypeScript frontend built with Preact. No backend, no cloud sync, no network calls after first load. Data persists in `localStorage` as CSVs (same format as the export ZIP) and is portable via explicit export/import — you own the migration path.

| Layer | Technology |
|-------|-----------|
| Core logic | Rust → WebAssembly (`wasm-bindgen`, `serde-wasm-bindgen`) — entities, validation, CSV/ZIP encode/decode, insert-only import semantics, chart geometry, weight math |
| Frontend | Deno + TypeScript + Preact + `@preact/signals`, bundled with `deno bundle --platform=browser` |
| Persistence | `localStorage` CSV mirror — keys `lymph.weight_records` / `lymph.period_records` / `lymph.bowel_records`, schema-identical to the export ZIP |
| Distribution | Installable PWA — zero-dependency service worker with build-time cache revisioning; Android via TWA is a planned milestone |

```
lymph/
├── core/                    # Rust workspace
│   ├── crates/lymph-core/   # Pure logic: entity, csv, zip, crc32, importer, weight, chart (+ wasm bindings)
│   └── pkg/                 # wasm-pack output (gitignored; built by wasm-pack)
├── frontend/                # Deno + Preact app
│   ├── src/                 # main, app, panels, charts, input modal, store, pwa, io
│   ├── sw.js                # Service worker (cache-first shell, BUILD_ID stamped)
│   ├── gen-revision.ts      # Hashes shell files → cache-manifest.js + stamps sw.js
│   ├── gen-icons.ts         # One-shot: rasterizes favicon.svg → PWA PNG icons
│   ├── manifest.webmanifest
│   ├── build.ts             # Bundle + stage + gen-revision (deno task build)
│   └── serve.ts             # Zero-dep static server
└── docs/
    ├── SPEC.md              # Product contract (stack-agnostic; canonical)
    └── DATA-EXCHANGE.md     # Export/import ZIP+CSV format spec (frozen)
```

## Prerequisites

- **Rust** (rustup) + `wasm32-unknown-unknown` target + **wasm-pack**
- **Deno** (not on PATH by default on this machine: `C:\Users\dalton\.deno\bin\deno.exe`)
- **Edge** at `C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe` (only needed for `deno task gen-icons`)

## Build & run

```bash
# 1. Core: test + build the wasm artifact (only needed after Rust changes)
cd core
cargo test                                                    # 72/72 must pass
cargo clippy --all-targets                                    # must be clean
wasm-pack build crates/lymph-core --target web --out-dir ../../pkg --release

# 2. Frontend: type-check, bundle, stage, hash + stamp the SW
cd ../frontend
deno check                                                    # 0 errors
deno task build

# 3. Serve → http://localhost:8080/
deno task serve
```

Shortcut: `deno task dev` = build + serve.

**Order matters:** after any Rust change, run `wasm-pack build` **before** `deno task build` — the frontend bundles `core/pkg/`, and a stale pkg produces silent runtime bugs.

## Testing

- **Unit**: `cargo test` in `core/` — the data contract (CSV round-trips, ZIP corruption fuzz, insert-only import semantics, weight integer-hundredths math incl. exact lb↔kg factor and Kotlin-parity tie rounding, chart geometry) is fully unit-tested in Rust.
- **Manual E2E** (the round trip that matters): add a record of each type → **EXPORT DATA** (downloads `lymph-export.zip`) → reload (data survives) → wipe localStorage (DevTools → Application) → reload → **IMPORT DATA** with the zip → `IMPORT OK — 3 added` → import again → `IMPORT OK — 0 added, 3 duplicates`.
- **PWA**: install via the drawer's "Install App…" (Edge/Chrome); after install the app runs fully offline. Rebuild while the app is open → "A new version is ready" toast → RELOAD → new build active, old cache deleted.

The browser E2E flows above were verified live in session (real browser, DOM assertions, exact snackbar strings) but are **not yet a persisted test suite** — that's the next milestone (`frontend/e2e/` as a Deno test, raw CDP, no OCR).

## Data model & exchange format

Four record types, never merged. Full frozen spec: [`docs/DATA-EXCHANGE.md`](docs/DATA-EXCHANGE.md); product contract: [`docs/SPEC.md`](docs/SPEC.md).

| Record | CSV entry | Header |
|--------|-----------|--------|
| Weight | `weight_records.csv` | `id,timestamp,weightKg,note` |
| Period | `period_records.csv` | `id,timestamp,durationDays,flowIntensity,symptoms` |
| Bowel | `bowel_records.csv` | `id,timestamp,bristolType,effortLevel,note` |
| Hydrate | `hydrate_records.csv` | `id,timestamp,volumeMl,note` |

Import is insert-only (existing UUIDs skipped, first occurrence wins); only structural archive failure aborts. Validation ranges are shared by UI and import (0.1–1000 kg; 1–14 days; flow 1–4; bristol-like 1–5; effort 1–3; 1–10000 ml) — one source of truth in the Rust core.

## Design language

Clinical command-line interface: strict monospace, sharp corners (no rounded cards, no shadows, no gradients), tabular data, glacial cyan `#D6F9FF` on true black `#111111` (+ amber `#FFB454` as the second chart series — color-blind-safe pair). Theming is exactly two modes: dark (default) and light.

## Development conventions

- **No new dependencies without justification** — the SW, server, and icon pipeline are all zero-dependency on purpose.
- **Chart geometry derives at render time** (signals × props) — never inside an effect (an earlier effect-based version went stale on insert/import).
- **Nothing may call wasm exports before `await init()` resolves**; store hydration + persistence arming live in `initStore()`.
- **After any Rust change, rebuild wasm-pack before the frontend.**
- **SW update model**: `gen-revision.ts` hashes the shell → new buildId → new cache name; the buildId is stamped into `sw.js` because Chromium byte-compares only the main SW script.
- **Preact hooks**: `useEffect` lives in `preact/hooks`; one-expression signal-write arrows trip strict TS — use brace bodies.
- **UUID primary keys.** All records use UUID v4 strings; timestamps are epoch millis, UTC.
- **Explicit multi-table design.** Never merge biometric types; each domain gets its own entity, storage key, and CSV file.

## Session status

`GOAL.md` is the canonical session-status doc (read at start, update when done). `AGENTS.md` holds environment/gotchas only.
