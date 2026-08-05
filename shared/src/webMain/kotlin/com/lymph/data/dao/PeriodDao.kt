package com.lymph.data.dao

import com.lymph.data.entity.PeriodRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemoryPeriodDao : PeriodDao {

    private val _all = MutableStateFlow(emptyList<PeriodRecord>())

    override fun getAll(): Flow<List<PeriodRecord>> = _all.asStateFlow()

    override suspend fun getSnapshot(): List<PeriodRecord> = _all.value

    override suspend fun insert(record: PeriodRecord) {
        val current = _all.value.toMutableList()
        if (!current.any { it.id == record.id }) {
            current.add(0, record)
            _all.value = current
        }
    }

    override suspend fun delete(id: String) {
        _all.value = _all.value.filter { it.id != id }
    }
}
