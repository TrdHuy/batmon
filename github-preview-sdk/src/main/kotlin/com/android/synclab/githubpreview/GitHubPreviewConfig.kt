package com.android.synclab.githubpreview

data class GitHubPreviewConfig(
    val githubOwner: String,
    val githubRepo: String,
    val oauthClientId: String,
    val releaseTagPrefix: String,
    val apkAssetNamePattern: String,
    val currentVersionName: String,
    val currentVersionCode: Long,
    val buildSha: String?,
    val oauthScopes: List<String> = listOf("repo")
) {
    fun missingFields(): List<String> {
        return buildList {
            if (githubOwner.isBlank()) add("githubOwner")
            if (githubRepo.isBlank()) add("githubRepo")
            if (oauthClientId.isBlank()) add("oauthClientId")
            if (releaseTagPrefix.isBlank()) add("releaseTagPrefix")
            if (apkAssetNamePattern.isBlank()) add("apkAssetNamePattern")
        }
    }
}
