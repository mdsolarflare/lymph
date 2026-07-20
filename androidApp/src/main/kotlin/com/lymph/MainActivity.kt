package com.lymph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lymph.platform.AndroidPlatform
import com.lymph.ui.LymphApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val platform = AndroidPlatform(this)

        setContent {
            LymphApp(
                database = platform.createLymphDatabase(),
                exporter = com.lymph.platform.AndroidDataExporter()
            )
        }
    }
}

@Preview
@Composable
fun AppPreview() {
    // Preview without a real database — uses empty-state stub for rendering only.
    com.lymph.ui.screen.DashboardScreen.Preview()
}
