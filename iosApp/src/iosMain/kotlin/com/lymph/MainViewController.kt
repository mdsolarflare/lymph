package com.lymph

import androidx.compose.ui.window.ComposeUIViewController
import com.lymph.platform.IosDataExporter
import com.lymph.platform.IosPlatform
import com.lymph.ui.LymphApp

/**
 * Creates a Compose UI ViewController for iOS.
 * Bridge between SwiftUI and the shared Lymph App.
 */
fun MainViewController() = ComposeUIViewController(
    configure = {
        it.isOpaque = false
    }
) {
    val platform = IosPlatform()
    LymphApp(
        database = platform.createLymphDatabase(),
        exporter = IosDataExporter()
    )
}
