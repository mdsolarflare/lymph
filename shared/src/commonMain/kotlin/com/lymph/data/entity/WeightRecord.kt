package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/** Weight measurement record. */
data class WeightRecord(
    override val id: String,
    override val timestamp: Long,
    /** Body weight in kilograms. */
    val weightKg: Double,
    /** Optional freeform note (e.g. "fasting", "post-workout"). */
    val note: String? = null
) : BiometricRecord