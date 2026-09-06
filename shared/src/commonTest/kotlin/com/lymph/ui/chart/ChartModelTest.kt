package com.lymph.ui.chart

import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartModelTest {

    @Test
    fun emptyInputReturnsNull() {
        assertNull(mapMultiSeries(emptyList()))
        assertNull(mapMultiSeries(listOf(emptyList(), emptyList())))
    }

    @Test
    fun singleSampleIsCenteredWithSymmetricRange() {
        val s = mapMultiSeries(listOf(listOf(Sample(1000.0, 70.0))))!!
        assertEquals(1, s.series.size)
        assertEquals(1, s.series[0].points.size)
        assertEquals(0.5f, s.series[0].points[0].xNorm)
        assertTrue(s.yMin < 70.0 && s.yMax > 70.0, "range must bracket the value")
        // symmetric padding
        assertEquals((70.0 - s.yMin), (s.yMax - 70.0), 1e-9)
    }

    @Test
    fun flatSeriesGetsPaddedRange() {
        val s = mapMultiSeries(listOf(listOf(Sample(1.0, 70.0), Sample(2.0, 70.0), Sample(3.0, 70.0))))!!
        assertTrue(s.yMin < 70.0 && s.yMax > 70.0)
        // equal timestamps -> evenly spread x positions
        assertEquals(0.0, s.series[0].points[0].xNorm.toDouble(), 1e-6)
        assertEquals(1.0, s.series[0].points.last().xNorm.toDouble(), 1e-6)
    }

    @Test
    fun normalSeriesMapsMonotonically() {
        val samples = listOf(Sample(0.0, 70.0), Sample(50.0, 82.0), Sample(100.0, 74.0))
        val s = mapMultiSeries(listOf(samples))!!
        // x is time-proportional: first at 0, last at 1, middle in between
        assertEquals(0.0, s.series[0].points[0].xNorm.toDouble(), 1e-6)
        assertEquals(1.0, s.series[0].points.last().xNorm.toDouble(), 1e-6)
        assertTrue(s.series[0].points[1].xNorm > 0f && s.series[0].points[1].xNorm < 1f)
        // y ordering preserved: 82 is the top point
        val top = s.series[0].points.maxByOrNull { it.value }!!
        assertEquals(82.0, top.value)
        assertTrue(s.yMin < 70.0 && s.yMax > 82.0, "range must bracket data with padding")
    }

    @Test
    fun yNormIsNormalized() {
        val s = mapMultiSeries(listOf(listOf(Sample(0.0, 70.0), Sample(100.0, 82.0))))!!
        assertEquals(0.0, s.yNorm(s.yMin).toDouble(), 1e-6)
        assertEquals(1.0, s.yNorm(s.yMax).toDouble(), 1e-6)
        assertTrue(s.yNorm(76.0) > s.yNorm(70.0))
    }

    @Test
    fun ticksLieWithinRangeAndAreReasonable() {
        val s = mapMultiSeries(listOf(listOf(Sample(0.0, 70.0), Sample(100.0, 82.0))))!!
        assertTrue(s.ticks.isNotEmpty())
        assertTrue(s.ticks.size <= 6, "expected ~4 ticks, got ${s.ticks.size}")
        s.ticks.forEach { t ->
            assertTrue(t >= s.yMin && t <= s.yMax, "tick $t outside [${s.yMin}, ${s.yMax}]")
            // two-decimal precision (weight contract)
            assertEquals(roundToHundredthsCheck(t), t)
        }
    }

    @Test
    fun ticksAreStrictlyIncreasing() {
        val s = mapMultiSeries(listOf(listOf(Sample(0.0, 69.8531), Sample(100.0, 70.4))))!!
        for (i in 1 until s.ticks.size) {
            assertTrue(s.ticks[i] > s.ticks[i - 1], "ticks must increase: ${s.ticks}")
        }
    }

    // --- multi-series (period/bowel charts) ---

    @Test
    fun sharedAxisSpansAllSeries() {
        val duration = listOf(Sample(0.0, 3.0), Sample(100.0, 7.0))
        val flow = listOf(Sample(0.0, 4.0), Sample(100.0, 1.0))
        val s = mapMultiSeries(listOf(duration, flow))!!
        assertTrue(s.yMin < 1.0 && s.yMax > 7.0, "axis must span both series: [${s.yMin}, ${s.yMax}]")
        assertEquals(2, s.series.size)
        assertEquals(2, s.series[0].points.size)
        assertEquals(2, s.series[1].points.size)
    }

    @Test
    fun multiSeriesShareXMapping() {
        val a = listOf(Sample(0.0, 3.0), Sample(50.0, 6.0), Sample(100.0, 4.0))
        val b = listOf(Sample(0.0, 2.0), Sample(50.0, 3.0), Sample(100.0, 1.0))
        val s = mapMultiSeries(listOf(a, b))!!
        for (i in a.indices) {
            assertEquals(s.series[0].points[i].xNorm, s.series[1].points[i].xNorm, 1e-6f)
        }
    }

    @Test
    fun minTickStepForcesWholeNumberTicks() {
        // Tiny span (3..4) would normally produce 0.2-step ticks; integer charts need step >= 1.
        val s = mapMultiSeries(listOf(listOf(Sample(0.0, 3.0), Sample(100.0, 4.0))), minTickStep = 1.0)!!
        assertTrue(s.ticks.isNotEmpty())
        s.ticks.forEach { t -> assertEquals(t.toLong().toDouble(), t, "tick $t not whole") }
    }

    @Test
    fun emptySeriesAmongNonEmptyStillRenders() {
        val s = mapMultiSeries(listOf(emptyList(), listOf(Sample(0.0, 5.0))))!!
        assertEquals(2, s.series.size)
        assertTrue(s.series[0].points.isEmpty())
        assertEquals(1, s.series[1].points.size)
    }

    @Test
    fun formatIntegerTickTrimsDecimals() {
        assertEquals("3", formatIntegerTick(3.0))
        assertEquals("2.5", formatIntegerTick(2.5))
    }

    // --- range filtering (shared by all trend charts) ---

    @Test
    fun cutoffMath() {
        val now = 1_000_000_000_000L
        assertNull(cutoffMillis(TimeRange.ALL, now))
        assertEquals(now - 90 * 86_400_000L, cutoffMillis(TimeRange.DAYS_90, now))
        assertEquals(now - 365 * 86_400_000L, cutoffMillis(TimeRange.YEAR, now))
    }

    @Test
    fun rangeFilterKeepsOnlyRecentSamples() {
        val now = 1_000_000_000_000L
        val day = 86_400_000.0
        val samples = listOf(
            Sample(now - 400 * day, 70.0), // older than a year -> filtered by YEAR
            Sample(now - 100 * day, 71.0), // older than 90d -> filtered by DAYS_90
            Sample(now - 30 * day, 72.0)   // recent -> kept by both
        )
        val cutoff90 = cutoffMillis(TimeRange.DAYS_90, now)!!
        assertEquals(1, samples.count { it.t >= cutoff90 })
        val cutoffYear = cutoffMillis(TimeRange.YEAR, now)!!
        assertEquals(2, samples.count { it.t >= cutoffYear })
    }

    private fun roundToHundredthsCheck(v: Double): Double = (v * 100.0).roundToLong() / 100.0
}
