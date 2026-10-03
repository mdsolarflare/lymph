//! CRC-32 (PKZIP polynomial 0xEDB88320) — IEEE 802.3 compatible.
//!
//! The ZIP encoder stamps it into every entry; the decoder verifies it on
//! import so corrupted archives fail loudly instead of importing garbage.

pub fn compute(data: &[u8]) -> u32 {
    const POLYNOMIAL: u64 = 0xEDB88320;
    let mut crc: u64 = 0xFFFF_FFFF;
    for &byte in data {
        crc ^= byte as u64;
        for _ in 0..8 {
            if crc & 1 != 0 {
                crc = (crc >> 1) ^ POLYNOMIAL;
            } else {
                crc >>= 1;
            }
        }
    }
    !(crc as u32) // Kotlin: .inv() and 0xFFFFFFFF -> same low 32 bits
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn known_vectors() {
        // Canonical CRC-32 test vectors (IEEE / PKZIP).
        assert_eq!(compute(b""), 0x0000_0000);
        assert_eq!(compute(b"123456789"), 0xCBF4_3926);
        assert_eq!(compute(b"a"), 0xE8B7_BE43);
    }
}
