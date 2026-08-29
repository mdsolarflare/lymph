package com.lymph.util

import kotlin.math.roundToLong

/** Exact conversion factor: 1 international avoirdupois pound = exactly 0.45359237 kg (by definition). */
const val KG_PER_LB = 0.45359237

/** Valid range for stored weight, in canonical kilograms. */
const val MIN_WEIGHT_KG = 0.1
const val MAX_WEIGHT_KG = 1000.0

/** Round to at most two decimal places — the precision limit for stored weight values. */
fun roundToHundredths(value: Double): Double = (value * 100.0).roundToLong() / 100.0

/**
 * Parse a user-typed weight string into integer hundredths of the unit.
 * Returns null for empty input, non-numeric characters, or more than two decimal places.
 * A trailing dot ("70.") is accepted and treated as 70.00.
 */
fun parseHundredths(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    var intPart = 0L
    var fracValue = 0
    var fracDigits = 0
    var seenDot = false
    var anyDigit = false
    for (ch in t) {
        when {
            ch == '.' -> { if (seenDot) return null; seenDot = true }
            ch.isDigit() -> {
                anyDigit = true
                if (seenDot) {
                    if (fracDigits >= 2) return null
                    fracValue = fracValue * 10 + (ch - '0')
                    fracDigits++
                } else {
                    intPart = intPart * 10 + (ch - '0')
                }
            }
            else -> return null
        }
    }
    if (!anyDigit) return null
    val frac = when (fracDigits) { 0 -> 0; 1 -> fracValue * 10; else -> fracValue }
    val hundredths = intPart * 100 + frac
    return if (hundredths > Int.MAX_VALUE.toLong()) null else hundredths.toInt()
}

/**
 * Format a weight with up to two decimal places, trimming trailing zeros:
 * 70.0 -> "70", 70.4 -> "70.4", 69.8531 -> "69.85".
 */
fun formatWeight(value: Double): String {
    val hundredths = (value * 100.0).roundToLong()
    val intPart = hundredths / 100
    val frac = if (hundredths % 100 < 0) -(hundredths % 100) else hundredths % 100
    return when {
        frac == 0L -> "$intPart"
        frac % 10 == 0L -> "${intPart}.${frac / 10}"
        else -> "${intPart}.${if (frac < 10) "0$frac" else frac}"
    }
}
