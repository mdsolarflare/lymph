//! CSV encode/decode for biometric records — a faithful port of the Kotlin
//! `CsvEncoder`/`CsvDecoder`. The published header constants are the schema
//! contract (docs/DATA-EXCHANGE.md); a header mismatch fails the table.
//!
//! Rules: RFC 4180 quoting (last column always quoted, `""` escapes), LF on
//! export, CRLF + BOM tolerated on import, blank lines ignored. Row-level
//! problems are counted in `invalid_rows`, never thrown.

use crate::entity::{ranges, BowelRecord, HydrateRecord, PeriodRecord, WeightRecord};
use crate::volume::{format_volume_ml, MAX_VOLUME_ML, MIN_VOLUME_ML};
use crate::weight::{format_kg, MAX_WEIGHT_KG, MIN_WEIGHT_KG};

/// CSV header for weight records (schema contract).
pub const WEIGHT_HEADER: &str = "id,timestamp,weightKg,note";
/// CSV header for period records (schema contract).
pub const PERIOD_HEADER: &str = "id,timestamp,durationDays,flowIntensity,symptoms";
/// CSV header for bowel records (schema contract).
pub const BOWEL_HEADER: &str = "id,timestamp,bristolType,effortLevel,note";

/// CSV header for hydrate records (schema contract).
pub const HYDRATE_HEADER: &str = "id,timestamp,volumeMl,note";

/// Parsed result for one table: valid records plus the number of rejected rows.
pub struct DecodedTable<T> {
    pub records: Vec<T>,
    pub invalid_rows: usize,
}

/// Thrown-equivalent when a CSV's header line does not match the schema.
/// `message` is safe to show in the UI as-is.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CsvSchemaError {
    pub expected_header: String,
    pub actual_header: Option<String>,
}

impl CsvSchemaError {
    pub fn message(&self) -> String {
        match &self.actual_header {
            Some(actual) => format!(
                "header mismatch — expected [{}], got [{}]",
                self.expected_header, actual
            ),
            None => format!(
                "header mismatch — expected [{}], got [<empty>]",
                self.expected_header
            ),
        }
    }
}

fn quote(note: &Option<String>) -> String {
    match note {
        Some(n) => format!("\"{}\"", n.replace('"', "\"\"")),
        None => "\"\"".to_string(),
    }
}

pub fn encode_weight_records(records: &[WeightRecord]) -> String {
    let mut s = String::from(WEIGHT_HEADER);
    s.push('\n');
    for r in records {
        s.push_str(&format!(
            "{},{},{},{}\n",
            r.id,
            r.timestamp,
            format_kg(r.weight_kg),
            quote(&r.note)
        ));
    }
    s
}

pub fn encode_period_records(records: &[PeriodRecord]) -> String {
    let mut s = String::from(PERIOD_HEADER);
    s.push('\n');
    for r in records {
        s.push_str(&format!(
            "{},{},{},{},{}\n",
            r.id,
            r.timestamp,
            r.duration_days,
            r.flow_intensity,
            quote(&r.symptoms)
        ));
    }
    s
}

pub fn encode_bowel_records(records: &[BowelRecord]) -> String {
    let mut s = String::from(BOWEL_HEADER);
    s.push('\n');
    for r in records {
        s.push_str(&format!(
            "{},{},{},{},{}\n",
            r.id,
            r.timestamp,
            r.bristol_type,
            r.effort_level,
            quote(&r.note)
        ));
    }
    s
}

pub fn encode_hydrate_records(records: &[HydrateRecord]) -> String {
    let mut s = String::from(HYDRATE_HEADER);
    s.push('\n');
    for r in records {
        s.push_str(&format!(
            "{},{},{},{}\n",
            r.id,
            r.timestamp,
            format_volume_ml(r.volume_ml),
            quote(&r.note)
        ));
    }
    s
}

/// Splits CSV text into data lines: BOM stripped, CRLF tolerated, blank lines dropped.
fn data_lines(csv: &str) -> Vec<&str> {
    let text = csv.strip_prefix('\u{FEFF}').unwrap_or(csv);
    text.split('\n')
        .map(|l| l.strip_suffix('\r').unwrap_or(l))
        .filter(|l| !l.trim().is_empty())
        .collect()
}

