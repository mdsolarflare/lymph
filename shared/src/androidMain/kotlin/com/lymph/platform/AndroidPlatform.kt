package com.lymph.platform

import android.content.Context
import com.lymph.data.database.LymphDatabase

/**
 * Android implementation of [PlatformContext].
 * Uses in-memory database for now — persistent backend to be added.
 */
class AndroidPlatform(private val context: Context) : PlatformContext {

    override fun getDataDirectory(): String = context.filesDir.absolutePath

    override fun createLymphDatabase(): LymphDatabase {
        // TODO: Implement Android-specific persistent database (SQLDelight, Realm, etc.)
        // For now return stub - will need actual implementation
        throw UnsupportedOperationException("Android database not yet implemented")
    }
}