package com.lymph.platform

import com.lymph.data.database.LymphDatabase
import com.lymph.data.export.CsvEncoder
import com.lymph.data.export.DataExporter
import com.lymph.data.export.ZipEncoder

class WebPlatform : PlatformContext {

    override fun getDataDirectory(): String = "lymph_data"

    override fun createLymphDatabase(): LymphDatabase {
        return LymphDatabase("lymph.db")
    }
}

class WebDataExporter : DataExporter {

    /** Name of the exported archive as saved by the browser. */
    private val exportFileName = "lymph-export.zip"

    override suspend fun exportZip(database: LymphDatabase): ByteArray {
        val weights = database.weightDao().getSnapshot()
        val periods = database.periodDao().getSnapshot()
        val bowels  = database.bowelDao().getSnapshot()

        val zip = ZipEncoder.buildZip(
            mapOf(
                "weight_records.csv" to CsvEncoder.encodeWeightRecords(weights),
                "period_records.csv" to CsvEncoder.encodePeriodRecords(periods),
                "bowel_records.csv"  to CsvEncoder.encodeBowelRecords(bowels)
            )
        )

        // Web has no file system — hand the archive straight to the browser's
        // download mechanism so it lands in the user's Downloads folder.
        downloadInBrowser(exportFileName, zip)

        return zip
    }
}