/// Splits one CSV line into fields per RFC 4180 (quotes, embedded commas, `""` escapes).
fn split_csv_line(line: &str) -> Vec<String> {
    let mut fields: Vec<String> = Vec::new();
    let mut field = String::new();
    let mut in_quotes = false;
    let chars: Vec<char> = line.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        match chars[i] {
            '"' => {
                if in_quotes && i + 1 < chars.len() && chars[i + 1] == '"' {
                    field.push('"');
                    i += 1; // escaped quote
                } else {
                    in_quotes = !in_quotes;
                }
            }
            ',' => {
                if !in_quotes {
                    fields.push(std::mem::take(&mut field));
                } else {
                    field.push(',');
                }
            }
            c => field.push(c),
        }
        i += 1;
    }
    fields.push(field);
    fields
}

fn decode_table<T>(
    csv: &str,
    expected_header: &str,
    parse_row: impl Fn(&[String]) -> Option<T>,
) -> Result<DecodedTable<T>, CsvSchemaError> {
    let lines = data_lines(csv);
    let expected: Vec<String> = expected_header.split(',').map(String::from).collect();
    if lines.is_empty() {
        return Err(CsvSchemaError {
            expected_header: expected_header.to_string(),
            actual_header: None,
        });
    }
    let header_line = lines[0];
    if split_csv_line(header_line) != expected {
        return Err(CsvSchemaError {
            expected_header: expected_header.to_string(),
            actual_header: Some(header_line.to_string()),
        });
    }

    let mut records = Vec::new();
    let mut invalid = 0usize;
    for line in lines.into_iter().skip(1) {
        match parse_row(&split_csv_line(line)) {
            Some(r) => records.push(r),
            None => invalid += 1,
        }
    }
    Ok(DecodedTable {
        records,
        invalid_rows: invalid,
    })
}

pub fn decode_weight_records(csv: &str) -> Result<DecodedTable<WeightRecord>, CsvSchemaError> {
    decode_table(csv, WEIGHT_HEADER, |f| {
        if f.len() != 4 {
            return None;
        }
        let id = f[0].trim();
        if id.is_empty() {
            return None;
        }
        let timestamp: i64 = f[1].parse().ok()?;
        if timestamp <= 0 {
            return None;
        }
        let weight_kg: f64 = f[2].parse().ok()?;
        if !weight_kg.is_finite() || !(MIN_WEIGHT_KG..=MAX_WEIGHT_KG).contains(&weight_kg) {
            return None;
        }
        Some(WeightRecord {
            id: id.to_string(),
            timestamp,
            weight_kg,
            note: empty_to_none(&f[3]),
        })
    })
}

pub fn decode_period_records(csv: &str) -> Result<DecodedTable<PeriodRecord>, CsvSchemaError> {
    decode_table(csv, PERIOD_HEADER, |f| {
        if f.len() != 5 {
            return None;
        }
        let id = f[0].trim();
        if id.is_empty() {
            return None;
        }
        let timestamp: i64 = f[1].parse().ok()?;
        if timestamp <= 0 {
            return None;
        }
        let duration_days: i32 = f[2].parse().ok()?;
        let flow_intensity: i32 = f[3].parse().ok()?;
        if !(1..=14).contains(&duration_days) || !(1..=4).contains(&flow_intensity) {
            return None;
        }
        Some(PeriodRecord {
            id: id.to_string(),
            timestamp,
            duration_days,
            flow_intensity,
            symptoms: empty_to_none(&f[4]),
        })
    })
}

pub fn decode_bowel_records(csv: &str) -> Result<DecodedTable<BowelRecord>, CsvSchemaError> {
    decode_table(csv, BOWEL_HEADER, |f| {
        if f.len() != 5 {
            return None;
        }
        let id = f[0].trim();
        if id.is_empty() {
            return None;
        }
        let timestamp: i64 = f[1].parse().ok()?;
        if timestamp <= 0 {
            return None;
        }
        let bristol_type: i32 = f[2].parse().ok()?;
        let effort_level: i32 = f[3].parse().ok()?;
        if !(ranges::MIN_BRISTOL_TYPE..=ranges::MAX_BRISTOL_TYPE).contains(&bristol_type)
            || !(ranges::MIN_EFFORT_LEVEL..=ranges::MAX_EFFORT_LEVEL).contains(&effort_level)
        {
            return None;
        }
        Some(BowelRecord {
            id: id.to_string(),
            timestamp,
            bristol_type,
            effort_level,
            note: empty_to_none(&f[4]),
        })
    })
}

