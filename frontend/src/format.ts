// Shared pure helpers: terminal timestamp format + UUID v4.
// Both are UI-side only — the core crate is deterministic and takes ids as data.

/** `YYYY-MM-DD HH:MM`, zero-padded, UTC — identical output to the Kotlin app's formatTimestamp. */
export function formatTimestamp(epochMillis: number): string {
  const d = new Date(epochMillis);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`;
}

/** `YYYY-MM-DD` UTC — for chart axis labels. */
export function formatDate(epochMillis: number): string {
  const d = new Date(epochMillis);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())}`;
}

/** UUID v4 via the platform RNG (spec-compliant in all our runtimes). */
export function uuidV4(): string {
  return crypto.randomUUID();
}

export function nowMillis(): number {
  return Date.now();
}
