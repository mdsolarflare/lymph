package com.lymph.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lymph.ui.theme.LymphCyanDim
import com.lymph.util.currentTimeMillis
import com.lymph.util.formatTimestamp

/**
 * Generic multi-line trend chart with a 90D / 1Y / ALL x-range selector.
 * Clinical-CLI styling: dim gridlines, one polyline per series (square markers),
 * monospace tick labels, legend for two or more lines. Static in v0.2 (no touch scrubbing).
 *
 * [timestamps] and every list in [seriesValues] must be parallel (same size, same
 * record order — DAO lists are newest-first; sorting happens here). All lines share
 * one auto-scaled y-axis; use [tickLabel] to format axis labels.
 *
 * Tick/date labels are positioned [Text] overlays rather than canvas text —
 * the Skia canvas text API differs across Compose MP versions/targets, while
 * Text composables render identically everywhere.
 */
@Composable
fun MultiTrendChart(
    timestamps: List<Long>,
    seriesValues: List<List<Double>>,
    legendLabels: List<String>,
    colors: List<Color>,
    tickLabel: (Double) -> String = { it.toString() },
    minTickStep: Double = 0.01,
    modifier: Modifier = Modifier
) {
    var range by remember { mutableStateOf(TimeRange.DAYS_90) }

    // Filter to the selected range and map into chart geometry; recomputed when
    // data or range changes (remember keys are content-equality based). Chart
    // input must be chronological (DAO lists are newest-first).
    val chart = remember(timestamps, seriesValues, range) {
        val cutoff = cutoffMillis(range, currentTimeMillis())
        val idx = if (cutoff == null) timestamps.indices.toList()
                  else timestamps.indices.filter { timestamps[it] >= cutoff }
        val sortedIdx = idx.sortedBy { timestamps[it] }
        MultiTrendChartState(
            visibleTimestamps = sortedIdx.map { timestamps[it] },
            series = mapMultiSeries(
                sortedIdx.map { i -> seriesValues.map { values -> Sample(timestamps[i].toDouble(), values[i]) } },
                minTickStep = minTickStep
            )
        )
    }

    Column(modifier = modifier) {
        // Range selector — same toggle style as the KG/LB buttons in the input modal.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeRange.entries.forEach { r ->
                OutlinedButton(
                    onClick = { range = r },
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (range == r) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(r.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Legend — multi-line charts only; swatch color matches the line.
        if (legendLabels.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                legendLabels.forEachIndexed { i, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(colors[i % colors.size]))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (chart.series == null || chart.visibleTimestamps.isEmpty()) {
            Text(
                "> no data in range",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            val series = chart.series!!
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp).padding(top = 8.dp)) {
                // px → dp by hand: Int.toDp() does not exist in Compose MP (only TextUnit.toDp()).
                val wDp = constraints.maxWidth / LocalDensity.current.density
                if (wDp.isFinite()) {
                    // Shared geometry in dp (canvas + label overlays must agree).
                    val plotW = wDp - PAD_LEFT_DP - PAD_RIGHT_DP
                    val plotH = CHART_HEIGHT_DP - PAD_TOP_DP - PAD_BOTTOM_DP

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Gridlines (dim).
                        series.ticks.forEach { t ->
                            val y = (PAD_BOTTOM_DP + plotH * (1f - series.yNorm(t))).dp.toPx()
                            drawLine(LymphCyanDim, Offset(PAD_LEFT_DP.dp.toPx(), y), Offset((wDp - PAD_RIGHT_DP).dp.toPx(), y), 1.dp.toPx())
                        }
                        // One polyline per series; square markers cover the joints.
                        series.series.forEachIndexed { si, line ->
                            val c = colors[si % colors.size]
                            val pts = line.points.map { p ->
                                Offset(
                                    (PAD_LEFT_DP + plotW * p.xNorm).dp.toPx(),
                                    (PAD_BOTTOM_DP + plotH * (1f - series.yNorm(p.value))).dp.toPx()
                                )
                            }
                            for (i in 1 until pts.size) {
                                drawLine(c, pts[i - 1], pts[i], 2.dp.toPx())
                            }
                            val m = 4.dp.toPx()
                            pts.forEach { p ->
                                drawRect(c, Offset(p.x - m / 2f, p.y - m / 2f), Size(m, m))
                            }
                        }
                    }

                    // Y tick labels (dim monospace).
                    series.ticks.forEach { t ->
                        val yTop = (PAD_BOTTOM_DP + plotH * (1f - series.yNorm(t))) - 6f
                        Text(
                            tickLabel(t),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = LymphCyanDim),
                            modifier = Modifier.offset(x = 2.dp, y = yTop.coerceAtLeast(0f).dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTimestamp(chart.visibleTimestamps.first()), style = MaterialTheme.typography.labelSmall)
                Text(formatTimestamp(chart.visibleTimestamps.last()), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private data class MultiTrendChartState(
    val visibleTimestamps: List<Long>,
    val series: MultiSeries?
)

private const val CHART_HEIGHT_DP = 140f
private const val PAD_LEFT_DP = 40f // gutter for y tick labels
private const val PAD_RIGHT_DP = 8f
private const val PAD_TOP_DP = 6f
private const val PAD_BOTTOM_DP = 6f
