package com.lymph.platform

import com.lymph.data.database.LymphDatabase
import com.lymph.data.export.CsvEncoder
import com.lymph.data.export.DataExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Android-specific exporter. Uses [java.util.zip] for native zip creation.
 */
class AndroidDataExporter : DataExporter {

    override suspend fun exportZip(database: LymphDatabase): ByteArray = withContext(Dispatchers.IO) {
        val weights = database.weightDao().getSnapshot()
        val periods = database.periodDao().getSnapshot()
        val bowels  = database.bowelDao().getSnapshot()

        val entries = mapOf(
            "weight_records.csv" to CsvEncoder.encodeWeightRecords(weights),
            "period_records.csv" to CsvEncoder.encodePeriodRecords(periods),
            "bowel_records.csv"  to CsvEncoder.encodeBowelRecords(bowels)
        )

        ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                for ((name, content) in entries) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            baos.toByteArray()
        }
    }
}
