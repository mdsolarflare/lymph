package com.lymph.data.dao

import com.lymph.data.entity.BowelRecord
import kotlinx.coroutines.flow.Flow

/**
 * WASM stub interface for bowel records.
 * Same contract as Room DAO, implemented in-memory in [LymphDatabase.bowelDao].
 */
actual interface BowelDao {

    actual fun getAll(): Flow<List<BowelRecord>>

    actual suspend fun getSnapshot(): List<BowelRecord>

    actual suspend fun insert(record: BowelRecord)

    actual suspend fun delete(id: String)
}
