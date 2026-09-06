package com.lymph.data.export

import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord

/**
 * CSV encoder for biometric records.
 * Produces header + rows for each record type.
 *
 * Constraints:
 *  - Records are RFC 4180 quoted, with `""` for embedded quotes.
 *  - Timestamps are stored as epoch millis (Long).
 */
object CsvEncoder {

    /** CSV header for weight records (also the schema contract checked by [CsvDecoder]). */
    const val WEIGHT_HEADER = "id,timestamp,weightKg,note"

    /** CSV header for period records (also the schema contract checked by [CsvDecoder]). */
    const val PERIOD_HEADER = "id,timestamp,durationDays,flowIntensity,symptoms"

    /** CSV header for bowel records (also the schema contract checked by [CsvDecoder]). */
    const val BOWEL_HEADER = "id,timestamp,bristolType,effortLevel,note"

    /**
     * Encodes a list of weight records to CSV string.
     */
    fun encodeWeightRecords(records: List<WeightRecord>): String {
        val sb = StringBuilder()
        sb.append("$WEIGHT_HEADER\n")
        for (r in records) {
            val note = r.note?.replace("\"", "\"\"") ?: ""
            sb.append("${r.id},${r.timestamp},${r.weightKg},\"$note\"\n")
        }
        return sb.toString()
    }

    /**
     * Encodes a list of period records to CSV string.
     */
    fun encodePeriodRecords(records: List<PeriodRecord>): String {
        val sb = StringBuilder()
        sb.append("$PERIOD_HEADER\n")
        for (r in records) {
            val symptoms = r.symptoms?.replace("\"", "\"\"") ?: ""
            sb.append("${r.id},${r.timestamp},${r.durationDays},${r.flowIntensity},\"$symptoms\"\n")
        }
        return sb.toString()
    }

    /**
     * Encodes a list of bowel records to CSV string.
     */
    fun encodeBowelRecords(records: List<BowelRecord>): String {
        val sb = StringBuilder()
        sb.append("$BOWEL_HEADER\n")
        for (r in records) {
            val note = r.note?.replace("\"", "\"\"") ?: ""
            sb.append("${r.id},${r.timestamp},${r.bristolType},${r.effortLevel},\"$note\"\n")
        }
        return sb.toString()
    }
}
