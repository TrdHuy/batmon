package com.android.synclab.glimpse

import android.app.Application
import com.android.synclab.glimpse.utils.BatmonFileLogger
import com.android.synclab.glimpse.utils.LogCompat

open class BatmonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BatmonFileLogger.init(this)
        LogCompat.i("BatmonApplication created")
    }
}
