package com.lymph.data.export

/**
 * CRC-32 (PKZIP polynomial 0xEDB88320) — IEEE 802.3 compatible.
 *
 * Shared by [ZipEncoder] and [ZipDecoder]: the encoder stamps it into every entry,
 * the decoder verifies it on import so corrupted archives fail loudly instead of
 * importing garbage.
 */
internal object Crc32 {

    fun compute(data: ByteArray): Int {
        val polynomial = 0xEDB88320L
        var crc = 0xFFFFFFFFL
        for (byte in data) {
            crc = crc xor (byte.toLong() and 0xFFL)
            for (i in 0 until 8) {
                crc = if (crc and 1L != 0L) (crc ushr 1) xor polynomial else crc ushr 1
            }
        }
        return (crc.inv() and 0xFFFFFFFFL).toInt()
    }
}
