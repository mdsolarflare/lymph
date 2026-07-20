package com.lymph.data.dao

import com.lymph.data.entity.PeriodRecord
import kotlinx.coroutines.flow.Flow

/**
 * WASM stub interface for period records.
 * Same contract as Room DAO, implemented in-memory in [LymphDatabase.periodDao].
 */
actual interface PeriodDao {

    actual fun getAll(): Flow<List<PeriodRecord>>

    actual suspend fun getSnapshot(): List<PeriodRecord>

    actual suspend fun insert(record: PeriodRecord)

    actual suspend fun delete(id: String)
}
