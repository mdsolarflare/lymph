//! Biometric record entities — plain data, no behavior.
//!
//! Contract (docs/SPEC.md §3): three record types, never merged into one
//! table. All records carry a UUID v4 string id assigned at insert and an
//! epoch-millisecond UTC timestamp.

use serde::{Deserialize, Serialize};

/// Common behavior of every biometric record — mirrors the Kotlin
/// `BiometricRecord` interface. Records are kept as separate structs (never
/// merged into one table), so this trait exists purely so generic machinery
/// (import) can read the id without caring which table it is serving.
pub trait BiometricRecord {
    fn id(&self) -> &str;
    fn timestamp(&self) -> i64;
}

impl BiometricRecord for WeightRecord {
    fn id(&self) -> &str {
        &self.id
    }
    fn timestamp(&self) -> i64 {
        self.timestamp
    }
}

impl BiometricRecord for PeriodRecord {
    fn id(&self) -> &str {
        &self.id
    }
    fn timestamp(&self) -> i64 {
        self.timestamp
    }
}

impl BiometricRecord for BowelRecord {
    fn id(&self) -> &str {
        &self.id
    }
    fn timestamp(&self) -> i64 {
        self.timestamp
    }
}

/// Validation ranges shared by the input modal and CSV import — one source of
/// truth so UI and import can never disagree.
pub mod ranges {
    pub const MIN_WEIGHT_KG: f64 = 0.1;
    pub const MAX_WEIGHT_KG: f64 = 1000.0;
    pub const MIN_DURATION_DAYS: i32 = 1;
    pub const MAX_DURATION_DAYS: i32 = 14;
    pub const MIN_FLOW_INTENSITY: i32 = 1;
    pub const MAX_FLOW_INTENSITY: i32 = 4;
    pub const MIN_BRISTOL_TYPE: i32 = 1;
    pub const MAX_BRISTOL_TYPE: i32 = 5;
    pub const MIN_EFFORT_LEVEL: i32 = 1;
    pub const MAX_EFFORT_LEVEL: i32 = 3;
    pub const MIN_VOLUME_ML: f64 = 1.0;
    pub const MAX_VOLUME_ML: f64 = 10000.0;
}

/// Qualitative labels for the period flow-intensity scale, as presented in the
/// UI. Storage and CSV stay quantitative 1–4 (frozen contract); this maps
/// 1→NONE, 2→LIGHT, 3→MEDIUM, 4→HEAVY. UI-only presentation — callers must
/// never persist the label.
pub fn flow_intensity_label(v: i32) -> &'static str {
    match v {
        1 => "NONE",
        2 => "LIGHT",
        3 => "MEDIUM",
        4 => "HEAVY",
        _ => "",
    }
}

/// Qualitative labels for the bowel bristol-like scale, as presented in the
/// UI. Storage and CSV stay quantitative 1–5 (frozen column names); this maps
/// 1→"LIKE PEBBLES", 2→"HARD", 3→"PERFECT", 4→"MUSHY :(", 5→"WATER".
/// UI-only presentation — callers must never persist the label.
pub fn bristol_label(v: i32) -> &'static str {
    match v {
        1 => "LIKE PEBBLES",
        2 => "HARD",
        3 => "PERFECT",
        4 => "MUSHY :(",
        5 => "WATER",
        _ => "",
    }
}

/// Qualitative labels for the bowel effort scale, as presented in the UI.
/// Storage and CSV stay quantitative 1–3 (frozen column names); this maps
/// 1→"LOW", 2→"MEDIUM", 3→"HIGH". UI-only presentation — callers must never
/// persist the label.
pub fn effort_label(v: i32) -> &'static str {
    match v {
        1 => "LOW",
        2 => "MEDIUM",
        3 => "HIGH",
        _ => "",
    }
}

/// Weight measurement record. `weight_kg` is canonical kilograms.
/// serde-camelCase so the wasm/TS boundary speaks the same names as the old
/// Kotlin/JS app: `weightKg`, `durationDays`, `flowIntensity`, `bristolType`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WeightRecord {
    pub id: String,
    pub timestamp: i64,
    pub weight_kg: f64,
    #[serde(default)]
    pub note: Option<String>,
}

/// Menstrual period record.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PeriodRecord {
    pub id: String,
    pub timestamp: i64,
    pub duration_days: i32,
    pub flow_intensity: i32,
    #[serde(default)]
    pub symptoms: Option<String>,
}

/// Bowel movement record (bristol-like scale + effort).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BowelRecord {
    pub id: String,
    pub timestamp: i64,
    pub bristol_type: i32,
    pub effort_level: i32,
    #[serde(default)]
    pub note: Option<String>,
}

