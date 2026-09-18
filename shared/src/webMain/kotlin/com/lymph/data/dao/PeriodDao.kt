package com.lymph.data.dao

import com.lymph.data.entity.PeriodRecord
import com.lymph.data.export.CsvDecoder
import com.lymph.data.export.CsvEncoder
import com.lymph.platform.LocalStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [PeriodDao] mirrored to localStorage as CSV (the export schema contract).
 * Hydrates once at construction; every mutation re-encodes the full table back to
 * storage — records survive a page refresh as long as storage is available.
 */
class MemoryPeriodDao : PeriodDao {

    private val _all = MutableStateFlow(
        WebTableStore.hydrate(LocalStorage.PERIOD_KEY, CsvDecoder::decodePeriodRecords)
    )

    override fun getAll(): Flow<List<PeriodRecord>> = _all.asStateFlow()

    override suspend fun getSnapshot(): List<PeriodRecord> = _all.value

    override suspend fun insert(record: PeriodRecord) {
        val current = _all.value.toMutableList()
        if (!current.any { it.id == record.id }) {
            current.add(0, record)
            _all.value = current
            WebTableStore.persist(LocalStorage.PERIOD_KEY, current, CsvEncoder::encodePeriodRecords)
        }
    }

    override suspend fun delete(id: String) {
        val next = _all.value.filter { it.id != id }
        if (next.size != _all.value.size) {
            _all.value = next
            WebTableStore.persist(LocalStorage.PERIOD_KEY, next, CsvEncoder::encodePeriodRecords)
        }
    }
}
