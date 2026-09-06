package com.lymph.data.export

import com.lymph.data.BiometricRecord
import com.lymph.data.dao.BowelDao
import com.lymph.data.dao.PeriodDao
import com.lymph.data.dao.WeightDao

/** Expected failure of an import operation — [message] is safe to show in the UI as-is. */
class ImportException(message: String) : Exception(message)

/** Per-table outcome of one import run. */
data class TableImport(
    val added: Int = 0,
    val skippedDuplicates: Int = 0,
    val invalidRows: Int = 0,
    /** Non-null when the table could not be imported at all (e.g. header mismatch). */
    val error: String? = null
)

/** Aggregate outcome of one import run across all three tables. */
data class ImportResult(
    val weight: TableImport,
    val period: TableImport,
    val bowel: TableImport
) {
    val totalAdded: Int get() = weight.added + period.added + bowel.added
    val totalSkippedDuplicates: Int get() = weight.skippedDuplicates + period.skippedDuplicates + bowel.skippedDuplicates
    val totalInvalidRows: Int get() = weight.invalidRows + period.invalidRows + bowel.invalidRows

    /** Terminal-style one-line summary for the UI snackbar. */
    fun summary(): String {
        var s = "IMPORT OK — $totalAdded added"
        if (totalSkippedDuplicates > 0) s += ", $totalSkippedDuplicates duplicates"
        if (totalInvalidRows > 0) s += ", $totalInvalidRows invalid"
        val errors = listOfNotNull(
            weight.error?.let { "weight: $it" },
            period.error?.let { "period: $it" },
            bowel.error?.let { "bowel: $it" }
        )
        if (errors.isNotEmpty()) s += " | " + errors.joinToString("; ")
        return s
    }
}

/**
 * Imports a lymph export archive (ZIP of CSVs) into the database.
 *
 * Semantics (full spec in docs/DATA-EXCHANGE.md):
 *  - **Insert-only.** Records whose UUID already exists are skipped, never updated —
 *    two legitimate entries can never share an ID, so re-importing an old export is a
 *    no-op rather than a data change. Duplicates within the archive itself are handled
 *    the same way (first occurrence wins).
 *  - **Best-effort.** Structural problems with the archive itself abort the run with
 *    [ImportException]; per-table and per-row problems do not — they are counted in
 *    [ImportResult] so nothing is silently lost.
 */
object DataImporter {

    /** Entry names recognized inside the archive (exact match, case-sensitive). */
    const val WEIGHT_CSV = "weight_records.csv"
    const val PERIOD_CSV = "period_records.csv"
    const val BOWEL_CSV = "bowel_records.csv"

    private val KNOWN_TABLES = setOf(WEIGHT_CSV, PERIOD_CSV, BOWEL_CSV)

    /**
     * Imports [zipBytes] into the given DAOs. Returns per-table counts; throws
     * [ImportException] when the archive itself is unusable (not a ZIP, corrupt, or
     * contains no recognizable lymph CSV).
     */
    suspend fun importZip(
        zipBytes: ByteArray,
        weightDao: WeightDao,
        periodDao: PeriodDao,
        bowelDao: BowelDao
    ): ImportResult {
        val entries = ZipDecoder.decode(zipBytes) // throws ImportException on structural problems
        if (entries.keys.none { it in KNOWN_TABLES }) {
            throw ImportException("no recognizable lymph CSVs found — expected $WEIGHT_CSV / $PERIOD_CSV / $BOWEL_CSV")
        }

        val weight = importTable(
            entries[WEIGHT_CSV], CsvDecoder::decodeWeightRecords,
            weightDao.getSnapshot().mapTo(HashSet()) { it.id }) { r -> weightDao.insert(r) }
        val period = importTable(
            entries[PERIOD_CSV], CsvDecoder::decodePeriodRecords,
            periodDao.getSnapshot().mapTo(HashSet()) { it.id }) { r -> periodDao.insert(r) }
        val bowel = importTable(
            entries[BOWEL_CSV], CsvDecoder::decodeBowelRecords,
            bowelDao.getSnapshot().mapTo(HashSet()) { it.id }) { r -> bowelDao.insert(r) }

        return ImportResult(weight, period, bowel)
    }

    /**
     * Imports one table: decodes the CSV (header mismatch → [TableImport.error]), then
     * inserts records whose ID is not already present — neither in the database nor
     * earlier in this same archive.
     */
    private suspend fun <T : BiometricRecord> importTable(
        csv: String?,
        decode: (String) -> CsvDecoder.DecodedTable<T>,
        existingIds: Set<String>,
        insert: suspend (T) -> Unit
    ): TableImport {
        if (csv == null) return TableImport() // table absent from archive — nothing to do

        val decoded = try {
            decode(csv)
        } catch (e: CsvDecoder.CsvSchemaException) {
            return TableImport(error = e.message)
        }

        val seen = existingIds.toMutableSet()
        var added = 0
        var skipped = 0
        for (record in decoded.records) {
            if (seen.add(record.id)) { insert(record); added++ } else skipped++
        }
        return TableImport(added, skipped, decoded.invalidRows)
    }
}
