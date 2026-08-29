package com.lymph.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord
import com.lymph.ui.theme.LymphBlack
import com.lymph.ui.theme.LymphCyan
import com.lymph.ui.theme.LymphRed
import com.lymph.ui.viewmodel.BiometricViewModel
import com.lymph.util.KG_PER_LB
import com.lymph.util.MAX_WEIGHT_KG
import com.lymph.util.MIN_WEIGHT_KG
import com.lymph.util.Uuid
import com.lymph.util.currentTimeMillis
import com.lymph.util.formatWeight
import com.lymph.util.parseHundredths
import com.lymph.util.roundToHundredths
import kotlin.math.roundToLong

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

/** Input units for weight. Storage is canonical kilograms; pounds convert at save time. */
private enum class WeightUnit(val symbol: String) { KG("kg"), LB("lb") }

/**
 * True if [hundredths] of the given unit, converted to canonical kg (rounded to hundredths,
 * exactly as it would be stored), falls within MIN_WEIGHT_KG..MAX_WEIGHT_KG.
 */
private fun isCanonicalInRange(hundredths: Int, unit: WeightUnit): Boolean {
    val v = hundredths / 100.0
    val kg = if (unit == WeightUnit.LB) roundToHundredths(v * KG_PER_LB) else v
    return kg >= MIN_WEIGHT_KG && kg <= MAX_WEIGHT_KG
}

@Composable
private fun WeightForm(vm: BiometricViewModel, onDismiss: () -> Unit) {
    var unit by remember { mutableStateOf(WeightUnit.KG) }
    // Pre-fill from the last recorded weight (list is newest-first on both platforms);
    // 70 kg default when there are no records yet.
    val initialText = vm.weights.firstOrNull()?.let { formatWeight(it.weightKg) } ?: "70"
    // Editable text in the selected unit; parsed to integer hundredths for all logic.
    var text by remember { mutableStateOf(initialText) }
    var note by remember { mutableStateOf("") }

    val parsed = parseHundredths(text)
    val valid = parsed != null && isCanonicalInRange(parsed, unit)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Unit toggle — switching converts the current value to the other unit.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WeightUnit.entries.forEach { u ->
                OutlinedButton(
                    onClick = {
                        if (u == unit) return@OutlinedButton
                        val h = parseHundredths(text)
                        if (h != null) {
                            val current = h / 100.0
                            text = formatWeight(if (unit == WeightUnit.KG) current / KG_PER_LB else current * KG_PER_LB)
                        }
                        unit = u
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (unit == u) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(u.symbol.uppercase())
                }
            }
        }

        DialCounter(
            label = "WEIGHT (${unit.symbol})",
            onDecrement = {
                val h = parseHundredths(text) ?: return@DialCounter
                if (isCanonicalInRange(h - 10, unit)) text = formatWeight((h - 10) / 100.0)
            },
            onIncrement = {
                val h = parseHundredths(text) ?: return@DialCounter
                if (isCanonicalInRange(h + 10, unit)) text = formatWeight((h + 10) / 100.0)
            }
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (isAcceptableWeightInput(it)) text = it },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = if (valid) LymphCyan else LymphRed
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("NOTE") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            singleLine = true
        )

        Button(
            enabled = valid,
            onClick = {
                val h = parsed ?: return@Button
                val valueInUnit = h / 100.0
                vm.insertWeight(
                    WeightRecord(
                        id = Uuid.randomV4(),
                        timestamp = currentTimeMillis(),
                        weightKg = if (unit == WeightUnit.LB) roundToHundredths(valueInUnit * KG_PER_LB)
                                  else valueInUnit,
                        note = note.ifEmpty { null }
                    )
                )
                onDismiss()
            },
            modifier = Modifier.align(Alignment.End),
            shape = MaterialTheme.shapes.small
        ) {
            Text("SAVE", style = MaterialTheme.typography.bodyLarge.copy(color = LymphBlack))
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
            onDecrement = { if (duration > 1) duration-- },
            onIncrement = { if (duration < 14) duration++ }
        ) { Text("$duration", style = MaterialTheme.typography.headlineLarge) }

        DialCounter(
            label = "FLOW [1-4]",
            onDecrement = { if (flow > 1) flow-- },
            onIncrement = { if (flow < 4) flow++ }
        ) { Text("$flow", style = MaterialTheme.typography.headlineLarge) }

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
                    PeriodRecord(
                        id = Uuid.randomV4(),
                        timestamp = currentTimeMillis(),
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
            Text("SAVE", style = MaterialTheme.typography.bodyLarge.copy(color = LymphBlack))
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
            onDecrement = { if (bristol > 1) bristol-- },
            onIncrement = { if (bristol < 7) bristol++ }
        ) { Text("$bristol", style = MaterialTheme.typography.headlineLarge) }

        DialCounter(
            label = "EFFORT [1-5]",
            onDecrement = { if (effort > 1) effort-- },
            onIncrement = { if (effort < 5) effort++ }
        ) { Text("$effort", style = MaterialTheme.typography.headlineLarge) }

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
                    BowelRecord(
                        id = Uuid.randomV4(),
                        timestamp = currentTimeMillis(),
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
            Text("SAVE", style = MaterialTheme.typography.bodyLarge.copy(color = LymphBlack))
        }
    }
}

/**
 * Clean dial/counter control with [-] center [+] layout.
 * The caller supplies the center content — a static display string for integer
 * counters, or an editable text field (weight).
 */
@Composable
private fun DialCounter(
    label: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    center: @Composable () -> Unit
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
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                center()
            }
            OutlinedButton(onClick = onIncrement, shape = MaterialTheme.shapes.small) {
                Text("+")
            }
        }
    }
}

/**
 * Live input filter for the editable weight field: digits plus at most one dot
 * with two decimal places. Rejects everything else so the parser only ever sees
 * well-formed (possibly incomplete, e.g. "" or "70.") input.
 */
private fun isAcceptableWeightInput(s: String): Boolean {
    if (s.length > 8) return false
    var dots = 0
    var fracDigits = 0
    for (ch in s) {
        when {
            ch == '.' -> { dots++; if (dots > 1) return false }
            ch.isDigit() -> { if (dots == 1) { fracDigits++; if (fracDigits > 2) return false } }
            else -> return false
        }
    }
    return true
}
