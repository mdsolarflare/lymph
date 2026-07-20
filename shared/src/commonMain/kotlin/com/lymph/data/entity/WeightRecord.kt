package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/**
 * Interface for weight measurement record.
 * Implemented by platform-specific data classes.
 */
interface WeightRecord : BiometricRecord {
    val weightKg: Double
    val note: String?
}

/** Factory function for creating WeightRecord instances. */
expect fun createWeightRecord(
    id: String,
    timestamp: Long,
    weightKg: Double,
    note: String?
): WeightRecord