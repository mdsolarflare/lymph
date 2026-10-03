//! Weight entry math, ported bit-for-bit from the Kotlin `WeightFormat`.
//!
//! The integer-hundredths discipline is load-bearing: the Kotlin app had a
//! real bug where `+= 0.1` on a Double stored 70.4999999…; values are held and
//! stored as integer hundredths of the selected unit and no floating-point
//! accumulation is ever allowed.

use serde::{Deserialize, Serialize};

/// Exact conversion factor: 1 international avoirdupois pound = exactly 0.45359237 kg.
pub const KG_PER_LB: f64 = 0.45359237;

pub use crate::entity::ranges::{MAX_WEIGHT_KG, MIN_WEIGHT_KG};

/// Kotlin `Double.roundToLong()` rounds ties **towards positive infinity**
/// (2.5 -> 3, -2.5 -> -2). Rust's `f64::round()` rounds ties away from zero,
/// so the rule is written out explicitly to keep ported behavior identical.
fn kotlin_round_to_long(v: f64) -> i64 {
    let floor = v.floor();
    let diff = v - floor;
    if diff >= 0.5 {
        (floor + 1.0) as i64
    } else {
        floor as i64
    }
}

/// Round to at most two decimal places — the precision limit for stored weight.
pub fn round_to_hundredths(value: f64) -> f64 {
    kotlin_round_to_long(value * 100.0) as f64 / 100.0
}

/// Parse a user-typed weight string into integer hundredths of the unit.
/// Returns `None` for empty input, non-numeric characters, or more than two
/// decimal places. A trailing dot ("70.") is accepted and treated as 70.00.
pub fn parse_hundredths(text: &str) -> Option<i32> {
    let t = text.trim();
    if t.is_empty() {
        return None;
    }
    let mut int_part: i64 = 0;
    let mut frac_value: i32 = 0;
    let mut frac_digits: i32 = 0;
    let mut seen_dot = false;
    let mut any_digit = false;
    for ch in t.chars() {
        if ch == '.' {
            if seen_dot {
                return None;
            }
            seen_dot = true;
        } else if let Some(d) = ch.to_digit(10) {
            any_digit = true;
            if seen_dot {
                if frac_digits >= 2 {
                    return None;
                }
                frac_value = frac_value * 10 + d as i32;
                frac_digits += 1;
            } else {
                int_part = int_part * 10 + d as i64;
            }
        } else {
            return None;
        }
    }
    if !any_digit {
        return None;
    }
    let frac = match frac_digits {
        0 => 0,
        1 => frac_value * 10,
        _ => frac_value,
    };
    let hundredths = int_part * 100 + frac as i64;
    if hundredths > i32::MAX as i64 {
        return None;
    }
    Some(hundredths as i32)
}

/// Format a weight with up to two decimal places, trimming trailing zeros:
/// 70.0 -> "70", 70.4 -> "70.4", 69.8531 -> "69.85".
pub fn format_weight(value: f64) -> String {
    let hundredths = kotlin_round_to_long(value * 100.0);
    let int_part = hundredths / 100;
    let frac = (hundredths % 100).abs();
    match frac {
        0 => int_part.to_string(),
        _ if frac % 10 == 0 => format!("{}.{}", int_part, frac / 10),
        _ if frac < 10 => format!("{}.0{}", int_part, frac),
        _ => format!("{}.{}", int_part, frac),
    }
}

/// Formats a stored-kilograms double the way the CSV encoder needs it:
/// Java/Kotlin `Double.toString` semantics (shortest round-trip form, but
/// always with a decimal point for whole numbers: 70.0, never "70").
pub(crate) fn format_kg(value: f64) -> String {
    let s = format!("{}", value);
    if s.contains('.') || s.contains("inf") || s.contains("NaN") {
        s
    } else {
        format!("{}.0", s)
    }
}

/// Validation applies to the **canonical stored value**: in LB mode the typed
/// value is converted to kg and rounded to hundredths first, so exactly what
/// would be stored is validated.
pub fn is_canonical_weight_in_range(weight_kg: f64) -> bool {
    let rounded = round_to_hundredths(weight_kg);
    rounded.is_finite() && (MIN_WEIGHT_KG..=MAX_WEIGHT_KG).contains(&rounded)
}

