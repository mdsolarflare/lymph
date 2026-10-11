// Record panels: trend chart above a paginated table (10/page, newest first),
// terminal pager `[ n/N ]` rendered only when >1 page. Page state clamps as
// record counts change.

import type { Signal } from "@preact/signals";
import { useSignal } from "@preact/signals";
import type { VNode } from "preact";
import { TrendChart } from "./chart.tsx";

const PAGE_SIZE = 10;

function clampPage(page: number, pageCount: number): number {
  return Math.max(1, Math.min(page, pageCount));
}

export interface PagerState {
  page: number;
  pageCount: number;
}

/** Terminal pager: [ n/N ] with < > buttons, only when >1 page. */
export function Pager({ state }: { state: Signal<PagerState> }) {
  const { page, pageCount } = state.value;
  if (pageCount <= 1) return null;
  const go = (next: number) => {
    state.value = { page: clampPage(next, pageCount), pageCount };
  };
  return (
    <div class="pager" data-testid="pager">
      <span class="page-label">[ {page}/{pageCount} ]</span>
      <button data-testid="pager-prev" disabled={page <= 1} onClick={() => go(page - 1)}>
        {"<"}
      </button>
      <button data-testid="pager-next" disabled={page >= pageCount} onClick={() => go(page + 1)}>
        {">"}
      </button>
    </div>
  );
}

export interface PanelProps<T> {
  title: string;
  records: T[];
  now: number;
  chart: VNode;
  columns: (string | { label: string; class?: string })[];
  row: (record: T) => (VNode | string)[];
  onDelete: (id: string) => void;
}

export function Panel<T extends { id: string }>(props: PanelProps<T>) {
  const pager = useSignal<PagerState>({ page: 1, pageCount: 1 });

  const records = props.records;
  const pageCount = Math.max(1, Math.ceil(records.length / PAGE_SIZE));
  const page = clampPage(pager.value.page, pageCount);
  // Clamp on record-count change; guarded so the write converges in one pass
  // (an unguarded signal write during render would loop forever).
  if (pager.value.page !== page || pager.value.pageCount !== pageCount) {
    pager.value = { page, pageCount };
  }
  const pageRecords = records.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <section class="panel" data-testid="panel">
      <header class="panel-header">
        <span>[ {props.title} ]</span>
        <span class="count">{records.length} records</span>
      </header>
      <div class="panel-body">
        {records.length === 0 ? (
          <div class="empty-state">{"> no data"}</div>
        ) : (
          <>
            {props.chart}
            <table class="records-table" data-testid="records-table">
              <thead>
                <tr>
                  {props.columns.map((c, i) => (
                    <th key={i} class={typeof c === "object" ? c.class : undefined}>
                      {typeof c === "object" ? c.label : c}
                    </th>
                  ))}
                  <th class="del-col" />
                </tr>
              </thead>
              <tbody>
                {pageRecords.map((record) => (
                  <tr key={record.id} data-record-id={record.id}>
                    {props.row(record).map((cell, i) => (
                      <td key={i}>{cell}</td>
                    ))}
                    <td class="del-col">
                      <button class="del-btn" data-testid="delete-record"
                              title="delete" onClick={() => props.onDelete(record.id)}>x</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pager state={pager} />
          </>
        )}
      </div>
    </section>
  );
}
