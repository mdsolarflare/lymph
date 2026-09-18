package e2e

import java.io.File
import kotlin.math.abs

/** A single OCR'd word box (absolute image coordinates). */
data class Word(val text: String, val x: Int, val y: Int, val w: Int, val h: Int) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2
}

/** A horizontal line of words (grouped by y-proximity), sorted left→right. */
data class Line(val words: List<Word>) {
    /** Whitespace-stripped concatenation, in x-order — the basis for phrase matching. */
    val text get() = words.joinToString("") { it.text }
}

/** Click target returned by [Ocr.findPhrase]. */
data class Point(val x: Int, val y: Int)

/**
 * OCR over tesseract (TSV word boxes) + ImageMagick preprocessing. Encodes the hard-won rules from
 * driving a Skiko `<canvas>` UI where there is no DOM text:
 *
 *  - TSV config arg (`tsv`) is REQUIRED — without it tesseract emits plain text → zero words.
 *  - **Banding**: page segmentation drops sparse lines on tall images (e.g. ADD RECORD). We OCR
 *    overlapping horizontal bands and merge, so no line is ever "too isolated" to be read.
 *  - **Three passes per band**: raw; inverted (`-negate -colorspace Gray -threshold 55%`); and
 *    grayscale-threshold-60 (the SAVE button is black-on-cyan — only that pass reads it).
 *  - **Fuzzy phrase match** (Levenshtein over substrings): OCR mangles words ("EXPORT DATA"→"EXPOKT").
 *  - Click center is computed ONLY over the word boxes that matched, so neighbouring garbage doesn't
 *    shift the target.
 */
