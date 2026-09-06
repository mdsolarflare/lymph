package com.lymph.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lymph.data.database.LymphDatabase
import com.lymph.data.export.DataExporter
import com.lymph.platform.FilePicker

import com.lymph.ui.theme.LymphRed
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
    exporter: DataExporter,
    filePicker: FilePicker? = null // web only for now — native pickers deferred; import UI hides itself without one
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
            InitState.LOADING -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("> initializing...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            InitState.ERROR -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("> INIT FAILED", color = LymphRed)
                    Text("> database could not be opened", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            InitState.READY  -> {
                val vm = viewModel!!
                val scope2 = rememberCoroutineScope()

                LaunchedEffect(vm) {
                    vm.startObserving(scope2)
                }

                BoxWithConstraints {
                    val isExpanded = maxWidth > 840.dp
                    com.lymph.ui.screen.DashboardScreenContent(
                        viewModel = vm,
                        exporter = exporter,
                        isExpanded = isExpanded,
                        filePicker = filePicker
                    )
                }
            }
        }
    }
}
