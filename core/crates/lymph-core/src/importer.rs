//! Import semantics — a faithful port of the Kotlin `DataImporter`.
//!
//! **Insert-only:** a record whose id already exists is skipped (counted as a
//! duplicate) and never modified — ids are UUIDs assigned at insert time, so
//! re-importing an old export is a no-op, not a data corruption path. The same
//! rule applies to duplicates within the archive (first occurrence wins).
//!
//! **Best-effort:** only structural problems with the archive itself abort the
//! run; per-table header mismatches and per-row validation failures are counted
//! and reported so nothing is silently lost.
//!
//! This module is pure: it takes the existing ids per table and returns the
//! records to insert. The caller performs the actual insertion — semantics are
//! identical as long as the caller inserts exactly what is returned, in order.

use crate::csv;
use crate::entity::{BiometricRecord, BowelRecord, HydrateRecord, PeriodRecord, WeightRecord};
use crate::zip;
use serde::Serialize;
use std::collections::HashSet;

/// Entry names recognized inside the archive (exact match, case-sensitive).
pub const WEIGHT_CSV: &str = "weight_records.csv";
pub const PERIOD_CSV: &str = "period_records.csv";
pub const BOWEL_CSV: &str = "bowel_records.csv";
pub const HYDRATE_CSV: &str = "hydrate_records.csv";

const KNOWN_TABLES: [&str; 4] = [WEIGHT_CSV, PERIOD_CSV, BOWEL_CSV, HYDRATE_CSV];

/// Expected failure of an import operation — `message` is safe to show in the UI as-is.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ImportException {
    pub message: String,
}

impl ImportException {
    pub fn new(message: &str) -> Self {
        Self {
            message: message.to_string(),
        }
    }
}

/// Per-table outcome of one import run.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TableImport {
    pub added: usize,
    pub skipped_duplicates: usize,
    pub invalid_rows: usize,
    /// Non-empty when the table could not be imported at all (e.g. header mismatch).
    pub error: Option<String>,
}

/// Aggregate outcome of one import run across all four tables.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
pub struct ImportResult {
    pub weight: TableImport,
    pub period: TableImport,
    pub bowel: TableImport,
    pub hydrate: TableImport,
}

impl ImportResult {
    pub fn total_added(&self) -> usize {
        self.weight.added + self.period.added + self.bowel.added + self.hydrate.added
    }
    pub fn total_skipped_duplicates(&self) -> usize {
        self.weight.skipped_duplicates
            + self.period.skipped_duplicates
            + self.bowel.skipped_duplicates
            + self.hydrate.skipped_duplicates
    }
    pub fn total_invalid_rows(&self) -> usize {
        self.weight.invalid_rows
            + self.period.invalid_rows
            + self.bowel.invalid_rows
            + self.hydrate.invalid_rows
    }

    /// Terminal-style one-line summary for the UI snackbar (exact strings, em dash).
    pub fn summary(&self) -> String {
        let mut s = format!("IMPORT OK — {} added", self.total_added());
        if self.total_skipped_duplicates() > 0 {
            s += &format!(", {} duplicates", self.total_skipped_duplicates());
        }
        if self.total_invalid_rows() > 0 {
            s += &format!(", {} invalid", self.total_invalid_rows());
        }
        let errors = [
            self.weight.error.as_ref().map(|e| format!("weight: {e}")),
            self.period.error.as_ref().map(|e| format!("period: {e}")),
            self.bowel.error.as_ref().map(|e| format!("bowel: {e}")),
            self.hydrate.error.as_ref().map(|e| format!("hydrate: {e}")),
        ];
        let errors: Vec<String> = errors.into_iter().flatten().collect();
        if !errors.is_empty() {
            s += &format!(" | {}", errors.join("; "));
        }
        s
    }
}

/// Everything the caller needs after an import: the summary counts plus the
/// exact records to insert (in archive order).
#[derive(Debug, Clone, Default)]
pub struct ImportOutcome {
    pub result: ImportResult,
    pub weight_to_insert: Vec<WeightRecord>,
    pub period_to_insert: Vec<PeriodRecord>,
    pub bowel_to_insert: Vec<BowelRecord>,
    pub hydrate_to_insert: Vec<HydrateRecord>,
}

