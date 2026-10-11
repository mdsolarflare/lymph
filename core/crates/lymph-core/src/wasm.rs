//! wasm-bindgen surface for the frontend (Deno/TypeScript) — the only
//! platform-specific code in the crate, and only glue: parse JS values, call
//! the pure core, serialize back. serde-wasm-bindgen does the conversions;
//! entities are `#[serde(rename_all = "camelCase")]` so the boundary speaks the
//! same camelCase names as the old Kotlin/JS app (weightKg, durationDays, …).

use crate::chart::{map_multi_series, MultiSeries, Sample, TimeRange};
use crate::csv;
use crate::entity::{BowelRecord, HydrateRecord, PeriodRecord, WeightRecord};
use crate::importer::{self, ImportOutcome};
use crate::volume;
use crate::weight;
use serde::{Deserialize, Serialize};
use wasm_bindgen::prelude::*;

fn de<T: for<'a> Deserialize<'a>>(value: &JsValue) -> Result<T, JsError> {
    serde_wasm_bindgen::from_value(value.clone())
        .map_err(|e| JsError::new(&format!("invalid data: {e}")))
}

fn ser<T: Serialize>(value: &T) -> Result<JsValue, JsError> {
    serde_wasm_bindgen::to_value(value).map_err(|e| JsError::new(&format!("serialize: {e}")))
}

// ── Table CSV (localStorage mirror uses the same format as the export ZIP) ──

#[wasm_bindgen(js_name = "encodeWeightCsv")]
pub fn encode_weight_csv(records: JsValue) -> Result<String, JsError> {
    Ok(csv::encode_weight_records(&de::<Vec<WeightRecord>>(
        &records,
    )?))
}

#[wasm_bindgen(js_name = "encodePeriodCsv")]
pub fn encode_period_csv(records: JsValue) -> Result<String, JsError> {
    Ok(csv::encode_period_records(&de::<Vec<PeriodRecord>>(
        &records,
    )?))
}

#[wasm_bindgen(js_name = "encodeBowelCsv")]
pub fn encode_bowel_csv(records: JsValue) -> Result<String, JsError> {
    Ok(csv::encode_bowel_records(&de::<Vec<BowelRecord>>(
        &records,
    )?))
}

#[wasm_bindgen(js_name = "encodeHydrateCsv")]
pub fn encode_hydrate_csv(records: JsValue) -> Result<String, JsError> {
    Ok(csv::encode_hydrate_records(&de::<Vec<HydrateRecord>>(
        &records,
    )?))
}

#[derive(Serialize)]
struct JsDecodedTable<T> {
    records: Vec<T>,
    #[serde(rename = "invalidRows")]
    invalid_rows: usize,
}

fn decoded<T: Serialize>(
    res: Result<csv::DecodedTable<T>, csv::CsvSchemaError>,
) -> Result<JsValue, JsError> {
    match res {
        Ok(t) => ser(&JsDecodedTable {
            records: t.records,
            invalid_rows: t.invalid_rows,
        }),
        Err(e) => Err(JsError::new(&e.message())),
    }
}

#[wasm_bindgen(js_name = "decodeWeightCsv")]
pub fn decode_weight_csv(csv_text: &str) -> Result<JsValue, JsError> {
    decoded(csv::decode_weight_records(csv_text))
}

#[wasm_bindgen(js_name = "decodePeriodCsv")]
pub fn decode_period_csv(csv_text: &str) -> Result<JsValue, JsError> {
    decoded(csv::decode_period_records(csv_text))
}

#[wasm_bindgen(js_name = "decodeBowelCsv")]
pub fn decode_bowel_csv(csv_text: &str) -> Result<JsValue, JsError> {
    decoded(csv::decode_bowel_records(csv_text))
}

#[wasm_bindgen(js_name = "decodeHydrateCsv")]
pub fn decode_hydrate_csv(csv_text: &str) -> Result<JsValue, JsError> {
    decoded(csv::decode_hydrate_records(csv_text))
}

// ── Export / import (ZIP round-trip) ─────────────────────────────────────────

/// Builds the full lymph-export.zip from the four record lists.
/// Returns the archive bytes as a Uint8Array.
#[wasm_bindgen(js_name = "buildExportZip")]
pub fn build_export_zip(
    weights: JsValue,
    periods: JsValue,
    bowels: JsValue,
    hydrates: JsValue,
) -> Result<Vec<u8>, JsError> {
    let weights: Vec<WeightRecord> = de(&weights)?;
    let periods: Vec<PeriodRecord> = de(&periods)?;
    let bowels: Vec<BowelRecord> = de(&bowels)?;
    let hydrates: Vec<HydrateRecord> = de(&hydrates)?;
    Ok(crate::zip::build_zip(&[
        (importer::WEIGHT_CSV, &csv::encode_weight_records(&weights)),
        (importer::PERIOD_CSV, &csv::encode_period_records(&periods)),
        (importer::BOWEL_CSV, &csv::encode_bowel_records(&bowels)),
        (
            importer::HYDRATE_CSV,
            &csv::encode_hydrate_records(&hydrates),
        ),
    ]))
}

