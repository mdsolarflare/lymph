package com.lymph.data.dao

import com.lymph.data.entity.BowelRecord
import kotlinx.coroutines.flow.Flow

/**
 * DAO for bowel records.
 * Room annotations removed — platform-specific implementations provide persistence.
 */
actual interface BowelDao {

    fun getAll(): Flow<List<BowelRecord>>

    suspend fun getSnapshot(): List<BowelRecord>

    suspend fun insert(record: BowelRecord)

    suspend fun delete(id: String)
}