/// Imports a lymph export archive (ZIP of CSVs).
///
/// `*_existing_ids` are the ids already in each table **before** the import —
/// they must be snapshotted before calling, not read lazily during insertion.
pub fn import_zip(
    zip_bytes: &[u8],
    weight_existing_ids: &[String],
    period_existing_ids: &[String],
    bowel_existing_ids: &[String],
    hydrate_existing_ids: &[String],
) -> Result<ImportOutcome, ImportException> {
    let entries = zip::decode(zip_bytes)?; // Err on structural problems
    if !entries
        .iter()
        .any(|(name, _)| KNOWN_TABLES.contains(&name.as_str()))
    {
        return Err(ImportException::new(&format!(
            "no recognizable lymph CSVs found — expected {WEIGHT_CSV} / {PERIOD_CSV} / {BOWEL_CSV} / {HYDRATE_CSV}"
        )));
    }

    let find = |name: &str| {
        entries
            .iter()
            .find(|(n, _)| n == name)
            .map(|(_, content)| content.as_str())
    };

    let (weight, weight_to_insert) = import_table(
        find(WEIGHT_CSV),
        csv::decode_weight_records,
        weight_existing_ids,
    );
    let (period, period_to_insert) = import_table(
        find(PERIOD_CSV),
        csv::decode_period_records,
        period_existing_ids,
    );
    let (bowel, bowel_to_insert) = import_table(
        find(BOWEL_CSV),
        csv::decode_bowel_records,
        bowel_existing_ids,
    );
    let (hydrate, hydrate_to_insert) = import_table(
        find(HYDRATE_CSV),
        csv::decode_hydrate_records,
        hydrate_existing_ids,
    );

    Ok(ImportOutcome {
        result: ImportResult {
            weight,
            period,
            bowel,
            hydrate,
        },
        weight_to_insert,
        period_to_insert,
        bowel_to_insert,
        hydrate_to_insert,
    })
}

