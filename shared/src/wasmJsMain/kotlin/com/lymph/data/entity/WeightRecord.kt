package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/**
 * WASM/JS plain class for weight records.
 * No Room annotations — in-memory storage only.
 * Using 'class' instead of 'data class' to work around WASM backend bug in Kotlin 2.1.
 */
class WeightRecord(
    override val id: String,
    override val timestamp: Long,
    /** Body weight in kilograms. */
    override val weightKg: Double,
    /** Optional freeform note (e.g. "fasting", "post-workout"). */
    override val note: String? = null
) : BiometricRecord, com.lymph.data.entity.WeightRecord {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeightRecord) return false
        return id == other.id &&
            timestamp == other.timestamp &&
            weightKg == other.weightKg &&
            note == other.note
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + weightKg.hashCode()
        result = 31 * result + (note?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "WeightRecord(id=$id, timestamp=$timestamp, weightKg=$weightKg, note=$note)"
    }
}

actual fun createWeightRecord(
    id: String,
    timestamp: Long,
    weightKg: Double,
    note: String?
): WeightRecord = WeightRecord(id, timestamp, weightKg, note)