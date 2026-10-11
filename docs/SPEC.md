# Lymph — Product Specification (v2)

**Status: canonical product contract.** This document defines what Lymph *is* and
*aspires to be*, deliberately independent of any toolchain. README.md will be
rewritten per-stack at implementation time; until then this file plus
[`DATA-EXCHANGE.md`](DATA-EXCHANGE.md) are the source of truth. Items are marked
**[DECIDED]** (user direction, this reboot), **[PROPOSED]** (recommendation,
confirm or override), or **[OPEN]** (blocking question outstanding).

---

## 1. Identity

Lymph is an **offline-first biometric data sanctuary**: a personal health-data
tracker — weight, menstrual period, bowel movements — that keeps every byte on the
user's device. Zero servers. Zero telemetry. Zero accounts. Your body's data never
leaves your hardware except when *you* explicitly export it.

```
┌──────────────────────────────────────────────┐
│  LYMPH                                       │
│  STATUS: NOMINAL                             │
│  RECORDS: 0 WEIGHT | 0 PERIOD | 0 BOWEL     │
│                                              │
│  > AWAITING INPUT                            │
└──────────────────────────────────────────────┘
```

**Pillars:**

1. **Data sovereignty.** The export ZIP *is* the product; the app is a convenience.
   Data lives in open, human-readable CSV forever — a person must be able to open
   their own health archive in a text editor 20 years from now, with no Lymph in
   sight. No proprietary binary, no lock-in, no cloud.
2. **Clinical precision.** The UI is a clinical command-line interface, not a
   wellness app: monospace, data-dense, sharp corners, no decoration. Precision
   over comfort — values, ranges, and validation behave like instruments.
3. **One artifact, every surface.** The canonical deliverable is a single
   client-side web app. Every platform target is a *packaging* of that app —
   never a second codebase.

**"Zero servers" — precise meaning:** no server-side data processing, no sync
backend, no runtime network calls. Static asset hosting (a CDN/file host serving
HTML/JS/WASM) is infrastructure, not a server: after first load the service
worker precaches everything and the app runs fully offline. The app makes no
fetches for data, ever.

## 2. Platform & distribution strategy **[DECIDED]**

| Surface   | Delivery | Mechanism | Notes |
|-----------|----------|-----------|-------|
| **Web**   | Static-hosted site | The app in its vanilla form — installable PWA (manifest + service worker + offline) | The canonical artifact everything else wraps |
| **Desktop** | PWA install | Browser "install app" (Edge/Chrome; Windows is the primary dev target) | Same origin, same storage, same binary |
| **Android** | Play Store | **Trusted Web Activity (TWA)** via Bubblewrap/PWABuilder → signed AAB | Requirements researched; see §6 |
| **Apple** | *Aspirational — roadmap only* | Nothing decided today. Options documented in §7 | Explicitly out of current scope |

Consequences of "one artifact":

- Storage for web/desktop/Android-TWA is all the **same browser storage** for the
  same origin. A user's data in the installed desktop PWA and in the Play Store
  TWA is one dataset per browser profile per origin (TWA storage lives in the
  device's Chrome storage partition; clearing browser data clears it — this is
  accepted for v2, export/import remains the backup path).
- The ZIP export/import round-trip is the only supported migration path between
  browsers, devices, and app generations. It must keep working forever.

## 3. Domain model — frozen contract **[DECIDED]**

Three biometric record types. **Never merge them into one table** — each domain
gets its own entity, storage, and CSV file. All records:

- `id`: UUID v4 string, assigned at insert, never reused or mutated by import.
- `timestamp`: epoch milliseconds (UTC), auto-populated to `now()` at entry.

| Record  | Fields | Validation (UI and import enforce the same ranges) |
|---------|--------|------------------------------------------------------|
| Weight  | `weightKg: number`, `note: string?` | 0.1 ≤ weightKg ≤ 1000 (canonical kilograms) |
| Period  | `durationDays: int`, `flowIntensity: int` (default 2), `symptoms: string?` | 1 ≤ durationDays ≤ 14; 1 ≤ flowIntensity ≤ 4 |
| Bowel   | `bristolType: int`, `effortLevel: int` (default 3 / 2), `note: string?` | 1 ≤ bristolType ≤ 5; 1 ≤ effortLevel ≤ 3 |
| Hydrate | `volumeMl: number`, `note: string?` | 1 ≤ volumeMl ≤ 10000 (canonical milliliters) |

**Hydrate [DECIDED]**: the fourth measurement type, mirroring WEIGHT's entry pattern.
UI displays/edits in **L** or **OZ** (unit toggle); storage and CSV are canonical
milliliters (`volumeMl`). Integer-hundredths discipline, validation against the
canonical stored value, and prefill-from-last all match the weight semantics.
Exact factor: 1 US fl oz = 29.5735295625 ml. Dial step is unit-aware: ±0.1 L in
liter mode, ±1 OZ in ounce mode. Default 500 ml ("a standard water bottle").

