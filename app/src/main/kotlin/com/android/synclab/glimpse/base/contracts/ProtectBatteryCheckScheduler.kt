package com.android.synclab.glimpse.base.contracts

interface ProtectBatteryCheckScheduler {
    fun scheduleNextCheck(delayMs: Long)

    fun cancelNextCheck()
}
