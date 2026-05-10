package com.android.synclab.glimpse.utils

import android.util.Log
import com.android.synclab.glimpse.BuildConfig

object LogCompat {
    const val TAG = "ds4batmon"

    fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    fun d(message: String) {
        Log.d(TAG, message)
    }

    inline fun dDebug(message: () -> String) {
        if (isDebugBuild()) {
            Log.d(TAG, message())
        }
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

    inline fun wDebug(throwable: Throwable, message: () -> String) {
        if (isDebugBuild()) {
            Log.w(TAG, message(), throwable)
        }
    }

    fun e(message: String) {
        Log.e(TAG, message)
    }

    fun e(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
    }
}
