package com.android.synclab.githubpreview

import java.io.File

interface GitHubPreviewHost {
    fun collectDiagnostics(): Map<String, String>
    fun collectLogFile(): File?
}
