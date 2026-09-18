package e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Paths
import java.util.zip.ZipFile

/**
 * Web import/export round-trip E2E (Playwright Java client driving headless Edge).
 *
 * Prereq: dev server on :8080 (`./gradlew :webApp:jsBrowserDevelopmentRun`). Run with `-Pe2e=true`.
 *
 * Flow (web app now persists to localStorage — reload keeps data):
 *   1. launch Playwright + headless Edge, wait for app render ("EXPORT DATA" visible via OCR)
 *   2. export via EXPORT DATA — Playwright's native download interception captures the real
 *      zip (headless Edge under Playwright completes downloads; previously verified with a
 *      real 474-byte archive)
 *   3. seed one weight + one period + one bowel record via ADD RECORD → tab → SAVE (modal defaults)
 *   4. assert "70 kg" visible, click EXPORT DATA, capture zip, verify structure (3 CSVs, headers, rows)
 *   5. RELOAD: seeded data must still be visible (localStorage persistence regression check)
 *   6. clear storage + reload = fresh empty DB; import the captured zip via the hidden
 *      input (setInputFiles) → expect "IMPORT OK — 3 added"; import again → "0 added, 3 duplicates"
 *
 * The UI renders to a Skiko `<canvas>` with no DOM text, so all targeting/assertions are OCR-based
 * (see [Ocr]); clicks are coordinate-based via page.mouse().
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LymphImportExportTest {

    private val workdir = File(System.getProperty("e2e.workdir"))
    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var page: Page
    private val ocr = Ocr(workdir = workdir)
    private var lastShot = ""

    /** Count of file-chooser events seen — lets click retries detect a swallowed click. */
    private val chooserEvents = java.util.concurrent.atomic.AtomicInteger(0)

    companion object {
        const val APP_URL = "http://localhost:8080"
    }

    @BeforeAll
    fun setUp() {
        workdir.mkdirs()
        assertTrue(httpOk(APP_URL), "dev server not reachable at $APP_URL — start it first: ./gradlew :webApp:jsBrowserDevelopmentRun")

        playwright = Playwright.create()
        // Reuse the installed Edge (no separate browser download); headless.
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setChannel("msedge").setHeadless(true)
        )
        val context = browser.newContext(Browser.NewContextOptions().setViewportSize(1280, 900))
        page = context.newPage()

        // File chooser: DETECTION ONLY. The app's picker helper clicks a persistent hidden
        // <input type=file> (id __lymph_file_input, light DOM); Playwright intercepts that as a
        // FileChooser event, which proves the IMPORT DATA click reached the app and the input
        // exists with a fresh onchange handler. File DELIVERY deliberately goes through
        // page.setInputFiles (see importZip) — FileChooser.setFiles was observed to fail
        // silently on the second chooser in one session (chooser fired, no change event,
        // no import, no snackbar).
        page.onFileChooser { fc -> println("FILECHOOSER event (count=${chooserEvents.incrementAndGet()})") }

        // Diagnostics: surface app console + uncaught errors (helps distinguish init failure from context mismatch).
        page.onConsoleMessage { msg -> println("CONSOLE[${msg.type()}]: ${msg.text()}") }
        page.onPageError { err -> println("PAGEERROR: $err") }

        page.navigate(APP_URL)
        // App render takes 15–30s (sql.js CDN + skiko).
        waitPhrase("EXPORT DATA", timeoutMs = 90_000, expect = true)
    }

    @AfterAll
    fun tearDown() {
        try { browser.close() } catch (_: Exception) {}
        playwright.close()
    }

    @Test
    fun importExportRoundTrip() {
        // ── 2. Seed one record per table (modal defaults are valid; no typing needed) ───
        seedRecord("weight")   // default tab already WEIGHT
        seedRecord("period")
        seedRecord("bowel")

        // Weight panel shows the seeded value: "70 kg".
        assertTrue(ocrTextContains("70kg"), "expected '70 kg' on weight panel; got:\n${lastOcr()}")

        // ── 3. Export → capture the real download via Playwright's native interception, verify structure ──
        // (Headless Chromium still completes the file under Playwright — verified in DiagTest: 474-byte zip.)
        val dlLatch = java.util.concurrent.CountDownLatch(1)
        val dlRef = java.util.concurrent.atomic.AtomicReference<com.microsoft.playwright.Download>()
        page.onDownload { d -> dlRef.set(d); dlLatch.countDown() }

        clickPhraseUntil("EXPORT DATA", Ocr.Pick.LEFTMOST) { dlLatch.count == 0L }
        if (!dlLatch.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
            throw AssertionError("no download event within 20s after EXPORT DATA; snackbar: ${ocr.snackbarText(lastShot)}")
        }
        val zipFile = File(workdir, "lymph-export.zip")
        dlRef.get()!!.saveAs(zipFile.toPath())
        assertTrue(zipFile.length() > 0, "downloaded zip is empty")
        assertZipStructure(zipFile.readBytes())
        assertTrue(snackbarMatches(Regex("EXPORT\\s*OK", RegexOption.IGNORE_CASE), 8_000), "expected 'EXPORT OK' snackbar; got: ${ocr.snackbarText(lastShot)}")

        // ── 4. RELOAD REGRESSION CHECK: data must SURVIVE a refresh (localStorage persistence) ──
        // (The old in-memory DB lost everything here — the exact regression this guards against.)
        page.reload()
        waitPhrase("IMPORT DATA", timeoutMs = 90_000, expect = true)
        assertTrue(ocrTextContains("70kg"), "seeded data lost after page reload (localStorage persistence broken); got:\n${lastOcr()}")

        // ── 5. Fresh-DB import scenario: clear storage, reload, then import the captured zip ──
        page.evaluate("() => window.localStorage.clear()")
        page.reload()
        waitPhrase("IMPORT DATA", timeoutMs = 90_000, expect = true)

        val zipPath = File(workdir, "lymph-export.zip").absolutePath
        importZip(zipPath)
        assertTrue(snackbarMatches(Regex("3\\s*added", RegexOption.IGNORE_CASE), 8_000), "expected 'IMPORT OK — 3 added'; got: ${ocr.snackbarText(lastShot)}")

        // ── 6. Re-import the same zip → insert-only semantics skip all duplicates ────────
        importZip(zipPath)
        assertTrue(snackbarMatches(Regex("0\\s*added.*3\\s*duplicates", RegexOption.IGNORE_CASE), 8_000), "expected 'IMPORT OK — 0 added, 3 duplicates'; got: ${ocr.snackbarText(lastShot)}")

        // Seeded weight still visible after import (data survived the round trip).
        assertTrue(ocrTextContains("70kg"), "weight row missing after import; got:\n${lastOcr()}")
    }

    // ── scenario helpers ────────────────────────────────────────────────────

    /** ADD RECORD → (switch tab if needed) → SAVE; modal closed when SAVE disappears. */
    private fun seedRecord(tab: String) {
        val add = waitPhrase("ADD RECORD", timeoutMs = 15_000, expect = true) ?: fail("ADD RECORD not found")
        click(add.x, add.y)
        if (tab != "weight") {
            // Modal tabs are bare WEIGHT/PERIOD/BOWEL; sidebar labels are bracketed → rightmost bare match.
            val t = waitPhrase(tab.uppercase(), timeoutMs = 10_000, expect = true, pick = Ocr.Pick.RIGHTMOST)
                ?: fail("modal tab $tab not found")
            click(t.x, t.y)
        }
        val save = waitPhrase("SAVE", timeoutMs = 10_000, expect = true) ?: fail("SAVE button not found (after $tab)")
        click(save.x, save.y)
        // Modal closed when SAVE disappears — require two consecutive misses (OCR can drop a word).
        // NB: refresh lastShot EVERY iteration — re-OCR-ing the pre-click frame never sees the close.
        var misses = 0
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline && misses < 2) {
            Thread.sleep(700)
            lastShot = File(workdir, "shot.png").absolutePath
            File(lastShot).writeBytes(page.screenshot())
            if (ocr.findPhrase(lastShot, "SAVE") == null) misses++ else misses = 0
        }
        assertEquals(2, misses, "modal did not close after SAVE ($tab)")
    }

    // ── low-level helpers ────────────────────────────────────────────────────

    private fun click(x: Int, y: Int) {
        println("click → ($x, $y)")
        page.mouse().click(x.toDouble(), y.toDouble())
        Thread.sleep(150) // let Compose settle before the next screenshot
    }

    private fun clickPhrase(phrase: String, pick: Ocr.Pick) {
        val p = waitPhrase(phrase, timeoutMs = 15_000, expect = true, pick = pick) ?: fail("$phrase not found")
        click(p.x, p.y)
    }

    /**
     * Click [phrase] and retry (re-locate from a FRESH screenshot) until [eventFired] is true.
     * Compose-on-canvas occasionally swallows a click at verified-correct coordinates — observed
     * twice in this harness (export click → no download event; import click → no chooser event).
     * Budget: 3 attempts, ~2.5s apart.
     */
    private fun clickPhraseUntil(phrase: String, pick: Ocr.Pick, eventFired: () -> Boolean) {
        val attempts = 3
        repeat(attempts) { i ->
            val p = waitPhrase(phrase, timeoutMs = 15_000, expect = true, pick = pick) ?: fail("$phrase not found")
            click(p.x, p.y)
            val deadline = System.currentTimeMillis() + 2_500
            while (System.currentTimeMillis() < deadline) {
                if (eventFired()) return
                Thread.sleep(500)
            }
            println("WARN: $phrase click #${i + 1} did not fire its event — ${if (i < attempts - 1) "retrying" else "giving up"}")
        }
        throw AssertionError("event never fired after $attempts '$phrase' clicks")
    }

    /**
     * Click IMPORT DATA (retrying swallowed clicks until the chooser fires) and deliver the zip
     * into the app's persistent hidden input. The chooser event proves the click landed and the
     * input exists; delivery then goes through setInputFiles, which sets the files and fires
     * the change event the app's onchange handler consumes.
     */
    private fun importZip(zipPath: String) {
        val before = chooserEvents.get()
        clickPhraseUntil("IMPORT DATA", Ocr.Pick.LEFTMOST) { chooserEvents.get() > before }
        try {
            page.setInputFiles("#__lymph_file_input", Paths.get(zipPath))
            println("setInputFiles delivered: $zipPath")
        } catch (e: Exception) {
            println("setInputFiles FAILED: $e")
        }
    }

    /** Poll screenshot+OCR until phrase presence matches [expect]. Absence needs two consecutive misses. */
    private fun waitPhrase(phrase: String, timeoutMs: Long, expect: Boolean, pick: Ocr.Pick = Ocr.Pick.LEFTMOST): Point? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var misses = 0
        while (System.currentTimeMillis() < deadline) {
            lastShot = File(workdir, "shot.png").absolutePath
            File(lastShot).writeBytes(page.screenshot())
            val found = ocr.findPhrase(lastShot, phrase, pick)
            if (expect && found != null) return found
            if (!expect) {
                misses = if (found == null) misses + 1 else 0
                if (misses >= 2) return null
            }
            Thread.sleep(3_000)
        }
        throw AssertionError("phrase '$phrase' expect=$expect not satisfied within ${timeoutMs}ms; last OCR:\n${lastOcr()}")
    }

    /** Poll the snackbar until [regex] matches; snackbars auto-dismiss in ~4s. */
    private fun snackbarMatches(regex: Regex, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            lastShot = File(workdir, "shot.png").absolutePath
            File(lastShot).writeBytes(page.screenshot())
            // Newlines → spaces so `.*` in caller regexes can match across OCR line-group splits.
            val sn = ocr.snackbarText(lastShot).replace('\n', ' ')
            if (sn.isNotBlank()) println("snackbarOCR: $sn") // telemetry: any snackbar content seen
            if (regex.containsMatchIn(sn)) return true
            Thread.sleep(700)
        }
        return false
    }

    private fun ocrTextContains(normalized: String): Boolean {
        lastShot = File(workdir, "shot.png").absolutePath
        File(lastShot).writeBytes(page.screenshot())
        // Fuzzy, not exact: OCR mangles the row ("70 kg" → "70kq70Tokg"). Line words concatenate
        // without separators, hence the whitespace-stripped phrase ("70kg").
        return ocr.findPhrase(lastShot, normalized) != null
    }

    private fun lastOcr(): String {
        if (lastShot.isEmpty() || !File(lastShot).exists()) return "(no screenshot)"
        return try { ocr.ocrText(lastShot) } catch (e: Exception) { "OCR failed: ${e.message}" }
    }

    /** Structural check of the exported archive against the CsvEncoder schema contract. */
    private fun assertZipStructure(bytes: ByteArray) {
        ZipFile(File(workdir, "lymph-export.zip")).use { zip ->
            for (entry in listOf("weight_records.csv", "period_records.csv", "bowel_records.csv")) {
                val e = zip.getEntry(entry) ?: fail("zip missing entry $entry; has: ${zip.entries().asSequence().map { it.name }.toList()}")
                val lines = zip.getInputStream(e).bufferedReader().readLines()
                assertTrue(lines.size >= 2, "$entry has no data rows (only header?)")
            }
        }
    }

    private fun httpOk(url: String): Boolean = try {
        val conn = (URI(url).toURL().openConnection()) as HttpURLConnection
        conn.connectTimeout = 3_000; conn.readTimeout = 3_000
        conn.responseCode in 200..399
    } catch (_: Exception) { false }
}
