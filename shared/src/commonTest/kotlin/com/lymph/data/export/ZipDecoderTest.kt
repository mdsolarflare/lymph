package com.lymph.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZipDecoderTest {

    @Test
    fun roundTrip_singleEntry() {
        val zip = ZipEncoder.buildZip(mapOf("weight_records.csv" to "id,timestamp\na,1\n"))
        assertEquals(
            mapOf("weight_records.csv" to "id,timestamp\na,1\n"),
            ZipDecoder.decode(zip)
        )
    }

    @Test
    fun roundTrip_multipleEntriesWithUnicode() {
        val entries = mapOf(
            "weight_records.csv" to "w",
            "period_records.csv" to "p — ünïcode ✓",
            "bowel_records.csv" to "b\nline2"
        )
        assertEquals(entries, ZipDecoder.decode(ZipEncoder.buildZip(entries)))
    }

    @Test
    fun notAZipThrows() {
        assertFailsWith<ImportException> {
            ZipDecoder.decode("hello world, definitely not a zip".encodeToByteArray())
        }
        // Shorter than the minimum EOCD record.
        assertFailsWith<ImportException> { ZipDecoder.decode(ByteArray(10)) }
    }

    @Test
    fun crcMismatchDetected() {
        val content = "AAAAAAAAAA"
        val zip = ZipEncoder.buildZip(mapOf("x.csv" to content)).toMutableList()
        // Single entry: local header is 30 + name.length(5) = 35 bytes, so data starts at index 35.
        zip[35] = 'B'.code.toByte()
        val e = assertFailsWith<ImportException> { ZipDecoder.decode(zip.toByteArray()) }
        assertTrue(e.message!!.contains("CRC-32 mismatch"), "unexpected message: ${e.message}")
    }

    @Test
    fun unsupportedCompressionRejected() {
        val zip = ZipEncoder.buildZip(mapOf("x.csv" to "data")).toMutableList()
        // Flip the compression method (local header offset 8, central directory offset 10) to DEFLATE.
        zip[8] = 8; zip[9] = 0
        val nameLen = "x.csv".length
        val dataStart = 30 + nameLen            // end of local header
        val centralStart = dataStart + 4        // length of "data"
        zip[centralStart + 10] = 8; zip[centralStart + 11] = 0
        val e = assertFailsWith<ImportException> { ZipDecoder.decode(zip.toByteArray()) }
        assertTrue(e.message!!.contains("unsupported compression"), "unexpected message: ${e.message}")
    }

    @Test
    fun truncatedArchiveThrows() {
        val zip = ZipEncoder.buildZip(mapOf("x.csv" to "some content here")).toList()
        assertFailsWith<ImportException> { ZipDecoder.decode(zip.subList(0, 20).toByteArray()) }
    }
}
