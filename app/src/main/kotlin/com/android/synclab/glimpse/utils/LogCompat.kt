package com.android.synclab.glimpse.utils

import android.util.Log

object LogCompat {
    const val TAG = "ds4batmon"
    private val isDebugBuildCached: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            val buildConfigClass = Class.forName("com.android.synclab.glimpse.BuildConfig")
            buildConfigClass.getField("DEBUG").getBoolean(null)
        }.getOrDefault(false)
    }

    fun isDebugBuild(): Boolean = isDebugBuildCached

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun w(message: String, throwable: Throwable) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String) {
        Log.e(TAG, message)
    }

    fun e(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
    }
}
