package com.lymph.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord
import com.lymph.data.entity.createBowelRecord
import com.lymph.data.entity.createPeriodRecord
import com.lymph.data.entity.createWeightRecord
import com.lymph.ui.viewmodel.BiometricViewModel
import com.lymph.util.Uuid
import kotlinx.datetime.Clock

/**
 * Input modal with clean dial/counter controls for manual data entry.
 * Presents three tabs: Weight, Period, Bowel — each with its own form.
 */
@Composable
fun InputModal(
    onDismiss: () -> Unit,
    viewModel: BiometricViewModel
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabLabels = listOf("WEIGHT", "PERIOD", "BOWEL")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small, // sharp corners
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ADD RECORD", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Tab row
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabLabels.forEachIndexed { index, label ->
                        val selected = index == tab
                        OutlinedButton(
                            onClick = { tab = index },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                when (tab) {
                    0 -> WeightForm(viewModel, onDismiss)
                    1 -> PeriodForm(viewModel, onDismiss)
                    2 -> BowelForm(viewModel, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun WeightForm(vm: BiometricViewModel, onDismiss: () -> Unit) {
    var weight by remember { mutableDoubleStateOf(70.0) }
    var note by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DialCounter(
            label = "WEIGHT (kg)",
            value = weight,
            onDecrement = { if (weight > 30.0) weight -= 0.1 },
            onIncrement = { if (weight < 300.0) weight += 0.1 }
        )

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("NOTE") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            singleLine = true
        )

        Button(
            onClick = {
                vm.insertWeight(
                                    createWeightRecord(
                                        id = Uuid.randomV4(),
                                        timestamp = Clock.System.now().toEpochMilliseconds(),
                                        weightKg = weight,
                                        note = note.ifEmpty { null }
                                    )
                                )
                onDismiss()
            },
            modifier = Modifier.align(Alignment.End),
            shape = MaterialTheme.shapes.small
        ) {
            Text("SAVE")
        }
    }
}

@Composable
private fun PeriodForm(vm: BiometricViewModel, onDismiss: () -> Unit) {
    var duration by remember { mutableIntStateOf(5) }
    var flow by remember { mutableIntStateOf(2) }
    var symptoms by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DialCounter(
            label = "DURATION (days)",
            value = duration,
            onDecrement = { if (duration > 1) duration-- },
            onIncrement = { if (duration < 14) duration++ }
        )

        DialCounter(
            label = "FLOW [1-4]",
            value = flow,
            onDecrement = { if (flow > 1) flow-- },
            onIncrement = { if (flow < 4) flow++ }
        )

        OutlinedTextField(
            value = symptoms,
            onValueChange = { symptoms = it },
            label = { Text("SYMPTOMS") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            singleLine = true
        )

        Button(
            onClick = {
                vm.insertPeriod(
                                    createPeriodRecord(
                                        id = Uuid.randomV4(),
                                        timestamp = Clock.System.now().toEpochMilliseconds(),
                                        durationDays = duration,
                                        flowIntensity = flow,
                                        symptoms = symptoms.ifEmpty { null }
                                    )
                                )
                onDismiss()
            },
            modifier = Modifier.align(Alignment.End),
            shape = MaterialTheme.shapes.small
        ) {
            Text("SAVE")
        }
    }
}

@Composable
private fun BowelForm(vm: BiometricViewModel, onDismiss: () -> Unit) {
    var bristol by remember { mutableIntStateOf(4) }
    var effort by remember { mutableIntStateOf(3) }
    var note by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DialCounter(
            label = "BRISTOL [1-7]",
            value = bristol,
            onDecrement = { if (bristol > 1) bristol-- },
            onIncrement = { if (bristol < 7) bristol++ }
        )

        DialCounter(
            label = "EFFORT [1-5]",
            value = effort,
            onDecrement = { if (effort > 1) effort-- },
            onIncrement = { if (effort < 5) effort++ }
        )

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("NOTE") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            singleLine = true
        )

        Button(
            onClick = {
                vm.insertBowel(
                                    createBowelRecord(
                                        id = Uuid.randomV4(),
                                        timestamp = Clock.System.now().toEpochMilliseconds(),
                                        bristolType = bristol,
                                        effortLevel = effort,
                                        note = note.ifEmpty { null }
                                    )
                                )
                onDismiss()
            },
            modifier = Modifier.align(Alignment.End),
            shape = MaterialTheme.shapes.small
        ) {
            Text("SAVE")
        }
    }
}

/**
 * Clean dial/counter control with [-] value [+] layout for integers.
 */
@Composable
private fun DialCounter(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onDecrement, shape = MaterialTheme.shapes.small) {
                Text("-")
            }
            Text("$value", style = MaterialTheme.typography.headlineLarge)
            OutlinedButton(onClick = onIncrement, shape = MaterialTheme.shapes.small) {
                Text("+")
            }
        }
    }
}

/**
 * Double variant of [DialCounter] for weight input.
 */
@Composable
private fun DialCounter(
    label: String,
    value: Double,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onDecrement, shape = MaterialTheme.shapes.small) {
                Text("-")
            }
            // Format to 1 decimal place without String.format (KMP-safe)
            val rounded = (value * 10).toInt() / 10.0
            val intPart = rounded.toInt()
            val decPart = ((rounded - intPart) * 10).toInt()
            Text("$intPart.$decPart", style = MaterialTheme.typography.headlineLarge)
            OutlinedButton(onClick = onIncrement, shape = MaterialTheme.shapes.small) {
                Text("+")
            }
        }
    }
}
