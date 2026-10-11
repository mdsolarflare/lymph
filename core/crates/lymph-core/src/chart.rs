//! Chart geometry — a faithful port of the Kotlin `ChartModel` (pure functions,
//! no rendering). All geometry is normalized to 0..1; rendering multiplies by
//! the plot size in the frontend.

use crate::weight::round_to_hundredths;

use serde::{Deserialize, Serialize};

/// X-range options for the trend charts (weight / period / bowel).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TimeRange {
    Days90,
    Year,
    All,
}

impl TimeRange {
    pub fn label(self) -> &'static str {
        match self {
            TimeRange::Days90 => "90D",
            TimeRange::Year => "1Y",
            TimeRange::All => "ALL",
        }
    }
    pub fn days(self) -> Option<i64> {
        match self {
            TimeRange::Days90 => Some(90),
            TimeRange::Year => Some(365),
            TimeRange::All => None,
        }
    }
}

/// One sample: epoch millis + value.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Sample {
    pub t: f64,
    pub v: f64,
}

/// A single plotted point in normalized chart space (x_norm: 0.0 = left .. 1.0 = right).
#[derive(Debug, Clone, Copy, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ChartPoint {
    pub x_norm: f32,
    pub value: f64,
}

/// One line's points, chronological order.
#[derive(Debug, Clone, PartialEq, Serialize)]
pub struct SeriesLine {
    pub points: Vec<ChartPoint>,
}

/// Mapped series ready for rendering: per-line points plus a shared real-unit
/// y-axis ([y_min], [y_max], [ticks]) for labels.
#[derive(Debug, Clone, PartialEq)]
pub struct MultiSeries {
    pub series: Vec<SeriesLine>,
    pub y_min: f64,
    pub y_max: f64,
    pub ticks: Vec<f64>,
}

impl MultiSeries {
    /// Normalized y position of a value: 0.0 = bottom, 1.0 = top.
    pub fn y_norm(&self, value: f64) -> f32 {
        ((value - self.y_min) / (self.y_max - self.y_min)) as f32
    }
}

/// Inclusive lower timestamp bound for the given range, or None for ALL.
pub fn cutoff_millis(range: TimeRange, now: i64) -> Option<i64> {
    range.days().map(|d| now - d * 86_400_000)
}

/// Maps one or more chronological sample series into shared chart geometry.
/// Returns None when every input list is empty.
///
/// All lines share a single y-axis auto-scaled to the combined [min,max] + 10%
/// padding — never zero-based, so small trends do not look flat. A fully flat
/// input gets symmetric padding. X mapping is time-proportional over the union
/// of all timestamps; if every timestamp is equal, each line's points are
/// spread evenly instead (they are distinct records and would otherwise stack).
pub fn map_multi_series(all_samples: &[Vec<Sample>], min_tick_step: f64) -> Option<MultiSeries> {
    let total: usize = all_samples.iter().map(|s| s.len()).sum();
    if total == 0 {
        return None;
    }

    let mut min = f64::MAX;
    let mut max = f64::MIN;
    for samples in all_samples {
        for s in samples {
            if s.v < min {
                min = s.v;
            }
            if s.v > max {
                max = s.v;
            }
        }
    }
    let span = max - min;
    let (y_min, y_max) = if span < 1e-9 {
        // Flat input — pad symmetrically so the lines sit mid-chart.
        let pad = if min > 0.0 {
            (min * 0.02).max(1.0)
        } else {
            1.0
        };
        (min - pad, min + pad)
    } else {
        let pad = span * 0.1;
        (min - pad, max + pad)
    };

    // Union of timestamps across non-empty series only (min on an empty list throws).
    let non_empty: Vec<&Vec<Sample>> = all_samples.iter().filter(|s| !s.is_empty()).collect();
    // total > 0 guarantees at least one non-empty series, so the folds see real data.
    let t_min = non_empty
        .iter()
        .flat_map(|s| s.iter())
        .map(|s| s.t)
        .fold(f64::MAX, f64::min);
    let t_max = non_empty
        .iter()
        .flat_map(|s| s.iter())
        .map(|s| s.t)
        .fold(f64::MIN, f64::max);
    let t_span = t_max - t_min;

    let series = all_samples
        .iter()
        .map(|samples| {
            let n = samples.len();
            if n == 0 {
                SeriesLine { points: Vec::new() }
            } else {
                SeriesLine {
                    points: samples
                        .iter()
                        .enumerate()
                        .map(|(i, s)| {
                            let x_norm = if n == 1 {
                                0.5f32
                            } else if t_span < 1e-9 {
                                i as f32 / (n as f32 - 1.0)
                            } else {
                                ((s.t - t_min) / t_span) as f32
                            };
                            ChartPoint { x_norm, value: s.v }
                        })
                        .collect(),
                }
            }
        })
        .collect();

    Some(MultiSeries {
        series,
        y_min,
        y_max,
        ticks: nice_ticks(y_min, y_max, 4, min_tick_step),
    })
}

