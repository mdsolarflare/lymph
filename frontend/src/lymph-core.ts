// Typed wrapper over the wasm-pack module (../core/pkg/lymph_core.js).
// The generated .d.ts is all `any` (wasm-bindgen limitation), which strict
// mode punishes with implicit-any errors everywhere — so this wrapper is the
// single place casts happen, and the app imports fully-typed functions from
// "lymph-core" (mapped here in deno.json).
//
// Field names mirror the crate's serde camelCase renames exactly.

import * as raw from "../../core/pkg/lymph_core.js";
import rawInit from "../../core/pkg/lymph_core.js";

export interface WeightRecord {
  id: string;
  timestamp: number;
  weightKg: number;
  note: string | null;
}
export interface PeriodRecord {
  id: string;
  timestamp: number;
  durationDays: number;
  flowIntensity: number;
  symptoms: string | null;
}
export interface BowelRecord {
  id: string;
  timestamp: number;
  bristolType: number;
  effortLevel: number;
  note: string | null;
}
export interface HydrateRecord {
  id: string;
  timestamp: number;
  volumeMl: number;
  note: string | null;
}
export interface DecodedTable<T> {
  records: T[];
  invalidRows: number;
}
export interface TableImport {
  added: number;
  skippedDuplicates: number;
  invalidRows: number;
  error: string | null;
}
export interface ImportResult {
  weight: TableImport;
  period: TableImport;
  bowel: TableImport;
  hydrate: TableImport;
}
export interface ImportOutcome {
  result: ImportResult;
  /** Exact snackbar string, built by the Rust core (contract lives there). */
  summary: string;
  weightToInsert: WeightRecord[];
  periodToInsert: PeriodRecord[];
  bowelToInsert: BowelRecord[];
  hydrateToInsert: HydrateRecord[];
}
export interface WeightCheck {
  valid: boolean;
  canonicalKg: number;
}
export interface ChartPoint {
  xNorm: number;
  value: number;
}
export interface SeriesLine {
  points: ChartPoint[];
}
export interface MultiSeries {
  series: SeriesLine[];
  yMin: number;
  yMax: number;
  ticks: number[];
}
export interface Sample {
  t: number;
  v: number;
}

export const init = rawInit as (moduleOrPath?: string | URL | WebAssembly.Module) => Promise<unknown>;

export const encodeWeightCsv = raw.encodeWeightCsv as (records: WeightRecord[]) => string;
export const encodePeriodCsv = raw.encodePeriodCsv as (records: PeriodRecord[]) => string;
export const encodeBowelCsv = raw.encodeBowelCsv as (records: BowelRecord[]) => string;
export const encodeHydrateCsv = raw.encodeHydrateCsv as (records: HydrateRecord[]) => string;

export const decodeWeightCsv = raw.decodeWeightCsv as (csvText: string) => DecodedTable<WeightRecord>;
export const decodePeriodCsv = raw.decodePeriodCsv as (csvText: string) => DecodedTable<PeriodRecord>;
export const decodeBowelCsv = raw.decodeBowelCsv as (csvText: string) => DecodedTable<BowelRecord>;
export const decodeHydrateCsv = raw.decodeHydrateCsv as (csvText: string) => DecodedTable<HydrateRecord>;

export const buildExportZip = raw.buildExportZip as (
  weights: WeightRecord[],
  periods: PeriodRecord[],
  bowels: BowelRecord[],
  hydrates: HydrateRecord[],
) => Uint8Array;

export const importZip = raw.importZip as (
  zipBytes: Uint8Array,
  weightExistingIds: string[],
  periodExistingIds: string[],
  bowelExistingIds: string[],
  hydrateExistingIds: string[],
) => ImportOutcome;

export const parseHundredths = raw.parseHundredths as (text: string) => number | undefined;
export const formatWeight = raw.formatWeight as (value: number) => string;
export const checkWeight = raw.checkWeight as (
  typedHundredths: number,
  unitIsLb: boolean,
) => WeightCheck;
export const hundredthsLbToKg = raw.hundredthsLbToKg as (hundredthsLb: number) => number;
export const kgToHundredthsLb = raw.kgToHundredthsLb as (weightKg: number) => number;

export interface VolumeCheck {
  valid: boolean;
  canonicalMl: number;
}
export const checkVolume = raw.checkVolume as (
  typedHundredths: number,
  unitIsL: boolean,
) => VolumeCheck;
export const hundredthsLToMl = raw.hundredthsLToMl as (hundredthsL: number) => number;
export const hundredthsOzToMl = raw.hundredthsOzToMl as (hundredthsOz: number) => number;
export const mlToHundredthsL = raw.mlToHundredthsL as (ml: number) => number;
export const mlToHundredthsOz = raw.mlToHundredthsOz as (ml: number) => number;
export const formatVolumeMl = raw.formatVolumeMl as (ml: number) => string;

export const cutoffMillis = raw.cutoffMillis as (
  label: string,
  now: number,
) => number | undefined;

export const mapMultiSeries = raw.mapMultiSeries as (
  samples: Sample[][],
  minTickStep: number,
) => MultiSeries | undefined;

export const formatIntegerTick = raw.formatIntegerTick as (v: number) => string;

/** UI-only: qualitative flow label for the stored 1-4 intensity (NONE/LIGHT/MEDIUM/HEAVY). */
export const flowIntensityLabel = raw.flowIntensityLabel as (v: number) => string;

/** UI-only: qualitative bristol-like label for the stored 1-5 type. */
export const bristolLabel = raw.bristolLabel as (v: number) => string;

/** UI-only: qualitative effort label for the stored 1-3 level. */
export const effortLabel = raw.effortLabel as (v: number) => string;
