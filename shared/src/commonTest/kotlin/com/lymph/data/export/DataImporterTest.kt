package com.lymph.data.export

import com.lymph.data.dao.BowelDao
import com.lymph.data.dao.PeriodDao
import com.lymph.data.dao.WeightDao
import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataImporterTest {

    // Three separate fakes: the DAO interfaces share method names with different
    // return types, so one class cannot implement all three.

    private class FakeWeightDao : WeightDao {
        private val flow = MutableStateFlow<List<WeightRecord>>(emptyList())
        override fun getAll(): Flow<List<WeightRecord>> = flow
        override suspend fun getSnapshot() = flow.value
        override suspend fun insert(record: WeightRecord) { flow.value = listOf(record) + flow.value }
        override suspend fun delete(id: String) { flow.value = flow.value.filterNot { it.id == id } }
    }

    private class FakePeriodDao : PeriodDao {
        private val flow = MutableStateFlow<List<PeriodRecord>>(emptyList())
        override fun getAll(): Flow<List<PeriodRecord>> = flow
        override suspend fun getSnapshot() = flow.value
        override suspend fun insert(record: PeriodRecord) { flow.value = listOf(record) + flow.value }
        override suspend fun delete(id: String) { flow.value = flow.value.filterNot { it.id == id } }
    }

    private class FakeBowelDao : BowelDao {
        private val flow = MutableStateFlow<List<BowelRecord>>(emptyList())
        override fun getAll(): Flow<List<BowelRecord>> = flow
        override suspend fun getSnapshot() = flow.value
        override suspend fun insert(record: BowelRecord) { flow.value = listOf(record) + flow.value }
        override suspend fun delete(id: String) { flow.value = flow.value.filterNot { it.id == id } }
    }

    private fun w(id: String, ts: Long = 1_700_000_000_000L, kg: Double = 70.0) = WeightRecord(id, ts, kg)
    private fun p(id: String, ts: Long = 1_700_000_000_000L) = PeriodRecord(id, ts, 5, 2)
    private fun b(id: String, ts: Long = 1_700_000_000_000L) = BowelRecord(id, ts, 4, 3)

    /** Builds a lymph export archive from record lists (the realistic import source). */
    private fun zipOf(
        weights: List<WeightRecord> = emptyList(),
        periods: List<PeriodRecord> = emptyList(),
        bowels: List<BowelRecord> = emptyList()
    ): ByteArray = ZipEncoder.buildZip(
        mapOf(
            DataImporter.WEIGHT_CSV to CsvEncoder.encodeWeightRecords(weights),
            DataImporter.PERIOD_CSV to CsvEncoder.encodePeriodRecords(periods),
            DataImporter.BOWEL_CSV to CsvEncoder.encodeBowelRecords(bowels)
        )
    )

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun freshImportAddsAllTables() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        val result = DataImporter.importZip(
            zipOf(listOf(w("w1"), w("w2")), listOf(p("p1")), listOf(b("b1"))), fw, fp, fb
        )
        assertEquals(4, result.totalAdded) // 2 weights + 1 period + 1 bowel
        assertEquals(0, result.totalSkippedDuplicates)
        assertEquals(0, result.totalInvalidRows)
        assertEquals(setOf("w1", "w2"), fw.getSnapshot().map { it.id }.toSet())
        assertEquals(setOf("p1"), fp.getSnapshot().map { it.id }.toSet())
        assertEquals(setOf("b1"), fb.getSnapshot().map { it.id }.toSet())
    }

    @Test
    fun reimportSkipsAllAsDuplicates() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        val zip = zipOf(listOf(w("w1"), w("w2")), listOf(p("p1")))

        DataImporter.importZip(zip, fw, fp, fb)
        val second = DataImporter.importZip(zip, fw, fp, fb)

        assertEquals(0, second.totalAdded)
        assertEquals(3, second.totalSkippedDuplicates)
        assertEquals(2, fw.getSnapshot().size) // unchanged — insert-only, never updated
    }

    @Test
    fun partialOverlapSkipsOnlyExisting() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        fw.insert(w("w1")) // pre-existing record

        val result = DataImporter.importZip(zipOf(listOf(w("w1"), w("w2"))), fw, fp, fb)

        assertEquals(1, result.weight.added)
        assertEquals(1, result.weight.skippedDuplicates)
        assertEquals(setOf("w1", "w2"), fw.getSnapshot().map { it.id }.toSet())
    }

    @Test
    fun inBatchDuplicatesFirstOccurrenceWins() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        val csv = "id,timestamp,weightKg,note\ndup,100,70.0,\"\"\ndup,200,80.0,\"\"\n"
        val zip = ZipEncoder.buildZip(mapOf(DataImporter.WEIGHT_CSV to csv))

        val result = DataImporter.importZip(zip, fw, fp, fb)

        assertEquals(1, result.weight.added)
        assertEquals(1, result.weight.skippedDuplicates)
        assertEquals(100L, fw.getSnapshot().single().timestamp) // first row wins
    }

    @Test
    fun missingTableYieldsZeroCountsNoError() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        val zip = ZipEncoder.buildZip(mapOf(DataImporter.WEIGHT_CSV to CsvEncoder.encodeWeightRecords(listOf(w("w1")))))

        val result = DataImporter.importZip(zip, fw, fp, fb)

        assertEquals(1, result.weight.added)
        assertNull(result.period.error)
        assertEquals(TableImport(), result.period)
        assertEquals(TableImport(), result.bowel)
    }

    // ── Best-effort: bad rows/tables counted, good ones still imported ────────

    @Test
    fun invalidRowsCountedNotInserted() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        val csv = """
            id,timestamp,weightKg,note
            ok1,100,70.4,""
            ,200,71.0,""
            ok2,300,999999.0,""
            ok3,400,72.0,""
        """.trimIndent()
        val zip = ZipEncoder.buildZip(mapOf(DataImporter.WEIGHT_CSV to csv))

        val result = DataImporter.importZip(zip, fw, fp, fb)

        assertEquals(2, result.weight.added)
        assertEquals(2, result.weight.invalidRows)
        assertEquals(setOf("ok1", "ok3"), fw.getSnapshot().map { it.id }.toSet())
    }

    @Test
    fun badHeaderOnOneTableDoesNotBlockOthers() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        val zip = ZipEncoder.buildZip(
            mapOf(
                DataImporter.WEIGHT_CSV to "id,timestamp,WRONG,note\nw1,100,70.0,\"\"\n",
                DataImporter.PERIOD_CSV to CsvEncoder.encodePeriodRecords(listOf(p("p1"))),
                DataImporter.BOWEL_CSV to CsvEncoder.encodeBowelRecords(listOf(b("b1")))
            )
        )

        val result = DataImporter.importZip(zip, fw, fp, fb)

        assertNotNull(result.weight.error)
        assertEquals(0, result.weight.added)
        assertEquals(1, result.period.added)
        assertEquals(1, result.bowel.added)
    }

    // ── Structural failures abort the whole run ───────────────────────────────

    @Test
    fun archiveWithoutKnownTablesThrows() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        val zip = ZipEncoder.buildZip(mapOf("random.csv" to "x,y\n1,2\n"))
        assertFailsWith<ImportException> { DataImporter.importZip(zip, fw, fp, fb) }
    }

    @Test
    fun notAZipThrows() = runTest {
        val fw = FakeWeightDao(); val fp = FakePeriodDao(); val fb = FakeBowelDao()
        assertFailsWith<ImportException> {
            DataImporter.importZip("nope, not a zip".encodeToByteArray(), fw, fp, fb)
        }
    }

    // ── Summary formatting ────────────────────────────────────────────────────

    @Test
    fun summaryFormatsCounts() {
        val clean = ImportResult(TableImport(2), TableImport(), TableImport())
        assertEquals("IMPORT OK — 2 added", clean.summary())

        val mixed = ImportResult(TableImport(2, 1, 0), TableImport(0, 0, 3), TableImport())
        assertEquals("IMPORT OK — 2 added, 1 duplicates, 3 invalid", mixed.summary())

        val withError = ImportResult(TableImport(error = "header mismatch"), TableImport(1), TableImport())
        assertTrue(withError.summary().contains("weight: header mismatch"))
    }
}