/// "Nice" tick values for a y-axis: 1/2/5×10ⁿ steps, ~4 ticks. `min_step`
/// keeps integer-valued charts (period/bowel) on whole-number ticks; the
/// default of 0.01 preserves two-decimal precision for weight. All returned
/// values are rounded to hundredths and lie within [lo, hi].
pub fn nice_ticks(lo: f64, hi: f64, target_count: usize, min_step: f64) -> Vec<f64> {
    let span = hi - lo;
    if span <= 0.0 {
        return vec![round_to_hundredths(lo)];
    }

    let raw_step = span / target_count as f64;
    let magnitude = 10f64.powf(raw_step.log10().floor());
    let normalized = raw_step / magnitude;
    let nice_normalized = if normalized < 1.5 {
        1.0
    } else if normalized < 3.5 {
        2.0
    } else if normalized < 7.5 {
        5.0
    } else {
        10.0
    };
    let step = (nice_normalized * magnitude).max(min_step.max(0.01));

    let mut ticks: Vec<f64> = Vec::new();
    let mut v = (lo / step).ceil() * step;
    while v <= hi + step * 1e-6 && ticks.len() < 20 {
        let rounded = round_to_hundredths(v);
        if ticks.last() != Some(&rounded) {
            ticks.push(rounded);
        }
        v += step;
    }
    ticks
}

