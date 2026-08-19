package com.lymph.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lymph.data.entity.PeriodRecord
import com.lymph.util.formatTimestamp

/**
 * Terminal-style period tracking panel.
 */
@Composable
fun PeriodPanel(
    records: List<PeriodRecord>,
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
                Text("[ PERIOD ]", style = MaterialTheme.typography.titleMedium)
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
                records.forEach { record ->
                    PeriodRow(record, onDelete)
                }
            }
        }
    }
}

@Composable
private fun PeriodRow(record: PeriodRecord, onDelete: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatTimestamp(record.timestamp), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${record.durationDays}d | flow: ${record.flowIntensity}",
                style = MaterialTheme.typography.bodyLarge
            )
            if (!record.symptoms.isNullOrEmpty()) {
                Text(
                    "symptoms: ${record.symptoms}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        IconButton(onClick = { onDelete(record.id) }) {
            Text("x", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