pub fn decode_hydrate_records(csv: &str) -> Result<DecodedTable<HydrateRecord>, CsvSchemaError> {
    decode_table(csv, HYDRATE_HEADER, |f| {
        if f.len() != 4 {
            return None;
        }
        let id = f[0].trim();
        if id.is_empty() {
            return None;
        }
        let timestamp: i64 = f[1].parse().ok()?;
        if timestamp <= 0 {
            return None;
        }
        // Same canonical range the input modal enforces (see volume.rs).
        let volume_ml: f64 = f[2].parse().ok()?;
        if !volume_ml.is_finite() || !(MIN_VOLUME_ML..=MAX_VOLUME_ML).contains(&volume_ml) {
            return None;
        }
        Some(HydrateRecord {
            id: id.to_string(),
            timestamp,
            volume_ml,
            note: empty_to_none(&f[3]),
        })
    })
}

fn empty_to_none(s: &str) -> Option<String> {
    if s.is_empty() {
        None
    } else {
        Some(s.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn weight(id: &str, ts: i64, kg: f64, note: Option<&str>) -> WeightRecord {
        WeightRecord {
            id: id.into(),
            timestamp: ts,
            weight_kg: kg,
            note: note.map(String::from),
        }
    }
    fn period(id: &str, ts: i64, days: i32, flow: i32, symptoms: Option<&str>) -> PeriodRecord {
        PeriodRecord {
            id: id.into(),
            timestamp: ts,
            duration_days: days,
            flow_intensity: flow,
            symptoms: symptoms.map(String::from),
        }
    }
    fn bowel(id: &str, ts: i64, bristol: i32, effort: i32, note: Option<&str>) -> BowelRecord {
        BowelRecord {
            id: id.into(),
            timestamp: ts,
            bristol_type: bristol,
            effort_level: effort,
            note: note.map(String::from),
        }
    }

    #[test]
    fn round_trip_weight() {
        let records = vec![
            weight("a", 1, 70.4, None),
            weight("b", 2, 69.85, Some("comma, in note")),
            weight("c", 3, 81.0, Some("quote \" inside")),
        ];
        let decoded = decode_weight_records(&encode_weight_records(&records)).unwrap();
        assert_eq!(decoded.records, records);
        assert_eq!(decoded.invalid_rows, 0);
    }

    #[test]
    fn round_trip_period() {
        let records = vec![
            period("a", 1, 5, 2, None),
            period("b", 2, 7, 4, Some("cramps, bloating")),
            period("c", 3, 3, 1, Some("mood \"low\"")),
        ];
        let decoded = decode_period_records(&encode_period_records(&records)).unwrap();
        assert_eq!(decoded.records, records);
        assert_eq!(decoded.invalid_rows, 0);
    }

    #[test]
    fn round_trip_bowel() {
        let records = vec![
            bowel("a", 1, 4, 3, None),
            bowel("b", 2, 5, 2, Some("straining, note")),
            bowel("c", 3, 2, 1, None),
        ];
        let decoded = decode_bowel_records(&encode_bowel_records(&records)).unwrap();
        assert_eq!(decoded.records, records);
        assert_eq!(decoded.invalid_rows, 0);
    }

    #[test]
    fn crlf_line_endings_accepted() {
        let csv = "id,timestamp,weightKg,note\r\na,100,70.4,\"\"\r\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 1);
        assert_eq!(decoded.records[0].id, "a");
    }

    #[test]
    fn bom_stripped() {
        let csv = "\u{FEFF}id,timestamp,weightKg,note\na,100,70.4,\"\"\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 1);
    }

    #[test]
    fn header_only_yields_zero_records() {
        let decoded = decode_weight_records(&format!("{}\n", WEIGHT_HEADER)).unwrap();
        assert!(decoded.records.is_empty());
        assert_eq!(decoded.invalid_rows, 0);
    }

    #[test]
    fn blank_lines_ignored() {
        let csv = "id,timestamp,weightKg,note\n\na,100,70.4,\"\"\n   \nb,200,71.0,\"\"\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 2);
        assert_eq!(decoded.invalid_rows, 0);
    }

    #[test]
    fn empty_file_is_schema_error() {
        assert!(decode_weight_records("").is_err());
    }

    #[test]
    fn wrong_header_order_is_schema_error() {
        let csv = "id,timestamp,note,weightKg\na,100,\"\",70.4\n";
        assert!(decode_weight_records(csv).is_err());
    }

    #[test]
    fn extra_header_column_is_schema_error() {
        let csv = "id,timestamp,weightKg,note,extra\na,100,70.4,\"\",x\n";
        assert!(decode_weight_records(csv).is_err());
    }

    #[test]
    fn wrong_field_count_counts_invalid() {
        let csv = "id,timestamp,weightKg,note\na,100,70.4\nb,200,71.0,\"\"\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 1);
        assert_eq!(decoded.records[0].id, "b");
        assert_eq!(decoded.invalid_rows, 1);
    }

    #[test]
    fn bad_timestamp_counts_invalid() {
        let csv = "id,timestamp,weightKg,note\na,notanumber,70.4,\"\"\nb,0,70.4,\"\"\nc,-5,70.4,\"\"\nd,100,70.4,\"\"\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 1);
        assert_eq!(decoded.records[0].id, "d");
        assert_eq!(decoded.invalid_rows, 3);
    }

    #[test]
    fn out_of_range_or_non_finite_weight_counts_invalid() {
        // NaN parses as a Double — the is_finite check must catch it.
        let csv =
            "id,timestamp,weightKg,note\na,100,0.05,\"\"\nb,200,1000.5,\"\"\nc,300,NaN,\"\"\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 0);
        assert_eq!(decoded.invalid_rows, 3);
    }

    #[test]
    fn boundary_weights_accepted() {
        let csv = "id,timestamp,weightKg,note\na,100,0.1,\"\"\nb,200,1000.0,\"\"\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 2);
        assert_eq!(decoded.invalid_rows, 0);
    }

    #[test]
    fn period_bounds_enforced() {
        let csv = "id,timestamp,durationDays,flowIntensity,symptoms\na,100,0,2,\"\"\nb,200,15,2,\"\"\nc,300,5,0,\"\"\nd,400,5,5,\"\"\ne,500,1,1,\"\"\n";
        let decoded = decode_period_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 1);
        assert_eq!(decoded.records[0].id, "e");
        assert_eq!(decoded.invalid_rows, 4);
    }

    #[test]
    fn bowel_bounds_enforced() {
        // Bristol-like 1-5, effort 1-3 (docs/SPEC.md §3; old 1-7/1-5 scales retired).
        let csv = "id,timestamp,bristolType,effortLevel,note\na,100,0,2,\"\"\nb,200,6,2,\"\"\nc,300,4,0,\"\"\nd,400,4,4,\"\"\ne,500,5,3,\"\"\n";
        let decoded = decode_bowel_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 1);
        assert_eq!(decoded.records[0].id, "e");
        assert_eq!(decoded.invalid_rows, 4);
    }

    #[test]
    fn bowel_boundary_values_accepted() {
        // Exactly the scale edges are valid: bristol 1 and 5, effort 1 and 3.
        let csv = "id,timestamp,bristolType,effortLevel,note\na,100,1,1,\"\"\nb,200,5,3,\"\"\n";
        let decoded = decode_bowel_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 2);
        assert_eq!(decoded.invalid_rows, 0);
    }

    #[test]
    fn empty_note_field_becomes_none() {
        let csv = "id,timestamp,weightKg,note\na,100,70.4,\"\"\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert!(decoded.records[0].note.is_none());
    }

    #[test]
    fn empty_id_counts_invalid() {
        let csv = "id,timestamp,weightKg,note\n,100,70.4,\"\"\n";
        let decoded = decode_weight_records(csv).unwrap();
        assert_eq!(decoded.records.len(), 0);
        assert_eq!(decoded.invalid_rows, 1);
    }

    #[test]
    fn encoder_quotes_notes_and_doubles_embedded_quotes() {
        let records = vec![weight("a", 1, 70.0, Some("say \"hi\", ok"))];
        let csv = encode_weight_records(&records);
        assert!(csv.contains("\"say \"\"hi\"\", ok\"\n"));
    }
}
