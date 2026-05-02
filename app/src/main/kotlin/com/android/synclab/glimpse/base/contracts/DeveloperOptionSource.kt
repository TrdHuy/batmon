package com.android.synclab.glimpse.base.contracts

interface DeveloperOptionSource {
    val isDebuggableApp: Boolean

    fun getSystemProperty(name: String): String?
}
