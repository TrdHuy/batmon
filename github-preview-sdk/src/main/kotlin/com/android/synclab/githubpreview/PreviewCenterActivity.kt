package com.android.synclab.githubpreview

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreviewCenterActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var root: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var accountView: TextView
    private lateinit var releaseContainer: LinearLayout
    private lateinit var progressBar: ProgressBar

    private var state: GitHubPreviewState? = null
    private var api: GitHubApi? = null
    private var currentTask: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = GitHubPreview.state
        api = state?.config?.let(::GitHubApi)
        buildLayout()
        renderInitialState()
    }

    override fun onDestroy() {
        currentTask?.interrupt()
        super.onDestroy()
    }

    private fun buildLayout() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(38, 38, 38))
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32.dp(), 40.dp(), 32.dp(), 40.dp())
        }
        scrollView.addView(root)
        setContentView(scrollView)

        root.addView(text("Preview Program", 28f, Color.WHITE, bold = true))
        statusView = text("Ready", 14f, Color.LTGRAY)
        root.addView(statusView.withTopMargin(12.dp()))

        accountView = text("", 16f, Color.WHITE)
        root.addView(accountView.withTopMargin(20.dp()))

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        root.addView(progressBar.withTopMargin(16.dp()))

        root.addView(button("Login GitHub") { login() }.withTopMargin(20.dp()))
        root.addView(button("Check Update") { loadReleases() }.withTopMargin(10.dp()))
        root.addView(button("Report Bug") { showReportDialog() }.withTopMargin(10.dp()))
        root.addView(button("Logout") { logout() }.withTopMargin(10.dp()))

        root.addView(sectionTitle("Current Version").withTopMargin(28.dp()))
        val config = state?.config
        root.addView(
            text(
                "${config?.currentVersionName ?: "unknown"} (${config?.currentVersionCode ?: "unknown"})\n" +
                        "Build SHA: ${config?.buildSha?.takeIf { it.isNotBlank() } ?: "n/a"}",
                15f,
                Color.LTGRAY
            ).withTopMargin(8.dp())
        )

        root.addView(sectionTitle("Version List").withTopMargin(28.dp()))
        releaseContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(releaseContainer.withTopMargin(8.dp()))
    }

    private fun renderInitialState() {
        val currentState = state
        if (currentState == null) {
            setStatus("GitHubPreview.init(...) has not been called.")
            accountView.text = "Not initialized"
            return
        }
        val missingFields = currentState.config.missingFields()
        if (missingFields.isNotEmpty()) {
            setStatus("Missing preview config: ${missingFields.joinToString()}")
        }
        val token = currentState.tokenStore.token()
        accountView.text = if (token == null) {
            "GitHub Account: Not logged in"
        } else {
            "GitHub Account: Logged in"
        }
    }

    private fun login() {
        val currentState = requireStateOrShow() ?: return
        val currentApi = requireApiOrShow() ?: return
        val missingFields = currentState.config.missingFields()
        if (missingFields.isNotEmpty()) {
            setStatus("Cannot login. Missing config: ${missingFields.joinToString()}")
            return
        }

        runTask(
            startMessage = "Requesting GitHub device code...",
            task = { currentApi.requestDeviceCode() },
            success = { deviceCode ->
                showDeviceCodeDialog(deviceCode)
                pollDeviceToken(deviceCode)
            }
        )
    }

    private fun showDeviceCodeDialog(deviceCode: DeviceCode) {
        val message = "Open ${deviceCode.verificationUri} and enter code:\n\n${deviceCode.userCode}"
        AlertDialog.Builder(this)
            .setTitle("Login GitHub")
            .setMessage(message)
            .setPositiveButton("Open GitHub") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deviceCode.verificationUri)))
            }
            .setNegativeButton("Copy Code") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("GitHub device code", deviceCode.userCode))
                showToast("Code copied")
            }
            .setNeutralButton("Close", null)
            .show()
    }

    private fun pollDeviceToken(deviceCode: DeviceCode) {
        val currentState = requireStateOrShow() ?: return
        val currentApi = requireApiOrShow() ?: return
        currentTask?.interrupt()
        currentTask = Thread {
            var interval = deviceCode.interval.coerceAtLeast(1L)
            val deadline = System.currentTimeMillis() + deviceCode.expiresIn * 1000L
            while (!Thread.currentThread().isInterrupted && System.currentTimeMillis() < deadline) {
                Thread.sleep(interval * 1000L)
                when (val result = currentApi.pollAccessToken(deviceCode)) {
                    is PollResult.Success -> {
                        currentState.tokenStore.saveToken(result.token)
                        currentApi.verifyRepoAccess(result.token)
                        val user = currentApi.currentUser(result.token)
                        onMain {
                            setProgress(false)
                            accountView.text = "GitHub Account: @$user"
                            setStatus("Login successful.")
                        }
                        return@Thread
                    }

                    PollResult.Pending -> onMain { setStatus("Waiting for GitHub authorization...") }
                    PollResult.SlowDown -> {
                        interval += 5L
                        onMain { setStatus("GitHub asked to slow down polling...") }
                    }

                    PollResult.Expired -> {
                        onMain {
                            setProgress(false)
                            setStatus("Device code expired. Login again.")
                        }
                        return@Thread
                    }

                    PollResult.Denied -> {
                        onMain {
                            setProgress(false)
                            setStatus("GitHub login denied.")
                        }
                        return@Thread
                    }

                    is PollResult.Failed -> {
                        onMain {
                            setProgress(false)
                            setStatus(result.message)
                        }
                        return@Thread
                    }
                }
            }
            onMain {
                setProgress(false)
                setStatus("Device code expired. Login again.")
            }
        }.also {
            setProgress(true)
            setStatus("Waiting for GitHub authorization...")
            it.start()
        }
    }

    private fun loadReleases() {
        val token = requireTokenOrShow() ?: return
        val currentApi = requireApiOrShow() ?: return
        runTask(
            startMessage = "Loading releases...",
            task = { currentApi.releases(token) },
            success = { releases ->
                renderReleases(releases)
                setStatus("Loaded ${releases.size} preview release(s).")
            }
        )
    }

    private fun renderReleases(releases: List<PreviewRelease>) {
        releaseContainer.removeAllViews()
        if (releases.isEmpty()) {
            releaseContainer.addView(text("No preview APK releases found.", 15f, Color.LTGRAY))
            return
        }
        releases.forEach { release ->
            releaseContainer.addView(releaseCard(release).withTopMargin(10.dp()))
        }
    }

    private fun releaseCard(release: PreviewRelease): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
            setBackgroundColor(Color.rgb(48, 48, 48))
            addView(text(release.label, 18f, Color.WHITE, bold = true))
            addView(
                text(
                    "Asset: ${release.asset.name}\nPublished: ${release.publishedAt.ifBlank { "n/a" }}\n\n${release.body.ifBlank { "No release notes." }}",
                    14f,
                    Color.LTGRAY
                ).withTopMargin(8.dp())
            )
            addView(button("Install") { installRelease(release) }.withTopMargin(12.dp()))
        }
    }

    private fun installRelease(release: PreviewRelease) {
        val token = requireTokenOrShow() ?: return
        val currentApi = requireApiOrShow() ?: return
        val config = state?.config ?: return
        runTask(
            startMessage = "Downloading ${release.asset.name}...",
            task = {
                val destination = File(cacheDir, "github-preview/${release.asset.name}")
                currentApi.downloadAsset(token, release.asset, destination)
                verifyDownloadedApk(destination, config)
                destination
            },
            success = { apkFile ->
                setStatus("Download verified. Opening installer...")
                openInstaller(apkFile)
            }
        )
    }

    private fun verifyDownloadedApk(apkFile: File, config: GitHubPreviewConfig) {
        val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: throw IllegalStateException("Downloaded file is not a valid APK.")
        if (packageInfo.packageName != packageName) {
            throw IllegalStateException(
                "APK package mismatch. Expected $packageName, got ${packageInfo.packageName}."
            )
        }
        val downloadedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        if (downloadedVersionCode < config.currentVersionCode) {
            throw IllegalStateException(
                "APK is older than current version: $downloadedVersionCode < ${config.currentVersionCode}."
            )
        }
    }

    private fun openInstaller(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            showToast("Allow install unknown apps for preview updates.")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.githubpreview.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun showReportDialog() {
        val token = requireTokenOrShow() ?: return
        val currentApi = requireApiOrShow() ?: return
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 0)
        }
        val titleInput = input("Title")
        val categoryInput = input("Category (optional)")
        val descriptionInput = input("Description").apply {
            minLines = 5
            gravity = Gravity.TOP
        }
        form.addView(titleInput)
        form.addView(categoryInput.withTopMargin(8.dp()))
        form.addView(descriptionInput.withTopMargin(8.dp()))

        AlertDialog.Builder(this)
            .setTitle("Report Bug")
            .setView(form)
            .setPositiveButton("Create Issue") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                val category = categoryInput.text.toString().trim()
                if (title.isBlank() || description.isBlank()) {
                    showToast("Title and description are required.")
                    return@setPositiveButton
                }
                createIssue(
                    api = currentApi,
                    token = token,
                    title = title,
                    description = description,
                    category = category
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createIssue(
        api: GitHubApi,
        token: String,
        title: String,
        description: String,
        category: String
    ) {
        runTask(
            startMessage = "Creating GitHub issue...",
            task = {
                val body = buildIssueBody(description = description, category = category)
                api.createIssue(token = token, title = title, body = body)
            },
            success = { issueUrl ->
                setStatus("Issue created.")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)))
            }
        )
    }

    private fun buildIssueBody(description: String, category: String): String {
        val currentState = state ?: return description
        val diagnostics = currentState.host.collectDiagnostics()
        val logText = currentState.host.collectLogFile()
            ?.takeIf { it.exists() }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
            ?: "(no log file)"
        return buildString {
            appendLine("## Description")
            appendLine(description)
            appendLine()
            appendLine("## Category")
            appendLine(category.ifBlank { "(not provided)" })
            appendLine()
            appendLine("## Preview Build")
            appendLine("- Version: ${currentState.config.currentVersionName} (${currentState.config.currentVersionCode})")
            appendLine("- Build SHA: ${currentState.config.buildSha?.takeIf { it.isNotBlank() } ?: "n/a"}")
            appendLine("- Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine()
            appendLine("## Diagnostics")
            diagnostics.forEach { (key, value) ->
                appendLine("### $key")
                appendLine(value)
                appendLine()
            }
            appendLine("## Log Tail")
            appendLine("```")
            appendLine(logText.takeLast(MAX_ISSUE_LOG_CHARS))
            appendLine("```")
        }
    }

    private fun logout() {
        GitHubPreview.logout()
        accountView.text = "GitHub Account: Not logged in"
        releaseContainer.removeAllViews()
        setStatus("Logged out.")
    }

    private fun requireStateOrShow(): GitHubPreviewState? {
        return state.also {
            if (it == null) {
                setStatus("GitHubPreview.init(...) has not been called.")
            }
        }
    }

    private fun requireApiOrShow(): GitHubApi? {
        return api.also {
            if (it == null) {
                setStatus("GitHub preview API is not initialized.")
            }
        }
    }

    private fun requireTokenOrShow(): String? {
        val token = state?.tokenStore?.token()
        if (token == null) {
            setStatus("Login GitHub first.")
        }
        return token
    }

    private fun <T> runTask(
        startMessage: String,
        task: () -> T,
        success: (T) -> Unit
    ) {
        currentTask?.interrupt()
        setProgress(true)
        setStatus(startMessage)
        currentTask = Thread {
            try {
                val result = task()
                onMain {
                    setProgress(false)
                    success(result)
                }
            } catch (throwable: Throwable) {
                onMain {
                    setProgress(false)
                    setStatus(throwable.message ?: throwable.javaClass.simpleName)
                }
            }
        }.also { it.start() }
    }

    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun setProgress(visible: Boolean) {
        progressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun setStatus(message: String) {
        statusView.text = message
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun text(
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (bold) {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun sectionTitle(value: String): TextView {
        return text(value, 18f, Color.WHITE, bold = true)
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    private fun input(hintValue: String): EditText {
        return EditText(this).apply {
            hint = hintValue
            setSingleLine(false)
        }
    }

    private fun View.withTopMargin(marginTop: Int): View {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
        }
        return this
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private companion object {
        private const val MAX_ISSUE_LOG_CHARS = 60_000
    }
}