class Ocr(
    private val tesseract: String = System.getProperty("e2e.tesseract"),
    private val magick: String = System.getProperty("e2e.magick"),
    private val workdir: File = File(System.getProperty("java.io.tmpdir")),
) {
    // Cache keyed by file identity (path+size+mtime), NOT path alone — poll loops reuse filenames.
    private val cache = HashMap<String, List<Word>>()

    fun ocrWords(imgPath: String): List<Word> {
        val f = File(imgPath)
        val key = "${f.absolutePath}|${f.length()}|${f.lastModified()}"
        cache[key]?.let { return it }
        if (cache.size > 200) cache.clear()

        val words = mutableListOf<Word>()
        var y0 = 0
        // Hard cap at image height so a misbehaving crop can never turn this into an infinite loop.
        while (y0 < IMG_H) {
            val bandFile = File(workdir, "band-${System.nanoTime()}-$y0.png")
            if (!cropBand(f.absolutePath, bandFile.absolutePath, y0)) break
            for (w in wordsForBand(bandFile, y0)) if (!words.any { isNear(it, w) }) words.add(w)
            bandFile.delete()
            y0 += BAND_STEP
        }
        cache[key] = words
        return words
    }

    fun ocrLines(imgPath: String): List<Line> {
        val words = ocrWords(imgPath).sortedBy { it.cy }
        val lines = mutableListOf<List<Word>>()
        var current = mutableListOf<Word>()
        for (w in words) {
            if (current.isEmpty() || abs(w.cy - current.last().cy) <= LINE_TOL) {
                current.add(w)
            } else {
                lines.add(current); current = mutableListOf(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.map { Line(it.sortedBy { w -> w.x }) }
    }

    fun ocrText(imgPath: String): String = ocrLines(imgPath).joinToString("\n") { it.text }

    /**
     * Read the M3 snackbar. The dark-theme snackbar renders as a LIGHT surface (#E6E0E9-ish
     * inverseSurface) with dark text, floating near the bottom of the detail area — sparse
     * enough that psm-6 page segmentation over a full-width strip DROPS it entirely (verified:
     * strip OCR sees only the sidebar). So instead: detect the surface by color (ImageMagick
     * trim bbox), tight-crop, 2× upscale, then turn the light surface into white-on-black and
     * OCR that. No surface → no snackbar → "".
     */
    fun snackbarText(imgPath: String): String {
        val f = File(imgPath)
        // 1. bbox of the light snackbar surface; empty when none present. EXACT color match —
        //    the M3 inverseSurface renders as exactly #E6E0E9, and fuzz here would merge in the
        //    app's cyan text (#D6F9FF is within ~8%).
        val bbox = run(listOf(
            magick, f.absolutePath, "-fill", "black", "+opaque", "#E6E0E9",
            "-format", "%@", "info:"
        )).output.trim()
        if (bbox.isEmpty() || bbox.startsWith("0x0")) return ""
        val m = Regex("(\\d+)x(\\d+)\\+(\\d+)\\+(\\d+)").find(bbox) ?: return ""
        val (w, h, x, y) = m.destructured
        if (w.toInt() == 0 || h.toInt() == 0) return "" // trim reports 0x0 when the surface is absent
        // 2. crop + upscale 2× + fuzz-fill the surface to white on black (the combination that
        //    OCRs the snackbar perfectly — plain gray-threshold mangles it at 1×).
        val cropFile = File(workdir, "snack-crop-${System.nanoTime()}.png")
        run(listOf(magick, f.absolutePath, "-crop", "${w}x${h}+${x}+${y}", "+repage",
            "-resize", "200%", "-fuzz", "25%", "-fill", "white", "-opaque", "#E6E0E9",
            "-fuzz", "0", "-fill", "black", "+opaque", "white", cropFile.absolutePath))
        val text = tsvText(cropFile.absolutePath)
        cropFile.delete()
        return text
    }

    /** Find a phrase (fuzzy, case-insensitive) in any line; returns the click center of matched words. */
    fun findPhrase(imgPath: String, phrase: String, pick: Pick = Pick.LEFTMOST): Point? {
        val matches = mutableListOf<Point>()
        for (line in ocrLines(imgPath)) {
            val loc = fuzzyLocate(line.text, phrase) ?: continue
            var off = 0; val spans = ArrayList<Pair<Word, IntRange>>()
            for (w in line.words) { spans.add(w to (off until off + w.text.length)); off += w.text.length }
            val hit = spans.filter { (_, s) -> s.first < loc.second && s.last > loc.first }.map { it.first }
            if (hit.isEmpty()) continue
            matches.add(Point(
                x = (hit.minOf { it.x } + hit.maxOf { it.x + it.w }) / 2,
                y = (hit.minOf { it.y } + hit.maxOf { it.y + it.h }) / 2,
            ))
        }
        if (matches.isEmpty()) return null
        return when (pick) {
            Pick.LEFTMOST -> matches.minByOrNull { it.x }!!
            Pick.RIGHTMOST -> matches.maxByOrNull { it.x }!!
        }
    }

    enum class Pick { LEFTMOST, RIGHTMOST }

    // ── internals ─────────────────────────────────────────────────────────
    private fun isNear(a: Word, b: Word): Boolean =
        a.text.equals(b.text, ignoreCase = true) && abs(a.cx - b.cx) < 20 && abs(a.cy - b.cy) < 20

    /** Run the three preprocessing passes over one band file; returns word boxes offset by [y0]. */
    private fun wordsForBand(bandFile: File, y0: Int): List<Word> {
        val out = mutableListOf<Word>()
        for (preproc in listOf(
            emptyList<String>(),
            listOf("-negate", "-colorspace", "Gray", "-threshold", "55%"),
            listOf("-colorspace", "Gray", "-threshold", "60%"),
        )) {
            val src = if (preproc.isEmpty()) bandFile.absolutePath else preprocess(bandFile, preproc)
            for (cand in tsvWords(src)) out.add(Word(cand.text, cand.x, cand.y + y0, cand.w, cand.h))
            // Generated preprocessing files are one-shot — delete or a run leaks ~12 per OCR call.
            if (src != bandFile.absolutePath) File(src).delete()
        }
        return out
    }

    /** Crop an overlapping horizontal band; returns false when past the bottom of the image. */
    private fun cropBand(src: String, dst: String, y0: Int): Boolean {
        val r = run(listOf(magick, src, "-crop", "${IMG_W}x${BAND_H}+0+$y0", "+repage", dst))
        return r.exitCode == 0 && File(dst).length() > 0
    }

    private fun preprocess(band: File, args: List<String>): String {
        val out = File(workdir, "pre-${System.nanoTime()}.png")
        run(listOf(magick) + listOf(band.absolutePath) + args + listOf(out.absolutePath))
        return out.absolutePath
    }

    private fun tsvWords(img: String): List<Word> {
        val out = run(listOf(tesseract, img, "stdout", "--psm", "6", "tsv")).output.trim()
        val words = mutableListOf<Word>()
        for (line in out.lineSequence()) {
            val c = line.split("\t")
            if (c.size < 12 || c[0] != "5" || c[11].isBlank()) continue
            words.add(Word(c[11], c[6].toInt(), c[7].toInt(), c[8].toInt(), c[9].toInt()))
        }
        return words
    }

    /** Plain text from a preprocessed image — snackbar reading. Words grouped into lines by
     *  y-proximity, then x-sorted WITHIN each line so reading order is preserved (a global
     *  y-sort interleaves words of one line when box heights differ by a few px). */
    private fun tsvText(img: String): String {
        val words = tsvWords(img)
        if (words.isEmpty()) return ""
        val lines = mutableListOf<List<Word>>()
        var current = mutableListOf<Word>()
        for (w in words.sortedBy { it.cy }) {
            if (current.isEmpty() || abs(w.cy - current.last().cy) <= LINE_TOL) current.add(w)
            else { lines.add(current); current = mutableListOf(w) }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.joinToString("\n") { ln -> ln.sortedBy { it.x }.joinToString(" ") { it.text } }
    }

    /** Min Levenshtein distance between [phrase] and ANY substring of [text]; returns (start,end) or null. */
    private fun fuzzyLocate(text: String, phrase: String): Pair<Int, Int>? {
        val t = text.lowercase(); val p = phrase.lowercase()
        if (p.isEmpty()) return null
        val threshold = if (p.length <= 8) 1 else 2
        var best = -1; var bs = 0; var be = 0
        for (s in t.indices) {
            for (e in s + 1..t.length) {
                if (abs((e - s) - p.length) > threshold) continue
                val d = levenshtein(p, t.substring(s, e))
                if (d <= threshold && (best < 0 || d < best)) { best = d; bs = s; be = e }
            }
        }
        return if (best < 0) null else Pair(bs, be)
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..m) {
            cur[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[n]
    }

    private data class RunResult(val exitCode: Int, val output: String)

    private fun run(cmd: List<String>): RunResult {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        return RunResult(p.waitFor(), out)
    }

    companion object {
        const val IMG_W = 1280
        const val IMG_H = 900
        private const val BAND_H = 300
        private const val BAND_STEP = 240 // 60px overlap
        private const val LINE_TOL = 12   // px: words within this vertical distance share a line
    }
}
