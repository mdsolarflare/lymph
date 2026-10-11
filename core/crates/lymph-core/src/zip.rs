//! Minimal ZIP encoder/decoder — a faithful port of the Kotlin
//! `ZipEncoder`/`ZipDecoder`. STORED (uncompressed) entries only, which is
//! deliberate: no inflate step keeps the parser trivial, and the payload is
//! small (docs/DATA-EXCHANGE.md). Every entry's CRC-32 is verified on read so
//! corrupted archives fail loudly instead of importing garbage.

use crate::crc32;
use crate::importer::ImportException;

const LOCAL_FILE_SIGNATURE: u32 = 0x0403_4B50;
const CENTRAL_DIR_SIGNATURE: u32 = 0x0201_4B50;
const EOCD_SIGNATURE: u32 = 0x0605_4B50;
const METHOD_STORED: u16 = 0;

fn write_int(buf: &mut Vec<u8>, value: u32) {
    buf.extend_from_slice(&value.to_le_bytes());
}

fn write_short(buf: &mut Vec<u8>, value: u16) {
    buf.extend_from_slice(&value.to_le_bytes());
}

/// Wraps ordered `(entry-name, content)` pairs into a ZIP archive.
/// Order is preserved exactly as given (callers pass the canonical export order).
pub fn build_zip(entries: &[(&str, &str)]) -> Vec<u8> {
    struct CentralEntry {
        name_bytes: Vec<u8>,
        crc: u32,
        size: u32,
        offset: u32,
    }

    let mut out: Vec<u8> = Vec::new();
    let mut central: Vec<CentralEntry> = Vec::new();

    for (name, content) in entries {
        let name_bytes = name.as_bytes();
        let data = content.as_bytes();
        let crc = crc32::compute(data);
        let offset = out.len() as u32;

        // Local file header (30 bytes + name) then the data.
        let start = out.len();
        write_int(&mut out, LOCAL_FILE_SIGNATURE);
        write_short(&mut out, 20); // version needed to extract
        write_short(&mut out, 0); // general purpose bit flag
        write_short(&mut out, METHOD_STORED);
        write_short(&mut out, 0); // last mod time
        write_short(&mut out, 0); // last mod date
        write_int(&mut out, crc);
        write_int(&mut out, data.len() as u32); // compressed size
        write_int(&mut out, data.len() as u32); // uncompressed size
        write_short(&mut out, name_bytes.len() as u16);
        write_short(&mut out, 0); // extra field length
        out.extend_from_slice(name_bytes);
        debug_assert_eq!(out.len() - start, 30 + name_bytes.len());
        out.extend_from_slice(data);

        central.push(CentralEntry {
            name_bytes: name_bytes.to_vec(),
            crc,
            size: data.len() as u32,
            offset,
        });
    }

    let central_dir_start = out.len() as u32;
    for e in &central {
        write_int(&mut out, CENTRAL_DIR_SIGNATURE);
        write_short(&mut out, 20); // version made by
        write_short(&mut out, 20); // version needed to extract
        write_short(&mut out, 0); // general purpose bit flag
        write_short(&mut out, METHOD_STORED);
        write_short(&mut out, 0); // last mod time
        write_short(&mut out, 0); // last mod date
        write_int(&mut out, e.crc);
        write_int(&mut out, e.size); // compressed size
        write_int(&mut out, e.size); // uncompressed size
        write_short(&mut out, e.name_bytes.len() as u16);
        write_short(&mut out, 0); // extra field length
        write_short(&mut out, 0); // file comment length
        write_short(&mut out, 0); // disk number start
        write_short(&mut out, 0); // internal file attributes
        write_int(&mut out, 0); // external file attributes
        write_int(&mut out, e.offset); // relative offset of local header
        out.extend_from_slice(&e.name_bytes);
    }
    let central_dir_size = out.len() as u32 - central_dir_start;

    // End of central directory record (22 bytes).
    write_int(&mut out, EOCD_SIGNATURE);
    write_short(&mut out, 0); // disk number
    write_short(&mut out, 0); // disk where central directory starts
    write_short(&mut out, central.len() as u16); // entries on this disk
    write_short(&mut out, central.len() as u16); // total entries
    write_int(&mut out, central_dir_size);
    write_int(&mut out, central_dir_start);
    write_short(&mut out, 0); // comment length

    out
}

