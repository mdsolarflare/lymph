package com.lymph.platform

import android.content.Context
import com.lymph.data.database.LymphDatabase

class AndroidPlatform(private val context: Context) : PlatformContext {

    override fun getDataDirectory(): String = context.filesDir.absolutePath

    override fun createLymphDatabase(): LymphDatabase {
        val dbPath = "$getDataDirectory()/lymph.db"
        return LymphDatabase(dbPath)
    }
}
