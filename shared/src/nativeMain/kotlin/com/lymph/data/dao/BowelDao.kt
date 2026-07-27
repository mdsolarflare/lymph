package com.lymph.data.dao

import com.lymph.data.db.SqlExecutor
import com.lymph.data.db.SqlRowMapper
import com.lymph.data.entity.BowelRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object BowelRowMapper : SqlRowMapper<BowelRecord> {
    override fun map(row: com.lymph.data.db.SqlRow): BowelRecord = BowelRecord(
        id = row.getString("id")!!,
        timestamp = row.getLong("timestamp"),
        bristolType = row.getInt("bristol_type"),
        effortLevel = row.getInt("effort_level"),
        note = if (row.getString("note").isNullOrEmpty()) null else row.getString("note")
    )
}

class SqlBowelDao(private val executor: SqlExecutor) : BowelDao {

    private val _all = MutableStateFlow(emptyList<BowelRecord>())

    override fun getAll(): Flow<List<BowelRecord>> = _all.asStateFlow()

    override suspend fun getSnapshot(): List<BowelRecord> {
        val records = executor.query(
            "SELECT id, timestamp, bristol_type, effort_level, note FROM bowel_records ORDER BY timestamp DESC",
            emptyList(),
            BowelRowMapper
        )
        _all.value = records
        return records
    }

    override suspend fun insert(record: BowelRecord) {
        executor.execute(
            "INSERT OR REPLACE INTO bowel_records (id, timestamp, bristol_type, effort_level, note) VALUES (?, ?, ?, ?, ?)",
            listOf<Any>(record.id, record.timestamp, record.bristolType, record.effortLevel, record.note ?: "")
        )
        getSnapshot()
    }

    override suspend fun delete(id: String) {
        executor.execute("DELETE FROM bowel_records WHERE id = ?", listOf(id))
        getSnapshot()
    }
}
