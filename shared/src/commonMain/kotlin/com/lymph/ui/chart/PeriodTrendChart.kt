package com.lymph.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lymph.data.entity.PeriodRecord
import com.lymph.ui.theme.LymphAmber
import com.lymph.ui.theme.LymphCyan

/**
 * Period trend chart: two lines over time — duration in days (cyan) and flow
 * intensity 1-4 (amber). Both parameters are small positive integers, so one
 * shared auto-scaled y-axis reads naturally for both; the legend disambiguates.
 */
@Composable
fun PeriodTrendChart(
    records: List<PeriodRecord>,
    modifier: Modifier = Modifier
) {
    MultiTrendChart(
        timestamps = records.map { it.timestamp },
        seriesValues = listOf(
            records.map { it.durationDays.toDouble() },
            records.map { it.flowIntensity.toDouble() }
        ),
        legendLabels = listOf("DURATION (days)", "FLOW [1-4]"),
        colors = listOf(LymphCyan, LymphAmber),
        tickLabel = ::formatIntegerTick,
        minTickStep = 1.0,
        modifier = modifier
    )
}
