// Input modal — per-type controls with the weight semantics ported exactly:
// integer hundredths (no float drift), KG/LB toggle with exact factor,
// validation against the canonical stored kg, ±0.1 dials, prefill from last
// record, defaults valid on open.

import { useSignal, useSignalEffect } from "@preact/signals";
import { checkWeight, formatWeight, hundredthsLbToKg, kgToHundredthsLb, parseHundredths } from "lymph-core";
import { useEffect } from "preact/hooks";
import type { VNode } from "preact";
import { insertBowel, insertPeriod, insertWeight } from "./store.ts";

export type RecordType = "weight" | "period" | "bowel";
export const RECORD_TYPES: RecordType[] = ["weight", "period", "bowel"];

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
  const bristolType = useSignal(4);
  const effortLevel = useSignal(3);
  const weightInvalid = useSignal(false);

  // Prefill once per MOUNT — App mounts this component only while the modal
  // is open (fresh instance per open, same as the Kotlin app), so this runs
  // on every open. All hooks run before any early return (hook-order rule).
  const prefill = () => {
    const start = props.lastWeightKg ?? 70;
    hundredths.value = Math.round(start * 100);
    weightText.value = formatWeight(start);
    unitIsLb.value = false;
    weightInvalid.value = false;
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

  const canSave = () => {
    if (type.value !== "weight") return true;
    const h = parsedHundredths();
    if (h === undefined) return false;
    return checkWeight(h, unitIsLb.value).valid;
  };

  const save = () => {
    if (!canSave()) return;
    const h = parsedHundredths() ?? hundredths.value;
    const checkResult = checkWeight(h, unitIsLb.value);
    if (type.value === "weight") {
      insertWeight({ weightKg: checkResult!.canonicalKg, note: note.value || null });
    } else if (type.value === "period") {
      insertPeriod({
        durationDays: durationDays.value,
        flowIntensity: persistenceFlow(flowIntensity.value),
        symptoms: symptoms.value || null,
      });
    } else {
      insertBowel({
        bristolType: bristolType.value,
        effortLevel: effortLevel.value,
        note: note.value || null,
      });
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
      <DialRow label="duration" value={String(durationDays.value)}
        onDec={() => (durationDays.value = Math.max(1, durationDays.value - 1))}
        onInc={() => (durationDays.value = Math.min(14, durationDays.value + 1))} />
      <DialRow label="flow" value={String(flowIntensity.value)}
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
      <DialRow label="bristol" value={String(bristolType.value)}
        onDec={() => (bristolType.value = Math.max(1, bristolType.value - 1))}
        onInc={() => (bristolType.value = Math.min(7, bristolType.value + 1))} />
      <DialRow label="effort" value={String(effortLevel.value)}
        onDec={() => (effortLevel.value = Math.max(1, effortLevel.value - 1))}
        onInc={() => (effortLevel.value = Math.min(5, effortLevel.value + 1))} />
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
          : bowelSection}
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
