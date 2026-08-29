package com.lymph.ui.chart

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import com.lymph.util.roundToHundredths

/** X-range options for the weight trend chart. */
enum class WeightRange(val label: String, val days: Long?) {
    DAYS_90("90D", 90),
    YEAR("1Y", 365),
    ALL("ALL", null)
}

/** One sample: epoch millis + value (kg). */
data class Sample(val t: Double, val v: Double)

/** A single plotted point in normalized chart space (xNorm: 0.0 = left .. 1.0 = right). */
data class ChartPoint(val xNorm: Float, val value: Double)

/**
 * Mapped series ready for rendering. All geometry is normalized to 0..1;
 * [yMin]/[yMax] and [ticks] are in real units (kg) for axis labels.
 */
class WeightSeries(
    val points: List<ChartPoint>, // chronological order
    val yMin: Double,             // axis lower bound
    val yMax: Double,             // axis upper bound
    val ticks: List<Double>       // y-axis tick values within [yMin, yMax]
) {
    /** Normalized y position of a value: 0.0 = bottom, 1.0 = top. */
    fun yNorm(value: Double): Float = ((value - yMin) / (yMax - yMin)).toFloat()
}

/**
 * Inclusive lower timestamp bound for the given range, or null for ALL.
 * Pure function so it is unit-testable without Compose.
 */
fun cutoffMillis(range: WeightRange, now: Long): Long? =
    range.days?.let { now - it * 86_400_000L }

/**
 * Maps chronological weight samples into chart geometry.
 * Returns null for empty input.
 *
 * X mapping is time-proportional; if all timestamps are equal the points are
 * spread evenly instead (they are distinct records and would otherwise stack).
 * Y axis auto-scales to [min,max] + 10% padding — never zero-based, so a
 * 70→82 kg trend does not look flat. A flat series gets symmetric padding.
 */
fun mapWeightSeries(samples: List<Sample>): WeightSeries? {
    if (samples.isEmpty()) return null

    val min = samples.minOf { it.v }
    val max = samples.maxOf { it.v }
    val span = max - min
    val yMin: Double
    val yMax: Double
    if (span < 1e-9) {
        // Flat series — pad symmetrically so the line sits mid-chart.
        val pad = if (min > 0.0) max(min * 0.02, 1.0) else 1.0
        yMin = min - pad
        yMax = min + pad
    } else {
        val pad = span * 0.1
        yMin = min - pad
        yMax = max + pad
    }

    val n = samples.size
    val tMin = samples.minOf { it.t }
    val tSpan = samples.maxOf { it.t } - tMin
    val points = samples.mapIndexed { i, s ->
        val xNorm = when {
            n == 1 -> 0.5f
            tSpan < 1e-9 -> if (n > 1) i.toFloat() / (n - 1) else 0.5f
            else -> ((s.t - tMin) / tSpan).toFloat()
        }
        ChartPoint(xNorm, s.v)
    }

    return WeightSeries(points, yMin, yMax, niceTicks(yMin, yMax))
}

/**
 * "Nice" tick values for a y-axis: 1/2/5×10ⁿ steps, ~4 ticks, step ≥ 0.01 so
 * labels stay within two decimal places (weight precision). All returned
 * values are rounded to hundredths and lie within [lo, hi].
 */
fun niceTicks(lo: Double, hi: Double, targetCount: Int = 4): List<Double> {
    val span = hi - lo
    if (span <= 0.0) return listOf(roundToHundredths(lo))

    val rawStep = span / targetCount
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalized = rawStep / magnitude
    val niceNormalized = when {
        normalized < 1.5 -> 1.0
        normalized < 3.5 -> 2.0
        normalized < 7.5 -> 5.0
        else -> 10.0
    }
    val step = max(niceNormalized * magnitude, 0.01)

    val ticks = mutableListOf<Double>()
    var v = ceil(lo / step) * step
    while (v <= hi + step * 1e-6 && ticks.size < 20) {
        val rounded = roundToHundredths(v)
        if (ticks.lastOrNull() != rounded) ticks.add(rounded)
        v += step
    }
    return ticks
}
