# Data Exchange Format

Lymph exchanges data as a ZIP archive of CSV files, one per record type. The format is
stable across all targets (Android, iOS, web) and is the only supported migration path
— the app is 100% client-side and has no cloud sync.

## Archive layout

`lymph-export.zip` (export) and any import archive both contain up to four entries:

| Entry name            | Record type | CSV header (exact)                                   |
| --------------------- | ----------- | ---------------------------------------------------- |
| `weight_records.csv`  | Weight      | `id,timestamp,weightKg,note`                          |
| `period_records.csv`  | Period      | `id,timestamp,durationDays,flowIntensity,symptoms`    |
| `bowel_records.csv`   | Bowel       | `id,timestamp,bristolType,effortLevel,note`           |
| `hydrate_records.csv` | Hydrate     | `id,timestamp,volumeMl,note`                          |

Entry names are case-sensitive exact matches. An archive may omit tables entirely
(importing a weight-only archive works, leaving period and bowel untouched).

### ZIP encoding

Entries are stored with **no compression** (`STORED` method). This is deliberate: it
keeps the parser trivial (no inflate step is required in Kotlin/Wasm), and the payload
is small enough that compression gains are negligible. CRC-32 is verified against the
ZIP header on import.

## CSV schema

CSV follows RFC 4180:

- Header row first, exact string as listed above. A header mismatch fails the whole
  table (the importer refuses to guess columns).
- One record per row, comma-separated.
- The last column (`note` / `symptoms`) is always quoted; embedded `"` characters are
  escaped as `""` (RFC 4180 doubling).
- Line endings: LF on export; import accepts LF, CRLF, and a leading BOM.

### Column meaning

| Column          | Type    | Notes                                                              |
| --------------- | ------- | ------------------------------------------------------------------ |
| `id`            | string  | UUID assigned at insert time; never reused or updated by import.  |
| `timestamp`     | long    | Epoch milliseconds (UTC).                                          |
| `weightKg`      | double  | 0.1 – 1000 (inclusive).                                             |
| `note`          | string  | Free text, may be empty.                                           |
| `durationDays`  | int     | 1 – 14.                                                            |
| `flowIntensity` | int     | 1 (lightest) – 4 (heaviest).                                       |
| `symptoms`      | string  | Free text, may be empty.                                           |
| `bristolType`   | int     | 1 – 5 (bristol-like scale: 1 like pebbles, 3 perfect, 5 water).    |
| `effortLevel`   | int     | 1 (low) – 3 (high).                                                |
| `volumeMl`      | double  | 1 – 10000 (inclusive), canonical milliliters.                      |

Validation ranges mirror the input modal in the UI. Rows out of range are counted as
**invalid** and skipped; they do not abort the import.

## Import semantics

**Insert-only.** A record whose `id` already exists in the database is skipped
(counted as a duplicate) and never modified. The rationale: IDs are UUIDs assigned at
insert time, so two legitimate records can never share an ID — re-importing an old
export is a no-op, not a data corruption path. The same rule applies to duplicates
within the archive itself (first occurrence wins).

**Best-effort.** Only structural problems with the archive itself abort the import
(not a ZIP, truncated, CRC mismatch, unsupported compression, no recognizable entries)
— these surface as `IMPORT FAILED — <reason>`. Per-table header mismatches and
per-row validation failures never abort the run; they are counted and reported in the
summary so nothing is silently lost.

Example summaries (shown as a snackbar after import):

```
IMPORT OK — 3 added
IMPORT OK — 0 added, 3 duplicates
IMPORT OK — 5 added, 2 invalid
IMPORT OK — 4 added, 1 duplicates, 2 invalid | period: bad header
IMPORT FAILED — not a zip archive
```

## Round-tripping

The only fully supported workflow is **export → (optionally edit a CSV by hand,
respecting the schema) → import**. Import is not a backup/restore primitive — it never
deletes or overwrites rows, so it cannot roll the database back to an earlier state.

Import is currently wired on **web only** (`BrowserFilePicker` in `webMain`). Android
and iOS receive a `null` picker and the UI hides the import affordance; native file
pickers are future work.
