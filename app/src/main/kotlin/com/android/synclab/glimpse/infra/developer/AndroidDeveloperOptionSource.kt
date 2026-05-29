package com.android.synclab.glimpse.infra.developer

import android.content.Context
import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource

class AndroidDeveloperOptionSource(
    context: Context,
    override val isDebuggableApp: Boolean
) : DeveloperOptionSource {
    private val appContext = context.applicationContext

    override fun getSystemProperty(name: String): String? {
        return runCatching {
            val process = ProcessBuilder("/system/bin/getprop", name)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { reader ->
                reader.readText().trim().takeIf { it.isNotEmpty() }
            }.also {
                process.waitFor()
            }
        }.getOrNull()
    }

    override fun isProtectBatteryToolsEnabled(): Boolean {
        return DeveloperOptionPrefs.isProtectBatteryToolsEnabled(appContext)
    }
}
