package com.lymph.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Native SqlExecutor that delegates to platform-specific SQLite binding.
 * Android uses android.database.sqlite.SQLiteDatabase; iOS uses sqlite3 C API.
 */
class NativeSqlExecutor(private val dbPath: String) : SqlExecutor {

    private lateinit var handle: SqlHandle

    override suspend fun open() = withContext(Dispatchers.IO) {
        handle = platformOpen(dbPath)
        // Create tables if they don't exist
        handle.execute(TABLE_WEIGHT_RECORDS)
        handle.execute(TABLE_PERIOD_RECORDS)
        handle.execute(TABLE_BOWEL_RECORDS)
    }

    override suspend fun execute(sql: String) = withContext(Dispatchers.IO) {
        handle.execute(sql)
    }

    override suspend fun execute(sql: String, args: List<Any>) = withContext(Dispatchers.IO) {
        handle.execute(sql, args)
    }

    override suspend fun <T> query(
        sql: String,
        args: List<Any>,
        mapper: SqlRowMapper<T>,
    ): List<T> = withContext(Dispatchers.IO) {
        handle.query(sql, args, mapper)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        handle.close()
    }
}

/** Low-level SQLite operations — implemented per platform. */
interface SqlHandle {
    fun execute(sql: String)
    fun execute(sql: String, args: List<Any>)
    fun <T> query(sql: String, args: List<Any>, mapper: SqlRowMapper<T>): List<T>
    fun close()
}

expect fun platformOpen(path: String): SqlHandle

/** Table creation SQL — shared across all platforms. */
const val TABLE_WEIGHT_RECORDS = """
    CREATE TABLE IF NOT EXISTS weight_records (
        id          TEXT PRIMARY KEY,
        timestamp   INTEGER NOT NULL,
        weight_kg   REAL    NOT NULL,
        note        TEXT
    )
"""

const val TABLE_PERIOD_RECORDS = """
    CREATE TABLE IF NOT EXISTS period_records (
        id             TEXT PRIMARY KEY,
        timestamp      INTEGER NOT NULL,
        duration_days  INTEGER NOT NULL,
        flow_intensity INTEGER NOT NULL DEFAULT 2,
        symptoms       TEXT
    )
"""

const val TABLE_BOWEL_RECORDS = """
    CREATE TABLE IF NOT EXISTS bowel_records (
        id           TEXT PRIMARY KEY,
        timestamp    INTEGER NOT NULL,
        bristol_type INTEGER NOT NULL,
        effort_level INTEGER NOT NULL DEFAULT 3,
        note         TEXT
    )
"""
