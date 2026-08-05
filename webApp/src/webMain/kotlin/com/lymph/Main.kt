package com.lymph

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.lymph.platform.WebPlatform
import com.lymph.platform.WebDataExporter
import com.lymph.ui.LymphApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val platform = WebPlatform()
    ComposeViewport {
        LymphApp(
            database = platform.createLymphDatabase(),
            exporter = WebDataExporter()
        )
    }
}
