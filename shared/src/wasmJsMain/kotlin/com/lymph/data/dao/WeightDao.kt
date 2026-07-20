package com.lymph.data.dao

import com.lymph.data.entity.WeightRecord
import kotlinx.coroutines.flow.Flow

/**
 * WASM stub interface for weight records.
 * Same contract as Room DAO, implemented in-memory in [LymphDatabase.weightDao].
 */
actual interface WeightDao {

    actual fun getAll(): Flow<List<WeightRecord>>

    actual suspend fun getSnapshot(): List<WeightRecord>

    actual suspend fun insert(record: WeightRecord)

    actual suspend fun delete(id: String)
}
