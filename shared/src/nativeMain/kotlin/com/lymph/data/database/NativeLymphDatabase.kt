package com.lymph.data.database

import com.lymph.data.dao.BowelDao
import com.lymph.data.dao.PeriodDao
import com.lymph.data.dao.WeightDao
import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory LymphDatabase implementation for native (Android/iOS).
 * Mirrors [WasmLymphDatabase] — data is kept in Compose StateFlows.
 * Data is lost on process restart; add persistent backend later.
 */
class NativeLymphDatabase : LymphDatabase() {

    private val _weights = MutableStateFlow(emptyList<WeightRecord>())
    private val _periods = MutableStateFlow(emptyList<PeriodRecord>())
    private val _bowels  = MutableStateFlow(emptyList<BowelRecord>())

    override fun weightDao(): WeightDao = object : WeightDao {
        override fun getAll(): Flow<List<WeightRecord>> = _weights

        override suspend fun getSnapshot(): List<WeightRecord> = _weights.value

        override suspend fun insert(record: WeightRecord) {
            val current = _weights.value.toMutableList()
            if (!current.any { it.id == record.id }) {
                current.add(0, record)
                _weights.value = current
            }
        }

        override suspend fun delete(id: String) {
            _weights.value = _weights.value.filter { it.id != id }
        }
    }

    override fun periodDao(): PeriodDao = object : PeriodDao {
        override fun getAll(): Flow<List<PeriodRecord>> = _periods

        override suspend fun getSnapshot(): List<PeriodRecord> = _periods.value

        override suspend fun insert(record: PeriodRecord) {
            val current = _periods.value.toMutableList()
            if (!current.any { it.id == record.id }) {
                current.add(0, record)
                _periods.value = current
            }
        }

        override suspend fun delete(id: String) {
            _periods.value = _periods.value.filter { it.id != id }
        }
    }

    override fun bowelDao(): BowelDao = object : BowelDao {
        override fun getAll(): Flow<List<BowelRecord>> = _bowels

        override suspend fun getSnapshot(): List<BowelRecord> = _bowels.value

        override suspend fun insert(record: BowelRecord) {
            val current = _bowels.value.toMutableList()
            if (!current.any { it.id == record.id }) {
                current.add(0, record)
                _bowels.value = current
            }
        }

        override suspend fun delete(id: String) {
            _bowels.value = _bowels.value.filter { it.id != id }
        }
    }
}