// PWA hook for Lymph — install prompt + service-worker update UX.
// Port of taskman's usePWA to Preact + signals.
//
// Handles:
// - SW registration status (registered in main.tsx; here we only observe)
// - `beforeinstallprompt` capture → `canInstall` + `promptInstall()`
// - Standalone display detection (media query)
// - New-version detection: `updatefound` → `waiting` worker → toast
//   approval → `SKIP_WAITING` message → `controllerchange` → reload

import { useSignal } from "@preact/signals";
import { useEffect } from "preact/hooks";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

export interface UsePwaReturn {
  /** The install prompt is available (Chromium fires beforeinstallprompt). */
  canInstall: boolean;
  /** App is running standalone (installed / display-mode: standalone). */
  isStandalone: boolean;
  /** A replacement service worker is installed and waiting for approval. */
  waitingToReload: boolean;
  /** Show the browser install prompt (no-op if unavailable). */
  promptInstall: () => Promise<void>;
  /** Approve the update: new SW activates, then the page reloads once. */
  applyUpdate: () => void;
}

export function usePWA(): UsePwaReturn {
  const installEvent = useSignal<BeforeInstallPromptEvent | null>(null);
  const isStandalone = useSignal(
    globalThis.matchMedia?.("(display-mode: standalone)").matches ?? false,
  );
  const waitingToReload = useSignal(false);
  // Set by applyUpdate() so the controllerchange listener only reloads for
  // user-approved updates, not for the SW's first activation (clients.claim()).
  const updateApproved = useSignal(false);

  useEffect(() => {
    const onBeforeInstall = (e: Event) => {
      e.preventDefault();
      installEvent.value = e as BeforeInstallPromptEvent;
    };
    const onInstalled = () => {
      installEvent.value = null;
    };

    globalThis.addEventListener("beforeinstallprompt", onBeforeInstall);
    globalThis.addEventListener("appinstalled", onInstalled);

    // Track standalone display-mode changes (user installs while open).
    const mq = globalThis.matchMedia?.("(display-mode: standalone)");
    const onModeChange = () => {
      isStandalone.value = mq!.matches;
    };
    mq?.addEventListener?.("change", onModeChange);

    if ("serviceWorker" in navigator) {
      const sw = navigator.serviceWorker;
      // A waiting worker may already exist at mount (update checked earlier).
      sw.ready.then((reg) => {
        if (reg.waiting) waitingToReload.value = true;
        reg.addEventListener("updatefound", () => {
          const incoming = reg.installing;
          if (!incoming) return;
          incoming.addEventListener("statechange", () => {
            if (incoming.state === "installed" && reg.active) {
              waitingToReload.value = true;
            }
          });
        });
      });

      // Only reload when the user approved an update. `controllerchange` also
      // fires on FIRST activation (clients.claim()), which must not reload a
      // brand-new visitor's page.
      const onControllerChange = () => {
        if (updateApproved.value) globalThis.location.reload();
      };
      sw.addEventListener("controllerchange", onControllerChange);

      return () => {
        globalThis.removeEventListener("beforeinstallprompt", onBeforeInstall);
        globalThis.removeEventListener("appinstalled", onInstalled);
        mq?.removeEventListener?.("change", onModeChange);
        sw.removeEventListener("controllerchange", onControllerChange);
      };
    }

    return () => {
      globalThis.removeEventListener("beforeinstallprompt", onBeforeInstall);
      globalThis.removeEventListener("appinstalled", onInstalled);
      mq?.removeEventListener?.("change", onModeChange);
    };
  }, []);

  const promptInstall = async () => {
    const ev = installEvent.value;
    if (!ev) return;
    await ev.prompt();
    await ev.userChoice; // either way, the prompt is consumed
    installEvent.value = null;
  };

  const applyUpdate = () => {
    updateApproved.value = true;
    navigator.serviceWorker.getRegistration()?.then((reg) => {
      reg?.waiting?.postMessage({ type: "SKIP_WAITING" });
      // controllerchange fires when the new SW takes over; the listener
      // above reloads the page exactly once.
    });
  };

  return {
    canInstall: installEvent.value !== null,
    isStandalone: isStandalone.value,
    waitingToReload: waitingToReload.value,
    promptInstall,
    applyUpdate,
  };
}
