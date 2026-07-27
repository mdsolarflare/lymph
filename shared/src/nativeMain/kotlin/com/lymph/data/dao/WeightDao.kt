package com.lymph.data.dao

import com.lymph.data.db.SqlExecutor
import com.lymph.data.db.SqlRowMapper
import com.lymph.data.entity.WeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WeightRowMapper : SqlRowMapper<WeightRecord> {
    override fun map(row: com.lymph.data.db.SqlRow): WeightRecord = WeightRecord(
        id = row.getString("id")!!,
        timestamp = row.getLong("timestamp"),
        weightKg = row.getDouble("weight_kg"),
        note = if (row.getString("note").isNullOrEmpty()) null else row.getString("note")
    )
}

class SqlWeightDao(private val executor: SqlExecutor) : WeightDao {

    private val _all = MutableStateFlow(emptyList<WeightRecord>())

    override fun getAll(): Flow<List<WeightRecord>> = _all.asStateFlow()

    override suspend fun getSnapshot(): List<WeightRecord> {
        val records = executor.query(
            "SELECT id, timestamp, weight_kg, note FROM weight_records ORDER BY timestamp DESC",
            emptyList(),
            WeightRowMapper
        )
        _all.value = records
        return records
    }

    override suspend fun insert(record: WeightRecord) {
        executor.execute(
            "INSERT OR REPLACE INTO weight_records (id, timestamp, weight_kg, note) VALUES (?, ?, ?, ?)",
            listOf<Any>(record.id, record.timestamp, record.weightKg, record.note ?: "")
        )
        getSnapshot()
    }

    override suspend fun delete(id: String) {
        executor.execute("DELETE FROM weight_records WHERE id = ?", listOf(id))
        getSnapshot()
    }
}
