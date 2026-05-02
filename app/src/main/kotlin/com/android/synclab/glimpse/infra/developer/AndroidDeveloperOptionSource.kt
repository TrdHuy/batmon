package com.android.synclab.glimpse.infra.developer

import com.android.synclab.glimpse.base.contracts.DeveloperOptionSource

class AndroidDeveloperOptionSource(
    override val isDebuggableApp: Boolean
) : DeveloperOptionSource {
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
}
