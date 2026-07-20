package com.lymph.data.dao

import com.lymph.data.entity.WeightRecord
import kotlinx.coroutines.flow.Flow

/**
 * DAO for weight records.
 * Room annotations removed — platform-specific implementations provide persistence.
 */
actual interface WeightDao {

    fun getAll(): Flow<List<WeightRecord>>

    suspend fun getSnapshot(): List<WeightRecord>

    suspend fun insert(record: WeightRecord)

    suspend fun delete(id: String)
}