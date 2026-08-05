package com.lymph.data.database

import com.lymph.data.dao.*

actual class LymphDatabase actual constructor(dbPath: String) {

    actual companion object {
        actual const val DATABASE_NAME: String = "lymph.db"
    }

    private var _weightDao: MemoryWeightDao? = null
    private var _periodDao: MemoryPeriodDao? = null
    private var _bowelDao: MemoryBowelDao? = null

    actual fun weightDao(): WeightDao = (_weightDao ?: MemoryWeightDao()).also { _weightDao = it }
    actual fun periodDao(): PeriodDao = (_periodDao ?: MemoryPeriodDao()).also { _periodDao = it }
    actual fun bowelDao(): BowelDao  = (_bowelDao ?: MemoryBowelDao()).also { _bowelDao = it }

    actual suspend fun open() {}
    actual suspend fun close() {}
}
