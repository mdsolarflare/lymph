package com.lymph.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lymph.data.entity.WeightRecord
import com.lymph.ui.theme.LymphCyan
import com.lymph.ui.theme.LymphCyanDim
import com.lymph.util.currentTimeMillis
import com.lymph.util.formatTimestamp
import com.lymph.util.formatWeight

/**
 * Weight trend line chart with a 90D / 1Y / ALL x-range selector.
 * Clinical-CLI styling: dim gridlines, full-cyan polyline, square markers,
 * monospace tick labels. Static in v0.1 (no touch scrubbing).
 *
 * Tick/date labels are positioned [Text] overlays rather than canvas text —
 * the Skia canvas text API differs across Compose MP versions/targets, while
 * Text composables render identically everywhere.
 */
@Composable
fun WeightTrendChart(
    records: List<WeightRecord>,
    modifier: Modifier = Modifier
) {
    var range by remember { mutableStateOf(WeightRange.DAYS_90) }

    // Filter to the selected range; recomputed when data or range changes.
    val visible = remember(records, range) {
        val cutoff = cutoffMillis(range, currentTimeMillis()) ?: return@remember records
        records.filter { it.timestamp >= cutoff }
    }

    // Chart input must be chronological (DAO lists are newest-first).
    val series = remember(visible) {
        mapWeightSeries(visible.sortedBy { it.timestamp }.map { Sample(it.timestamp.toDouble(), it.weightKg) })
    }

    Column(modifier = modifier) {
        // Range selector — same toggle style as the KG/LB buttons in the input modal.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeightRange.entries.forEach { r ->
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

        if (series == null || visible.isEmpty()) {
            Text(
                "> no data in range",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
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
                        // Polyline as segments; square markers cover the joints.
                        val pts = series.points.map { p ->
                            Offset(
                                (PAD_LEFT_DP + plotW * p.xNorm).dp.toPx(),
                                (PAD_BOTTOM_DP + plotH * (1f - series.yNorm(p.value))).dp.toPx()
                            )
                        }
                        for (i in 1 until pts.size) {
                            drawLine(LymphCyan, pts[i - 1], pts[i], 2.dp.toPx())
                        }
                        val m = 4.dp.toPx()
                        pts.forEach { p ->
                            drawRect(LymphCyan, Offset(p.x - m / 2f, p.y - m / 2f), Size(m, m))
                        }
                    }

                    // Y tick labels (dim monospace).
                    series.ticks.forEach { t ->
                        val yTop = (PAD_BOTTOM_DP + plotH * (1f - series.yNorm(t))) - 6f
                        Text(
                            formatWeight(t),
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
                Text(formatTimestamp(visible.minOf { it.timestamp }), style = MaterialTheme.typography.labelSmall)
                Text(formatTimestamp(visible.maxOf { it.timestamp }), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private const val CHART_HEIGHT_DP = 140f
private const val PAD_LEFT_DP = 40f // gutter for y tick labels
private const val PAD_RIGHT_DP = 8f
private const val PAD_TOP_DP = 6f
private const val PAD_BOTTOM_DP = 6f
