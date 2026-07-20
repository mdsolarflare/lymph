package com.lymph.data.entity

import com.lymph.data.BiometricRecord

/**
 * Interface for bowel movement record using the Bristol Stool Scale.
 * Implemented by platform-specific data classes.
 */
interface BowelRecord : BiometricRecord {
    val bristolType: Int
    val effortLevel: Int
    val note: String?
}

/** Factory function for creating BowelRecord instances. */
expect fun createBowelRecord(
    id: String,
    timestamp: Long,
    bristolType: Int,
    effortLevel: Int,
    note: String?
): BowelRecord