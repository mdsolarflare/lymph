package com.lymph.data

/**
 * Common contract for all biometric records in Lymph.
 * Every tracked measurement exposes a stable UUID and epoch-millis timestamp.
 */
interface BiometricRecord {
    val id: String
    val timestamp: Long
}