/// Imports one table: decodes the CSV (header mismatch → `error`), then selects
/// records whose id is not already present — neither in the database nor
/// earlier in this same archive. Returns (counts, records to insert).
fn import_table<T: BiometricRecord + Clone>(
    csv_text: Option<&str>,
    decode: fn(&str) -> Result<csv::DecodedTable<T>, csv::CsvSchemaError>,
    existing_ids: &[String],
) -> (TableImport, Vec<T>) {
    let Some(csv_text) = csv_text else {
        return (TableImport::default(), Vec::new()); // table absent — nothing to do
    };

    let decoded = match decode(csv_text) {
        Ok(d) => d,
        Err(e) => {
            return (
                TableImport {
                    error: Some(e.message()),
                    ..Default::default()
                },
                Vec::new(),
            )
        }
    };

    let mut seen: HashSet<&str> = existing_ids.iter().map(|s| s.as_str()).collect();
    // The records borrow from decoded — but ids must be compared against existing too.
    // Collect insertion indices first, then map them out.
    let mut to_insert_idx = Vec::new();
    let mut added = 0usize;
    let mut skipped = 0usize;
    for (i, record) in decoded.records.iter().enumerate() {
        if seen.insert(BiometricRecord::id(record)) {
            to_insert_idx.push(i);
            added += 1;
        } else {
            skipped += 1;
        }
    }

    let records = decoded.records;
    let to_insert = to_insert_idx
        .into_iter()
        .map(|i| records[i].clone())
        .collect();
    (
        TableImport {
            added,
            skipped_duplicates: skipped,
            invalid_rows: decoded.invalid_rows,
            error: None,
        },
        to_insert,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::csv::{
        encode_bowel_records, encode_hydrate_records, encode_period_records, encode_weight_records,
    };

    fn w(id: &str, ts: i64) -> WeightRecord {
        WeightRecord {
            id: id.into(),
            timestamp: ts,
            weight_kg: 70.0,
            note: None,
        }
    }
    fn p(id: &str, ts: i64) -> PeriodRecord {
        PeriodRecord {
            id: id.into(),
            timestamp: ts,
            duration_days: 5,
            flow_intensity: 2,
            symptoms: None,
        }
    }
    fn b(id: &str, ts: i64) -> BowelRecord {
        BowelRecord {
            id: id.into(),
            timestamp: ts,
            bristol_type: 4,
            effort_level: 3,
            note: None,
        }
    }
    #[allow(dead_code)] // parity helper; hydrate-specific tests build their own
    fn h(id: &str, ts: i64) -> HydrateRecord {
        HydrateRecord {
            id: id.into(),
            timestamp: ts,
            volume_ml: 250.0,
            note: None,
        }
    }
    fn ids(records: &[WeightRecord]) -> Vec<String> {
        records.iter().map(|r| r.id.clone()).collect()
    }

    fn zip_of(
        weights: &[WeightRecord],
        periods: &[PeriodRecord],
        bowels: &[BowelRecord],
    ) -> Vec<u8> {
        zip::build_zip(&[
            (WEIGHT_CSV, &encode_weight_records(weights)),
            (PERIOD_CSV, &encode_period_records(periods)),
            (BOWEL_CSV, &encode_bowel_records(bowels)),
            (HYDRATE_CSV, &encode_hydrate_records(&[])),
        ])
    }

    #[test]
    fn fresh_import_adds_all_tables() {
        let zip = zip_of(&[w("w1", 1), w("w2", 2)], &[p("p1", 1)], &[b("b1", 1)]);
        let outcome = import_zip(&zip, &[], &[], &[], &[]).unwrap();
        assert_eq!(outcome.result.total_added(), 4);
        assert_eq!(outcome.result.total_skipped_duplicates(), 0);
        assert_eq!(outcome.result.total_invalid_rows(), 0);
        assert_eq!(
            outcome
                .weight_to_insert
                .iter()
                .map(|r| r.id.clone())
                .collect::<Vec<_>>(),
            vec!["w1", "w2"]
        );
        assert_eq!(outcome.period_to_insert.len(), 1);
        assert_eq!(outcome.bowel_to_insert.len(), 1);
    }

    #[test]
    fn reimport_skips_all_as_duplicates() {
        let zip = zip_of(&[w("w1", 1), w("w2", 2)], &[p("p1", 1)], &[]);
        let first = import_zip(&zip, &[], &[], &[], &[]).unwrap();
        let second = import_zip(
            &zip,
            &ids(&first.weight_to_insert),
            &first
                .period_to_insert
                .iter()
                .map(|r| r.id.clone())
                .collect::<Vec<_>>(),
            &[],
            &[],
        )
        .unwrap();
        assert_eq!(second.result.total_added(), 0);
        assert_eq!(second.result.total_skipped_duplicates(), 3);
        assert!(second.weight_to_insert.is_empty());
    }

    #[test]
    fn partial_overlap_skips_only_existing() {
        let zip = zip_of(&[w("w1", 1), w("w2", 2)], &[], &[]);
        let outcome = import_zip(&zip, &["w1".to_string()], &[], &[], &[]).unwrap();
        assert_eq!(outcome.result.weight.added, 1);
        assert_eq!(outcome.result.weight.skipped_duplicates, 1);
        assert_eq!(
            outcome
                .weight_to_insert
                .iter()
                .map(|r| r.id.clone())
                .collect::<Vec<_>>(),
            vec!["w2"]
        );
    }

    #[test]
    fn in_batch_duplicates_first_occurrence_wins() {
        let csv_text = "id,timestamp,weightKg,note\ndup,100,70.0,\"\"\ndup,200,80.0,\"\"\n";
        let zip = zip::build_zip(&[(WEIGHT_CSV, csv_text)]);
        let outcome = import_zip(&zip, &[], &[], &[], &[]).unwrap();
        assert_eq!(outcome.result.weight.added, 1);
        assert_eq!(outcome.result.weight.skipped_duplicates, 1);
        assert_eq!(outcome.weight_to_insert.len(), 1);
        assert_eq!(outcome.weight_to_insert[0].timestamp, 100); // first row wins
    }

    #[test]
    fn missing_table_yields_zero_counts_no_error() {
        let zip = zip::build_zip(&[(WEIGHT_CSV, &encode_weight_records(&[w("w1", 1)]))]);
        let outcome = import_zip(&zip, &[], &[], &[], &[]).unwrap();
        assert_eq!(outcome.result.weight.added, 1);
        assert_eq!(outcome.result.period.error, None);
        assert_eq!(outcome.result.period, TableImport::default());
        assert_eq!(outcome.result.bowel, TableImport::default());
    }

    #[test]
    fn invalid_rows_counted_not_inserted() {
        let csv_text = "id,timestamp,weightKg,note\nok1,100,70.4,\"\"\n,200,71.0,\"\"\nok2,300,999999.0,\"\"\nok3,400,72.0,\"\"\n";
        let zip = zip::build_zip(&[(WEIGHT_CSV, csv_text)]);
        let outcome = import_zip(&zip, &[], &[], &[], &[]).unwrap();
        assert_eq!(outcome.result.weight.added, 2);
        assert_eq!(outcome.result.weight.invalid_rows, 2);
        assert_eq!(
            outcome
                .weight_to_insert
                .iter()
                .map(|r| r.id.clone())
                .collect::<Vec<_>>(),
            vec!["ok1", "ok3"]
        );
    }

    #[test]
    fn bad_header_on_one_table_does_not_block_others() {
        let zip = zip::build_zip(&[
            (WEIGHT_CSV, "id,timestamp,WRONG,note\nw1,100,70.0,\"\"\n"),
            (PERIOD_CSV, &encode_period_records(&[p("p1", 1)])),
            (BOWEL_CSV, &encode_bowel_records(&[b("b1", 1)])),
        ]);
        let outcome = import_zip(&zip, &[], &[], &[], &[]).unwrap();
        assert!(outcome.result.weight.error.is_some());
        assert_eq!(outcome.result.weight.added, 0);
        assert_eq!(outcome.result.period.added, 1);
        assert_eq!(outcome.result.bowel.added, 1);
    }

    #[test]
    fn archive_without_known_tables_throws() {
        let zip = zip::build_zip(&[("random.csv", "x,y\n1,2\n")]);
        assert!(import_zip(&zip, &[], &[], &[], &[]).is_err());
    }

    #[test]
    fn not_a_zip_throws() {
        assert!(import_zip(b"nope, not a zip", &[], &[], &[], &[]).is_err());
    }

    #[test]
    fn summary_formats_counts() {
        let clean = ImportResult {
            weight: TableImport {
                added: 2,
                ..Default::default()
            },
            ..Default::default()
        };
        assert_eq!(clean.summary(), "IMPORT OK — 2 added");

        let mixed = ImportResult {
            weight: TableImport {
                added: 2,
                skipped_duplicates: 1,
                ..Default::default()
            },
            period: TableImport {
                invalid_rows: 3,
                ..Default::default()
            },
            ..Default::default()
        };
        assert_eq!(
            mixed.summary(),
            "IMPORT OK — 2 added, 1 duplicates, 3 invalid"
        );

        let with_error = ImportResult {
            weight: TableImport {
                error: Some("header mismatch".into()),
                ..Default::default()
            },
            period: TableImport {
                added: 1,
                ..Default::default()
            },
            ..Default::default()
        };
        assert!(with_error.summary().contains("weight: header mismatch"));
    }
}

#[cfg(test)]
mod hydrate_tests {
    use super::*;
    use crate::csv::encode_hydrate_records;

    fn h(id: &str, ts: i64, ml: f64) -> HydrateRecord {
        HydrateRecord {
            id: id.into(),
            timestamp: ts,
            volume_ml: ml,
            note: None,
        }
    }

    /// Hydrate-only archive: importable on its own, other tables untouched.
    #[test]
    fn hydrate_only_archive_imports() {
        let records = vec![h("h1", 1, 250.0), h("h2", 2, 1000.0)];
        let zip = zip::build_zip(&[(HYDRATE_CSV, &encode_hydrate_records(&records))]);
        let outcome = import_zip(&zip, &[], &[], &[], &[]).unwrap();
        assert_eq!(outcome.result.total_added(), 2);
        assert_eq!(outcome.hydrate_to_insert.len(), 2);
        assert_eq!(outcome.result.hydrate.added, 2);
        assert!(outcome.weight_to_insert.is_empty());
        // Insert-only: re-import counts all as duplicates.
        let second = import_zip(
            &zip,
            &[],
            &[],
            &[],
            &outcome
                .hydrate_to_insert
                .iter()
                .map(|r| r.id.clone())
                .collect::<Vec<_>>(),
        )
        .unwrap();
        assert_eq!(second.result.total_added(), 0);
        assert_eq!(second.result.total_skipped_duplicates(), 2);
    }

    /// Out-of-range volumes are counted invalid, not imported.
    #[test]
    fn hydrate_invalid_rows_counted() {
        let csv_text =
            "id,timestamp,volumeMl,note\nok1,100,250.0,\"\"\nbad1,200,0.5,\"\"\nbad2,300,20000.0,\"\"\nok2,400,29.5735295625,\"\"\n";
        let zip = zip::build_zip(&[(HYDRATE_CSV, csv_text)]);
        let outcome = import_zip(&zip, &[], &[], &[], &[]).unwrap();
        assert_eq!(outcome.result.hydrate.added, 2);
        assert_eq!(outcome.result.hydrate.invalid_rows, 2);
        assert_eq!(
            outcome
                .hydrate_to_insert
                .iter()
                .map(|r| r.id.clone())
                .collect::<Vec<_>>(),
            vec!["ok1", "ok2"]
        );
    }
}
