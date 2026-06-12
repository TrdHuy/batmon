package com.android.synclab.glimpse.utils

import android.content.Context
import android.util.Log
import com.android.synclab.glimpse.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogCompat {
    const val TAG = "ds4batmon"

    val logFile: File?
        get() = FileLogger.logFile

    fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    fun initFileLogging(context: Context) {
        FileLogger.init(context)
    }

    fun d(message: String) {
        Log.d(TAG, message)
        FileLogger.write("D", TAG, message)
    }

    fun dDebug(message: () -> String) {
        if (isDebugBuild()) {
            val resolvedMessage = message()
            Log.d(TAG, resolvedMessage)
            FileLogger.write("D", TAG, resolvedMessage)
        }
    }

    fun i(message: String) {
        Log.i(TAG, message)
        FileLogger.write("I", TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
        FileLogger.write("W", TAG, message)
    }

    fun w(message: String, throwable: Throwable) {
        Log.w(TAG, message, throwable)
        FileLogger.write("W", TAG, message, throwable)
    }

    fun wDebug(throwable: Throwable, message: () -> String) {
        if (isDebugBuild()) {
            val resolvedMessage = message()
            Log.w(TAG, resolvedMessage, throwable)
            FileLogger.write("W", TAG, resolvedMessage, throwable)
        }
    }

    fun e(message: String) {
        Log.e(TAG, message)
        FileLogger.write("E", TAG, message)
    }

    fun e(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
        FileLogger.write("E", TAG, message, throwable)
    }

    private object FileLogger {
        private const val LOG_DIR = "logs"
        private const val LOG_FILE_NAME = "batmon.log"
        private const val MAX_LINES = 1000

        private val lock = Any()
        private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)

        @Volatile
        private var initialized = false

        private var logFileInternal: File? = null

        val logFile: File?
            get() = logFileInternal?.takeIf { it.exists() }

        fun init(context: Context) {
            synchronized(lock) {
                val directory = File(context.filesDir, LOG_DIR)
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                logFileInternal = File(directory, LOG_FILE_NAME)
                if (logFileInternal?.exists() != true) {
                    logFileInternal?.createNewFile()
                }
                initialized = true
            }
        }

        fun write(level: String, tag: String, message: String, throwable: Throwable? = null) {
            if (!initialized) {
                return
            }
            val file = logFileInternal ?: return
            val line = buildString {
                append(timestampFormat.format(Date()))
                append(' ')
                append(level)
                append('/')
                append(tag)
                append(": ")
                append(message.replace('\n', ' '))
                throwable?.let {
                    append(" | ")
                    append(it.javaClass.simpleName)
                    append(": ")
                    append(it.message.orEmpty().replace('\n', ' '))
                }
            }

            synchronized(lock) {
                file.appendText(line + "\n")
                trimToLastLines(file)
            }
        }

        private fun trimToLastLines(file: File) {
            val lines = file.readLines()
            if (lines.size <= MAX_LINES) {
                return
            }
            file.writeText(lines.takeLast(MAX_LINES).joinToString(separator = "\n", postfix = "\n"))
        }
    }
}
