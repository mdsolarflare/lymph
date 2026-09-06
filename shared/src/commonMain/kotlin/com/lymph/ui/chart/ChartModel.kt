package com.lymph.ui.chart

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import com.lymph.util.roundToHundredths

/** X-range options for the trend charts (weight / period / bowel). */
enum class TimeRange(val label: String, val days: Long?) {
    DAYS_90("90D", 90),
    YEAR("1Y", 365),
    ALL("ALL", null)
}

/** One sample: epoch millis + value. */
data class Sample(val t: Double, val v: Double)

/** A single plotted point in normalized chart space (xNorm: 0.0 = left .. 1.0 = right). */
data class ChartPoint(val xNorm: Float, val value: Double)

/** One line's points, chronological order. */
class SeriesLine(val points: List<ChartPoint>)

/**
 * Mapped series ready for rendering. All geometry is normalized to 0..1;
 * [yMin]/[yMax] and [ticks] are in real units (shared by every line) for axis labels.
 */
class MultiSeries(
    val series: List<SeriesLine>, // one per plotted parameter, chronological order
    val yMin: Double,             // shared axis lower bound
    val yMax: Double,             // shared axis upper bound
    val ticks: List<Double>       // y-axis tick values within [yMin, yMax]
) {
    /** Normalized y position of a value: 0.0 = bottom, 1.0 = top. */
    fun yNorm(value: Double): Float = ((value - yMin) / (yMax - yMin)).toFloat()
}

/**
 * Inclusive lower timestamp bound for the given range, or null for ALL.
 * Pure function so it is unit-testable without Compose.
 */
fun cutoffMillis(range: TimeRange, now: Long): Long? =
    range.days?.let { now - it * 86_400_000L }

/**
 * Maps one or more chronological sample series into shared chart geometry.
 * Returns null when every input list is empty.
 *
 * All lines share a single y-axis auto-scaled to the combined [min,max] + 10%
 * padding — never zero-based, so small trends do not look flat. A fully flat
 * input gets symmetric padding. X mapping is time-proportional over the union
 * of all timestamps; if every timestamp is equal, each line's points are spread
 * evenly instead (they are distinct records and would otherwise stack).
 */
fun mapMultiSeries(allSamples: List<List<Sample>>, minTickStep: Double = 0.01): MultiSeries? {
    val total = allSamples.sumOf { it.size }
    if (total == 0) return null

    var min = Double.MAX_VALUE
    var max = -Double.MAX_VALUE
    for (samples in allSamples) for (s in samples) {
        if (s.v < min) min = s.v
        if (s.v > max) max = s.v
    }
    val span = max - min
    val yMin: Double
    val yMax: Double
    if (span < 1e-9) {
        // Flat input — pad symmetrically so the lines sit mid-chart.
        val pad = if (min > 0.0) max(min * 0.02, 1.0) else 1.0
        yMin = min - pad
        yMax = min + pad
    } else {
        val pad = span * 0.1
        yMin = min - pad
        yMax = max + pad
    }

    // Union of timestamps across non-empty series only (minOf on an empty list throws).
    val nonEmpty = allSamples.filter { it.isNotEmpty() }
    val tMin = nonEmpty.minOfOrNull { it.minOf { s -> s.t } } ?: return null
    val tSpan = (nonEmpty.maxOfOrNull { it.maxOf { s -> s.t } } ?: 0.0) - tMin

    val series = allSamples.map { samples ->
        val n = samples.size
        if (n == 0) SeriesLine(emptyList())
        else SeriesLine(samples.mapIndexed { i, s ->
            val xNorm = when {
                n == 1 -> 0.5f
                tSpan < 1e-9 -> i.toFloat() / (n - 1)
                else -> ((s.t - tMin) / tSpan).toFloat()
            }
            ChartPoint(xNorm, s.v)
        })
    }

    return MultiSeries(series, yMin, yMax, niceTicks(yMin, yMax, minStep = minTickStep))
}

/**
 * "Nice" tick values for a y-axis: 1/2/5×10ⁿ steps, ~4 ticks. [minStep] keeps
 * integer-valued charts (period/bowel) on whole-number ticks; the default of
 * 0.01 preserves two-decimal precision for weight. All returned values are
 * rounded to hundredths and lie within [lo, hi].
 */
fun niceTicks(lo: Double, hi: Double, targetCount: Int = 4, minStep: Double = 0.01): List<Double> {
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
    val step = max(niceNormalized * magnitude, minStep.coerceAtLeast(0.01))

    val ticks = mutableListOf<Double>()
    var v = ceil(lo / step) * step
    while (v <= hi + step * 1e-6 && ticks.size < 20) {
        val rounded = roundToHundredths(v)
        if (ticks.lastOrNull() != rounded) ticks.add(rounded)
        v += step
    }
    return ticks
}

/** Tick label for integer-valued series: whole numbers without a decimal point. */
fun formatIntegerTick(v: Double): String =
    if (v == floor(v)) v.toLong().toString() else roundToHundredths(v).toString()
