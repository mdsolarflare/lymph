package com.lymph.data.dao

import com.lymph.data.entity.PeriodRecord
import kotlinx.coroutines.flow.Flow

/**
 * DAO for period records.
 * Room annotations removed — platform-specific implementations provide persistence.
 */
actual interface PeriodDao {

    fun getAll(): Flow<List<PeriodRecord>>

    suspend fun getSnapshot(): List<PeriodRecord>

    suspend fun insert(record: PeriodRecord)

    suspend fun delete(id: String)
}