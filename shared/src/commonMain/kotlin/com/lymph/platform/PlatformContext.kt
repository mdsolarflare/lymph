package com.lymph.platform

import com.lymph.data.database.LymphDatabase

/**
 * Platform abstraction for database creation and file-system paths.
 * Each target provides its own implementation (Android, iOS, Wasm).
 */
interface PlatformContext {

    /** Returns the absolute path to the app's data directory on this platform. */
    fun getDataDirectory(): String

    /** Creates a [LymphDatabase] instance using the platform-specific store location. */
    fun createLymphDatabase(): LymphDatabase
}