/// Tick label for integer-valued series: whole numbers without a decimal point.
pub fn format_integer_tick(v: f64) -> String {
    if v == v.floor() {
        format!("{}", v as i64)
    } else {
        format!("{}", round_to_hundredths(v))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample(t: f64, v: f64) -> Sample {
        Sample { t, v }
    }

    #[test]
    fn empty_input_returns_none() {
        assert!(map_multi_series(&[], 0.01).is_none());
        assert!(map_multi_series(&[vec![], vec![]], 0.01).is_none());
    }

    #[test]
    fn single_sample_is_centered_with_symmetric_range() {
        let s = map_multi_series(&[vec![sample(1000.0, 70.0)]], 0.01).unwrap();
        assert_eq!(s.series.len(), 1);
        assert_eq!(s.series[0].points.len(), 1);
        assert_eq!(s.series[0].points[0].x_norm, 0.5);
        assert!(
            s.y_min < 70.0 && s.y_max > 70.0,
            "range must bracket the value"
        );
        // symmetric padding
        assert!((70.0 - s.y_min - (s.y_max - 70.0)).abs() < 1e-9);
    }

    #[test]
    fn flat_series_gets_padded_range() {
        let s = map_multi_series(
            &[vec![
                sample(1.0, 70.0),
                sample(2.0, 70.0),
                sample(3.0, 70.0),
            ]],
            0.01,
        )
        .unwrap();
        assert!(s.y_min < 70.0 && s.y_max > 70.0);
        // equal timestamps -> evenly spread x positions
        assert!((s.series[0].points[0].x_norm - 0.0).abs() < 1e-6);
        assert!((s.series[0].points.last().unwrap().x_norm - 1.0).abs() < 1e-6);
    }

    #[test]
    fn normal_series_maps_monotonically() {
        let samples = vec![sample(0.0, 70.0), sample(50.0, 82.0), sample(100.0, 74.0)];
        let s = map_multi_series(std::slice::from_ref(&samples), 0.01).unwrap();
        // x is time-proportional: first at 0, last at 1, middle in between
        assert!((s.series[0].points[0].x_norm - 0.0).abs() < 1e-6);
        assert!((s.series[0].points.last().unwrap().x_norm - 1.0).abs() < 1e-6);
        assert!(s.series[0].points[1].x_norm > 0.0 && s.series[0].points[1].x_norm < 1.0);
        // y ordering preserved: 82 is the top point
        let top = s.series[0]
            .points
            .iter()
            .max_by(|a, b| a.value.total_cmp(&b.value))
            .unwrap();
        assert_eq!(top.value, 82.0);
        assert!(
            s.y_min < 70.0 && s.y_max > 82.0,
            "range must bracket data with padding"
        );
    }

    #[test]
    fn y_norm_is_normalized() {
        let s = map_multi_series(&[vec![sample(0.0, 70.0), sample(100.0, 82.0)]], 0.01).unwrap();
        assert!((s.y_norm(s.y_min) as f64 - 0.0).abs() < 1e-6);
        assert!((s.y_norm(s.y_max) as f64 - 1.0).abs() < 1e-6);
        assert!(s.y_norm(76.0) > s.y_norm(70.0));
    }

    #[test]
    fn ticks_lie_within_range_and_are_reasonable() {
        let s = map_multi_series(&[vec![sample(0.0, 70.0), sample(100.0, 82.0)]], 0.01).unwrap();
        assert!(!s.ticks.is_empty());
        assert!(
            s.ticks.len() <= 6,
            "expected ~4 ticks, got {}",
            s.ticks.len()
        );
        for &t in &s.ticks {
            assert!(
                t >= s.y_min && t <= s.y_max,
                "tick {t} outside [{}, {}]",
                s.y_min,
                s.y_max
            );
            // two-decimal precision (weight contract)
            assert_eq!(round_to_hundredths(t), t);
        }
    }

    #[test]
    fn ticks_are_strictly_increasing() {
        let s = map_multi_series(&[vec![sample(0.0, 69.8531), sample(100.0, 70.4)]], 0.01).unwrap();
        for i in 1..s.ticks.len() {
            assert!(
                s.ticks[i] > s.ticks[i - 1],
                "ticks must increase: {:?}",
                s.ticks
            );
        }
    }

    // --- multi-series (period/bowel charts) ---

    #[test]
    fn shared_axis_spans_all_series() {
        let duration = vec![sample(0.0, 3.0), sample(100.0, 7.0)];
        let flow = vec![sample(0.0, 4.0), sample(100.0, 1.0)];
        let s = map_multi_series(&[duration, flow], 0.01).unwrap();
        assert!(
            s.y_min < 1.0 && s.y_max > 7.0,
            "axis must span both series: [{}, {}]",
            s.y_min,
            s.y_max
        );
        assert_eq!(s.series.len(), 2);
        assert_eq!(s.series[0].points.len(), 2);
        assert_eq!(s.series[1].points.len(), 2);
    }

    #[test]
    fn multi_series_share_x_mapping() {
        let a = vec![sample(0.0, 3.0), sample(50.0, 6.0), sample(100.0, 4.0)];
        let b = vec![sample(0.0, 2.0), sample(50.0, 3.0), sample(100.0, 1.0)];
        let s = map_multi_series(&[a, b], 0.01).unwrap();
        for i in 0..3 {
            assert!((s.series[0].points[i].x_norm - s.series[1].points[i].x_norm).abs() < 1e-6);
        }
    }

    #[test]
    fn min_tick_step_forces_whole_number_ticks() {
        // Tiny span (3..4) would normally produce 0.2-step ticks; integer charts need step >= 1.
        let s = map_multi_series(&[vec![sample(0.0, 3.0), sample(100.0, 4.0)]], 1.0).unwrap();
        assert!(!s.ticks.is_empty());
        for &t in &s.ticks {
            assert_eq!(t, t as i64 as f64, "tick {t} not whole");
        }
    }

    #[test]
    fn empty_series_among_non_empty_still_renders() {
        let s = map_multi_series(&[vec![], vec![sample(0.0, 5.0)]], 0.01).unwrap();
        assert_eq!(s.series.len(), 2);
        assert!(s.series[0].points.is_empty());
        assert_eq!(s.series[1].points.len(), 1);
    }

    #[test]
    fn format_integer_tick_trims_decimals() {
        assert_eq!(format_integer_tick(3.0), "3");
        assert_eq!(format_integer_tick(2.5), "2.5");
    }

    // --- range filtering (shared by all trend charts) ---

    #[test]
    fn cutoff_math() {
        let now = 1_000_000_000_000i64;
        assert_eq!(cutoff_millis(TimeRange::All, now), None);
        assert_eq!(
            cutoff_millis(TimeRange::Days90, now),
            Some(now - 90 * 86_400_000)
        );
        assert_eq!(
            cutoff_millis(TimeRange::Year, now),
            Some(now - 365 * 86_400_000)
        );
    }

    #[test]
    fn range_filter_keeps_only_recent_samples() {
        let now = 1_000_000_000_000.0;
        let day = 86_400_000.0;
        let samples = [
            sample(now - 400.0 * day, 70.0), // older than a year -> filtered by YEAR
            sample(now - 100.0 * day, 71.0), // older than 90d -> filtered by DAYS_90
            sample(now - 30.0 * day, 72.0),  // recent -> kept by both
        ];
        let cutoff90 = cutoff_millis(TimeRange::Days90, now as i64).unwrap() as f64;
        assert_eq!(samples.iter().filter(|s| s.t >= cutoff90).count(), 1);
        let cutoff_year = cutoff_millis(TimeRange::Year, now as i64).unwrap() as f64;
        assert_eq!(samples.iter().filter(|s| s.t >= cutoff_year).count(), 2);
    }
}
