package com.lymph.data.db

/**
 * Thin abstraction over raw SQLite execution.
 *
 * Common interface — platform-specific implementations wire up to:
 *   Android : android.database.sqlite.SQLiteDatabase (built into SDK)
 *   iOS     : sqlite3 C API via Kotlin/Native interop
 *   Web     : sql.js (SQLite compiled to WASM/JS)
 */
interface SqlExecutor {

    /** Open the database, creating tables if they don't exist. Must be called before any query. */
    suspend fun open()

    /** Execute a statement that returns no rows (INSERT, DELETE, CREATE, etc.). */
    suspend fun execute(sql: String)

    /** Execute a parameterized statement that returns no rows. Arguments are positional (? placeholders). */
    suspend fun execute(sql: String, args: List<Any>)

    /** Execute a query and map each row to [T] using the provided mapper. */
    suspend fun <T> query(
        sql: String,
        args: List<Any>,
        mapper: SqlRowMapper<T>,
    ): List<T>

    /** Close the underlying database connection. */
    suspend fun close()
}

/** Maps a single row from a raw SQLite result set to a typed object. */
interface SqlRowMapper<T> {
    fun map(row: SqlRow): T
}

/** Read-only view of a single SQLite row, accessed by column name. */
interface SqlRow {
    operator fun get(name: String): Any?
    fun getString(name: String): String? = get(name) as? String
    fun getLong(name: String): Long = (get(name) as? Number)?.toLong() ?: 0L
    fun getInt(name: String): Int = (get(name) as? Number)?.toInt() ?: 0
    fun getDouble(name: String): Double = (get(name) as? Number)?.toDouble() ?: 0.0
}
