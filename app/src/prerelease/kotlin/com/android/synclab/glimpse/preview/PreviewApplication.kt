package com.android.synclab.glimpse.preview

import android.app.Application
import com.android.synclab.githubpreview.GitHubPreview
import com.android.synclab.githubpreview.GitHubPreviewConfig
import com.android.synclab.githubpreview.GitHubPreviewHost
import com.android.synclab.glimpse.BatmonApplication
import com.android.synclab.glimpse.BuildConfig
import com.android.synclab.glimpse.presentation.model.ReportProblemDiagnosticsCollector
import com.android.synclab.glimpse.utils.BatmonFileLogger
import com.android.synclab.glimpse.utils.LogCompat
import java.io.File

class PreviewApplication : BatmonApplication() {
    override fun onCreate() {
        super.onCreate()
        GitHubPreview.init(
            application = this,
            config = GitHubPreviewConfig(
                githubOwner = BuildConfig.GITHUB_PREVIEW_OWNER,
                githubRepo = BuildConfig.GITHUB_PREVIEW_REPO,
                oauthClientId = BuildConfig.GITHUB_PREVIEW_OAUTH_CLIENT_ID,
                releaseTagPrefix = BuildConfig.GITHUB_PREVIEW_RELEASE_TAG_PREFIX,
                apkAssetNamePattern = BuildConfig.GITHUB_PREVIEW_APK_ASSET_PATTERN,
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                buildSha = BuildConfig.GIT_SHA
            ),
            host = BatmonPreviewHost(this)
        )
        LogCompat.i("GitHubPreview initialized")
    }
}

private class BatmonPreviewHost(
    private val application: Application
) : GitHubPreviewHost {
    override fun collectDiagnostics(): Map<String, String> {
        val diagnostics = ReportProblemDiagnosticsCollector(application).collect()
        return mapOf("Batmon diagnostics" to diagnostics.toEmailSection())
    }

    override fun collectLogFile(): File? {
        return BatmonFileLogger.logFile
    }
}
