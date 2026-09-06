package com.lymph.ui.theme

import androidx.compose.ui.graphics.Color

/** True Black — deep background for maximum contrast. */
val LymphBlack = Color(0xFF111111)

/** Glacial Cyan — primary text, chart lines, and interactive elements. */
val LymphCyan = Color(0xFFD6F9FF)

/** Dimmed cyan for secondary labels and borders. */
val LymphCyanDim = Color(0xFF5A7A82)

/** Warm amber — second-series accent for multi-line charts (period/bowel).
 *  High hue + lightness contrast against both the near-black background (~10:1)
 *  and LymphCyan; cyan/amber is a color-blind-safe pair. */
val LymphAmber = Color(0xFFFFB454)

/** Error / alert accent — clinical red. */
val LymphRed = Color(0xFFFF4D4D)

/** Subtle surface elevation (still black, slightly lighter). */
val LymphSurface = Color(0xFF1A1A1A)
