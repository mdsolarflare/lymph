//! # lymph-core
//!
//! Pure data logic for Lymph, the offline-first biometric data sanctuary.
//! Everything here is deterministic and side-effect free: entities, validation
//! ranges, CSV/ZIP encode+decode, insert-only import semantics, weight math,
//! and chart geometry. No I/O, no clock, no browser APIs — storage and rendering
//! belong to the platform shell (see docs/SPEC.md).
//!
//! Contract sources of truth:
//! - CSV/ZIP format: docs/DATA-EXCHANGE.md
//! - Pinned behavior: the ported test suite (48 tests, parity with the Kotlin
//!   app's commonTest baseline).

pub mod chart;
pub mod crc32;
pub mod csv;
pub mod entity;
pub mod importer;
pub mod weight;
pub mod zip;

#[cfg(target_arch = "wasm32")]
mod wasm;
