package com.lymph

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.lymph.platform.IosDataExporter
import com.lymph.platform.IosPlatform
import com.lymph.platform.LocalPlatformContext
import com.lymph.ui.LymphApp

/**
 * Creates a Compose UI ViewController for iOS.
 * Bridge between SwiftUI and the shared Lymph App.
 */
fun MainViewController() = ComposeUIViewController(
    configure = {
        // Set true to enable dark mode by default on iOS
        it.isOpaque = false
    }
) {
    val platform = IosPlatform()

    CompositionLocalProvider(LocalPlatformContext provides platform) {
        LymphApp(
            database = platform.createLymphDatabase(),
            exporter = IosDataExporter()
        )
    }
}
