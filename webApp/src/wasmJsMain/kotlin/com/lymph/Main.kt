package com.lymph

import androidx.compose.ui.platform.ComposeUI
import org.jetbrains.compose.web.css.*
import org.w3c.dom.document
import com.lymph.platform.WasmPlatform
import com.lymph.ui.LymphApp

fun main() {
    val platform = WasmPlatform()

    document.body?.let { body ->
        ComposeUI(rootElement = body) {
            div {
                style {
                    width = 100.pct
                    height = 100.vh
                    margin = Margin(0.px, 0.px, 0.px, 0.px)
                    padding = Padding(0.px, 0.px, 0.px, 0.px)
                    overflow = Overflow.hidden
                }

                LymphApp(
                    database = platform.createLymphDatabase(),
                    exporter = com.lymph.platform.WasmDataExporter()
                )
            }
        }
    }
}
