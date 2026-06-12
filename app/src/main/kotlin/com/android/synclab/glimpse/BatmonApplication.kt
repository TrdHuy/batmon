package com.android.synclab.glimpse

import android.app.Application
import com.android.synclab.glimpse.utils.LogCompat

open class BatmonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogCompat.initFileLogging(this)
        LogCompat.i("BatmonApplication created")
    }
}
