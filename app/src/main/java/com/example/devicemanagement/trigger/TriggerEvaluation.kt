package com.example.devicemanagement.trigger

import com.example.devicemanagement.action.ActionRequest

sealed interface TriggerEvaluation {
    data class Valid(val request: ActionRequest) : TriggerEvaluation

    data class Invalid(
        val reason: String,
        val detail: String? = null,
    ) : TriggerEvaluation
}
