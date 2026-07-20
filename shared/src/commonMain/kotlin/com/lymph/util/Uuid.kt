package com.lymph.util

import kotlin.random.Random

/**
 * Cross-platform UUID v4 generator for KMP.
 */
object Uuid {

    private val HEX = "0123456789abcdef"

    fun randomV4(): String {
        val buf = CharArray(36)
        var idx = 0

        // Fill 32 hex digits with dashes at positions 8, 13, 18, 23
        for (i in 0 until 32) {
            if (idx == 8 || idx == 13 || idx == 18 || idx == 23) buf[idx++] = '-'
            buf[idx++] = HEX[Random.nextInt(16)]
        }

        // Version 4 marker
        buf[14] = '4'
        // Variant: 8, 9, a, or b
        buf[19] = HEX[(Random.nextInt(4) + 8)]

        return buf.concatToString()
    }
}
