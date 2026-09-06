package com.lymph.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lymph.data.entity.BowelRecord
import com.lymph.ui.theme.LymphAmber
import com.lymph.ui.theme.LymphCyan

/**
 * Bowel trend chart: two lines over time — Bristol Stool Scale type 1-7 (cyan)
 * and effort level 1-5 (amber). Both are small ordinal integers, so one shared
 * auto-scaled y-axis reads naturally for both; the legend disambiguates.
 */
@Composable
fun BowelTrendChart(
    records: List<BowelRecord>,
    modifier: Modifier = Modifier
) {
    MultiTrendChart(
        timestamps = records.map { it.timestamp },
        seriesValues = listOf(
            records.map { it.bristolType.toDouble() },
            records.map { it.effortLevel.toDouble() }
        ),
        legendLabels = listOf("BRISTOL [1-7]", "EFFORT [1-5]"),
        colors = listOf(LymphCyan, LymphAmber),
        tickLabel = ::formatIntegerTick,
        minTickStep = 1.0,
        modifier = modifier
    )
}
