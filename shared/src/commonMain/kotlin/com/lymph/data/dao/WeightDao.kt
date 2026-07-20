package com.lymph.data.dao

import com.lymph.data.entity.WeightRecord
import kotlinx.coroutines.flow.Flow

/**
 * Expect declaration for weight DAO.
 * Actual implementations provided in nativeMain (Room) and wasmJsMain (in-memory).
 */
expect interface WeightDao {

    fun getAll(): Flow<List<WeightRecord>>

    suspend fun getSnapshot(): List<WeightRecord>

    suspend fun insert(record: WeightRecord)

    suspend fun delete(id: String)
}
