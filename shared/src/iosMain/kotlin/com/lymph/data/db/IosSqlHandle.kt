package com.lymph.data.db

import kotlinx.cinterop.*
import platform.posix.memcpy
import sqlite3.*

/**
 * iOS implementation of [SqlHandle] using the built-in sqlite3 C API.
 */
actual fun platformOpen(path: String): SqlHandle {
    val dbPtr = memScoped {
        val handle = alloc<COpaquePointerVar>()
        val rc = sqlite3_open(path.toCValues(getAllocator()), handle.ptr)
        if (rc != SQLITE_OK) error("sqlite3_open failed: $rc")
        handle.value
    }
    IosSqlHandle(handlePtr = dbPtr!!)
}

class IosSqlHandle(private val handlePtr: COpaquePointer) : SqlHandle {

    override fun execute(sql: String) {
        memScoped {
            val err = alloc<ByteArrayVar>(512)
            val rc = sqlite3_exec(handlePtr, sql.toCValues(getAllocator()), null, null, err.ptr)
            if (rc != SQLITE_OK) error("sqlite3_exec failed ($rc): ${err.value.toKString()}")
        }
    }

    override fun execute(sql: String, args: List<Any>) {
        memScoped {
            val stmt = alloc<COpaquePointerVar>()
            val rc = sqlite3_prepare_v2(handlePtr, sql.toCValues(getAllocator()), sql.length, stmt.ptr, null)
            if (rc != SQLITE_OK) error("sqlite3_prepare failed: $rc")

            for ((i, arg) in args.withIndex()) {
                bind(stmt.value!!, i + 1, arg)
            }

            val stepRc = sqlite3_step(stmt.value!!)
            if (stepRc != SQLITE_DONE && stepRc != SQLITE_ROW) error("sqlite3_step failed: $stepRc")
            sqlite3_finalize(stmt.value!!)
        }
    }

    override fun <T> query(sql: String, args: List<Any>, mapper: SqlRowMapper<T>): List<T> {
        val results = mutableListOf<T>()

        memScoped {
            val stmt = alloc<COpaquePointerVar>()
            val rc = sqlite3_prepare_v2(handlePtr, sql.toCValues(getAllocator()), sql.length, stmt.ptr, null)
            if (rc != SQLITE_OK) error("sqlite3_prepare failed: $rc")

            for ((i, arg) in args.withIndex()) {
                bind(stmt.value!!, i + 1, arg)
            }

            // Collect column names before stepping
            val colCount = sqlite3_column_count(stmt.value!!).toInt()
            val colNames = List(colCount) { i ->
                sqlite3_column_name(stmt.value!!, i)?.toKString() ?: ""
            }

            while (sqlite3_step(stmt.value!!) == SQLITE_ROW) {
                results.add(mapper.map(IosSqlRow(stmt.value!!, colCount, colNames)))
            }

            sqlite3_finalize(stmt.value!!)
        }

        return results
    }

    override fun close() {
        sqlite3_close(handlePtr)
    }

    private fun bind(stmt: COpaquePointer, index: Int, value: Any?) {
        when (value) {
            is String -> memScoped {
                sqlite3_bind_text(stmt, index, value.toCValues(getAllocator()), value.length, null)
            }
            is Long -> sqlite3_bind_int64(stmt, index, value)
            is Int -> sqlite3_bind_int(stmt, index, value.toLong())
            is Double -> sqlite3_bind_double(stmt, index, value)
            is Boolean -> sqlite3_bind_int(stmt, index, if (value) 1L else 0L)
            null -> sqlite3_bind_null(stmt, index)
            else -> error("Unsupported bind type: ${value::class.simpleName}")
        }
    }
}

class IosSqlRow(
    private val stmt: COpaquePointer,
    private val colCount: Int,
    private val colNames: List<String>,
) : SqlRow {
    override fun get(name: String): Any? {
        val idx = colNames.indexOf(name)
        if (idx == -1) return null

        // Check for NULL first
        if (sqlite3_column_type(stmt, idx) == SQLITE_NULL) return null

        when (val type = sqlite3_column_type(stmt, idx)) {
            SQLITE_INTEGER -> return sqlite3_column_int64(stmt, idx)
            SQLITE_FLOAT   -> return sqlite3_column_double(stmt, idx)
            SQLITE_TEXT    -> memScoped {
                val ptr = sqlite3_column_text(stmt, idx) ?: return@memScoped null
                return ptr.toKString()
            }
            else           -> return sqlite3_column_text(stmt, idx)?.toKString()
        }
    }
}

// Minimal Kotlin/Native bindings for sqlite3 — iOS ships with libsqlite3.dylib
@CName("SQLITE_OK") external val SQLITE_OK: Int
@CName("SQLITE_DONE") external val SQLITE_DONE: Int
@CName("SQLITE_ROW") external val SQLITE_ROW: Int
@CName("SQLITE_INTEGER") external val SQLITE_INTEGER: Int
@CName("SQLITE_FLOAT") external val SQLITE_FLOAT: Int
@CName("SQLITE_TEXT") external val SQLITE_TEXT: Int
@CName("SQLITE_NULL") external val SQLITE_NULL: Int
