package com.android.synclab.glimpse.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ListPopupWindow
import com.android.synclab.glimpse.BuildConfig
import com.android.synclab.glimpse.R
import com.android.synclab.glimpse.di.AppContainer
import com.android.synclab.glimpse.presentation.model.ReportProblemDiagnostics
import com.android.synclab.glimpse.utils.LogCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ReportProblemActivity : AppCompatActivity() {
    private lateinit var reportProblemScroll: ScrollView
    private lateinit var problemTypeLayout: TextInputLayout
    private lateinit var problemTypeInput: MaterialAutoCompleteTextView
    private lateinit var descriptionLayout: TextInputLayout
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var stepsInput: TextInputEditText
    private lateinit var contactEmailLayout: TextInputLayout
    private lateinit var contactEmailInput: TextInputEditText
    private var baseScrollPaddingBottom: Int = 0
    private var imeBottomInsetPx: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_problem)

        reportProblemScroll = findViewById(R.id.reportProblemScroll)
        baseScrollPaddingBottom = reportProblemScroll.paddingBottom
        problemTypeLayout = findViewById(R.id.problemTypeLayout)
        problemTypeInput = findViewById(R.id.problemTypeInput)
        descriptionLayout = findViewById(R.id.problemDescriptionLayout)
        descriptionInput = findViewById(R.id.problemDescriptionInput)
        stepsInput = findViewById(R.id.problemStepsInput)
        contactEmailLayout = findViewById(R.id.contactEmailLayout)
        contactEmailInput = findViewById(R.id.contactEmailInput)

        setupProblemTypeDropdown()
        setupKeyboardAwareFieldScrolling()
        setupImeInsetsTracking()

        findViewById<View>(R.id.reportProblemBackButton).setOnClickListener {
            LogCompat.d("Report problem back clicked")
            finish()
        }
        findViewById<MaterialButton>(R.id.sendReportButton).setOnClickListener {
            LogCompat.d("Report problem send clicked")
            sendProblemReport()
        }
    }

    private fun setupImeInsetsTracking() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            updateImeBottomInset(
                insets.getInsets(WindowInsets.Type.ime()).bottom
            )
            insets
        }
        window.decorView.requestApplyInsets()
    }

    private fun updateImeBottomInset(imeBottom: Int) {
        if (imeBottomInsetPx == imeBottom) {
            return
        }

        imeBottomInsetPx = imeBottom
        reportProblemScroll.setPadding(
            reportProblemScroll.paddingLeft,
            reportProblemScroll.paddingTop,
            reportProblemScroll.paddingRight,
            baseScrollPaddingBottom + imeBottom
        )

        if (imeBottom > 0) {
            currentFocus
                ?.takeIf(::isKeyboardAwareField)
                ?.let(::ensureFocusedFieldVisible)
        }
    }

    private fun isKeyboardAwareField(view: View): Boolean {
        return view == descriptionInput ||
                view == stepsInput ||
                view == contactEmailInput
    }

    private fun setupKeyboardAwareFieldScrolling() {
        listOf(
            descriptionInput,
            stepsInput,
            contactEmailInput
        ).forEach { input ->
            input.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    ensureFocusedFieldVisible(view)
                }
            }
            input.setOnClickListener { view ->
                ensureFocusedFieldVisible(view)
            }
        }
    }

    private fun ensureFocusedFieldVisible(focusedView: View) {
        focusedView.post {
            scrollFocusedFieldIntoView(focusedView)
        }
        focusedView.postDelayed(
            { scrollFocusedFieldIntoView(focusedView) },
            KEYBOARD_SCROLL_DELAY_MS
        )
    }

    private fun scrollFocusedFieldIntoView(focusedView: View) {
        if (!focusedView.hasFocus() || reportProblemScroll.height == 0) {
            return
        }

        val imeBottom = currentImeBottomInset()
        updateImeBottomInset(imeBottom)

        val visibleWindowFrame = Rect()
        window.decorView.getWindowVisibleDisplayFrame(visibleWindowFrame)

        val scrollLocation = IntArray(2)
        reportProblemScroll.getLocationOnScreen(scrollLocation)
        val scrollTopOnScreen = scrollLocation[1]
        val scrollBottomOnScreen = scrollLocation[1] + reportProblemScroll.height
        val keyboardTopOnScreen = keyboardTopOnScreen(imeBottom)
        val visibleTopOnScreen = maxOf(
            scrollTopOnScreen,
            visibleWindowFrame.top
        ) + FOCUSED_FIELD_VISIBLE_MARGIN_DP.toPx()
        val visibleBottomOnScreen = minOf(
            scrollBottomOnScreen,
            visibleWindowFrame.bottom,
            keyboardTopOnScreen ?: Int.MAX_VALUE
        ) - FOCUSED_FIELD_VISIBLE_MARGIN_DP.toPx()

        val fieldLocation = IntArray(2)
        focusedView.getLocationOnScreen(fieldLocation)
        val fieldTopOnScreen = fieldLocation[1]
        val fieldBottomOnScreen = fieldLocation[1] + focusedView.height

        val targetScrollY = when {
            fieldBottomOnScreen > visibleBottomOnScreen ->
                reportProblemScroll.scrollY + fieldBottomOnScreen - visibleBottomOnScreen
            fieldTopOnScreen < visibleTopOnScreen ->
                reportProblemScroll.scrollY - (visibleTopOnScreen - fieldTopOnScreen)
            else -> return
        }.coerceAtLeast(0)

        reportProblemScroll.smoothScrollTo(0, targetScrollY)
    }

    private fun currentImeBottomInset(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return 0
        }

        return window.decorView
            .rootWindowInsets
            ?.getInsets(WindowInsets.Type.ime())
            ?.bottom
            ?: imeBottomInsetPx
    }

    private fun keyboardTopOnScreen(imeBottom: Int): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        if (imeBottom <= 0) {
            return null
        }

        val decorLocation = IntArray(2)
        window.decorView.getLocationOnScreen(decorLocation)
        return decorLocation[1] + window.decorView.height - imeBottom
    }

    private fun setupProblemTypeDropdown() {
        problemTypeInput.setOnClickListener {
            showProblemTypeDropdown()
        }
        problemTypeInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showProblemTypeDropdown()
            }
        }
        problemTypeLayout.setEndIconOnClickListener {
            showProblemTypeDropdown()
        }
    }

    private fun showProblemTypeDropdown() {
        val options = problemTypeOptions()
        val popup = ListPopupWindow(this).apply {
            anchorView = problemTypeLayout
            width = problemTypeLayout.width
            height = ListPopupWindow.WRAP_CONTENT
            isModal = true
            setAdapter(
                ArrayAdapter(
                    this@ReportProblemActivity,
                    android.R.layout.simple_list_item_1,
                    options
                )
            )
            setOnItemClickListener { _, _, position, _ ->
                problemTypeInput.setText(options[position], false)
                problemTypeLayout.error = null
                dismiss()
            }
        }
        popup.show()
    }

    private fun sendProblemReport() {
        val problemType = selectedProblemType()
        val description = descriptionInput.text?.toString()?.trim().orEmpty()
        val steps = stepsInput.text?.toString()?.trim().orEmpty()
        val contactEmail = contactEmailInput.text?.toString()?.trim().orEmpty()

        if (problemType == null) {
            problemTypeLayout.error = getString(R.string.report_problem_type_required)
            return
        }
        problemTypeLayout.error = null
        if (description.isBlank()) {
            descriptionLayout.error = getString(R.string.report_problem_description_required)
            return
        }
        descriptionLayout.error = null

        if (
            contactEmail.isNotBlank() &&
            !Patterns.EMAIL_ADDRESS.matcher(contactEmail).matches()
        ) {
            contactEmailLayout.error = getString(R.string.report_problem_email_invalid)
            return
        }
        contactEmailLayout.error = null

        val diagnostics = buildDiagnostics()
        val subject = getString(R.string.report_problem_email_subject, problemType)
        val body = buildEmailBody(
            problemType = problemType,
            description = description,
            steps = steps,
            contactEmail = contactEmail,
            diagnostics = diagnostics
        )
        openEmailComposer(subject = subject, body = body)
    }

    private fun selectedProblemType(): String? {
        val selected = problemTypeInput.text?.toString()?.trim().orEmpty()
        return selected.takeIf { it in problemTypeOptions() }
    }

    private fun problemTypeOptions(): List<String> {
        return listOf(
            getString(R.string.report_problem_type_controller_not_detected),
            getString(R.string.report_problem_type_battery_info_wrong),
            getString(R.string.report_problem_type_live_battery_overlay),
            getString(R.string.report_problem_type_background_monitoring),
            getString(R.string.report_problem_type_customize_vibe),
            getString(R.string.report_problem_type_ui_layout),
            getString(R.string.report_problem_type_other)
        )
    }

    private fun buildDiagnostics(): ReportProblemDiagnostics {
        val appContainer = AppContainer.from(applicationContext)
        val monitoringState = appContainer.provideMonitoringStateProvider()
        val controllers = runCatching {
            appContainer
                .provideConnectedPs4ControllersUseCase()
                .invoke(getString(R.string.unknown_controller_name))
                .map { controller ->
                    ReportProblemDiagnostics.ControllerDiagnostics(
                        name = controller.name,
                        deviceId = controller.deviceId,
                        vendorId = controller.vendorId,
                        productId = controller.productId,
                        descriptor = controller.descriptor,
                        batteryPercent = controller.batteryPercent,
                        batteryStatus = controller.batteryStatus?.name ?: "UNKNOWN"
                    )
                }
        }.getOrElse { throwable ->
            LogCompat.e("Failed to collect report controller diagnostics", throwable)
            emptyList()
        }

        return ReportProblemDiagnostics(
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            packageName = packageName,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z",
                Locale.US
            ).format(Date()),
            serviceRunning = monitoringState.isServiceRunning,
            monitoringEnabled = monitoringState.isMonitoringEnabled,
            overlayVisible = monitoringState.isOverlayVisible,
            controllers = controllers
        )
    }

    private fun buildEmailBody(
        problemType: String,
        description: String,
        steps: String,
        contactEmail: String,
        diagnostics: ReportProblemDiagnostics
    ): String {
        return buildString {
            appendLine("Problem type:")
            appendLine(problemType)
            appendLine()
            appendLine("Description:")
            appendLine(description)
            appendLine()
            appendLine("Steps to reproduce:")
            appendLine(steps.ifBlank { "(not provided)" })
            appendLine()
            appendLine("Contact email:")
            appendLine(contactEmail.ifBlank { "(not provided)" })
            appendLine()
            appendLine(diagnostics.toEmailSection())
            appendLine()
            appendLine("Privacy note:")
            appendLine(getString(R.string.report_problem_privacy_note))
        }.trimEnd()
    }

    private fun openEmailComposer(subject: String, body: String) {
        val supportEmail = getString(R.string.report_problem_support_email)
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = buildMailtoUri(
                supportEmail = supportEmail,
                subject = subject,
                body = body
            )
            putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        if (emailIntent.resolveActivity(packageManager) == null) {
            showToast(R.string.toast_report_problem_no_email_app)
            return
        }

        try {
            startActivity(
                Intent.createChooser(
                    emailIntent,
                    getString(R.string.report_problem_email_chooser_title)
                )
            )
        } catch (_: ActivityNotFoundException) {
            showToast(R.string.toast_report_problem_no_email_app)
        }
    }

    private fun buildMailtoUri(
        supportEmail: String,
        subject: String,
        body: String
    ): Uri {
        val encodedEmail = Uri.encode(supportEmail, "@.+")
        val encodedSubject = Uri.encode(subject)
        val encodedBody = Uri.encode(body)
        return Uri.parse("mailto:$encodedEmail?subject=$encodedSubject&body=$encodedBody")
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        private const val KEYBOARD_SCROLL_DELAY_MS = 280L
        private const val FOCUSED_FIELD_VISIBLE_MARGIN_DP = 24
    }
}
