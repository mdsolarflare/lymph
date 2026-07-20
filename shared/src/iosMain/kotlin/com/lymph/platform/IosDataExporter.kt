package com.lymph.platform

import com.lymph.data.database.LymphDatabase
import com.lymph.data.export.CsvEncoder
import com.lymph.data.export.DataExporter
import com.lymph.data.export.ZipEncoder

/**
 * iOS-specific exporter. Delegates to the shared [ZipEncoder] utility backed by kotlinx.io.
 */
class IosDataExporter : DataExporter {

    override suspend fun exportZip(database: LymphDatabase): ByteArray {
        val weights = database.weightDao().getSnapshot()
        val periods = database.periodDao().getSnapshot()
        val bowels  = database.bowelDao().getSnapshot()

        return ZipEncoder.buildZip(
            mapOf(
                "weight_records.csv" to CsvEncoder.encodeWeightRecords(weights),
                "period_records.csv" to CsvEncoder.encodePeriodRecords(periods),
                "bowel_records.csv"  to CsvEncoder.encodeBowelRecords(bowels)
            )
        )
    }
}
