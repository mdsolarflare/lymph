package com.lymph.data.dao

import com.lymph.data.entity.BowelRecord
import kotlinx.coroutines.flow.Flow

/**
 * Expect declaration for bowel DAO.
 * Actual implementations provided in nativeMain (Room) and wasmJsMain (in-memory).
 */
expect interface BowelDao {

    fun getAll(): Flow<List<BowelRecord>>

    suspend fun getSnapshot(): List<BowelRecord>

    suspend fun insert(record: BowelRecord)

    suspend fun delete(id: String)
}
