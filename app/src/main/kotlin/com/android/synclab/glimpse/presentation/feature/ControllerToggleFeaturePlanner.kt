package com.android.synclab.glimpse.presentation.feature

abstract class ControllerToggleFeaturePlanner<State, Decision> {
    abstract fun planUserToggle(
        enabled: Boolean,
        state: State,
        reason: String
    ): Decision

    abstract fun planProfilePreference(
        enabled: Boolean,
        state: State,
        reason: String
    ): Decision

    protected fun String?.normalizedIdentifier(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