/// Hydration intake record. `volume_ml` is canonical milliliters — the
/// weightKg analog: UI displays/edits in L or OZ, storage stays metric.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HydrateRecord {
    pub id: String,
    pub timestamp: i64,
    pub volume_ml: f64,
    #[serde(default)]
    pub note: Option<String>,
}

impl WeightRecord {
    /// True when `weight_kg` is finite and inside the canonical range — the
    /// same rule the input modal and the CSV decoder enforce.
    pub fn is_valid(&self) -> bool {
        self.weight_kg.is_finite()
            && self.weight_kg >= ranges::MIN_WEIGHT_KG
            && self.weight_kg <= ranges::MAX_WEIGHT_KG
    }
}

impl PeriodRecord {
    pub fn is_valid(&self) -> bool {
        (ranges::MIN_DURATION_DAYS..=ranges::MAX_DURATION_DAYS).contains(&self.duration_days)
            && (ranges::MIN_FLOW_INTENSITY..=ranges::MAX_FLOW_INTENSITY)
                .contains(&self.flow_intensity)
    }
}

impl BowelRecord {
    pub fn is_valid(&self) -> bool {
        (ranges::MIN_BRISTOL_TYPE..=ranges::MAX_BRISTOL_TYPE).contains(&self.bristol_type)
            && (ranges::MIN_EFFORT_LEVEL..=ranges::MAX_EFFORT_LEVEL).contains(&self.effort_level)
    }
}

impl HydrateRecord {
    /// True when `volume_ml` is finite and inside the canonical range — the
    /// same rule the input modal and the CSV decoder enforce.
    pub fn is_valid(&self) -> bool {
        self.volume_ml.is_finite()
            && self.volume_ml >= ranges::MIN_VOLUME_ML
            && self.volume_ml <= ranges::MAX_VOLUME_ML
    }
}

impl BiometricRecord for HydrateRecord {
    fn id(&self) -> &str {
        &self.id
    }
    fn timestamp(&self) -> i64 {
        self.timestamp
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn flow_labels_map_the_full_scale() {
        // UI-only presentation mapping (docs/SPEC.md §3); storage stays 1-4.
        assert_eq!(flow_intensity_label(1), "NONE");
        assert_eq!(flow_intensity_label(2), "LIGHT");
        assert_eq!(flow_intensity_label(3), "MEDIUM");
        assert_eq!(flow_intensity_label(4), "HEAVY");
        // Out-of-range values render as empty string — never a wrong label.
        assert_eq!(flow_intensity_label(0), "");
        assert_eq!(flow_intensity_label(5), "");
    }

    #[test]
    fn flow_labels_cover_exactly_the_valid_range() {
        // Every valid stored value must have a non-empty label.
        for v in ranges::MIN_FLOW_INTENSITY..=ranges::MAX_FLOW_INTENSITY {
            assert!(!flow_intensity_label(v).is_empty(), "no label for {v}");
        }
    }

    #[test]
    fn bristol_labels_map_the_full_scale() {
        // UI-only presentation mapping (docs/SPEC.md §3); storage stays 1-5.
        assert_eq!(bristol_label(1), "LIKE PEBBLES");
        assert_eq!(bristol_label(2), "HARD");
        assert_eq!(bristol_label(3), "PERFECT");
        assert_eq!(bristol_label(4), "MUSHY :(");
        assert_eq!(bristol_label(5), "WATER");
        // Out-of-range values render as empty string — never a wrong label.
        assert_eq!(bristol_label(0), "");
        assert_eq!(bristol_label(6), "");
    }

    #[test]
    fn effort_labels_map_the_full_scale() {
        // UI-only presentation mapping (docs/SPEC.md §3); storage stays 1-3.
        assert_eq!(effort_label(1), "LOW");
        assert_eq!(effort_label(2), "MEDIUM");
        assert_eq!(effort_label(3), "HIGH");
        // Out-of-range values render as empty string — never a wrong label.
        assert_eq!(effort_label(0), "");
        assert_eq!(effort_label(4), "");
    }

    #[test]
    fn bowel_labels_cover_exactly_the_valid_ranges() {
        for v in ranges::MIN_BRISTOL_TYPE..=ranges::MAX_BRISTOL_TYPE {
            assert!(!bristol_label(v).is_empty(), "no bristol label for {v}");
        }
        for v in ranges::MIN_EFFORT_LEVEL..=ranges::MAX_EFFORT_LEVEL {
            assert!(!effort_label(v).is_empty(), "no effort label for {v}");
        }
    }
}