#[wasm_bindgen(js_name = "importZip")]
pub fn import_zip(
    zip_bytes: Vec<u8>,
    weight_existing_ids: JsValue,
    period_existing_ids: JsValue,
    bowel_existing_ids: JsValue,
    hydrate_existing_ids: JsValue,
) -> Result<JsValue, JsError> {
    let w: Vec<String> = de(&weight_existing_ids)?;
    let p: Vec<String> = de(&period_existing_ids)?;
    let b: Vec<String> = de(&bowel_existing_ids)?;
    let h: Vec<String> = de(&hydrate_existing_ids)?;
    let outcome: ImportOutcome =
        importer::import_zip(&zip_bytes, &w, &p, &b, &h).map_err(|e| JsError::new(&e.message))?;
    let summary = outcome.result.summary();
    ser(&JsImportOutcome {
        result: outcome.result,
        summary,
        weight_to_insert: outcome.weight_to_insert,
        period_to_insert: outcome.period_to_insert,
        bowel_to_insert: outcome.bowel_to_insert,
        hydrate_to_insert: outcome.hydrate_to_insert,
    })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct JsImportOutcome {
    result: importer::ImportResult,
    /// Exact snackbar string — the contract lives in the Rust core.
    summary: String,
    weight_to_insert: Vec<WeightRecord>,
    period_to_insert: Vec<PeriodRecord>,
    bowel_to_insert: Vec<BowelRecord>,
    hydrate_to_insert: Vec<HydrateRecord>,
}

#[wasm_bindgen(js_name = "flowIntensityLabel")]
pub fn flow_intensity_label_js(v: i32) -> String {
    crate::entity::flow_intensity_label(v).to_string()
}

#[wasm_bindgen(js_name = "bristolLabel")]
pub fn bristol_label_js(v: i32) -> String {
    crate::entity::bristol_label(v).to_string()
}

#[wasm_bindgen(js_name = "effortLabel")]
pub fn effort_label_js(v: i32) -> String {
    crate::entity::effort_label(v).to_string()
}

// ── Weight math ──────────────────────────────────────────────────────────────

#[wasm_bindgen(js_name = "parseHundredths")]
pub fn parse_hundredths(text: &str) -> Option<i32> {
    weight::parse_hundredths(text)
}

#[wasm_bindgen(js_name = "formatWeight")]
pub fn format_weight(value: f64) -> String {
    weight::format_weight(value)
}

#[wasm_bindgen(js_name = "checkWeight")]
pub fn check_weight(typed_hundredths: i32, unit_is_lb: bool) -> Result<JsValue, JsError> {
    ser(&weight::check_weight(typed_hundredths, unit_is_lb))
}

#[wasm_bindgen(js_name = "hundredthsLbToKg")]
pub fn hundredths_lb_to_kg(hundredths_lb: i32) -> f64 {
    weight::hundredths_lb_to_kg(hundredths_lb)
}

#[wasm_bindgen(js_name = "kgToHundredthsLb")]
pub fn kg_to_hundredths_lb(weight_kg: f64) -> i32 {
    weight::kg_to_hundredths_lb(weight_kg)
}

// ── Hydration volume math ───────────────────────────────────────────────────

#[wasm_bindgen(js_name = "checkVolume")]
pub fn check_volume(typed_hundredths: i32, unit_is_l: bool) -> Result<JsValue, JsError> {
    ser(&volume::check_volume(typed_hundredths, unit_is_l))
}

#[wasm_bindgen(js_name = "hundredthsLToMl")]
pub fn hundredths_l_to_ml(hundredths_l: i32) -> f64 {
    volume::hundredths_l_to_ml(hundredths_l)
}

#[wasm_bindgen(js_name = "hundredthsOzToMl")]
pub fn hundredths_oz_to_ml(hundredths_oz: i32) -> f64 {
    volume::hundredths_oz_to_ml(hundredths_oz)
}

#[wasm_bindgen(js_name = "mlToHundredthsL")]
pub fn ml_to_hundredths_l(ml: f64) -> i32 {
    volume::ml_to_hundredths_l(ml)
}

#[wasm_bindgen(js_name = "mlToHundredthsOz")]
pub fn ml_to_hundredths_oz(ml: f64) -> i32 {
    volume::ml_to_hundredths_oz(ml)
}

#[wasm_bindgen(js_name = "formatVolumeMl")]
pub fn format_volume_ml_js(volume_ml: f64) -> String {
    volume::format_volume_ml(volume_ml)
}

// ── Chart ───────────────────────────────────────────────────────────────────

fn range_from_label(label: &str) -> TimeRange {
    match label {
        "90D" => TimeRange::Days90,
        "1Y" => TimeRange::Year,
        _ => TimeRange::All,
    }
}

/// `label` is the UI selector value: "90D" | "1Y" | "ALL". `now` is epoch
/// millis as a JS number (f64 — exact well beyond year 2286, avoids BigInt noise).
#[wasm_bindgen(js_name = "cutoffMillis")]
pub fn cutoff_millis_js(label: &str, now: f64) -> Option<f64> {
    crate::chart::cutoff_millis(range_from_label(label), now as i64).map(|v| v as f64)
}

/// `samples` is an array of series, each an array of `{t, v}` (t = epoch millis).
#[wasm_bindgen(js_name = "mapMultiSeries")]
pub fn map_multi_series_js(samples: JsValue, min_tick_step: f64) -> Result<JsValue, JsError> {
    let all: Vec<Vec<Sample>> = de(&samples)?;
    let mapped: Option<MultiSeries> = map_multi_series(&all, min_tick_step);
    Ok(match mapped {
        None => JsValue::UNDEFINED,
        Some(m) => ser(&JsMultiSeries {
            series: m.series,
            y_min: m.y_min,
            y_max: m.y_max,
            ticks: m.ticks,
        })?,
    })
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct JsMultiSeries {
    series: Vec<crate::chart::SeriesLine>,
    y_min: f64,
    y_max: f64,
    ticks: Vec<f64>,
}

#[wasm_bindgen(js_name = "formatIntegerTick")]
pub fn format_integer_tick(v: f64) -> String {
    crate::chart::format_integer_tick(v)
}
