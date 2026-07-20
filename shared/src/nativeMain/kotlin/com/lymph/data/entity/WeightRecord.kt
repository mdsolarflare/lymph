package com.lymph.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lymph.data.BiometricRecord

/**
 * Native (Android/iOS) Room entity for weight records.
 * Table name and index match the original commonMain schema.
 */
@Entity(tableName = "weight_records", indices = [Index(value = ["timestamp"])])
data class WeightRecord(
    @PrimaryKey override val id: String,
    override val timestamp: Long,
    /** Body weight in kilograms. */
    override val weightKg: Double,
    /** Optional freeform note (e.g. "fasting", "post-workout"). */
    override val note: String? = null
) : BiometricRecord, com.lymph.data.entity.WeightRecord

actual fun createWeightRecord(
    id: String,
    timestamp: Long,
    weightKg: Double,
    note: String?
): WeightRecord = WeightRecord(id, timestamp, weightKg, note)