**Flow-intensity presentation [DECIDED]**: storage and CSV remain
quantitative 1–4 (frozen contract, DATA-EXCHANGE.md unchanged). The UI presents
the value **qualitatively**: 1→NONE, 2→LIGHT, 3→MEDIUM, 4→HEAVY (mapping lives
in the Rust core, `entity::flow_intensity_label`). The stored quantitative
value is what charts plot and what validation checks — the label is display-only
and is never persisted.

**Bowel scales [DECIDED, breaking]**: the UI's bristol-like scale is 1–5 —
1→LIKE PEBBLES, 2→HARD, 3→PERFECT, 4→MUSHY :(, 5→WATER (labels:
`entity::bristol_label`) — and effort is 1–3 — 1→LOW, 2→MEDIUM, 3→HIGH
(`entity::effort_label`). CSV column *names* are frozen (`bristolType`,
`effortLevel`), but the **valid ranges are now 1–5 / 1–3**. Old exports with
bristol 6–7 or effort 4–5 have those rows counted as invalid on import (the
best-effort importer skips them; nothing else needed). Defaults: bristol 3
(PERFECT), effort 2 (MEDIUM).

**Weight-entry semantics (load-bearing, ported from the Kotlin app):**

- The value is held and stored as **integer hundredths** — no floating-point
  accumulation drift (the Kotlin app had a real bug here: `+= 0.1` stored
  70.4999999…; do not reintroduce it).
- **KG/LB unit toggle** in the entry UI; lb→kg conversion uses the exact factor
  `0.45359237`. Storage is *always* canonical kg.
- Validation applies to the **canonical stored value**: in LB mode the typed
  value is converted to kg and rounded to hundredths first, so exactly what
  would be stored is validated (effective LB window ≈ 0.21–2204.63).
- Input filter: digits only, at most one dot, at most two decimals; a trailing
  dot (`"70."`) is accepted. Out-of-range renders the error color and disables
  SAVE.
- The field is editable text **plus** ±0.1 dial buttons, pre-filled with the
  last recorded weight (70 kg default on first use).

## 4. Data exchange format — frozen contract **[DECIDED: keep byte-identical]**

Full normative spec: [`DATA-EXCHANGE.md`](DATA-EXCHANGE.md). Summary of the
invariants the v2 app must preserve exactly:

- `lymph-export.zip` containing up to three entries (case-sensitive exact names):
  `weight_records.csv` (`id,timestamp,weightKg,note`),
  `period_records.csv` (`id,timestamp,durationDays,flowIntensity,symptoms`),
  `bowel_records.csv` (`id,timestamp,bristolType,effortLevel,note`).
- CSV per RFC 4180; last column always quoted; `""` escaping; LF on export;
  import tolerates CRLF and a leading BOM. Header mismatch fails that table.
- ZIP entries **STORED** (no compression), CRC-32 verified.
- **Import is insert-only:** existing UUIDs are skipped as duplicates (first
  occurrence wins, also within the archive). Import never updates or deletes.
- **Best-effort:** only structural archive failure aborts (`IMPORT FAILED — <reason>`).
  Per-table header errors and per-row validation failures are counted, never abort.
- Snackbar summary strings (exact): `EXPORT OK — zip generated`;
  `IMPORT OK — 3 added`; `IMPORT OK — 0 added, 3 duplicates`; the general form
  `IMPORT OK — N added[, N duplicates][, N invalid][ | table: reason]`.

## 5. Persistence

- 100% client-side. Primary store on web-family targets: the **localStorage
  CSV mirror (decided)** — one CSV per table under the legacy keys, so
  existing browser data inherits itself with zero migration code. Either
  way, one-time auto-migration from the Kotlin app's storage is trivial if
  ever needed: the legacy keys are exactly
  `lymph.weight_records` / `lymph.period_records` / `lymph.bowel_records`
  (plus v2's `lymph.hydrate_records`), each holding the §4-format CSV
  *including the header row*, so migration is
  "read key → run the existing CSV decoder → insert".
- The store is an implementation detail; the CSV/ZIP contract (§4) is the
  durability guarantee. Records must survive page reload, app restart, and
  PWA/TWA relaunch.
- Reactive reads: the UI list layer must update live on insert/delete (the
  Kotlin app used Flow; v2 uses the frontend's reactive primitive).

## 6. Android via TWA — researched requirements **[DECIDED: TWA is the path]**

A TWA is a full-screen Chrome Custom Tab with the browser UI removed, verified by
Digital Asset Links. Operational checklist (from research, encode in CI/release
docs when built):

1. **PWA prerequisites:** HTTPS origin, web app manifest (name, icons,
   theme_color), **service worker with a fetch handler** (offline support — also
   required to pass Play review), decent Lighthouse PWA score.
2. **`https://<origin>/.well-known/assetlinks.json`** served with
   `Content-Type: application/json`, **no redirects** — a redirect silently fails
   verification:
   ```json
   [{ "relation": ["delegate_permission/common.handle_all_urls"],
      "target": { "namespace": "android_app",
                  "package_name": "<app id>",
                  "sha256_cert_fingerprints": ["<UPLOAD KEY SHA-256>", "<PLAY APP SIGNING SHA-256>"] } }]
   ```
3. **The classic trap:** include **both** the local upload key fingerprint and
   the Play App Signing key fingerprint (Play Console → Setup → App signing).
   Listing only the upload key → works locally, shows an address bar in review.
4. **Build:** Bubblewrap CLI (`bubblewrap init --manifest <url>`) or PWABuilder
   produce the signed AAB. App `applicationId` must match `package_name`.
5. **Verify** with Google's Statement List API
   (`digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=<url>&relation=delegate_permission/common.handle_all_urls`)
   before submitting.
6. **Play requirements:** privacy policy URL; personal developer accounts
   (post-Nov 2023) need the 12-testers-for-14-days closed testing rule; TWAs get
   standard + web-content policy scrutiny.
7. **Ongoing:** content updates ship via the web, no Play release needed; AAB
   releases only for icon/manifest/target-SDK changes. **Keep
   `assetlinks.json` hosted forever** — if it disappears, installed apps degrade
   to a Custom Tab with an address bar.

## 7. Apple — aspirational, roadmap only **[DECIDED: document, don't build]**

Not in scope today. The honest options when the time comes:

- **Home-screen web app (works today, zero extra work):** Safari → Add to Home
  Screen gives a standalone-ish app. `display: standalone`, safe-area insets, and
  iOS Safari storage eviction rules (7-day unused-ITP waiver for
  installed web apps) need verification. No store, no fee.
- **App Store via a WKWebView wrapper** (e.g. PWABuilder's Apple target): needs a
  Mac, Apple Developer ($99/yr), and carries App Store 4.2
  "minimum functionality / repackaged website" rejection risk unless padded with
  genuinely native modules.
- **Native/Swift rewrite:** contradicts the one-artifact pillar; last resort.

Decision deferred until Android/Play shipping is real.

## 8. UI contract

**Aesthetic [DECIDED]:** clinical CLI. Strict monospace across all surfaces.
Sharp corners only — **no rounded cards, no shadows, no gradients** — anywhere.
Tabular data with fixed-width columns. Think terminal dashboard, not wellness app.

**Theming [DECIDED: very limited]:** exactly **two themes — dark and light** —
via CSS custom properties. Nothing else.

**Dark palette (carried over):**

| Token | Value | Usage |
|-------|-------|-------|
| Black  | `#111111` | Background |
| Surface | `#1A1A1A` | Elevated surfaces |
| Cyan   | `#D6F9FF` | Body text, primary chart series, interactive elements |
| CyanDim | `#5A7A82` | Labels, timestamps, secondary data |
| Red    | `#FF4D4D` | Errors, out-of-range values |
| Amber  | `#FFB454` | Second chart series only (color-blind-safe pair with cyan) |

**Light palette [PROPOSED]** (verify WCAG contrast at implementation): white
background, near-black text, darkened cyan accent (glacial cyan is unreadable on
white — chart strokes and interactive accents need a darkened variant),
surface ≈ `#F0F0F0`, dim ≈ `#5A7A82`, red darkened ≈ `#D32F2F`. Chart series
colors are theme tokens, not constants.

**Layouts (breakpoint 840dp):**

- **Mobile (≤840dp):** single column; top bar (≡ LYMPH) with drawer (Export
  Data / Import Data / Settings); stacked WEIGHT / PERIOD / BOWEL panels;
  floating `+` (bottom-right) opens the input modal.
- **Expanded (>840dp):** master-detail. Sidebar: nav WEIGHT / PERIOD / BOWEL
  (selected item inverted: cyan background, black text), pinned bottom actions
  ADD RECORD, EXPORT DATA, IMPORT DATA. Detail area shows exactly the selected
  panel; scrolls vertically when content exceeds viewport.

**Panels** (each record type): trend chart above a paginated table.

- Table: newest first, ≤10 rows/page; terminal pager `[ n/N ]` with `<` `>`
  rendered only when >1 page; page state clamps on record-count change.
- Chart: line + square markers, dim gridlines, "nice" 1/2/5×10ⁿ y-ticks
  (~4 ticks), y-axis auto-scaled to visible range **+10% padding, never
  zero-based**; flat data gets symmetric padding; single record → centered
  marker; `niceTicks` respects a minimum step (integer ticks for period/bowel,
  hundredths for weight); period/bowel charts plot **two series on one shared
  axis** (duration + flow; bristol + effort), cyan + amber with legend.
- X-range selector: exactly `[90D] [1Y] [ALL]`, default 90D; records outside the
  window are excluded from chart and axis; empty states `> no data` /
  `> no data in range`.
- Chart math is **pure, unit-tested code** in the core library, not entangled
  with rendering.

## 8. UI contract (continued)

**Input modal:** per-type controls — Weight: unit toggle + editable field +
±0.1 dials (§3 semantics); Period: days counter (1–14), flow selector
1–4 presented as NONE/LIGHT/MEDIUM/HEAVY, symptoms text; Bowel: bristol-like
1–5 selector (LIKE PEBBLES/HARD/PERFECT/MUSHY :(/WATER), effort 1–3 selector
(LOW/MEDIUM/HIGH), note; Hydrate: unit toggle (L/OZ) + editable volume field
with ±0.1 L / ±1 OZ dials (default 500 ml), note. Timestamp
auto-`now()`. Defaults must be valid (modal opens savable).

**Feedback:** snackbars, bottom-center, auto-dismiss ~4s, exact strings in §4.

## 9. Toolchain (v2) — as built

- **Gradle and Kotlin abandoned entirely.** No dual-stack interim; the
  Kotlin/Gradle tree was deleted Oct 2.
- **Rust core library (decided)**: pure data/logic crate at
  `core/crates/lymph-core` — entities, validation, CSV/ZIP encode/decode,
  chart math, weight/volume math, import semantics — compiled to
  WebAssembly (wasm-pack, `web` target) and consumed by the frontend.
- **Deno + TypeScript frontend (decided)**: Preact + `@preact/signals`; a
  typed wasm wrapper (`frontend/src/lymph-core.ts`) is the single cast site.
- Deno owns: dev server, bundling (`deno bundle --platform=browser`), task
  runner. Cargo owns: core unit tests (`cargo test`) and the
  `wasm32-unknown-unknown` compile gate.
- PWA: zero-dependency service worker + build-time BUILD_ID cache
  revisioning (taskman-consistent; see AGENTS.md "PWA" section).

**Repository layout (as built):**

```
lymph/
├── core/        # Rust crate → WASM (no I/O, no browser APIs — pure logic)
├── frontend/    # Deno + TypeScript app; builds static output (dist/)
├── docs/        # SPEC.md (this file), DATA-EXCHANGE.md
├── android/     # Bubblewrap-generated TWA project (NOT STARTED — M3)
└── e2e/         # Persisted browser test suite (NOT STARTED — see §11 debt)
```

**Testing consequences of the reboot:** the canvas-in-Shadow-DOM rendering that
forced the entire Playwright + tesseract + ImageMagick OCR harness is gone —
v2 renders real DOM, so E2E assertions become plain DOM queries. Keep the
scenario checklist (it is platform-portable): seed → export (verify ZIP + 3
CSVs + headers + rows) → reload (data survives) → import "N added" → re-import
"0 added, N duplicates" → snackbars exact. Headless Edge via CDP, the pattern
already proven in this user's other projects. No OCR, ever again.

## 10. Non-goals (explicit)

- No cloud sync, no accounts, no telemetry/analytics, no server-side anything.
- No ORM / codegen for storage; no second codebase for any platform.
- No theming beyond dark/light. No rounded corners (not even small ones).
- No Apple store work in this phase.

## 11. Decisions (all resolved — recorded Sep 29, implemented since)

1. **Rust's role**: pure WASM core library (no Rust server). ✓ shipped as `core/crates/lymph-core`.
2. **Frontend**: Preact + signals, typed wasm wrapper, `deno bundle --platform=browser`. ✓ shipped.
3. **Storage**: localStorage CSV mirror, same legacy keys — existing browser data inherits itself. ✓ shipped.
4. **Cutover data**: user holds an export ZIP; import covers migration. Rebuild landed on `prototype` directly. ✓ done.
5. **PWA**: shipped (manifest + zero-dep SW + build-time BUILD_ID cache revisioning + update toast), taskman-consistent; verified offline + update cycle in-browser.

Post-parity additions (all shipped & verified): qualitative flow labels (NONE/LIGHT/MEDIUM/HEAVY), bowel scale redesign (bristol-like 1–5, effort 1–3 with labels), HYDRATE fourth measurement type (L/OZ, canonical ml, default 500 ml).
