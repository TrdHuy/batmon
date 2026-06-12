package com.android.synclab.githubpreview

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal class GitHubApi(
    private val config: GitHubPreviewConfig
) {
    fun requestDeviceCode(): DeviceCode {
        val response = postForm(
            url = "https://github.com/login/device/code",
            fields = mapOf(
                "client_id" to config.oauthClientId,
                "scope" to config.oauthScopes.joinToString(" ")
            ),
            token = null
        )
        return DeviceCode(
            deviceCode = response.getString("device_code"),
            userCode = response.getString("user_code"),
            verificationUri = response.getString("verification_uri"),
            expiresIn = response.optLong("expires_in", 900L),
            interval = response.optLong("interval", 5L)
        )
    }

    fun pollAccessToken(deviceCode: DeviceCode): PollResult {
        val response = postForm(
            url = "https://github.com/login/oauth/access_token",
            fields = mapOf(
                "client_id" to config.oauthClientId,
                "device_code" to deviceCode.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            ),
            token = null,
            throwOnError = false
        )
        val token = response.optString("access_token").takeIf { it.isNotBlank() }
        if (token != null) {
            return PollResult.Success(token)
        }
        return when (val error = response.optString("error")) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "expired_token" -> PollResult.Expired
            "access_denied" -> PollResult.Denied
            else -> PollResult.Failed(response.optString("error_description", error.ifBlank { "Unknown auth error" }))
        }
    }

    fun currentUser(token: String): String {
        return getJsonObject("https://api.github.com/user", token).getString("login")
    }

    fun verifyRepoAccess(token: String) {
        getJsonObject(repoApiUrl(), token)
    }

    fun releases(token: String): List<PreviewRelease> {
        val releases = getJsonArray("${repoApiUrl()}/releases", token)
        val regex = Regex(config.apkAssetNamePattern)
        return (0 until releases.length()).mapNotNull { index ->
            val item = releases.getJSONObject(index)
            val tagName = item.getString("tag_name")
            if (!tagName.startsWith(config.releaseTagPrefix)) {
                return@mapNotNull null
            }
            val asset = firstMatchingApkAsset(item.getJSONArray("assets"), regex) ?: return@mapNotNull null
            PreviewRelease(
                id = item.getLong("id"),
                tagName = tagName,
                name = item.optString("name").ifBlank { tagName },
                body = item.optString("body"),
                prerelease = item.optBoolean("prerelease"),
                publishedAt = item.optString("published_at"),
                asset = asset
            )
        }
    }

    fun downloadAsset(token: String, asset: PreviewAsset, destination: File) {
        val connection = openConnection(asset.apiUrl, token).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("Download failed: HTTP $code ${connection.errorText()}")
        }
        destination.parentFile?.mkdirs()
        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun createIssue(
        token: String,
        title: String,
        body: String
    ): String {
        val payload = JSONObject()
            .put("title", title)
            .put("body", body)
        val response = postJson("${repoApiUrl()}/issues", payload, token)
        return response.getString("html_url")
    }

    private fun firstMatchingApkAsset(assets: JSONArray, regex: Regex): PreviewAsset? {
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.getString("name")
            if (!regex.matches(name)) {
                continue
            }
            return PreviewAsset(
                id = asset.getLong("id"),
                name = name,
                size = asset.optLong("size"),
                apiUrl = asset.getString("url")
            )
        }
        return null
    }

    private fun repoApiUrl(): String {
        return "https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}"
    }

    private fun getJsonObject(url: String, token: String): JSONObject {
        return requestJsonObject(url = url, method = "GET", token = token, body = null)
    }

    private fun getJsonArray(url: String, token: String): JSONArray {
        val connection = openConnection(url, token).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val code = connection.responseCode
        val text = connection.responseText()
        if (code !in 200..299) {
            throw IllegalStateException("GitHub request failed: HTTP $code $text")
        }
        return JSONArray(text)
    }

    private fun postJson(url: String, payload: JSONObject, token: String): JSONObject {
        return requestJsonObject(
            url = url,
            method = "POST",
            token = token,
            body = payload.toString(),
            contentType = "application/json"
        )
    }

    private fun postForm(
        url: String,
        fields: Map<String, String>,
        token: String?,
        throwOnError: Boolean = true
    ): JSONObject {
        val body = fields.entries.joinToString("&") { entry ->
            "${Uri.encode(entry.key)}=${Uri.encode(entry.value)}"
        }
        return requestJsonObject(
            url = url,
            method = "POST",
            token = token,
            body = body,
            contentType = "application/x-www-form-urlencoded",
            throwOnError = throwOnError
        )
    }

    private fun requestJsonObject(
        url: String,
        method: String,
        token: String?,
        body: String?,
        contentType: String? = null,
        throwOnError: Boolean = true
    ): JSONObject {
        val connection = openConnection(url, token).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            contentType?.let { setRequestProperty("Content-Type", it) }
        }
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val text = connection.responseText()
        if (throwOnError && code !in 200..299) {
            throw IllegalStateException("GitHub request failed: HTTP $code $text")
        }
        return JSONObject(text.ifBlank { "{}" })
    }

    private fun openConnection(url: String, token: String?): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "github-preview-sdk")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    private fun HttpURLConnection.responseText(): String {
        return runCatching {
            val stream = if (responseCode in 200..299) inputStream else errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
    }

    private fun HttpURLConnection.errorText(): String {
        return runCatching {
            errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
    }
}

internal data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Long,
    val interval: Long
)

internal sealed class PollResult {
    data class Success(val token: String) : PollResult()
    data object Pending : PollResult()
    data object SlowDown : PollResult()
    data object Expired : PollResult()
    data object Denied : PollResult()
    data class Failed(val message: String) : PollResult()
}

internal data class PreviewRelease(
    val id: Long,
    val tagName: String,
    val name: String,
    val body: String,
    val prerelease: Boolean,
    val publishedAt: String,
    val asset: PreviewAsset
) {
    val label: String
        get() = String.format(Locale.US, "%s (%s)", name, tagName)
}

internal data class PreviewAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val apiUrl: String
)
