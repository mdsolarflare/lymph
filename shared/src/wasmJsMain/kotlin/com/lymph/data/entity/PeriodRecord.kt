package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/**
 * WASM/JS plain class for menstrual period records.
 * No Room annotations — in-memory storage only.
 * Using 'class' instead of 'data class' to work around WASM backend bug in Kotlin 2.1.
 */
class PeriodRecord(
    override val id: String,
    override val timestamp: Long,
    /** Duration of the period in days. */
    override val durationDays: Int,
    /** Flow intensity: 1=light, 2=medium, 3=heavy, 4=flooding. */
    override val flowIntensity: Int = 2,
    /** Optional symptom tags (comma-separated). */
    override val symptoms: String? = null
) : BiometricRecord, com.lymph.data.entity.PeriodRecord {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeriodRecord) return false
        return id == other.id &&
            timestamp == other.timestamp &&
            durationDays == other.durationDays &&
            flowIntensity == other.flowIntensity &&
            symptoms == other.symptoms
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + durationDays.hashCode()
        result = 31 * result + flowIntensity.hashCode()
        result = 31 * result + (symptoms?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "PeriodRecord(id=$id, timestamp=$timestamp, durationDays=$durationDays, flowIntensity=$flowIntensity, symptoms=$symptoms)"
    }
}

actual fun createPeriodRecord(
    id: String,
    timestamp: Long,
    durationDays: Int,
    flowIntensity: Int,
    symptoms: String?
): PeriodRecord = PeriodRecord(id, timestamp, durationDays, flowIntensity, symptoms)