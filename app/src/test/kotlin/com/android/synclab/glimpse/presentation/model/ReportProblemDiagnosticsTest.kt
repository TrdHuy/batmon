package com.android.synclab.glimpse.presentation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReportProblemDiagnosticsTest {
    @Test
    fun toEmailSection_redactsMissingBlankAndShortDescriptors() {
        val emailSection = diagnostics(
            controllers = listOf(
                controller(name = "Null descriptor", descriptor = null),
                controller(name = "Blank descriptor", descriptor = "   "),
                controller(name = "Short descriptor", descriptor = "ABC123456789")
            )
        ).toEmailSection()

        assertEquals(2, Regex("descriptor=n/a").findAll(emailSection).count())
        assertEquals(1, Regex("descriptor=\\*\\*\\*").findAll(emailSection).count())
        assertFalse(emailSection.contains("ABC123456789"))
    }

    @Test
    fun toEmailSection_redactsLongDescriptorButKeepsUsefulEdges() {
        val descriptor = "abcdef1234567890uvwxyz"
        val emailSection = diagnostics(
            controllers = listOf(
                controller(descriptor = descriptor)
            )
        ).toEmailSection()

        assertTrue(emailSection.contains("descriptor=abcdef...uvwxyz"))
        assertFalse(emailSection.contains(descriptor))
    }

    @Test
    fun toEmailSection_formatsVendorProductHexWithStableLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))

            val emailSection = diagnostics(
                controllers = listOf(
                    controller(vendorId = 0x054C, productId = 0x09CC)
                )
            ).toEmailSection()

            assertTrue(emailSection.contains("vendor/product=0x054C/0x09CC"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    private fun diagnostics(
        controllers: List<ReportProblemDiagnostics.ControllerDiagnostics>
    ): ReportProblemDiagnostics {
        return ReportProblemDiagnostics(
            appVersion = "1.0",
            appVersionCode = 1,
            packageName = "com.android.synclab.glimpse",
            androidRelease = "15",
            sdkInt = 35,
            manufacturer = "Samsung",
            model = "SM-S948U",
            timestamp = "2026-05-13T00:00:00Z",
            serviceRunning = false,
            monitoringEnabled = false,
            overlayVisible = false,
            controllers = controllers
        )
    }

    private fun controller(
        name: String = "Wireless Controller",
        vendorId: Int = 0x054C,
        productId: Int = 0x09CC,
        descriptor: String? = "abcdef1234567890uvwxyz"
    ): ReportProblemDiagnostics.ControllerDiagnostics {
        return ReportProblemDiagnostics.ControllerDiagnostics(
            name = name,
            deviceId = 12,
            vendorId = vendorId,
            productId = productId,
            descriptor = descriptor,
            batteryPercent = 80,
            batteryStatus = "Charging"
        )
    }
}
