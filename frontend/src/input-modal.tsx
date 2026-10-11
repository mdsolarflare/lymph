// Input modal — per-type controls with the weight semantics ported exactly:
// integer hundredths (no float drift), KG/LB toggle with exact factor,
// validation against the canonical stored kg, ±0.1 dials, prefill from last
// record, defaults valid on open.

import { useSignal, useSignalEffect } from "@preact/signals";
import {
  bristolLabel,
  checkVolume,
  checkWeight,
  effortLabel,
  flowIntensityLabel,
  formatVolumeMl,
  formatWeight,
  hundredthsLToMl,
  hundredthsLbToKg,
  hundredthsOzToMl,
  kgToHundredthsLb,
  mlToHundredthsL,
  mlToHundredthsOz,
  parseHundredths,
} from "lymph-core";
import { useEffect } from "preact/hooks";
import type { VNode } from "preact";
import { insertBowel, insertHydrate, insertPeriod, insertWeight } from "./store.ts";

export type RecordType = "weight" | "period" | "bowel" | "hydrate";
export const RECORD_TYPES: RecordType[] = ["weight", "period", "bowel", "hydrate"];

/** Live input filter: digits, one optional dot, ≤2 decimals (Kotlin contract). */
function filterWeightInput(raw: string): string {
  let seenDot = false;
  let decimals = 0;
  let out = "";
  for (const ch of raw) {
    if (ch >= "0" && ch <= "9") {
      if (seenDot) {
        if (decimals >= 2) continue;
        decimals++;
      }
      out += ch;
    } else if (ch === "." && !seenDot) {
      seenDot = true;
      out += ch;
    }
  }
  return out;
}

/** DialCounter: ± buttons around a centered editable value (weight) or static display. */
function DialRow(props: {
  value: string;
  invalid?: boolean;
  onDec: () => void;
  onInc: () => void;
  editable?: boolean;
  onInput?: (raw: string) => void;
  label: string;
}) {
  return (
    <div class="field">
      <label>{props.label}</label>
      <div class="dial-row">
        <button type="button" class="dial-btn" data-testid={`dial-${props.label}-dec`}
                onClick={props.onDec}>-</button>
        <div class={`dial-value${props.invalid ? " invalid" : ""}`}
             data-testid={`dial-value-${props.label}`}>
          {props.editable
            ? (
              <input
                inputmode="decimal"
                value={props.value}
                onInput={(e) => props.onInput?.((e.target as HTMLInputElement).value)}
              />
            )
            : props.value}
        </div>
        <button type="button" class="dial-btn" data-testid={`dial-${props.label}-inc`}
                onClick={props.onInc}>+</button>
      </div>
    </div>
  );
}

export interface InputModalProps {
  open: boolean;
  onClose: () => void;
  lastWeightKg: number | null; // null → 70 kg default on first use
  lastVolumeMl: number | null; // null → 500 ml default on first use (standard water bottle)
  onSaved: () => void;
}

