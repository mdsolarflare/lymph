package com.lymph.data.database

import com.lymph.data.dao.BowelDao
import com.lymph.data.dao.PeriodDao
import com.lymph.data.dao.WeightDao

/**
 * Expect declaration for the Lymph database.
 * Actual implementations provided in nativeMain (Room) and wasmJsMain (in-memory stub).
 */
expect abstract class LymphDatabase {

    companion object {
        val DATABASE_NAME: String
    }

    abstract fun weightDao(): WeightDao
    abstract fun periodDao(): PeriodDao
    abstract fun bowelDao(): BowelDao
}
