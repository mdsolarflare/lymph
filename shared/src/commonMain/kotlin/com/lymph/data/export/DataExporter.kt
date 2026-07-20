package com.lymph.data.export

import com.lymph.data.database.LymphDatabase

/**
 * Platform-agnostic interface for exporting biometric data.
 * Implementations produce a zipped CSV payload (one file per record type).
 */
interface DataExporter {

    /**
     * Exports all biometric tables to a single ZIP archive containing CSV files.
     *
     * @param database The Lymph database to export from.
     * @return ZIP archive as byte array.
     */
    suspend fun exportZip(database: LymphDatabase): ByteArray
}
