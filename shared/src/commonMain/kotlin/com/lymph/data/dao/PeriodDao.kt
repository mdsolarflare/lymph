package com.lymph.data.dao

import com.lymph.data.entity.PeriodRecord
import kotlinx.coroutines.flow.Flow

/**
 * Expect declaration for period DAO.
 * Actual implementations provided in nativeMain (Room) and wasmJsMain (in-memory).
 */
expect interface PeriodDao {

    fun getAll(): Flow<List<PeriodRecord>>

    suspend fun getSnapshot(): List<PeriodRecord>

    suspend fun insert(record: PeriodRecord)

    suspend fun delete(id: String)
}
