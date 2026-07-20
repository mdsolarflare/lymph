package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/**
 * WASM/JS plain class for bowel movement records.
 * No Room annotations — in-memory storage only.
 * Using 'class' instead of 'data class' to work around WASM backend bug in Kotlin 2.1.
 */
class BowelRecord(
    override val id: String,
    override val timestamp: Long,
    /** Bristol Stool Scale type: 1-7. */
    override val bristolType: Int,
    /** Effort level: 1=very easy, 5=straining. */
    override val effortLevel: Int = 3,
    /** Optional note (e.g. "after fiber supplement"). */
    override val note: String? = null
) : BiometricRecord, com.lymph.data.entity.BowelRecord {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BowelRecord) return false
        return id == other.id &&
            timestamp == other.timestamp &&
            bristolType == other.bristolType &&
            effortLevel == other.effortLevel &&
            note == other.note
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + bristolType.hashCode()
        result = 31 * result + effortLevel.hashCode()
        result = 31 * result + (note?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "BowelRecord(id=$id, timestamp=$timestamp, bristolType=$bristolType, effortLevel=$effortLevel, note=$note)"
    }
}

actual fun createBowelRecord(
    id: String,
    timestamp: Long,
    bristolType: Int,
    effortLevel: Int,
    note: String?
): BowelRecord = BowelRecord(id, timestamp, bristolType, effortLevel, note)