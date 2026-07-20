package com.lymph.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.lymph.data.database.LymphDatabase
import com.lymph.data.export.DataExporter
import com.lymph.ui.screen.DashboardScreen
import com.lymph.ui.theme.LymphTheme
import com.lymph.ui.viewmodel.BiometricViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Root Composable for Lymph.
 *
 * Detects screen width via [BoxWithConstraints] and switches between:
 * - MOBILE  (<= 840dp): single-column dashboard with FAB + sandwich menu
 * - EXPANDED (> 840dp): master-detail layout with side-by-side panels
 */
@Composable
fun LymphApp(
    database: LymphDatabase,
    exporter: DataExporter
) {
    LymphTheme {
        val vm = BiometricViewModel(
            weightDao = database.weightDao(),
            periodDao = database.periodDao(),
            bowelDao = database.bowelDao(),
            database = database
        )

        // Start observing flows. On native this is a no-op (already started in init).
        // On WASM this starts collecting from the in-memory flows.
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            vm.startObserving(scope)
        }

        BoxWithConstraints {
            val isExpanded = maxWidth > 840.dp

            DashboardScreen(
                viewModel = vm,
                exporter = exporter,
                isExpanded = isExpanded
            )
        }
    }
}
