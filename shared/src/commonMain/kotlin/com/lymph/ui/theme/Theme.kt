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
 *
 * WARNING: every typography slot below hardcodes its color. An explicit style
 * color beats M3's LocalContentColor mechanism, so components whose content
 * color differs from the slot color render invisible text — e.g. filled
 * [androidx.compose.material3.Button] (content should be onPrimary = LymphBlack)
 * draws cyan-on-cyan with a bare Text(). Any label inside such a component must
 * pass an explicit style: `style = MaterialTheme.typography.bodyLarge.copy(color = LymphBlack)`.
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
            primaryContainer = LymphCyan,
            onPrimaryContainer = LymphBlack,
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
