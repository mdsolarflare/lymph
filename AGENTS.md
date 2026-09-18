Environment
Windows host + git-bash (MSYS); repo at C:\Users\dalton\StudioProjects\lymph (/c/Users/dalton/... in bash).
Use Windows where appropriate, prefer to use git bash everywhere you are able
Backend can flip mid-session between git-bash and the Docker sandbox — after any "backend degraded" error, re-verify uname -a/pwd/repo location before running commands (paths and tools differ completely).
JDK 17 required: export JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.20.8-hotspot" before any gradle call.
Node v22 on PATH has global WebSocket (CDP scripts need no npm deps); no tesseract/chromium — the E2E browser is Edge at C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe.
Any usage of Node and test scripts or assets need to be defined in this project with the Node dependency called out in the app.

Build & test
Dev server: ./gradlew :webApp:jsBrowserDevelopmentRun --no-daemon → localhost:8080; first webpack compile takes minutes, poll with curl.
Unit tests: ./gradlew :shared:jsNodeTest, XMLs at shared/build/test-results/jsNodeTest/*.xml; baseline 48/48 green (18+10+6+14).
Wasm gate: ./gradlew :shared:compileKotlinWasmJs.
Yarn lock failures → ./gradlew clean kotlinUpgradeYarnLock --no-daemon then rebuild; cleanup via ./gradlew clean, never rm -rf.
Gradle can linger after jsNodeTest — poll test XMLs, don't block on process exit.
Web E2E (headless Edge + CDP + OCR)
UI renders to <canvas> via Skiko → no DOM text; all targeting/assertions are OCR-based (tesseract TSV word boxes → line grouping by y-proximity → case-insensitive phrase match → span center).
Launch: msedge --headless=new --no-sandbox --disable-gpu --remote-debugging-port=9336 --window-size=1280,900 http://localhost:8080; CDP at http://localhost:9336/json.
Desktop layout needs >840dp → 1280×900 window; sidebar 240dp with bracketed nav labels + ADD RECORD / EXPORT DATA / IMPORT DATA (import renders only when a picker exists).
Web DB is in-memory: Page.reload() = fresh empty DB → seed → export → reload → import flow.
Seeding needs no typing — modal defaults are valid; click ADD RECORD → rightmost bare PERIOD/BOWEL match (sidebar labels are bracketed) → SAVE; modal closed when SAVE disappears from OCR.
OCR: run tesseract on raw + inverted (convert img -negate -colorspace Gray -threshold 55%) and merge word boxes; icons read as garbage — match phrases, never full lines; em-dashes mangle → assert on words (/3\s*added/), not exact strings.
Expected snackbars: EXPORT OK — zip generated, IMPORT OK — 3 added, IMPORT OK — 0 added, 3 duplicates; auto-dismiss ~4s → poll OCR every ~700ms starting immediately after the action.
CDP: Browser.setDownloadBehavior must omit browserContextId; file chooser = register Page.fileChooserOpened listener before clicking IMPORT DATA, then backendNodeId → DOM.pushNodeByBackendIDToFrontend → nodeId → DOM.setFileInputFiles.
App render takes 15–30s (sql.js CDN + skiko) → poll OCR for "EXPORT DATA" up to 90s before failing.
Code conventions & pitfalls
No runBlocking in commonTest — use kotlinx.coroutines.test.runTest.
No String(ByteArray) in common JS/wasm — use .decodeToString().
Icons: no AutoMirrored FileDownload in this Compose version — plain Icons.Default.FileDownload.
BiometricRecord is in package com.lymph.data, not .entity.
Schema contract = public CsvEncoder.*_HEADER constants; entry names = DataImporter.WEIGHT_CSV/PERIOD_CSV/BOWEL_CSV; full spec in docs/DATA-EXCHANGE.md.
Import semantics: insert-only (skip existing UUIDs, first occurrence wins), best-effort snackbar summary, only structural failures abort; web-only UI (filePicker = null on native → no import affordance).
Commits
You manage all commits — never commit/push without an explicit request; advise only.
Plus a header note: GOAL.md is the canonical session-status doc (read at start, update when done); AGENTS.md holds environment/gotchas only, not project state.