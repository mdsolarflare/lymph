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
    pub const MAX_BRISTOL_TYPE: i32 = 7;
    pub const MIN_EFFORT_LEVEL: i32 = 1;
    pub const MAX_EFFORT_LEVEL: i32 = 5;
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

/// Bowel movement record (Bristol Stool Scale + effort).
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
