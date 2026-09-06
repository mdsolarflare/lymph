package com.lymph.data.export

/**
 * Minimal cross-platform ZIP decoder — the inverse of [ZipEncoder].
 *
 * Supports STORED (uncompressed) entries only, which is what lymph export produces.
 * Compressed archives (e.g. DEFLATE) are rejected with a clear error rather than
 * silently misread; see docs/DATA-EXCHANGE.md for how to produce compatible archives.
 *
 * Every entry's CRC-32 is verified before its content is returned, so corrupted or
 * truncated archives fail loudly instead of importing garbage.
 */
object ZipDecoder {

    /**
     * Decodes [zipBytes] into a map of {entry-name -> text content}.
     * Unknown entries are included as-is; callers pick the names they recognize.
     * Throws [ImportException] on any structural problem (not a ZIP, corrupt headers,
     * CRC mismatch, unsupported compression).
     */
    fun decode(zipBytes: ByteArray): Map<String, String> {
        val eocd = findEndOfCentralDirectory(zipBytes)
            ?: throw ImportException("not a valid ZIP archive")

        val entryCount = readShort(zipBytes, eocd + 10).toInt()
        val centralDirOffset = readInt(zipBytes, eocd + 16)

        val entries = LinkedHashMap<String, String>()
        var pos = centralDirOffset
        for (i in 0 until entryCount) {
            if (pos + 46 > zipBytes.size || readInt(zipBytes, pos) != CENTRAL_DIR_SIGNATURE) {
                throw ImportException("corrupt ZIP archive (central directory unreadable)")
            }
            val compressionMethod = readShort(zipBytes, pos + 10).toInt()
            val crc = readInt(zipBytes, pos + 16)
            val compressedSize = readInt(zipBytes, pos + 20)
            val nameLength = readShort(zipBytes, pos + 28).toInt()
            val extraLength = readShort(zipBytes, pos + 30).toInt()
            val commentLength = readShort(zipBytes, pos + 32).toInt()
            val localHeaderOffset = readInt(zipBytes, pos + 42)

            if (pos + 46 + nameLength > zipBytes.size || nameLength < 0) {
                throw ImportException("corrupt ZIP archive (entry $i truncated)")
            }
            val name = zipBytes.decodeToString(pos + 46, pos + 46 + nameLength)

            // Local file header: entry data starts after its own name + extra fields.
            if (localHeaderOffset + 30 > zipBytes.size || readInt(zipBytes, localHeaderOffset) != LOCAL_FILE_SIGNATURE) {
                throw ImportException("corrupt ZIP archive (local header for '$name' unreadable)")
            }
            val localNameLength = readShort(zipBytes, localHeaderOffset + 26).toInt()
            val localExtraLength = readShort(zipBytes, localHeaderOffset + 28).toInt()
            val dataStart = localHeaderOffset + 30 + localNameLength + localExtraLength

            if (dataStart < 0 || dataStart + compressedSize > zipBytes.size) {
                throw ImportException("corrupt ZIP archive (entry '$name' truncated)")
            }
            if (compressionMethod != METHOD_STORED) {
                throw ImportException(
                    "unsupported compression method $compressionMethod for entry '$name' — lymph import reads STORED entries only"
                )
            }

            val data = zipBytes.copyOfRange(dataStart, dataStart + compressedSize)
            if (Crc32.compute(data) != crc) {
                throw ImportException("CRC-32 mismatch for entry '$name' — archive is corrupted")
            }
            entries[name] = data.decodeToString()

            pos += 46 + nameLength + extraLength + commentLength
        }
        return entries
    }

    private const val LOCAL_FILE_SIGNATURE = 0x04034b50
    private const val CENTRAL_DIR_SIGNATURE = 0x02014b50
    private const val EOCD_SIGNATURE = 0x06054b50
    private const val METHOD_STORED = 0

    /** Scans backwards from the end of the archive for the EOCD record (its comment field may be up to 65535 bytes). */
    private fun findEndOfCentralDirectory(bytes: ByteArray): Int? {
        if (bytes.size < 22) return null
        val earliest = (bytes.size - 22 - 0xFFFF).coerceAtLeast(0)
        for (i in bytes.size - 22 downTo earliest) {
            if (readInt(bytes, i) == EOCD_SIGNATURE) return i
        }
        return null
    }

    private fun readShort(bytes: ByteArray, pos: Int): Long =
        (bytes[pos].toLong() and 0xFFL) or ((bytes[pos + 1].toLong() and 0xFFL) shl 8)

    private fun readInt(bytes: ByteArray, pos: Int): Int =
        ((bytes[pos].toLong() and 0xFFL) or
            ((bytes[pos + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[pos + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[pos + 3].toLong() and 0xFFL) shl 24)).toInt()
}
