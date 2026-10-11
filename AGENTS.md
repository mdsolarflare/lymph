# Environment

Windows host + git-bash (MSYS); repo at C:\Users\dalton\StudioProjects\lymph (/c/Users/dalton/... in bash).
Use Windows where appropriate, prefer git bash everywhere you are able.
Backend can flip mid-session between git-bash and the Docker sandbox — after any "backend degraded" error, re-verify uname -a/pwd/repo location before running commands (paths and tools differ completely).
Deno is NOT on the user's PATH: use C:\Users\dalton\.deno\bin\deno.exe (bash sessions may have it on PATH via the profile — check with `which deno` first).
Rust toolchain + wasm32-unknown-unknown target + wasm-pack are on PATH. Edge at C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe (used by gen-icons.ts and browser E2E).

# Build & test (v2: Rust core → WASM + Deno/Preact frontend)

```bash
# Core gate — all must pass before touching the frontend
cd core && cargo test          # 60/60 baseline
cargo clippy --all-targets     # zero warnings
wasm-pack build crates/lymph-core --target web --out-dir ../../pkg --release   # out-dir is RELATIVE TO THE CRATE

# Frontend gate
cd ../frontend
deno check                     # workspace-wide; excludes dist/
deno task build                # bundle → stage shell (incl. wasm) → gen-revision hashes + stamps sw.js
deno task serve                # zero-dep static server on :8080 (deno task dev = build + serve)
```

CRITICAL ordering: after ANY Rust change, rebuild wasm-pack BEFORE deno task build — the frontend bundles core/pkg/ and a stale pkg causes silent runtime bugs (seen once: serde field rename missed → weightKg read as 0).

# Hard-won gotchas

- Nothing may call wasm exports before `await init()` resolves. Store hydration + persistence-effect arming live in `initStore()` (called from main.tsx after init). Symptom if violated: `Cannot read properties of undefined (reading '__wbindgen_malloc')`, and write-through effects would OVERWRITE stored data with empty CSVs.
- Chart geometry is derived at render time in chart.tsx (signals × props). Never move it into a useSignalEffect — effects track signals but NOT props, so inserts/imports stopped refreshing the chart.
- wasm-pack's generated .d.ts is all `any` → strict-mode pain. The typed wrapper `frontend/src/lymph-core.ts` is the single cast site; deno.json maps "lymph-core" there.
- i64 across the wasm boundary becomes BigInt — keep epoch-millis params/returns as f64 in wasm-bindgen signatures.
- Preact: `useEffect` lives in `preact/hooks` (NOT `preact`, NOT `@preact/signals`). One-expression signal-write arrows trip strict TS (`boolean` not assignable to `void`) — use brace bodies.
- Deno import maps: subpath mappings need the `npm:/` form — `"preact/": "npm:/preact@10.26.9/"`.
- JSX: bare `<`/`>` as text children must be `{"<"}`. Snackbar strings use a true em-dash `—` (U+2014).
- serde field names cross the wasm boundary as data (NOT visible in the JS glue) — verify renames by running the app, not grepping lymph_core.js.

# PWA (taskman-consistent)

- sw.js is zero-dep, cache-first, precaches the shell. gen-revision.ts (last build step) SHA-256-hashes the 10 shell files → cache-manifest.js + STAMPS the buildId into sw.js. The stamp is load-bearing: Chromium byte-compares only the MAIN sw script — an unstamped changed manifest never triggers an update.
- Registration: `updateViaCache: "none"`, scope "./", after first render in main.tsx.
- Update UX: usePWA.ts — updatefound → waiting → toast → SKIP_WAITING → controllerchange → reload (guarded so first activation never reloads a fresh visitor).
- Expected snackbars (exact): `EXPORT OK — zip generated`, `IMPORT OK — N added`, `IMPORT OK — 0 added, N duplicates`. Weight CSV stores Java-style `70.0`.

# Storage contract

localStorage keys: `lymph.weight_records` / `lymph.period_records` / `lymph.bowel_records` — each a full CSV WITH header, identical to the export ZIP's entries. Same keys the old Kotlin app used, so legacy browser data inherits itself. Plus `lymph.theme` (dark|light). To reset state in a test: `localStorage.clear()` then reload.

# E2E patterns (browser_exec, DOM queries — no OCR)

- Click: `document.querySelector(sel).click()`; assert snackbars by polling `[data-testid=snackbar]` (~700ms; auto-dismiss 4s).
- Export capture: hook `URL.createObjectURL` (match type `application/zip`), pull the b64 to PYTHON before any reload, re-inject later via `window.__PY_ZIP_B64 = <json>`.
- Import delivery: DataTransfer + change event on `#__lymph_file_input` (the app's persistent hidden input).
- Update-cycle testing: the page must be LIVE and SW-controlled; rebuild mid-session (`subprocess.run([DENO, "task", "build"], cwd=FRONTEND)` works inside browser_exec). A fresh tab just loads the newest build — no toast.
- Compose LONG driver scripts in a file first; inline heredoc-style strings in tool calls got corrupted twice.
- The Hermes browser harness on this host wedges (WinError 5 on stale .port/.pid in ~/.config/browser-harness/runtime) at turn boundaries — just use a NEW session name per batch.

# Commits

You manage all commits — never commit/push without an explicit request; advise only.
Plus a header note: GOAL.md is the canonical session-status doc (read at start, update when done); AGENTS.md holds environment/gotchas only, not project state.
