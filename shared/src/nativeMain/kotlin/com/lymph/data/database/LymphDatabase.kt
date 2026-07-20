package com.lymph.data.database

import com.lymph.data.dao.BowelDao
import com.lymph.data.dao.PeriodDao
import com.lymph.data.dao.WeightDao

/**
 * Abstract database interface for native platforms (Android/iOS).
 * Room implementation removed — persistence will be added via platform-specific backends later.
 */
actual abstract class LymphDatabase {

    actual companion object {
        actual const val DATABASE_NAME = "lymph.db"
    }

    actual abstract fun weightDao(): WeightDao
    actual abstract fun periodDao(): PeriodDao
    actual abstract fun bowelDao(): BowelDao
}