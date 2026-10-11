// Reactive store: three record tables, mirrored to localStorage as CSVs —
// the SAME keys and format as the Kotlin app (lymph.weight_records etc.,
// full CSV with header), so existing browser data survives the cutover with
// zero migration code. Every mutation re-encodes the table's CSV back to
// storage, mirroring the Kotlin WebTableStore/WebDao contract.

import { effect, signal } from "@preact/signals";
import type {
  BowelRecord,
  HydrateRecord,
  PeriodRecord,
  WeightRecord,
} from "lymph-core";
import {
  decodeBowelCsv,
  decodeHydrateCsv,
  decodePeriodCsv,
  decodeWeightCsv,
  encodeBowelCsv,
  encodeHydrateCsv,
  encodePeriodCsv,
  encodeWeightCsv,
} from "lymph-core";
import { nowMillis, uuidV4 } from "./format.ts";

export const WEIGHT_KEY = "lymph.weight_records";
export const PERIOD_KEY = "lymph.period_records";
export const BOWEL_KEY = "lymph.bowel_records";
export const HYDRATE_KEY = "lymph.hydrate_records";

// ── localStorage with the Kotlin failure policy ─────────────────────────────
// Unreadable/corrupt stored data → warn + start empty; write failures →
// in-memory-only session. Storage never crashes the app.

function lsGet(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function lsSet(key: string, value: string): boolean {
  try {
    window.localStorage.setItem(key, value);
    return true;
  } catch (e) {
    console.warn(`localStorage write failed for '${key}' — data will not survive reload`, e);
    return false;
  }
}

// ── hydration ────────────────────────────────────────────────────────────────

/** Decodes a stored CSV; empty on absent key, unreadable data, or schema mismatch. */
function hydrate<T>(
  key: string,
  decode: (csv: string) => { records: T[]; invalidRows: number },
): T[] {
  const csv = lsGet(key);
  if (csv === null) return [];
  try {
    const table = decode(csv);
    if (table.invalidRows > 0) {
      console.warn(`${key} — ${table.invalidRows} invalid row(s) dropped on hydrate`);
    }
    return table.records;
  } catch (e) {
    console.warn(`${key} unreadable (${e instanceof Error ? e.message : e}) — starting empty`);
    return [];
  }
}

// Newest first — mirrors the SQL ORDER BY timestamp DESC of the native DAOs
// and the prepend-on-insert of the Kotlin web DAOs. Ties: newer-inserted first.
function newestFirst<T extends { timestamp: number }>(records: T[]): T[] {
  return [...records].sort((a, b) => b.timestamp - a.timestamp);
}

// Signals start EMPTY: wasm functions cannot run at module-load time (init
// has not completed when this module evaluates), and the write-through
// effects must not fire before hydration or they would overwrite stored
// data with empty tables. initStore() — called once from main.tsx after
// wasm init — does hydration and arms persistence.
export const weights = signal<WeightRecord[]>([]);
export const periods = signal<PeriodRecord[]>([]);
export const bowels = signal<BowelRecord[]>([]);
export const hydrates = signal<HydrateRecord[]>([]);

let storeInitialized = false;

/**
 * Hydrates the three tables from localStorage and arms write-through
 * persistence. MUST run exactly once, after the wasm core is initialized.
 */
export function initStore(): void {
  if (storeInitialized) return;
  storeInitialized = true;

  weights.value = newestFirst(hydrate(WEIGHT_KEY, decodeWeightCsv));
  periods.value = newestFirst(hydrate(PERIOD_KEY, decodePeriodCsv));
  bowels.value = newestFirst(hydrate(BOWEL_KEY, decodeBowelCsv));
  hydrates.value = newestFirst(hydrate(HYDRATE_KEY, decodeHydrateCsv));

  effect(() => {
    lsSet(WEIGHT_KEY, encodeWeightCsv(weights.value));
  });
  effect(() => {
    lsSet(PERIOD_KEY, encodePeriodCsv(periods.value));
  });
  effect(() => {
    lsSet(BOWEL_KEY, encodeBowelCsv(bowels.value));
  });
  effect(() => {
    lsSet(HYDRATE_KEY, encodeHydrateCsv(hydrates.value));
  });
}

// ── mutations ────────────────────────────────────────────────────────────────

export function insertWeight(rec: Omit<WeightRecord, "id" | "timestamp"> & Partial<WeightRecord>): void {
  const full: WeightRecord = {
    id: rec.id ?? uuidV4(),
    timestamp: rec.timestamp ?? nowMillis(),
    weightKg: rec.weightKg,
    note: rec.note ?? null,
  };
  weights.value = [full, ...weights.value];
}

export function insertPeriod(rec: Omit<PeriodRecord, "id" | "timestamp"> & Partial<PeriodRecord>): void {
  const full: PeriodRecord = {
    id: rec.id ?? uuidV4(),
    timestamp: rec.timestamp ?? nowMillis(),
    durationDays: rec.durationDays,
    flowIntensity: rec.flowIntensity,
    symptoms: rec.symptoms ?? null,
  };
  periods.value = [full, ...periods.value];
}

export function insertBowel(rec: Omit<BowelRecord, "id" | "timestamp"> & Partial<BowelRecord>): void {
  const full: BowelRecord = {
    id: rec.id ?? uuidV4(),
    timestamp: rec.timestamp ?? nowMillis(),
    bristolType: rec.bristolType,
    effortLevel: rec.effortLevel,
    note: rec.note ?? null,
  };
  bowels.value = [full, ...bowels.value];
}

export function insertHydrate(
  rec: Omit<HydrateRecord, "id" | "timestamp"> & Partial<HydrateRecord>,
): void {
  const full: HydrateRecord = {
    id: rec.id ?? uuidV4(),
    timestamp: rec.timestamp ?? nowMillis(),
    volumeMl: rec.volumeMl,
    note: rec.note ?? null,
  };
  hydrates.value = [full, ...hydrates.value];
}

export function deleteWeight(id: string): void {
  weights.value = weights.value.filter((r) => r.id !== id);
}
export function deletePeriod(id: string): void {
  periods.value = periods.value.filter((r) => r.id !== id);
}
export function deleteBowel(id: string): void {
  bowels.value = bowels.value.filter((r) => r.id !== id);
}
export function deleteHydrate(id: string): void {
  hydrates.value = hydrates.value.filter((r) => r.id !== id);
}

/** Import flows call these after wasm importZip returns the to-insert lists. */
export function insertManyWeights(records: WeightRecord[]): void {
  if (records.length === 0) return;
  weights.value = [...records, ...weights.value];
}
export function insertManyPeriods(records: PeriodRecord[]): void {
  if (records.length === 0) return;
  periods.value = [...records, ...periods.value];
}
export function insertManyBowels(records: BowelRecord[]): void {
  if (records.length === 0) return;
  bowels.value = [...records, ...bowels.value];
}
export function insertManyHydrates(records: HydrateRecord[]): void {
  if (records.length === 0) return;
  hydrates.value = [...records, ...hydrates.value];
}

// ── theme (the only setting: dark/light) ───────────────────────────────────

const THEME_KEY = "lymph.theme";

export type Theme = "dark" | "light";
export const theme = signal<Theme>(
  (lsGet(THEME_KEY) as Theme) === "light" ? "light" : "dark",
);
effect(() => {
  document.documentElement.dataset.theme = theme.value;
  lsSet(THEME_KEY, theme.value);
});