/// Convert integer hundredths between units using the exact factor.
pub fn hundredths_lb_to_kg(hundredths_lb: i32) -> f64 {
    (hundredths_lb as f64 / 100.0) * KG_PER_LB
}

pub fn kg_to_hundredths_lb(weight_kg: f64) -> i32 {
    kotlin_round_to_long(weight_kg / KG_PER_LB * 100.0) as i32
}

/// Result of validating a weight entry (used by the input modal).
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WeightCheck {
    pub valid: bool,
    pub canonical_kg: f64,
}

/// Validate a typed value in the given unit against the canonical kg range.
pub fn check_weight(typed_hundredths: i32, unit_is_lb: bool) -> WeightCheck {
    let kg = if unit_is_lb {
        hundredths_lb_to_kg(typed_hundredths)
    } else {
        typed_hundredths as f64 / 100.0
    };
    let canonical = round_to_hundredths(kg);
    WeightCheck {
        valid: is_canonical_weight_in_range(canonical),
        canonical_kg: canonical,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_hundredths_accepts_plain_integers() {
        assert_eq!(parse_hundredths("70"), Some(7000));
        assert_eq!(parse_hundredths("  70  "), Some(7000)); // trimmed
    }

    #[test]
    fn parse_hundredths_accepts_decimals_and_trailing_dot() {
        assert_eq!(parse_hundredths("70.4"), Some(7040));
        assert_eq!(parse_hundredths("70.40"), Some(7040));
        assert_eq!(parse_hundredths("70."), Some(7000));
        assert_eq!(parse_hundredths(".5"), Some(50));
    }

    #[test]
    fn parse_hundredths_rejects_garbage() {
        assert_eq!(parse_hundredths(""), None);
        assert_eq!(parse_hundredths("   "), None);
        assert_eq!(parse_hundredths("7o"), None);
        assert_eq!(parse_hundredths("1.2.3"), None);
        assert_eq!(parse_hundredths("1.234"), None);
        assert_eq!(parse_hundredths("—"), None);
    }

    #[test]
    fn parse_hundredths_rejects_overflow() {
        // > i32::MAX hundredths — cannot represent.
        assert_eq!(parse_hundredths("999999999999"), None);
    }

    #[test]
    fn format_weight_trims_zeros() {
        assert_eq!(format_weight(70.0), "70");
        assert_eq!(format_weight(70.4), "70.4");
        assert_eq!(format_weight(69.8531), "69.85");
        assert_eq!(format_weight(70.05), "70.05");
    }

    #[test]
    fn format_kg_matches_java_double_to_string() {
        assert_eq!(format_kg(70.0), "70.0");
        assert_eq!(format_kg(70.4), "70.4");
        assert_eq!(format_kg(81.0), "81.0");
        assert_eq!(format_kg(69.85), "69.85");
        assert_eq!(format_kg(1000.0), "1000.0");
        assert_eq!(format_kg(0.1), "0.1");
    }

    #[test]
    fn round_to_hundredths_kotlin_tie_semantics() {
        // Kotlin roundToLong ties toward +inf: 2.5 -> 3, -2.5 -> -2.
        assert_eq!(round_to_hundredths(70.005), 70.01);
        assert_eq!(round_to_hundredths(-2.505), -2.5);
    }

    #[test]
    fn lb_kg_round_trip_is_exact_factor() {
        // 70 kg -> 154.32 lb (integer hundredths), back to 70.0 kg.
        let lb = kg_to_hundredths_lb(70.0);
        assert_eq!(lb, 15432);
        assert_eq!(round_to_hundredths(hundredths_lb_to_kg(lb)), 70.0);
    }

    #[test]
    fn canonical_validation_uses_stored_value() {
        // 2204.63 lb -> 1000.00 kg -> valid; 2204.64 -> 1000.01 -> invalid.
        assert!(check_weight(220463, true).valid);
        assert!(!check_weight(220464, true).valid);
        // kg window itself
        assert!(check_weight(10, false).valid); // 0.10 kg
        assert!(!check_weight(9, false).valid); // 0.09 kg
        assert!(check_weight(100000, false).valid); // 1000.00 kg
        assert!(!check_weight(100001, false).valid);
    }
}
