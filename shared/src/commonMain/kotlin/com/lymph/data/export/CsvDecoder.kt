package com.lymph.data.export

import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord
import com.lymph.util.MAX_WEIGHT_KG
import com.lymph.util.MIN_WEIGHT_KG

/**
 * CSV decoder for biometric records — the inverse of [CsvEncoder].
 *
 * Parsing rules (full spec in docs/DATA-EXCHANGE.md):
 *  - The first non-blank line must match the table's header exactly. The constants
 *    published by [CsvEncoder] are the schema contract; a mismatch throws
 *    [CsvSchemaException] for that table only.
 *  - Lines split on LF; a trailing CR is tolerated (CRLF files). A UTF-8 BOM at the
 *    start of the file is stripped. Blank lines are ignored.
 *  - Fields follow RFC 4180 quoting: `"` delimits, `""` escapes an embedded quote.
 *  - Row-level problems (wrong field count, unparseable or out-of-range values) do
 *    not abort the table — they are counted in [DecodedTable.invalidRows].
 */
object CsvDecoder {

    /** Parsed result for one table: valid records plus the number of rejected rows. */
    data class DecodedTable<T>(val records: List<T>, val invalidRows: Int)

    /** Thrown when a CSV's header line does not match the expected schema contract. */
    class CsvSchemaException(
        val expectedHeader: String,
        val actualHeader: String?
    ) : Exception("header mismatch — expected [$expectedHeader], got [${actualHeader ?: "<empty>"}]")

    fun decodeWeightRecords(csv: String): DecodedTable<WeightRecord> = decodeTable(
        csv, CsvEncoder.WEIGHT_HEADER
    ) { f ->
        if (f.size != 4) return@decodeTable null
        val id = f[0].trim()
        val timestamp = f[1].toLongOrNull()
        val weightKg = f[2].toDoubleOrNull()
        if (id.isEmpty() || timestamp == null || timestamp <= 0L) return@decodeTable null
        // Same canonical range the input modal enforces (see WeightFormat).
        if (weightKg == null || !weightKg.isFinite() || weightKg < MIN_WEIGHT_KG || weightKg > MAX_WEIGHT_KG) {
            return@decodeTable null
        }
        WeightRecord(id, timestamp, weightKg, f[3].ifEmpty { null })
    }

    fun decodePeriodRecords(csv: String): DecodedTable<PeriodRecord> = decodeTable(
        csv, CsvEncoder.PERIOD_HEADER
    ) { f ->
        if (f.size != 5) return@decodeTable null
        val id = f[0].trim()
        val timestamp = f[1].toLongOrNull()
        // Same bounds the input modal dials allow.
        val durationDays = f[2].toIntOrNull() ?: return@decodeTable null
        val flowIntensity = f[3].toIntOrNull() ?: return@decodeTable null
        if (id.isEmpty() || timestamp == null || timestamp <= 0L) return@decodeTable null
        if (durationDays !in 1..14 || flowIntensity !in 1..4) return@decodeTable null
        PeriodRecord(id, timestamp, durationDays, flowIntensity, f[4].ifEmpty { null })
    }

    fun decodeBowelRecords(csv: String): DecodedTable<BowelRecord> = decodeTable(
        csv, CsvEncoder.BOWEL_HEADER
    ) { f ->
        if (f.size != 5) return@decodeTable null
        val id = f[0].trim()
        val timestamp = f[1].toLongOrNull()
        // Same bounds the input modal dials allow.
        val bristolType = f[2].toIntOrNull() ?: return@decodeTable null
        val effortLevel = f[3].toIntOrNull() ?: return@decodeTable null
        if (id.isEmpty() || timestamp == null || timestamp <= 0L) return@decodeTable null
        if (bristolType !in 1..7 || effortLevel !in 1..5) return@decodeTable null
        BowelRecord(id, timestamp, bristolType, effortLevel, f[4].ifEmpty { null })
    }

    /** Splits [csv] into data lines: BOM stripped, CRLF tolerated, blank lines dropped. */
    private fun dataLines(csv: String): List<String> {
        var text = csv
        if (text.startsWith("\uFEFF")) text = text.substring(1)
        return text.split('\n').map { it.removeSuffix("\r") }.filter { it.isNotBlank() }
    }

    /** Splits one CSV line into fields per RFC 4180 (quotes, embedded commas, `""` escapes). */
    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        field.append('"'); i++ // escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> if (!inQuotes) { fields.add(field.toString()); field.clear() } else field.append(c)
                else -> field.append(c)
            }
            i++
        }
        fields.add(field.toString())
        return fields
    }

    /**
     * Shared table machinery: header check, then per-row parsing. [parseRow] returns
     * null for rows that are structurally or semantically invalid (counted, not thrown).
     */
    private fun <T> decodeTable(
        csv: String,
        expectedHeader: String,
        parseRow: (List<String>) -> T?
    ): DecodedTable<T> {
        val lines = dataLines(csv)
        if (lines.isEmpty()) throw CsvSchemaException(expectedHeader, null)

        val headerLine = lines.first()
        if (splitCsvLine(headerLine) != expectedHeader.split(",")) {
            throw CsvSchemaException(expectedHeader, headerLine)
        }

        val records = mutableListOf<T>()
        var invalid = 0
        for (line in lines.drop(1)) {
            parseRow(splitCsvLine(line))?.let(records::add) ?: invalid++
        }
        return DecodedTable(records, invalid)
    }
}
