package com.lymph.data.database

import com.lymph.data.dao.*
import com.lymph.data.db.NativeSqlExecutor

actual class LymphDatabase actual constructor(dbPath: String) {

    actual companion object {
        actual const val DATABASE_NAME: String = "lymph.db"
    }

    private val executor = NativeSqlExecutor(dbPath)

    private var _weightDao: SqlWeightDao? = null
    private var _periodDao: SqlPeriodDao? = null
    private var _bowelDao: SqlBowelDao? = null

    actual fun weightDao(): WeightDao = (_weightDao ?: SqlWeightDao(executor)).also { _weightDao = it }
    actual fun periodDao(): PeriodDao = (_periodDao ?: SqlPeriodDao(executor)).also { _periodDao = it }
    actual fun bowelDao(): BowelDao  = (_bowelDao ?: SqlBowelDao(executor)).also { _bowelDao = it }

    actual suspend fun open() = executor.open()
    actual suspend fun close() = executor.close()
}
