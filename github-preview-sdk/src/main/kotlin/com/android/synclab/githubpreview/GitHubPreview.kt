package com.android.synclab.githubpreview

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build

object GitHubPreview {
    internal const val SHORTCUT_ID = "github_preview_program"

    @Volatile
    internal var state: GitHubPreviewState? = null
        private set

    fun init(
        application: Application,
        config: GitHubPreviewConfig,
        host: GitHubPreviewHost
    ) {
        state = GitHubPreviewState(
            application = application,
            config = config,
            host = host,
            tokenStore = TokenStore(application)
        )
        registerShortcut(application)
    }

    fun openPreviewCenter(context: Context) {
        context.startActivity(
            Intent(context, PreviewCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun logout() {
        state?.tokenStore?.clearToken()
    }

    private fun registerShortcut(application: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }
        val shortcutManager = application.getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = ShortcutInfo.Builder(application, SHORTCUT_ID)
            .setShortLabel("Preview Program")
            .setLongLabel("Preview Program")
            .setIcon(Icon.createWithResource(application, application.applicationInfo.icon))
            .setIntent(
                Intent(application, PreviewCenterActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                }
            )
            .build()
        shortcutManager.dynamicShortcuts = listOf(shortcut)
    }
}

internal data class GitHubPreviewState(
    val application: Application,
    val config: GitHubPreviewConfig,
    val host: GitHubPreviewHost,
    val tokenStore: TokenStore
)