export function InputModal(props: InputModalProps) {
  const type = useSignal<RecordType>("weight");
  const unitIsLb = useSignal(false);
  // Weight is held as integer hundredths of the SELECTED unit — no float drift.
  const hundredths = useSignal(7000);
  const weightText = useSignal("70");
  const note = useSignal("");
  const symptoms = useSignal("");
  const durationDays = useSignal(5);
  const flowIntensity = useSignal(2);
  const bristolType = useSignal(3);   // 1-5 scale; 3 = PERFECT (old default 4 was "normal" on the retired 1-7 scale)
  const effortLevel = useSignal(2);   // 1-3 scale; 2 = MEDIUM (old default 3 was the 1-5 midpoint)
  const weightInvalid = useSignal(false);

  // ── hydrate state (mirrors weight: integer hundredths of the SELECTED unit) ──
  const hydrateUnitIsL = useSignal(true);
  const hydrateHundredths = useSignal(50); // 0.5 L — standard water bottle
  const hydrateText = useSignal("0.5");
  const hydrateInvalid = useSignal(false);

  // Prefill once per MOUNT — App mounts this component only while the modal
  // is open (fresh instance per open, same as the Kotlin app), so this runs
  // on every open. All hooks run before any early return (hook-order rule).
  const prefill = () => {
    const start = props.lastWeightKg ?? 70;
    hundredths.value = Math.round(start * 100);
    weightText.value = formatWeight(start);
    unitIsLb.value = false;
    weightInvalid.value = false;
    // Hydrate mirrors weight: display in the selected unit (L), store ml.
    // Default 500 ml = a standard water bottle (SPEC §3).
    const startMl = props.lastVolumeMl ?? 500;
    hydrateUnitIsL.value = true;
    hydrateHundredths.value = mlToHundredthsL(startMl);
    hydrateText.value = formatWeight(hydrateHundredths.value / 100);
    hydrateInvalid.value = false;
  };
  useEffect(() => {
    prefill();
  }, []);

  if (!props.open) return null;

  // ── weight state helpers ──────────────────────────────────────────────
  const parsedHundredths = () => parseHundredths(weightText.value);
  const check = () => {
    const h = parsedHundredths();
    if (h === undefined) return undefined;
    return checkWeight(h, unitIsLb.value);
  };

  const displayWeight = () => {
    const h = parsedHundredths();
    return h === undefined ? weightText.value : formatWeight(h / 100);
  };

  // ── weight entry ──────────────────────────────────────────────────────
  const onWeightInput = (raw: string) => {
    const filtered = filterWeightInput(raw);
    weightText.value = filtered;
    const h = parseHundredths(filtered);
    if (h !== undefined) hundredths.value = h;
    weightInvalid.value = h === undefined ? false : !checkWeight(h, unitIsLb.value).valid;
  };

  const stepWeight = (delta: number) => {
    const h = parsedHundredths() ?? hundredths.value;
    const next = Math.max(0, h + delta);
    hundredths.value = next;
    weightText.value = formatWeight(next / 100);
    weightInvalid.value = !checkWeight(next, unitIsLb.value).valid;
  };

  const switchUnit = (toLb: boolean) => {
    if (unitIsLb.value === toLb) return;
    const h = parsedHundredths() ?? hundredths.value;
    const nextHundredths = toLb ? kgToHundredthsLb(h / 100) : Math.round(hundredthsLbToKg(h) * 100);
    unitIsLb.value = toLb;
    hundredths.value = nextHundredths;
    weightText.value = formatWeight(nextHundredths / 100);
    weightInvalid.value = !checkWeight(nextHundredths, toLb).valid;
  };

  // ── hydrate helpers (unit-aware: hundredths of L or of OZ) ───────────
  const parsedHydrateHundredths = () => parseHundredths(hydrateText.value);
  const hydrateStep = () => (hydrateUnitIsL.value ? 10 : 100); // ±0.1 L or ±1 OZ

  const onHydrateInput = (raw: string) => {
    const filtered = filterWeightInput(raw);
    hydrateText.value = filtered;
    const h = parseHundredths(filtered);
    if (h !== undefined) hydrateHundredths.value = h;
    hydrateInvalid.value = h === undefined ? false : !checkVolume(h, hydrateUnitIsL.value).valid;
  };

  const stepHydrate = (delta: number) => {
    const h = parsedHydrateHundredths() ?? hydrateHundredths.value;
    const next = Math.max(0, h + delta);
    hydrateHundredths.value = next;
    // Display in the selected unit (hundredths/100), exactly like weight.
    hydrateText.value = formatWeight(next / 100);
    hydrateInvalid.value = !checkVolume(next, hydrateUnitIsL.value).valid;
  };

  const switchHydrateUnit = (toL: boolean) => {
    if (hydrateUnitIsL.value === toL) return;
    const currentMl = hydrateUnitIsL.value
      ? hundredthsLToMl(parsedHydrateHundredths() ?? hydrateHundredths.value)
      : hundredthsOzToMl(parsedHydrateHundredths() ?? hydrateHundredths.value);
    const nextHundredths = toL ? mlToHundredthsL(currentMl) : mlToHundredthsOz(currentMl);
    hydrateUnitIsL.value = toL;
    hydrateHundredths.value = nextHundredths;
    hydrateText.value = formatWeight(nextHundredths / 100);
    hydrateInvalid.value = !checkVolume(nextHundredths, toL).valid;
  };

  const canSave = () => {
    if (type.value === "weight") {
      const h = parsedHundredths();
      if (h === undefined) return false;
      return checkWeight(h, unitIsLb.value).valid;
    }
    if (type.value === "hydrate") {
      const h = parsedHydrateHundredths();
      if (h === undefined) return false;
      return checkVolume(h, hydrateUnitIsL.value).valid;
    }
    return true;
  };

  const save = () => {
    if (!canSave()) return;
    if (type.value === "weight") {
      const h = parsedHundredths() ?? hundredths.value;
      const checkResult = checkWeight(h, unitIsLb.value);
      insertWeight({ weightKg: checkResult.canonicalKg, note: note.value || null });
    } else if (type.value === "period") {
      insertPeriod({
        durationDays: durationDays.value,
        flowIntensity: persistenceFlow(flowIntensity.value),
        symptoms: symptoms.value || null,
      });
    } else if (type.value === "bowel") {
      insertBowel({
        bristolType: bristolType.value,
        effortLevel: effortLevel.value,
        note: note.value || null,
      });
    } else {
      const h = parsedHydrateHundredths() ?? hydrateHundredths.value;
      const checkResult = checkVolume(h, hydrateUnitIsL.value);
      insertHydrate({ volumeMl: checkResult.canonicalMl, note: note.value || null });
    }
    props.onSaved();
  };

  const weightSection = (
    <>
      <div class="field">
        <label>UNIT</label>
        <div class="type-tabs" data-testid="unit-toggle">
          <button type="button" class={unitIsLb.value ? "" : "selected"}
                  onClick={() => switchUnit(false)}>KG</button>
          <button type="button" class={unitIsLb.value ? "selected" : ""}
                  onClick={() => switchUnit(true)}>LB</button>
        </div>
      </div>
      <DialRow
        label="weight"
        value={weightText.value}
        invalid={weightInvalid.value}
        editable
        onInput={onWeightInput}
        onDec={() => stepWeight(-10)}
        onInc={() => stepWeight(10)}
      />
      <div class="field">
        <label>note</label>
        <input type="text" value={note.value}
               onInput={(e) => (note.value = (e.target as HTMLInputElement).value)} />
      </div>
    </>
  );

  const periodSection = (
    <>
      <DialRow label="days" value={String(durationDays.value)}
        onDec={() => (durationDays.value = Math.max(1, durationDays.value - 1))}
        onInc={() => (durationDays.value = Math.min(14, durationDays.value + 1))} />
      <DialRow label="flow" value={flowIntensityLabel(flowIntensity.value)}
        onDec={() => (flowIntensity.value = Math.max(1, flowIntensity.value - 1))}
        onInc={() => (flowIntensity.value = Math.min(4, flowIntensity.value + 1))} />
      <div class="field">
        <label>symptoms</label>
        <input type="text" value={symptoms.value}
               onInput={(e) => (symptoms.value = (e.target as HTMLInputElement).value)} />
      </div>
    </>
  );

  const bowelSection = (
    <>
      <DialRow label="bristol-like" value={bristolLabel(bristolType.value)}
        onDec={() => (bristolType.value = Math.max(1, bristolType.value - 1))}
        onInc={() => (bristolType.value = Math.min(5, bristolType.value + 1))} />
      <DialRow label="effort" value={effortLabel(effortLevel.value)}
        onDec={() => (effortLevel.value = Math.max(1, effortLevel.value - 1))}
        onInc={() => (effortLevel.value = Math.min(3, effortLevel.value + 1))} />
      <div class="field">
        <label>note</label>
        <input type="text" value={note.value}
               onInput={(e) => (note.value = (e.target as HTMLInputElement).value)} />
      </div>
    </>
  );

  const hydrateSection = (
    <>
      <div class="field">
        <label>UNIT</label>
        <div class="type-tabs" data-testid="hydrate-unit-toggle">
          <button type="button" class={hydrateUnitIsL.value ? "selected" : ""}
                  onClick={() => switchHydrateUnit(true)}>L</button>
          <button type="button" class={hydrateUnitIsL.value ? "" : "selected"}
                  onClick={() => switchHydrateUnit(false)}>OZ</button>
        </div>
      </div>
      <DialRow
        label="volume"
        value={hydrateText.value}
        invalid={hydrateInvalid.value}
        editable
        onInput={onHydrateInput}
        onDec={() => stepHydrate(-hydrateStep())}
        onInc={() => stepHydrate(hydrateStep())}
      />
      <div class="field">
        <label>note</label>
        <input type="text" value={note.value}
               onInput={(e) => (note.value = (e.target as HTMLInputElement).value)} />
      </div>
    </>
  );

  return (
    <div class="modal-overlay" data-testid="modal-overlay">
      <div class="modal" data-testid="input-modal">
        <h2>ADD RECORD</h2>
        <div class="type-tabs" data-testid="type-tabs">
          {RECORD_TYPES.map((t) => (
            <button key={t} type="button" class={type.value === t ? "selected" : ""}
                    data-testid={`tab-${t}`}
                    onClick={() => (type.value = t)}>[ {t.toUpperCase()} ]</button>
          ))}
        </div>
        {type.value === "weight" ? weightSection
          : type.value === "period" ? periodSection
          : type.value === "bowel" ? bowelSection
          : hydrateSection}
        <div class="actions">
          <button type="button" onClick={props.onClose}>CANCEL</button>
          <button type="button" class="save-btn" data-testid="save" disabled={!canSave()}
                  onClick={save}>SAVE</button>
        </div>
      </div>
    </div>
  );
}

/** flowIntensity default is 2 — helper keeps the call site honest. */
function persistenceFlow(v: number): number {
  return v;
}
