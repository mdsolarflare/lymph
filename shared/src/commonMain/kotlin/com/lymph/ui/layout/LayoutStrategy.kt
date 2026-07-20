package com.lymph.layout

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.dp

/**
 * Responsive layout strategy for Lymph.
 *
 * Below [expandedBreakpoint] → single-column mobile dashboard with FAB and sandwich bar.
 * At or above [expandedBreakpoint] → master-detail split: sidebar nav + dense data panels side-by-side.
 */
@Stable
object LayoutStrategy {

    /** Standard breakpoint in dp for expanded layout. */
    val expandedBreakpoint = 840.dp
}
