//! Hydration volume math — mirrors the weight discipline exactly: values are
//! held/typed as integer hundredths of the SELECTED unit (no float drift),
//! stored as canonical milliliters, and validation applies to the canonical
//! stored value (what would actually be saved).
//!
//! Units: L (liters, metric) and OZ (US fluid ounces).
//! 1 US fl oz = 29.5735295625 ml EXACTLY (1/128 of the 3.785411784 L US
//! gallon) — the volume analog of weight's exact lb factor.

pub use crate::entity::ranges::{MAX_VOLUME_ML, MIN_VOLUME_ML};
use crate::weight::round_to_hundredths;
use serde::{Deserialize, Serialize};

/// Exact conversion: 1 US fluid ounce = 29.5735295625 ml (by definition).
pub const ML_PER_OZ: f64 = 29.5735295625;

/// Result of validating a hydration entry (used by the input modal).
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VolumeCheck {
    pub valid: bool,
    pub canonical_ml: f64,
}

/// Integer hundredths of a liter → canonical ml.
pub fn hundredths_l_to_ml(hundredths_l: i32) -> f64 {
    hundredths_l as f64 / 100.0 * 1000.0
}

/// Integer hundredths of a US fluid ounce → canonical ml (exact factor).
pub fn hundredths_oz_to_ml(hundredths_oz: i32) -> f64 {
    hundredths_oz as f64 / 100.0 * ML_PER_OZ
}

/// Canonical ml → integer hundredths of a liter (for display/editing in L).
pub fn ml_to_hundredths_l(ml: f64) -> i32 {
    crate::weight::kotlin_round_to_long(ml / 10.0) as i32
}

/// Canonical ml → integer hundredths of an ounce (for display/editing in OZ).
pub fn ml_to_hundredths_oz(ml: f64) -> i32 {
    crate::weight::kotlin_round_to_long(ml / ML_PER_OZ * 100.0) as i32
}

/// Validation applies to the canonical stored value: 1 ml ≤ v ≤ 10000 ml.
pub fn is_canonical_volume_in_range(volume_ml: f64) -> bool {
    let rounded = round_to_hundredths(volume_ml);
    rounded.is_finite() && (MIN_VOLUME_ML..=MAX_VOLUME_ML).contains(&rounded)
}

/// Validate a typed value (integer hundredths of the selected unit) against
/// the canonical ml range — exactly what would be stored is what is checked.
pub fn check_volume(typed_hundredths: i32, unit_is_l: bool) -> VolumeCheck {
    let ml = if unit_is_l {
        hundredths_l_to_ml(typed_hundredths)
    } else {
        hundredths_oz_to_ml(typed_hundredths)
    };
    let canonical = round_to_hundredths(ml);
    VolumeCheck {
        valid: is_canonical_volume_in_range(canonical),
        canonical_ml: canonical,
    }
}

/// Canonical-ml display: up to two decimals, trailing zeros trimmed —
/// same shape as `formatWeight` (250.0 → "250", 29.5735… → "29.57").
pub fn format_volume_ml(volume_ml: f64) -> String {
    crate::weight::format_weight(volume_ml)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn oz_ml_conversion_is_exact_factor() {
        // 8 fl oz = 236.5882365 ml exactly (8 × 29.5735295625).
        assert_eq!(hundredths_oz_to_ml(800), 236.5882365);
        // Round trip is stable at hundredth precision: 250 ml ↔ 845
        // hundredths-oz (8.45 oz) — converting back lands on the same step.
        let oz_h = ml_to_hundredths_oz(250.0);
        assert_eq!(oz_h, 845);
        assert_eq!(ml_to_hundredths_oz(hundredths_oz_to_ml(oz_h)), oz_h);
        // Monotonic: more ml → at least as many oz hundredths.
        assert!(ml_to_hundredths_oz(500.0) > oz_h);
    }

    #[test]
    fn liter_mode_hundredths_map_cleanly() {
        // 0.25 L = 250 ml; 1.5 L = 1500 ml.
        assert_eq!(hundredths_l_to_ml(25), 250.0);
        assert_eq!(hundredths_l_to_ml(150), 1500.0);
        assert_eq!(ml_to_hundredths_l(250.0), 25);
        assert_eq!(ml_to_hundredths_l(10000.0), 1000);
    }

    #[test]
    fn canonical_validation_uses_stored_value() {
        // In-range canonical values (ml window: 1 – 10000).
        assert!(check_volume(25, true).valid); // 0.25 L = 250 ml
        assert!(check_volume(1000, true).valid); // 10 L = 10000 ml (max)
        assert!(check_volume(339, false).valid); // 3.39 oz ≈ 100.14 ml
                                                 // Out of range: 10.01 L over max; 0.009 L = 90 ml fine but 0.0005 L
                                                 // (5 ml) is valid too — the true minimum is 1 ml, unreachable in
                                                 // hundredth-of-L steps (0.01 L = 10 ml), but reachable in OZ? No:
                                                 // 0.01 oz = 0.2957 ml < 1 → invalid.
        assert!(!check_volume(1001, true).valid);
        assert!(!check_volume(1, false).valid); // 0.01 oz ≈ 0.3 ml
                                                // Boundary: exactly 1 oz ≈ 29.57 ml is valid.
        assert!(check_volume(100, false).valid);
    }

    #[test]
    fn format_volume_trims_zeros() {
        assert_eq!(format_volume_ml(250.0), "250");
        assert_eq!(format_volume_ml(500.0), "500");
        assert_eq!(format_volume_ml(29.5735295625), "29.57");
        assert_eq!(format_volume_ml(10000.0), "10000");
    }
}
