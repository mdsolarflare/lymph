package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/**
 * Interface for menstrual period record.
 * Implemented by platform-specific data classes.
 */
interface PeriodRecord : BiometricRecord {
    val durationDays: Int
    val flowIntensity: Int
    val symptoms: String?
}

/** Factory function for creating PeriodRecord instances. */
expect fun createPeriodRecord(
    id: String,
    timestamp: Long,
    durationDays: Int,
    flowIntensity: Int,
    symptoms: String?
): PeriodRecord