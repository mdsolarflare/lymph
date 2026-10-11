// SVG trend chart over the pure chart model from the Rust core.
// Real DOM text labels (no canvas, no OCR) — tick labels and dates are
// queryable <text> nodes. Two series max (cyan + amber) for period/bowel.
//
// Chart geometry is DERIVED AT RENDER TIME from (range signal × props):
// reading range.value in render subscribes to range clicks, and every parent
// re-render (insert / import / delete passing new samples) re-derives too, so
// the timeline always reflects current data. The earlier version mapped inside
// a useSignalEffect, which tracked only signals — a new record changed props,
// never re-ran the effect, and the chart went stale.

import { useSignal } from "@preact/signals";
import { cutoffMillis, mapMultiSeries } from "lymph-core";
import type { VNode } from "preact";
import { formatDate } from "./format.ts";

export type RangeLabel = "90D" | "1Y" | "ALL";
const RANGE_LABELS: RangeLabel[] = ["90D", "1Y", "ALL"];

export interface TrendChartProps {
  /** Series of {t (epoch millis), v} in chronological order. One or two entries. */
  samples: { t: number; v: number }[][];
  /** Whole-number ticks (period/bowel) vs hundredth ticks (weight). */
  minTickStep: number;
  /** Tick formatter: weight uses formatWeight, integers use formatIntegerTick. */
  formatTick: (v: number) => string;
  /** Legend labels, one per series (rendered when 2 series). */
  legend?: [string] | [string, string];
  /** Map xNorm -> date label for the axis ends. */
  now: number;
}

export function TrendChart(props: TrendChartProps) {
  const range = useSignal<RangeLabel>("90D");
  // Local range state resets when the component unmounts (panel switch) — same
  // accepted v0.1 behavior as the Kotlin app.

  // Derivation happens on EVERY render (range click or new samples prop).
  const cutoff = range.value === "ALL"
    ? undefined
    : cutoffMillis(range.value, props.now);
  const visible = props.samples.map((series) =>
    cutoff === undefined ? series : series.filter((s) => s.t >= cutoff)
  );
  const mapped = mapMultiSeries(visible, props.minTickStep);

  const W = 640, H = 180, PAD_L = 48, PAD_R = 8, PAD_T = 8, PAD_B = 34;
  const plotW = W - PAD_L - PAD_R;
  const plotH = H - PAD_T - PAD_B;

  const seriesColor = (i: number) => (i === 0 ? "var(--series1)" : "var(--series2)");

  const body = (): VNode | null => {
    const m = mapped;
    if (!m) {
      const hasAnyData = props.samples.some((s) => s.length > 0);
      return <div class="empty-state">{hasAnyData ? "> no data in range" : "> no data"}</div>;
    }
    const px = (xNorm: number) => PAD_L + xNorm * plotW;
    const py = (yNorm: number) => PAD_T + (1 - yNorm) * plotH;
    const yNorm = (v: number) => (v - m.yMin) / (m.yMax - m.yMin);

    // Date axis: timestamps of the visible (range-filtered) samples.
    const cutoff = range.value === "ALL" ? undefined : cutoffMillis(range.value, props.now);
    const visibleTimes = props.samples
      .flat()
      .map((s) => s.t)
      .filter((t) => cutoff === undefined || t >= cutoff);
    const firstT = visibleTimes.length ? visibleTimes[0] : undefined;
    const lastT = visibleTimes.length ? visibleTimes[visibleTimes.length - 1] : undefined;

    return (
      <svg class="chart-svg" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none"
           role="img" data-testid="trend-chart">
        {/* gridlines + y tick labels */}
        {m.ticks.map((t) => (
          <g key={`t-${t}`}>
            <line x1={PAD_L} x2={W - PAD_R} y1={py(yNorm(t))} y2={py(yNorm(t))}
                  stroke="var(--grid)" stroke-width="1" />
            <text x={PAD_L - 6} y={py(yNorm(t)) + 4} text-anchor="end"
                  fill="var(--dim)" font-size="10">
              {props.formatTick(t)}
            </text>
          </g>
        ))}
        {/* series polylines + square markers */}
        {m.series.map((line, i) => (
          <g key={`s-${i}`}>
            {line.points.length > 1 && (
              <polyline
                fill="none" stroke={seriesColor(i)} stroke-width="2"
                points={line.points.map((p) => `${px(p.xNorm)},${py(yNorm(p.value))}`).join(" ")}
              />
            )}
            {line.points.map((p, j) => (
              <rect key={`m-${j}`} x={px(p.xNorm) - 3} y={py(yNorm(p.value)) - 3}
                    width="6" height="6" fill={seriesColor(i)} />
            ))}
          </g>
        ))}
        {/* date axis labels */}
        {firstT !== undefined && (
          <text x={PAD_L} y={H - 8} fill="var(--dim)" font-size="10">
            {formatDate(firstT)}
          </text>
        )}
        {lastT !== undefined && lastT !== firstT && (
          <text x={W - PAD_R} y={H - 8} text-anchor="end" fill="var(--dim)" font-size="10">
            {formatDate(lastT)}
          </text>
        )}
      </svg>
    );
  };

  return (
    <div class="chart-wrap">
      <div class="range-selector" data-testid="range-selector">
        {RANGE_LABELS.map((label) => (
          <button
            key={label}
            class={range.value === label ? "selected" : ""}
            data-testid={`range-${label}`}
            onClick={() => (range.value = label)}
          >
            [{label}]
          </button>
        ))}
      </div>
      {body()}
      {props.legend && props.legend.length === 2 && mapped && (
        <div class="chart-legend" data-testid="chart-legend">
          <span class="key"><span class="swatch s1" /> {props.legend[0]}</span>
          <span class="key"><span class="swatch s2" /> {props.legend[1]}</span>
        </div>
      )}
    </div>
  );
}
