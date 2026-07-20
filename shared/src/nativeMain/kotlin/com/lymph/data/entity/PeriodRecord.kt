package com.lymph.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lymph.data.BiometricRecord

/**
 * Native (Android/iOS) Room entity for menstrual period records.
 */
@Entity(tableName = "period_records")
data class PeriodRecord(
    @PrimaryKey override val id: String,
    override val timestamp: Long,
    /** Duration of the period in days. */
    override val durationDays: Int,
    /** Flow intensity: 1=light, 2=medium, 3=heavy, 4=flooding. */
    override val flowIntensity: Int = 2,
    /** Optional symptom tags (comma-separated). */
    override val symptoms: String? = null
) : BiometricRecord, com.lymph.data.entity.PeriodRecord

actual fun createPeriodRecord(
    id: String,
    timestamp: Long,
    durationDays: Int,
    flowIntensity: Int,
    symptoms: String?
): PeriodRecord = PeriodRecord(id, timestamp, durationDays, flowIntensity, symptoms)