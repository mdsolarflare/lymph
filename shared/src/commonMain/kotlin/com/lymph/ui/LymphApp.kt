package com.lymph.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.lymph.data.database.LymphDatabase
import com.lymph.data.export.DataExporter
import com.lymph.ui.screen.DashboardScreen
import com.lymph.ui.theme.LymphTheme
import com.lymph.ui.viewmodel.BiometricViewModel

/** Initialization state for the database-backed app. */
private enum class InitState { LOADING, READY, ERROR }

/**
 * Root Composable for Lymph.
 *
 * Opens the database asynchronously (required for web to load sql.js from CDN),
 * then creates the ViewModel and renders the dashboard.
 */
@Composable
fun LymphApp(
    database: LymphDatabase,
    exporter: DataExporter
) {
    var initState by remember { mutableStateOf<InitState>(InitState.LOADING) }
    var viewModel by remember { mutableStateOf<BiometricViewModel?>(null) }

    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            database.open()
            viewModel = BiometricViewModel(
                weightDao = database.weightDao(),
                periodDao = database.periodDao(),
                bowelDao = database.bowelDao(),
                database = database
            )
            initState = InitState.READY
        } catch (e: Exception) {
            e.printStackTrace()
            initState = InitState.ERROR
        }
    }

    LymphTheme {
        when (initState) {
            InitState.LOADING -> {} // TODO: Show loading indicator
            InitState.ERROR   -> {} // TODO: Show error screen
            InitState.READY  -> {
                val vm = viewModel!!
                val scope2 = rememberCoroutineScope()

                LaunchedEffect(vm) {
                    vm.startObserving(scope2)
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
    }
}
