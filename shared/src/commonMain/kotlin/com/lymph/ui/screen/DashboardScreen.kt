package com.lymph.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lymph.data.export.DataExporter
import com.lymph.ui.theme.LymphBlack
import com.lymph.ui.theme.LymphCyan
import com.lymph.ui.component.BowelPanel
import com.lymph.ui.component.InputModal
import com.lymph.ui.component.PeriodPanel
import com.lymph.ui.component.WeightPanel
import com.lymph.ui.viewmodel.BiometricViewModel
import kotlinx.coroutines.launch

/**
 * Top-level dashboard that adapts its layout based on [isExpanded].
 *
 * MOBILE:  Single column with top app bar, scrollable content, and FAB.
 * EXPANDED: Sidebar navigation (one data type selected at a time) + single detail panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
object DashboardScreen {
    @Composable
    fun Preview() {
        androidx.compose.material3.Text(
            text = "> PREVIEW",
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenContent(
    viewModel: BiometricViewModel,
    exporter: DataExporter,
    isExpanded: Boolean
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showModal by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Shared export action: builds the ZIP and (on web) triggers a browser download.
    val doExport: () -> Unit = {
        coroutineScope.launch {
            try {
                exporter.exportZip(viewModel.database)
                snackbarHostState.showSnackbar("EXPORT OK — zip generated")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("EXPORT FAILED: ${e.message ?: "unknown"}")
            }
        }
    }

    // Mobile uses a drawer; expanded does not need one.
    if (isExpanded) {
        ExpandedLayout(
            viewModel,
            onAddClick = { showModal = true },
            onExportClick = doExport,
            snackbarHostState = snackbarHostState
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("LYMPH v0.1", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        DrawerItem(text = "Export Data") {
                            coroutineScope.launch { drawerState.close() }
                            doExport()
                        }
                        DrawerItem(text = "Settings") { coroutineScope.launch { drawerState.close() } }
                    }
                }
            },
            gesturesEnabled = true,
            content = {
                MobileLayout(
                    vm = viewModel,
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    onAddClick = { showModal = true },
                    snackbarHostState = snackbarHostState
                )
            }
        )
    }

    // Input modal triggered by "+" FAB or sidebar button
    if (showModal) {
        InputModal(
            onDismiss = { showModal = false },
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileLayout(
    vm: BiometricViewModel,
    onMenuClick: () -> Unit,
    onAddClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LYMPH", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                shape = MaterialTheme.shapes.small // sharp corners
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Record")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            WeightPanel(vm.weights, onDelete = vm::deleteWeight)
            Spacer(Modifier.height(24.dp))
            PeriodPanel(vm.periods, onDelete = vm::deletePeriod)
            Spacer(Modifier.height(24.dp))
            BowelPanel(vm.bowels, onDelete = vm::deleteBowel)
        }
    }
}

/** Data types navigable from the expanded sidebar. */
private enum class DataType(val label: String) {
    WEIGHT("[ WEIGHT ]"),
    PERIOD("[ PERIOD ]"),
    BOWEL("[ BOWEL  ]")
}

@Composable
private fun ExpandedLayout(
    vm: BiometricViewModel,
    onAddClick: () -> Unit,
    onExportClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var selected by remember { mutableStateOf(DataType.WEIGHT) }

    Row(Modifier.fillMaxSize().background(LymphBlack)) {
        // Sidebar — master nav; selecting a data type swaps the detail panel.
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("LYMPH v0.1", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            DataType.entries.forEach { type ->
                NavItem(
                    label = type.label,
                    selected = type == selected,
                    onClick = { selected = type }
                )
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onAddClick, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ADD RECORD", style = MaterialTheme.typography.bodyLarge.copy(color = LymphBlack))
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onExportClick, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("EXPORT DATA")
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        // Detail — exactly one panel at a time, for the selected data type.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            when (selected) {
                DataType.WEIGHT -> WeightPanel(vm.weights, onDelete = vm::deleteWeight, modifier = Modifier.fillMaxSize())
                DataType.PERIOD -> PeriodPanel(vm.periods, onDelete = vm::deletePeriod, modifier = Modifier.fillMaxSize())
                DataType.BOWEL -> BowelPanel(vm.bowels, onDelete = vm::deleteBowel, modifier = Modifier.fillMaxSize())
            }

            // Snackbar host for export feedback (mobile has its own inside the Scaffold)
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

/** Sidebar navigation entry. Selected item is highlighted with an inverted cyan block. */
@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) LymphBlack else LymphCyan,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) LymphCyan else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    )
}

@Composable
private fun DrawerItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}
