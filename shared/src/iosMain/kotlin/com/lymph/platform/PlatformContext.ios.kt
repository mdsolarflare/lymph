package com.lymph.platform

import com.lymph.data.database.LymphDatabase
import kotlinx.cinterop.BetaInteropX
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS implementation of [PlatformContext].
 * Uses the app's Documents directory for persistent storage.
 */
@OptIn(BetaInteropX::class)
class IosPlatform : PlatformContext {

    override fun getDataDirectory(): String {
        val url = NSURL.fileURLWithPath(
            NSFileManager.defaultManager.URLForDirectory(
                NSDocumentDirectory,
                NSUserDomainMask,
                null,
                true,
                null
            ).path ?: ""
        )
        return url.path ?: ""
    }

    override fun createLymphDatabase(): LymphDatabase {
        // TODO: Implement iOS-specific persistent database (SQLDelight, Realm, etc.)
        // For now return stub - will need actual implementation
        throw UnsupportedOperationException("iOS database not yet implemented")
    }
}