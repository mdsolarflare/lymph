package com.lymph.ui.chart

import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartModelTest {

    @Test
    fun emptyInputReturnsNull() {
        assertNull(mapWeightSeries(emptyList()))
    }

    @Test
    fun singleSampleIsCenteredWithSymmetricRange() {
        val s = mapWeightSeries(listOf(Sample(1000.0, 70.0)))!!
        assertEquals(1, s.points.size)
        assertEquals(0.5f, s.points[0].xNorm)
        assertTrue(s.yMin < 70.0 && s.yMax > 70.0, "range must bracket the value")
        // symmetric padding
        assertEquals((70.0 - s.yMin), (s.yMax - 70.0), 1e-9)
    }

    @Test
    fun flatSeriesGetsPaddedRange() {
        val s = mapWeightSeries(listOf(Sample(1.0, 70.0), Sample(2.0, 70.0), Sample(3.0, 70.0)))!!
        assertTrue(s.yMin < 70.0 && s.yMax > 70.0)
        // equal timestamps -> evenly spread x positions
        assertEquals(0.0, s.points[0].xNorm.toDouble(), 1e-6)
        assertEquals(1.0, s.points.last().xNorm.toDouble(), 1e-6)
    }

    @Test
    fun normalSeriesMapsMonotonically() {
        val samples = listOf(Sample(0.0, 70.0), Sample(50.0, 82.0), Sample(100.0, 74.0))
        val s = mapWeightSeries(samples)!!
        // x is time-proportional: first at 0, last at 1, middle in between
        assertEquals(0.0, s.points[0].xNorm.toDouble(), 1e-6)
        assertEquals(1.0, s.points.last().xNorm.toDouble(), 1e-6)
        assertTrue(s.points[1].xNorm > 0f && s.points[1].xNorm < 1f)
        // y ordering preserved: 82 is the top point
        val top = s.points.maxByOrNull { it.value }!!
        assertEquals(82.0, top.value)
        assertTrue(s.yMin < 70.0 && s.yMax > 82.0, "range must bracket data with padding")
    }

    @Test
    fun yNormIsNormalized() {
        val s = mapWeightSeries(listOf(Sample(0.0, 70.0), Sample(100.0, 82.0)))!!
        assertEquals(0.0, s.yNorm(s.yMin).toDouble(), 1e-6)
        assertEquals(1.0, s.yNorm(s.yMax).toDouble(), 1e-6)
        assertTrue(s.yNorm(76.0) > s.yNorm(70.0))
    }

    @Test
    fun ticksLieWithinRangeAndAreReasonable() {
        val s = mapWeightSeries(listOf(Sample(0.0, 70.0), Sample(100.0, 82.0)))!!
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
        val s = mapWeightSeries(listOf(Sample(0.0, 69.8531), Sample(100.0, 70.4)))!!
        for (i in 1 until s.ticks.size) {
            assertTrue(s.ticks[i] > s.ticks[i - 1], "ticks must increase: ${s.ticks}")
        }
    }

    @Test
    fun cutoffMath() {
        val now = 1_000_000_000_000L
        assertNull(cutoffMillis(WeightRange.ALL, now))
        assertEquals(now - 90 * 86_400_000L, cutoffMillis(WeightRange.DAYS_90, now))
        assertEquals(now - 365 * 86_400_000L, cutoffMillis(WeightRange.YEAR, now))
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
        val cutoff90 = cutoffMillis(WeightRange.DAYS_90, now)!!
        assertEquals(1, samples.count { it.t >= cutoff90 })
        val cutoffYear = cutoffMillis(WeightRange.YEAR, now)!!
        assertEquals(2, samples.count { it.t >= cutoffYear })
    }

    private fun roundToHundredthsCheck(v: Double): Double = (v * 100.0).roundToLong() / 100.0
}
