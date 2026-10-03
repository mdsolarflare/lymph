// App shell: responsive layout (mobile ≤840dp / desktop master-detail >840dp),
// drawer, snackbar with the exact contract strings, theme toggle, import via
// hidden file input.

import { useSignal } from "@preact/signals";
import { formatIntegerTick, formatWeight } from "lymph-core";
import { useEffect } from "preact/hooks";
import type { VNode } from "preact";
import { TrendChart } from "./chart.tsx";
import { exportZip, importFromFile } from "./io.ts";
import { InputModal } from "./input-modal.tsx";
import { formatDate, formatTimestamp, nowMillis } from "./format.ts";
import { usePWA } from "./pwa.ts";
import { Panel } from "./panel.tsx";
import { bowels, deleteBowel, deletePeriod, deleteWeight, periods, theme, weights } from "./store.ts";

const BREAKPOINT = 840; // dp — same as the Kotlin LayoutStrategy

type Snackbar = { text: string } | null;

export function App() {
  const now = nowMillis();
  const pwa = usePWA();
  const modalOpen = useSignal(false);
  const drawerOpen = useSignal(false);
  const snackbar = useSignal<Snackbar>(null);
  const selected = useSignal<"weight" | "period" | "bowel">("weight");
  const isDesktop = useSignal(window.innerWidth > BREAKPOINT);
  useEffect(() => {
    const onResize = () => {
      isDesktop.value = window.innerWidth > BREAKPOINT;
    };
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  const showToast = (text: string) => {
    snackbar.value = { text };
    setTimeout(() => (snackbar.value = null), 4000); // auto-dismiss ~4s
  };

  const doExport = () => {
    exportZip();
    showToast("EXPORT OK — zip generated");
  };

  // Hidden file input owns import delivery (same pattern as the Kotlin app's
  // BrowserFilePicker with its persistent #__lymph_file_input).
  let fileInput: HTMLInputElement | null = null;
  const onImportFile = async (e: Event) => {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    try {
      const summary = await importFromFile(file);
      showToast(summary);
    } catch (err) {
      showToast(`IMPORT FAILED — ${err instanceof Error ? err.message : "unreadable archive"}`);
    }
    input.value = ""; // allow re-selecting the same file
  };

  const triggerImport = () => {
    drawerOpen.value = false;
    fileInput?.click();
  };

  // ── panels ─────────────────────────────────────────────────────────────

  const weightSamples = () => {
    const arr = [...weights.value].sort((a, b) => a.timestamp - b.timestamp);
    return [arr.map((r) => ({ t: r.timestamp, v: r.weightKg }))];
  };
  const periodSamples = () => {
    const arr = [...periods.value].sort((a, b) => a.timestamp - b.timestamp);
    return [
      arr.map((r) => ({ t: r.timestamp, v: r.durationDays })),
      arr.map((r) => ({ t: r.timestamp, v: r.flowIntensity })),
    ];
  };
  const bowelSamples = () => {
    const arr = [...bowels.value].sort((a, b) => a.timestamp - b.timestamp);
    return [
      arr.map((r) => ({ t: r.timestamp, v: r.bristolType })),
      arr.map((r) => ({ t: r.timestamp, v: r.effortLevel })),
    ];
  };

  const weightPanel = (
    <Panel
      title="WEIGHT"
      records={weights.value}
      now={now}
      chart={
        <TrendChart samples={weightSamples()} minTickStep={0.01}
          formatTick={formatWeight} now={now} />
      }
      columns={["id", "date", "weight", "note"]}
      row={(r) => [r.id.slice(0, 8), formatTimestamp(r.timestamp), formatWeight(r.weightKg), r.note ?? ""]}
      onDelete={deleteWeight}
    />
  );
  const periodPanel = (
    <Panel
      title="PERIOD"
      records={periods.value}
      now={now}
      chart={
        <TrendChart samples={periodSamples()} minTickStep={1}
          formatTick={formatIntegerTick} now={now}
          legend={["DURATION (days)", "FLOW [1-4]"]} />
      }
      columns={["id", "date", "duration", "flow", "symptoms"]}
      row={(r) => [r.id.slice(0, 8), formatTimestamp(r.timestamp), String(r.durationDays), String(r.flowIntensity), r.symptoms ?? ""]}
      onDelete={deletePeriod}
    />
  );
  const bowelPanel = (
    <Panel
      title="BOWEL"
      records={bowels.value}
      now={now}
      chart={
        <TrendChart samples={bowelSamples()} minTickStep={1}
          formatTick={formatIntegerTick} now={now}
          legend={["BRISTOL[1-7]", "EFFORT [1-5]"]} />
      }
      columns={["id", "date", "bristol", "effort", "note"]}
      row={(r) => [r.id.slice(0, 8), formatTimestamp(r.timestamp), String(r.bristolType), String(r.effortLevel), r.note ?? ""]}
      onDelete={deleteBowel}
    />
  );

  const panelsFor = (): VNode[] => [weightPanel, periodPanel, bowelPanel];
  const panelFor = (t: "weight" | "period" | "bowel"): VNode =>
    t === "weight" ? weightPanel : t === "period" ? periodPanel : bowelPanel;

  const navItems = ["weight", "period", "bowel"] as const;

  const sidebar = (
    <aside class="sidebar desktop-only" data-testid="sidebar">
      <nav>
        {navItems.map((t) => (
          <button key={t} class={`nav-item${selected.value === t ? " selected" : ""}`}
                  data-testid={`nav-${t}`}
                  onClick={() => (selected.value = t)}>{t.toUpperCase()}</button>
        ))}
      </nav>
      <div class="sidebar-actions">
        <button data-testid="add-record" onClick={() => (modalOpen.value = true)}>ADD RECORD</button>
        <button data-testid="export-data" onClick={doExport}>EXPORT DATA</button>
        <button data-testid="import-data" onClick={triggerImport}>IMPORT DATA</button>
      </div>
    </aside>
  );

  const drawer = drawerOpen.value && (
    <div class="drawer-overlay" onClick={() => {
      drawerOpen.value = false;
    }}
         data-testid="drawer-overlay">
      <div class="drawer" onClick={(e) => e.stopPropagation()}>
        <button class="drawer-item" data-testid="drawer-export"
                onClick={() => {
                  drawerOpen.value = false;
                  doExport();
                }}>Export Data</button>
        <button class="drawer-item" data-testid="drawer-import"
                onClick={triggerImport}>Import Data</button>
        <button class="drawer-item" data-testid="drawer-theme"
                onClick={() => {
                  theme.value = theme.value === "dark" ? "light" : "dark";
                }}>
          Theme: {theme.value === "dark" ? "LIGHT" : "DARK"}
        </button>
        {pwa.canInstall && (
          <button class="drawer-item" data-testid="drawer-install"
                  onClick={() => {
                    drawerOpen.value = false;
                    pwa.promptInstall();
                  }}>Install App…</button>
        )}
      </div>
    </div>
  );

  const themeToggle = (
    <button class="hamburger mobile-only" data-testid="theme-toggle" title="theme"
            onClick={() => (theme.value = theme.value === "dark" ? "light" : "dark")}>
      {theme.value === "dark" ? "◐" : "◑"}
    </button>
  );

  return (
    <>
      <input type="file" accept=".zip" style="display:none" id="__lymph_file_input"
             onChange={onImportFile} ref={(el) => (fileInput = el as HTMLInputElement)} />
      {isDesktop.value
        ? (
          <div class="expanded-layout">
            {sidebar}
            <main class="detail">
              {panelFor(selected.value)}
            </main>
          </div>
        )
        : (
          <div class="mobile-layout">
            <header class="topbar">
              <button class="hamburger" data-testid="hamburger"
                      onClick={() => (drawerOpen.value = true)}>≡</button>
              <span class="title">LYMPH</span>
              {themeToggle}
            </header>
            <div class="panels">
              {panelsFor().map((p, i) => (
                <div key={i} class="panel-slot">{p}</div>
              ))}
            </div>
            <button class="fab" data-testid="fab" onClick={() => (modalOpen.value = true)}>+</button>
          </div>
        )}
      {drawer}
      {modalOpen.value && (
        <InputModal
          open={true}
          onClose={() => {
            modalOpen.value = false;
          }}
          lastWeightKg={weights.value.length ? weights.value[0].weightKg : null}
          onSaved={() => {
            modalOpen.value = false;
            showToast("RECORD SAVED");
          }}
        />
      )}
      {snackbar.value && <div class="snackbar" data-testid="snackbar">{snackbar.value.text}</div>}
      {pwa.waitingToReload && (
        <div class="update-toast" data-testid="update-toast">
          <span>A new version is ready.</span>
          <button data-testid="apply-update" onClick={pwa.applyUpdate}>RELOAD</button>
        </div>
      )}
    </>
  );
}
