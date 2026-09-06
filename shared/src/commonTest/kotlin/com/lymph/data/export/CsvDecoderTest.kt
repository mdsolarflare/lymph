package com.lymph.data.export

import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CsvDecoderTest {

    private fun weight(id: String = "id-w", ts: Long = 1_700_000_000_000L, kg: Double = 70.4, note: String? = null) =
        WeightRecord(id, ts, kg, note)

    private fun period(id: String = "id-p", ts: Long = 1_700_000_000_000L, days: Int = 5, flow: Int = 2, symptoms: String? = null) =
        PeriodRecord(id, ts, days, flow, symptoms)

    private fun bowel(id: String = "id-b", ts: Long = 1_700_000_000_000L, bristol: Int = 4, effort: Int = 3, note: String? = null) =
        BowelRecord(id, ts, bristol, effort, note)

    // ── Round-trip (encoder → decoder) ────────────────────────────────────────

    @Test
    fun roundTrip_weight() {
        val records = listOf(
            weight("a", 1L, 70.4),
            weight("b", 2L, 69.85, "comma, in note"),
            weight("c", 3L, 81.0, "quote \" inside")
        )
        val decoded = CsvDecoder.decodeWeightRecords(CsvEncoder.encodeWeightRecords(records))
        assertEquals(records, decoded.records)
        assertEquals(0, decoded.invalidRows)
    }

    @Test
    fun roundTrip_period() {
        val records = listOf(
            period("a", 1L, 5, 2),
            period("b", 2L, 7, 4, "cramps, bloating"),
            period("c", 3L, 3, 1, "mood \"low\"")
        )
        val decoded = CsvDecoder.decodePeriodRecords(CsvEncoder.encodePeriodRecords(records))
        assertEquals(records, decoded.records)
        assertEquals(0, decoded.invalidRows)
    }

    @Test
    fun roundTrip_bowel() {
        val records = listOf(
            bowel("a", 1L, 4, 3),
            bowel("b", 2L, 6, 5, "straining, note"),
            bowel("c", 3L, 2, 1)
        )
        val decoded = CsvDecoder.decodeBowelRecords(CsvEncoder.encodeBowelRecords(records))
        assertEquals(records, decoded.records)
        assertEquals(0, decoded.invalidRows)
    }

    // ── File-format leniency ──────────────────────────────────────────────────

    @Test
    fun crlfLineEndingsAccepted() {
        val csv = "id,timestamp,weightKg,note\r\na,100,70.4,\"\"\r\n"
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertEquals(1, decoded.records.size)
        assertEquals("a", decoded.records[0].id)
    }

    @Test
    fun bomStripped() {
        val csv = "\uFEFFid,timestamp,weightKg,note\na,100,70.4,\"\"\n"
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertEquals(1, decoded.records.size)
    }

    @Test
    fun headerOnlyYieldsZeroRecords() {
        val decoded = CsvDecoder.decodeWeightRecords(CsvEncoder.WEIGHT_HEADER + "\n")
        assertEquals(emptyList<WeightRecord>(), decoded.records)
        assertEquals(0, decoded.invalidRows)
    }

    @Test
    fun blankLinesIgnored() {
        val csv = "id,timestamp,weightKg,note\n\na,100,70.4,\"\"\n   \nb,200,71.0,\"\"\n"
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertEquals(2, decoded.records.size)
        assertEquals(0, decoded.invalidRows)
    }

    // ── Schema contract (header line) ─────────────────────────────────────────

    @Test
    fun emptyFileThrowsSchemaException() {
        assertFailsWith<CsvDecoder.CsvSchemaException> { CsvDecoder.decodeWeightRecords("") }
    }

    @Test
    fun wrongHeaderOrderThrowsSchemaException() {
        val csv = "id,timestamp,note,weightKg\na,100,\"\",70.4\n"
        assertFailsWith<CsvDecoder.CsvSchemaException> { CsvDecoder.decodeWeightRecords(csv) }
    }

    @Test
    fun extraHeaderColumnThrowsSchemaException() {
        val csv = "id,timestamp,weightKg,note,extra\na,100,70.4,\"\",x\n"
        assertFailsWith<CsvDecoder.CsvSchemaException> { CsvDecoder.decodeWeightRecords(csv) }
    }

    // ── Row-level validation (best-effort: counted, never thrown) ─────────────

    @Test
    fun wrongFieldCountCountsInvalid() {
        val csv = "id,timestamp,weightKg,note\na,100,70.4\nb,200,71.0,\"\"\n"
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertEquals(1, decoded.records.size)
        assertEquals("b", decoded.records[0].id)
        assertEquals(1, decoded.invalidRows)
    }

    @Test
    fun badTimestampCountsInvalid() {
        val csv = """
            id,timestamp,weightKg,note
            a,notanumber,70.4,""
            b,0,70.4,""
            c,-5,70.4,""
            d,100,70.4,""
        """.trimIndent()
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertEquals(1, decoded.records.size)
        assertEquals("d", decoded.records[0].id)
        assertEquals(3, decoded.invalidRows)
    }

    @Test
    fun outOfRangeOrNonFiniteWeightCountsInvalid() {
        // NaN parses as a Double — the isFinite check must catch it.
        val csv = """
            id,timestamp,weightKg,note
            a,100,0.05,""
            b,200,1000.5,""
            c,300,NaN,""
        """.trimIndent()
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertEquals(0, decoded.records.size)
        assertEquals(3, decoded.invalidRows)
    }

    @Test
    fun boundaryWeightsAccepted() {
        // Exactly the canonical min/max are valid — same rule as the input modal.
        val csv = "id,timestamp,weightKg,note\na,100,0.1,\"\"\nb,200,1000.0,\"\"\n"
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertEquals(2, decoded.records.size)
        assertEquals(0, decoded.invalidRows)
    }

    @Test
    fun periodBoundsEnforced() {
        val csv = """
            id,timestamp,durationDays,flowIntensity,symptoms
            a,100,0,2,""
            b,200,15,2,""
            c,300,5,0,""
            d,400,5,5,""
            e,500,1,1,""
        """.trimIndent()
        val decoded = CsvDecoder.decodePeriodRecords(csv)
        assertEquals(1, decoded.records.size)
        assertEquals("e", decoded.records[0].id)
        assertEquals(4, decoded.invalidRows)
    }

    @Test
    fun bowelBoundsEnforced() {
        val csv = """
            id,timestamp,bristolType,effortLevel,note
            a,100,0,3,""
            b,200,8,3,""
            c,300,4,0,""
            d,400,4,6,""
            e,500,7,5,""
        """.trimIndent()
        val decoded = CsvDecoder.decodeBowelRecords(csv)
        assertEquals(1, decoded.records.size)
        assertEquals("e", decoded.records[0].id)
        assertEquals(4, decoded.invalidRows)
    }

    @Test
    fun emptyNoteFieldBecomesNull() {
        val csv = "id,timestamp,weightKg,note\na,100,70.4,\"\"\n"
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertNull(decoded.records[0].note)
    }

    @Test
    fun emptyIdCountsInvalid() {
        val csv = "id,timestamp,weightKg,note\n,100,70.4,\"\"\n"
        val decoded = CsvDecoder.decodeWeightRecords(csv)
        assertEquals(0, decoded.records.size)
        assertEquals(1, decoded.invalidRows)
    }
}
