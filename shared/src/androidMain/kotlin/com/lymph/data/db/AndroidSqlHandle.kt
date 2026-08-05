package com.lymph.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Android implementation of [SqlHandle] using the built-in SQLiteDatabase API.
 */
actual fun platformOpen(path: String): SqlHandle {
    val dbFile = File(path)
    if (!dbFile.parentFile?.exists() == true) {
        dbFile.parentFile?.mkdirs()
    }
    return AndroidSqlHandle(SQLiteDatabase.openOrCreateDatabase(dbFile, null))
}

class AndroidSqlHandle(private val db: SQLiteDatabase) : SqlHandle {

    override fun execute(sql: String) {
        db.execSQL(sql)
    }

    override fun execute(sql: String, args: List<Any>) {
        db.compileStatement(sql).use { stmt ->
            for ((i, arg) in args.withIndex()) {
                bind(stmt, i + 1L, arg)
            }
            stmt.execute()
        }
    }

    override fun <T> query(sql: String, args: List<Any>, mapper: SqlRowMapper<T>): List<T> {
        val results = mutableListOf<T>()
        db.rawQuery(sql, args.map { castToSqlArg(it) }.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(mapper.map(AndroidSqlRow(cursor)))
            }
        }
        return results
    }

    override fun close() {
        db.close()
    }

    private fun bind(stmt: SQLiteDatabase.Statement, index: Long, value: Any?) {
        when (value) {
            is String -> stmt.bindString(index, value)
            is Long   -> stmt.bindLong(index, value)
            is Int    -> stmt.bindLong(index, value.toLong())
            is Double -> stmt.bindDouble(index, value)
            null      -> stmt.bindNull(index)
            else      -> error("Unsupported bind type: ${value::class.simpleName}")
        }
    }

    private fun castToSqlArg(value: Any?): String? {
        return when (value) {
            is String -> "'${value.replace("'", "''")}'"
            is Number -> value.toString()
            null      -> "NULL"
            else      -> value.toString()
        }
    }
}

class AndroidSqlRow(private val cursor: Cursor) : SqlRow {
    override fun get(name: String): Any? {
        val idx = cursor.getColumnIndexOrThrow(name)
        return when (cursor.getType(idx)) {
            Cursor.FIELD_TYPE_NULL     -> null
            Cursor.FIELD_TYPE_INTEGER  -> if (cursor.columnNames[idx].lowercase().contains("timestamp")) cursor.getLong(idx) else cursor.getInt(idx).toLong()
            Cursor.FIELD_TYPE_FLOAT    -> cursor.getDouble(idx)
            Cursor.FIELD_TYPE_STRING   -> cursor.getString(idx)
            else                       -> cursor.getString(idx)
        }
    }
}
