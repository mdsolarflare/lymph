package com.lymph.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lymph.data.BiometricRecord

/**
 * Native (Android/iOS) Room entity for bowel movement records.
 * Uses Bristol Stool Scale (1-7) and effort level (1-5).
 */
@Entity(tableName = "bowel_records")
data class BowelRecord(
    @PrimaryKey override val id: String,
    override val timestamp: Long,
    /** Bristol Stool Scale type: 1-7. */
    override val bristolType: Int,
    /** Effort level: 1=very easy, 5=straining. */
    override val effortLevel: Int = 3,
    /** Optional note (e.g. "after fiber supplement"). */
    override val note: String? = null
) : BiometricRecord, com.lymph.data.entity.BowelRecord

actual fun createBowelRecord(
    id: String,
    timestamp: Long,
    bristolType: Int,
    effortLevel: Int,
    note: String?
): BowelRecord = BowelRecord(id, timestamp, bristolType, effortLevel, note)