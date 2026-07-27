package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/** Menstrual period record. */
data class PeriodRecord(
    override val id: String,
    override val timestamp: Long,
    /** Duration of the period in days. */
    val durationDays: Int,
    /** Flow intensity: 1=light, 2=medium, 3=heavy, 4=flooding. */
    val flowIntensity: Int = 2,
    /** Optional symptom tags (comma-separated). */
    val symptoms: String? = null
) : BiometricRecord