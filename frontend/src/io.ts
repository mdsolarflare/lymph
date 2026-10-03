// Export/import actions. The ZIP encode/decode, CRC, and insert-only import
// semantics all live in the Rust core (wasm); this file only handles the
// browser side: download delivery and file picking.

import { buildExportZip, importZip } from "lymph-core";
import { bowels, insertManyBowels, insertManyPeriods, insertManyWeights, periods, weights } from "./store.ts";

export function exportZip(): void {
  const bytes = buildExportZip(weights.value, periods.value, bowels.value);
  const blob = new Blob([bytes as unknown as BlobPart], { type: "application/zip" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "lymph-export.zip";
  a.click();
  URL.revokeObjectURL(url);
}

export async function importFromFile(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer());
  const outcome = importZip(
    bytes,
    weights.value.map((r) => r.id),
    periods.value.map((r) => r.id),
    bowels.value.map((r) => r.id),
  );
  insertManyWeights(outcome.weightToInsert);
  insertManyPeriods(outcome.periodToInsert);
  insertManyBowels(outcome.bowelToInsert);
  return outcome.summary;
}
