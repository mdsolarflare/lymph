package com.lymph.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Lymph uses a strict monospace typography everywhere.
 * No rounded corners, no soft shadows — clinical precision only.
 */
@Composable
fun LymphTheme(content: @Composable () -> Unit) {

    val monospace = FontFamily.Monospace

    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = LymphCyan
        ),
        displayMedium = TextStyle(
            fontFamily = monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = LymphCyan
        ),
        headlineLarge = TextStyle(
            fontFamily = monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            color = LymphCyan
        ),
        titleMedium = TextStyle(
            fontFamily = monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            color = LymphCyan
        ),
        bodyLarge = TextStyle(
            fontFamily = monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = LymphCyan
        ),
        bodyMedium = TextStyle(
            fontFamily = monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = LymphCyanDim
        ),
        labelSmall = TextStyle(
            fontFamily = monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            color = LymphCyanDim
        )
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LymphCyan,
            onPrimary = LymphBlack,
            background = LymphBlack,
            surface = LymphSurface,
            onSurface = LymphCyan,
            outline = LymphCyanDim,
            error = LymphRed
        ),
        typography = typography,
        content = content
    )
}
