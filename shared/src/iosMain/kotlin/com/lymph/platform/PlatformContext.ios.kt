package com.lymph.platform

import com.lymph.data.database.LymphDatabase
import kotlinx.cinterop.BetaInteropX
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

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
        val dbPath = "$getDataDirectory()/lymph.db"
        return LymphDatabase(dbPath)
    }
}
