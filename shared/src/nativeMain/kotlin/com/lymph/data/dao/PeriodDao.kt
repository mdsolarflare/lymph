package com.lymph.data.dao

import com.lymph.data.db.SqlExecutor
import com.lymph.data.db.SqlRowMapper
import com.lymph.data.entity.PeriodRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PeriodRowMapper : SqlRowMapper<PeriodRecord> {
    override fun map(row: com.lymph.data.db.SqlRow): PeriodRecord = PeriodRecord(
        id = row.getString("id")!!,
        timestamp = row.getLong("timestamp"),
        durationDays = row.getInt("duration_days"),
        flowIntensity = row.getInt("flow_intensity"),
        symptoms = if (row.getString("symptoms").isNullOrEmpty()) null else row.getString("symptoms")
    )
}

class SqlPeriodDao(private val executor: SqlExecutor) : PeriodDao {

    private val _all = MutableStateFlow(emptyList<PeriodRecord>())

    override fun getAll(): Flow<List<PeriodRecord>> = _all.asStateFlow()

    override suspend fun getSnapshot(): List<PeriodRecord> {
        val records = executor.query(
            "SELECT id, timestamp, duration_days, flow_intensity, symptoms FROM period_records ORDER BY timestamp DESC",
            emptyList(),
            PeriodRowMapper
        )
        _all.value = records
        return records
    }

    override suspend fun insert(record: PeriodRecord) {
        executor.execute(
            "INSERT OR REPLACE INTO period_records (id, timestamp, duration_days, flow_intensity, symptoms) VALUES (?, ?, ?, ?, ?)",
            listOf<Any>(record.id, record.timestamp, record.durationDays, record.flowIntensity, record.symptoms ?: "")
        )
        getSnapshot()
    }

    override suspend fun delete(id: String) {
        executor.execute("DELETE FROM period_records WHERE id = ?", listOf(id))
        getSnapshot()
    }
}
