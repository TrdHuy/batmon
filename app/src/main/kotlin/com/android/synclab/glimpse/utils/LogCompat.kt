package com.android.synclab.glimpse.utils

import android.util.Log
import com.android.synclab.glimpse.BuildConfig

object LogCompat {
    const val TAG = "ds4batmon"

    fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    fun d(message: String) {
        Log.d(TAG, message)
        BatmonFileLogger.write("D", TAG, message)
    }

    inline fun dDebug(message: () -> String) {
        if (isDebugBuild()) {
            val resolvedMessage = message()
            Log.d(TAG, resolvedMessage)
            BatmonFileLogger.write("D", TAG, resolvedMessage)
        }
    }

    fun i(message: String) {
        Log.i(TAG, message)
        BatmonFileLogger.write("I", TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
        BatmonFileLogger.write("W", TAG, message)
    }

    fun w(message: String, throwable: Throwable) {
        Log.w(TAG, message, throwable)
        BatmonFileLogger.write("W", TAG, message, throwable)
    }

    inline fun wDebug(throwable: Throwable, message: () -> String) {
        if (isDebugBuild()) {
            val resolvedMessage = message()
            Log.w(TAG, resolvedMessage, throwable)
            BatmonFileLogger.write("W", TAG, resolvedMessage, throwable)
        }
    }

    fun e(message: String) {
        Log.e(TAG, message)
        BatmonFileLogger.write("E", TAG, message)
    }

    fun e(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
        BatmonFileLogger.write("E", TAG, message, throwable)
    }
}
