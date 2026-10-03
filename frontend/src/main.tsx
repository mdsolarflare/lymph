// Entry point: init the wasm core, hydrate the store, then render. Nothing
// may touch the wasm exports before `await init` completes — module-level
// code in store.ts deliberately does no wasm work for exactly that reason.

import { render } from "preact";
import { App } from "./app.tsx";
import { init } from "lymph-core";
import { initStore } from "./store.ts";

const root = document.getElementById("app")!;

render(<div class="empty-state">INITIALIZING…</div>, root);

await init(new URL("./lymph_core_bg.wasm", import.meta.url));
initStore();
render(<App />, root);

// PWA: register the service worker AFTER first render — the app must be
// usable even if registration fails (file://, old browsers, dev servers).
// updateViaCache: "none" so the browser always revalidates sw.js itself —
// otherwise an HTTP-cached sw.js could pin the app to an old build.
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./sw.js", {
    scope: "./",
    updateViaCache: "none",
  }).catch((err) =>
    console.warn("[pwa] service worker registration failed:", err)
  );
}
