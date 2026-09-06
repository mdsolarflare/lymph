package com.lymph.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lymph.data.entity.BowelRecord
import com.lymph.ui.chart.BowelTrendChart
import com.lymph.util.formatTimestamp

/**
 * Terminal-style bowel movement panel using Bristol Stool Scale.
 */
@Composable
fun BowelPanel(
    records: List<BowelRecord>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("[ BOWEL  ]", style = MaterialTheme.typography.titleMedium)
                Text("${records.size} records", style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (records.isEmpty()) {
                Text(
                    "> no data. press + to log.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                BowelTrendChart(records, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))

                // Paginated table — newest first (DAO order), 10 records per page.
                val pageSize = 10
                var page by remember { mutableIntStateOf(0) }
                val pageCount = (records.size + pageSize - 1) / pageSize
                val safePage = page.coerceIn(0, pageCount - 1)
                records.subList(safePage * pageSize, minOf((safePage + 1) * pageSize, records.size))
                    .forEach { record -> BowelRow(record, onDelete) }

                if (pageCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("[ ${safePage + 1}/$pageCount ]", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { page-- },
                                enabled = safePage > 0,
                                shape = MaterialTheme.shapes.small
                            ) { Text("<") }
                            OutlinedButton(
                                onClick = { page++ },
                                enabled = safePage < pageCount - 1,
                                shape = MaterialTheme.shapes.small
                            ) { Text(">") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BowelRow(record: BowelRecord, onDelete: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatTimestamp(record.timestamp), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "bristol: ${record.bristolType} | effort: ${record.effortLevel}",
                style = MaterialTheme.typography.bodyLarge
            )
            if (!record.note.isNullOrEmpty()) {
                Text(
                    "\"${record.note}\"",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        IconButton(onClick = { onDelete(record.id) }) {
            Text("x", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
