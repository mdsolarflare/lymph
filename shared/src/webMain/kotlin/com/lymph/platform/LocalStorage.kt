package com.lymph.platform

import kotlin.js.JsName

/**
 * Minimal localStorage string access from Kotlin — one CSV per table.
 *
 * Same pattern as [BrowserDownload]: a `js(...)`-initialized val runs an IIFE at module
 * load that installs plain functions on `globalThis` (typed as String — wasmJs forbids
 * Unit, and `dynamic` is avoided entirely for portability), and Kotlin calls them
 * through typed `@JsName` external functions. Only [String]/[Boolean] cross the boundary.
 *
 * JS side catches storage failures (disabled, quota exceeded, privacy mode) so the
 * DAOs can fall back to in-memory-only behavior instead of crashing the app.
 */
private val localStorageHelper: String = js(
    "(function () {" +
        "  if (!globalThis.__lymphStorageGet) {" +
        "    globalThis.__lymphStorageGet = function (k) {" +
        "      try { return window.localStorage.getItem(k); } catch (e) { return null; }" +
        "    };" +
        "    globalThis.__lymphStorageSet = function (k, v) {" +
        "      try { window.localStorage.setItem(k, v); return true; } catch (e) { return false; }" +
        "    };" +
        "  }" +
        "  return 'installed';" +
        "})()"
)

/** Reads [key]; null when absent or when storage is unavailable. Installed by [localStorageHelper]. */
@JsName("__lymphStorageGet")
private external fun lymphStorageGet(key: String): String?

/** Writes [key]=[value]; false when storage is unavailable or the quota is exceeded. */
@JsName("__lymphStorageSet")
private external fun lymphStorageSet(key: String, value: String): Boolean

/** Storage keys — one CSV per table; each value carries the CsvEncoder header, so the format is self-describing. */
object LocalStorage {
    const val WEIGHT_KEY = "lymph.weight_records"
    const val PERIOD_KEY = "lymph.period_records"
    const val BOWEL_KEY = "lymph.bowel_records"

    init {
        check(localStorageHelper == "installed") { "localStorage helper failed to install" }
    }

    /** Reads [key]; null when absent or when storage is unavailable. */
    fun get(key: String): String? = lymphStorageGet(key)

    /**
     * Writes [key]. Returns false (and logs) when storage is unavailable or over quota —
     * callers treat the session as in-memory-only and keep going rather than crashing.
     */
    fun set(key: String, value: String): Boolean {
        val ok = lymphStorageSet(key, value)
        if (!ok) println("WARN: localStorage write failed for '$key' — data will not survive reload")
        return ok
    }
}
