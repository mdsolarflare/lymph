package com.lymph.data.export

/**
 * Minimal cross-platform ZIP encoder.
 *
 * Produces a valid PKZIP archive using STORED (uncompressed) entries.
 * Compatible with `unzip`, macOS Archive Utility, and standard ZIP libraries.
 *
 * This implementation is intentionally simple: no compression, no encryption,
 * single-segment only. It is specifically designed for CSV export payloads
 * where the data is small and predictable in size.
 */
object ZipEncoder {

    /**
     * Wraps a map of {entry-name -> content} into a ZIP archive returning the bytes.
     */
    fun buildZip(entries: Map<String, String>): ByteArray {
        val buffers = mutableListOf<ByteArray>()
        val centralRecords = mutableListOf<CentralDirectoryEntry>()

        var offset = 0

        for ((name, content) in entries) {
            val nameBytes = name.encodeToByteArray()
            val data = content.encodeToByteArray()
            val crc = Crc32.compute(data)

            // Local file header
            val localHeader = buildLocalFileHeader(nameBytes, data, crc)
            buffers.add(localHeader)

            // Track for central directory
            centralRecords.add(
                CentralDirectoryEntry(
                    name = name,
                    nameBytes = nameBytes,
                    crc = crc,
                    size = data.size,
                    offset = offset
                )
            )

            offset += localHeader.size
        }

        // Central directory
        val centralDirStart = offset
        for (entry in centralRecords) {
            val centralDirEntry = buildCentralDirectoryEntry(entry)
            buffers.add(centralDirEntry)
            offset += centralDirEntry.size
        }
        val centralDirSize = offset - centralDirStart

        // End of central directory record
        val eocd = buildEndOfCentralDirectory(centralRecords.size, centralDirSize, centralDirStart)
        buffers.add(eocd)

        // Concatenate all buffers
        val totalSize = buffers.sumOf { it.size }
        val result = ByteArray(totalSize)
        var pos = 0
        for (buf in buffers) {
            buf.copyInto(result, pos)
            pos += buf.size
        }
        return result
    }

    private data class CentralDirectoryEntry(
        val name: String,
        val nameBytes: ByteArray,
        val crc: Int,
        val size: Int,
        val offset: Int
    )

    private fun buildLocalFileHeader(name: ByteArray, data: ByteArray, crc: Int): ByteArray {
        val headerSize = 30 + name.size
        val out = ByteArray(headerSize + data.size)
        var p = 0

        // Local file header signature: 0x04034b50
        writeInt(out, p, 0x04034b50); p += 4
        // Version needed to extract
        writeShort(out, p, 20); p += 2
        // General purpose bit flag
        writeShort(out, p, 0); p += 2
        // Compression method (0 = stored)
        writeShort(out, p, 0); p += 2
        // Last mod file time
        writeShort(out, p, 0); p += 2
        // Last mod file date
        writeShort(out, p, 0); p += 2
        // CRC-32
        writeInt(out, p, crc); p += 4
        // Compressed size
        writeInt(out, p, data.size); p += 4
        // Uncompressed size
        writeInt(out, p, data.size); p += 4
        // File name length
        writeShort(out, p, name.size); p += 2
        // Extra field length
        writeShort(out, p, 0); p += 2
        // File name
        name.copyInto(out, p); p += name.size
        // File data
        data.copyInto(out, p)

        return out
    }

    private fun buildCentralDirectoryEntry(entry: CentralDirectoryEntry): ByteArray {
        val out = ByteArray(46 + entry.nameBytes.size)
        var p = 0

        // Central directory file header signature: 0x02014b50
        writeInt(out, p, 0x02014b50); p += 4
        // Version made by
        writeShort(out, p, 20); p += 2
        // Version needed to extract
        writeShort(out, p, 20); p += 2
        // General purpose bit flag
        writeShort(out, p, 0); p += 2
        // Compression method
        writeShort(out, p, 0); p += 2
        // File last mod time
        writeShort(out, p, 0); p += 2
        // File last mod date
        writeShort(out, p, 0); p += 2
        // CRC-32
        writeInt(out, p, entry.crc); p += 4
        // Compressed size
        writeInt(out, p, entry.size); p += 4
        // Uncompressed size
        writeInt(out, p, entry.size); p += 4
        // File name length
        writeShort(out, p, entry.nameBytes.size); p += 2
        // Extra field length
        writeShort(out, p, 0); p += 2
        // File comment length
        writeShort(out, p, 0); p += 2
        // Disk number start
        writeShort(out, p, 0); p += 2
        // Internal file attributes
        writeShort(out, p, 0); p += 2
        // External file attributes
        writeInt(out, p, 0); p += 4
        // Relative offset of local header
        writeInt(out, p, entry.offset); p += 4
        // File name
        entry.nameBytes.copyInto(out, p)

        return out
    }

    private fun buildEndOfCentralDirectory(
        entryCount: Int,
        centralDirSize: Int,
        centralDirOffset: Int
    ): ByteArray {
        val out = ByteArray(22)
        var p = 0

        writeInt(out, p, 0x06054b50); p += 4
        writeShort(out, p, 0); p += 2  // Disk number
        writeShort(out, p, 0); p += 2  // Disk where central directory starts
        writeShort(out, p, entryCount); p += 2  // Entries on this disk
        writeShort(out, p, entryCount); p += 2  // Total entries
        writeInt(out, p, centralDirSize); p += 4
        writeInt(out, p, centralDirOffset); p += 4
        writeShort(out, p, 0)  // Comment length

        return out
    }

    private fun writeInt(buffer: ByteArray, pos: Int, value: Int) {
        buffer[pos]     = (value and 0xFF).toByte()
        buffer[pos + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[pos + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[pos + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeShort(buffer: ByteArray, pos: Int, value: Int) {
        buffer[pos]     = (value and 0xFF).toByte()
        buffer[pos + 1] = ((value ushr 8) and 0xFF).toByte()
    }

}
