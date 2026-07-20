package com.lymph.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lymph.data.export.DataExporter
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
 * EXPANDED: Master-detail grid with sidebar nav + wide data panels side-by-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BiometricViewModel,
    exporter: DataExporter,
    isExpanded: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    var showModal by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Mobile uses a drawer; expanded does not need one.
    if (isExpanded) {
        ExpandedLayout(viewModel, onAddClick = { showModal = true })
    } else {
        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("LYMPH v0.1", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        DrawerItem(text = "Export Data") {
                            showMenu = false
                            coroutineScope.launch {
                                try {
                                    exporter.exportZip(viewModel.database)
                                    snackbarHostState.showSnackbar("EXPORT OK — zip generated")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("EXPORT FAILED: ${e.message ?: "unknown"}")
                                }
                            }
                        }
                        DrawerItem(text = "Settings") { showMenu = false }
                    }
                }
            },
            gesturesEnabled = showMenu,
            content = {
                MobileLayout(
                    vm = viewModel,
                    onMenuClick = { showMenu = true },
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

@Composable
private fun ExpandedLayout(
    vm: BiometricViewModel,
    onAddClick: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        // Sidebar — master nav
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
            Text("[ WEIGHT ]", style = MaterialTheme.typography.bodyLarge)
            Text("[ PERIOD ]", style = MaterialTheme.typography.bodyLarge)
            Text("[ BOWEL  ]", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Button(onClick = onAddClick, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ADD RECORD")
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        // Detail panels — side by side
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            WeightPanel(vm.weights, onDelete = vm::deleteWeight)
            PeriodPanel(vm.periods, onDelete = vm::deletePeriod)
            BowelPanel(vm.bowels, onDelete = vm::deleteBowel)
        }
    }
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
