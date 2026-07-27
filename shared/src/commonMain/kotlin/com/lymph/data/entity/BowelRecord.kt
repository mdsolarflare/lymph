package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/** Bowel movement record using Bristol Stool Scale (1-7) and effort level (1-5). */
data class BowelRecord(
    override val id: String,
    override val timestamp: Long,
    /** Bristol Stool Scale type: 1-7. */
    val bristolType: Int,
    /** Effort level: 1=very easy, 5=straining. */
    val effortLevel: Int = 3,
    /** Optional note (e.g. "after fiber supplement"). */
    val note: String? = null
) : BiometricRecord