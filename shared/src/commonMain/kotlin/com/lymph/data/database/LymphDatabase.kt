package com.lymph.data.database

import com.lymph.data.dao.BowelDao
import com.lymph.data.dao.PeriodDao
import com.lymph.data.dao.WeightDao

expect class LymphDatabase(dbPath: String) {

    companion object {
        val DATABASE_NAME: String
    }

    fun weightDao(): WeightDao
    fun periodDao(): PeriodDao
    fun bowelDao(): BowelDao

    suspend fun open()
    suspend fun close()
}
