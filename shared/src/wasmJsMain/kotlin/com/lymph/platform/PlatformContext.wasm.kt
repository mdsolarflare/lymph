package com.lymph.platform

import com.lymph.data.database.LymphDatabase
import com.lymph.data.export.CsvEncoder
import com.lymph.data.export.DataExporter
import com.lymph.data.export.ZipEncoder

class WasmPlatform : PlatformContext {

    override fun getDataDirectory(): String = "lymph_data"

    override fun createLymphDatabase(): LymphDatabase {
        return LymphDatabase("lymph.db")
    }
}

class WasmDataExporter : DataExporter {

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