fn read_short(bytes: &[u8], pos: usize) -> Option<u16> {
    let b = bytes.get(pos..pos + 2)?;
    Some(u16::from_le_bytes([b[0], b[1]]))
}

fn read_int(bytes: &[u8], pos: usize) -> Option<u32> {
    let b = bytes.get(pos..pos + 4)?;
    Some(u32::from_le_bytes([b[0], b[1], b[2], b[3]]))
}

/// Scans backwards from the end for the EOCD record (its comment may be up to 65535 bytes).
fn find_eocd(bytes: &[u8]) -> Option<usize> {
    if bytes.len() < 22 {
        return None;
    }
    let earliest = bytes.len().saturating_sub(22 + 0xFFFF);
    let mut i = bytes.len() - 22;
    loop {
        if read_int(bytes, i) == Some(EOCD_SIGNATURE) {
            return Some(i);
        }
        if i == earliest {
            return None;
        }
        i -= 1;
    }
}

/// Decodes a ZIP archive into ordered `(entry-name, text content)` pairs.
/// Unknown entries are included as-is; callers pick the names they recognize.
/// All offsets are validated with checked arithmetic — a corrupt archive can
/// never panic the decoder, only yield an [ImportException].
pub fn decode(bytes: &[u8]) -> Result<Vec<(String, String)>, ImportException> {
    let eocd = find_eocd(bytes).ok_or(ImportException::new("not a valid ZIP archive"))?;

    let entry_count = read_short(bytes, eocd + 10).unwrap_or(0) as usize;
    let central_dir_offset = read_int(bytes, eocd + 16).unwrap_or(0) as usize;

    let mut entries: Vec<(String, String)> = Vec::new();
    let mut pos = central_dir_offset;
    for i in 0..entry_count {
        if pos + 46 > bytes.len() || read_int(bytes, pos) != Some(CENTRAL_DIR_SIGNATURE) {
            return Err(ImportException::new(
                "corrupt ZIP archive (central directory unreadable)",
            ));
        }
        let compression_method = read_short(bytes, pos + 10).unwrap_or(0xFFFF);
        let crc = read_int(bytes, pos + 16).unwrap_or(0);
        let compressed_size = read_int(bytes, pos + 20).unwrap_or(u32::MAX) as usize;
        let name_length = read_short(bytes, pos + 28).unwrap_or(0) as usize;
        let extra_length = read_short(bytes, pos + 30).unwrap_or(0) as usize;
        let comment_length = read_short(bytes, pos + 32).unwrap_or(0) as usize;
        let local_header_offset = read_int(bytes, pos + 42).unwrap_or(u32::MAX) as usize;

        if pos + 46 + name_length > bytes.len() {
            return Err(ImportException::new(&format!(
                "corrupt ZIP archive (entry {i} truncated)"
            )));
        }
        let name = String::from_utf8_lossy(&bytes[pos + 46..pos + 46 + name_length]).into_owned();

        // Local file header: entry data starts after its own name + extra fields.
        if local_header_offset + 30 > bytes.len()
            || read_int(bytes, local_header_offset) != Some(LOCAL_FILE_SIGNATURE)
        {
            return Err(ImportException::new(&format!(
                "corrupt ZIP archive (local header for '{name}' unreadable)"
            )));
        }
        let local_name_length = read_short(bytes, local_header_offset + 26).unwrap_or(0) as usize;
        let local_extra_length = read_short(bytes, local_header_offset + 28).unwrap_or(0) as usize;
        let Some(data_start) = (local_header_offset + 30)
            .checked_add(local_name_length)
            .and_then(|v| v.checked_add(local_extra_length))
        else {
            return Err(ImportException::new(&format!(
                "corrupt ZIP archive (entry '{name}' truncated)"
            )));
        };
        let Some(data_end) = data_start.checked_add(compressed_size) else {
            return Err(ImportException::new(&format!(
                "corrupt ZIP archive (entry '{name}' truncated)"
            )));
        };
        if data_end > bytes.len() {
            return Err(ImportException::new(&format!(
                "corrupt ZIP archive (entry '{name}' truncated)"
            )));
        }
        if compression_method != METHOD_STORED {
            return Err(ImportException::new(&format!(
                "unsupported compression method {compression_method} for entry '{name}' — lymph import reads STORED entries only"
            )));
        }

        let data = &bytes[data_start..data_end];
        if crc32::compute(data) != crc {
            return Err(ImportException::new(&format!(
                "CRC-32 mismatch for entry '{name}' — archive is corrupted"
            )));
        }
        // Duplicate names: later occurrence wins, mirroring the Kotlin map put.
        if let Some(slot) = entries.iter_mut().find(|(n, _)| *n == name) {
            slot.1 = String::from_utf8_lossy(data).into_owned();
        } else {
            entries.push((name, String::from_utf8_lossy(data).into_owned()));
        }

        pos += 46 + name_length + extra_length + comment_length;
    }
    Ok(entries)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip_single_entry() {
        let zip = build_zip(&[("weight_records.csv", "id,timestamp\na,1\n")]);
        assert_eq!(
            decode(&zip).unwrap(),
            vec![(
                "weight_records.csv".to_string(),
                "id,timestamp\na,1\n".to_string()
            )]
        );
    }

    #[test]
    fn round_trip_multiple_entries_with_unicode() {
        let zip = build_zip(&[
            ("weight_records.csv", "w"),
            ("period_records.csv", "p — ünïcode ✓"),
            ("bowel_records.csv", "b\nline2"),
        ]);
        let decoded = decode(&zip).unwrap();
        assert_eq!(decoded.len(), 3);
        assert_eq!(decoded[1].1, "p — ünïcode ✓");
        assert_eq!(decoded[2].1, "b\nline2");
    }

    #[test]
    fn not_a_zip_throws() {
        assert!(decode(b"hello world, definitely not a zip").is_err());
        // Shorter than the minimum EOCD record.
        assert!(decode(&[0u8; 10]).is_err());
    }

    #[test]
    fn crc_mismatch_detected() {
        let content = "AAAAAAAAAA";
        let mut zip = build_zip(&[("x.csv", content)]);
        // Single entry: local header is 30 + name.len()(5) = 35 bytes, data starts at 35.
        zip[35] = b'B';
        let e = decode(&zip).unwrap_err();
        assert!(
            e.message.contains("CRC-32 mismatch"),
            "unexpected: {}",
            e.message
        );
    }

    #[test]
    fn unsupported_compression_rejected() {
        let mut zip = build_zip(&[("x.csv", "data")]);
        // Flip the compression method (local header offset 8, central directory offset 10) to DEFLATE.
        zip[8] = 8;
        zip[9] = 0;
        let name_len = "x.csv".len();
        let data_start = 30 + name_len; // end of local header
        let central_start = data_start + 4; // length of "data"
        zip[central_start + 10] = 8;
        zip[central_start + 11] = 0;
        let e = decode(&zip).unwrap_err();
        assert!(
            e.message.contains("unsupported compression"),
            "unexpected: {}",
            e.message
        );
    }

    #[test]
    fn truncated_archive_throws() {
        let zip = build_zip(&[("x.csv", "some content here")]);
        assert!(decode(&zip[..20]).is_err());
    }

    #[test]
    fn corrupt_archive_never_panics() {
        // Fuzz-ish sanity: every truncation of a valid archive must be Ok or Err, never a panic.
        let zip = build_zip(&[
            ("weight_records.csv", "id,timestamp\na,1\n"),
            ("period_records.csv", "id,timestamp\nb,2\n"),
        ]);
        for cut in 0..zip.len() {
            let _ = decode(&zip[..cut]);
        }
    }
}
