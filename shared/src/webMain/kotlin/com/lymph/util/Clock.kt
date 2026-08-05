package com.lymph.util

actual fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
