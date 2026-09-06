package com.lymph.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lymph.data.entity.WeightRecord
import com.lymph.ui.theme.LymphCyan
import com.lymph.util.formatWeight

/**
 * Weight trend line chart with a 90D / 1Y / ALL x-range selector.
 * Single-line instance of [MultiTrendChart] (no legend for one series).
 */
@Composable
fun WeightTrendChart(
    records: List<WeightRecord>,
    modifier: Modifier = Modifier
) {
    MultiTrendChart(
        timestamps = records.map { it.timestamp },
        seriesValues = listOf(records.map { it.weightKg }),
        legendLabels = listOf("WEIGHT (kg)"),
        colors = listOf(LymphCyan),
        tickLabel = ::formatWeight,
        modifier = modifier
    )
}
