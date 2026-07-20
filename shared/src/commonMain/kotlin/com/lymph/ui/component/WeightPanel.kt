package com.lymph.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lymph.data.entity.WeightRecord
import com.lymph.util.formatTimestamp

/**
 * Terminal-style weight readout panel.
 * Sharp corners, monospace, cyan-on-black.
 */
@Composable
fun WeightPanel(
    records: List<WeightRecord>,
    onDelete: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small, // sharp corners
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("[ WEIGHT ]", style = MaterialTheme.typography.titleMedium)
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
                    WeightRow(record, onDelete)
                }
            }
        }
    }
}

@Composable
private fun WeightRow(record: WeightRecord, onDelete: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            formatTimestamp(record.timestamp),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "${record.weightKg} kg",
            style = MaterialTheme.typography.bodyLarge
        )
        if (!record.note.isNullOrEmpty()) {
            Text(
                "\"${record.note}\"",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        IconButton(onClick = { onDelete(record.id) }) {
            Text("x", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
