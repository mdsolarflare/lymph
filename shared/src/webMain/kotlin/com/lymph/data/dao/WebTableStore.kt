package com.lymph.data.dao

import com.lymph.data.export.CsvDecoder
import com.lymph.platform.LocalStorage

/**
 * Bridges the in-memory web DAOs to [LocalStorage] using the CSV schema contract
 * (CsvEncoder/CsvDecoder) — the same format the export ZIP carries, so storage,
 * export, and import all share one serialization layer.
 *
 * Failure policy: unreadable/corrupt stored data logs a warning and starts the
 * session empty rather than crashing; write failures fall back to in-memory-only
 * behavior (see [LocalStorage.set]).
 */
internal object WebTableStore {

    /** Reads [key]'s CSV into records; empty on absent key, unreadable data, or schema mismatch. */
    fun <T> hydrate(key: String, decode: (String) -> CsvDecoder.DecodedTable<T>): List<T> {
        val csv = LocalStorage.get(key) ?: return emptyList()
        return try {
            val table = decode(csv)
            if (table.invalidRows > 0) {
                println("WARN: $key — ${table.invalidRows} invalid row(s) dropped on hydrate")
            }
            table.records
        } catch (e: Exception) {
            println("WARN: $key unreadable (${e.message}) — starting empty")
            emptyList()
        }
    }

    /** Encodes [records] back to [key]; a no-op failure just means the session is in-memory-only. */
    fun <T> persist(key: String, records: List<T>, encode: (List<T>) -> String) {
        LocalStorage.set(key, encode(records))
    }
}
