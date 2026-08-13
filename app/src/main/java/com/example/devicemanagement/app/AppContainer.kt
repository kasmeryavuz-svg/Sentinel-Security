package com.example.devicemanagement.app

import com.example.devicemanagement.action.ActionExecutor
import com.example.devicemanagement.action.SafeMockWipeAction
import com.example.devicemanagement.decision.DecisionEngine
import com.example.devicemanagement.decision.FailSafeDecisionEngine
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.InMemoryStateRepository
import com.example.devicemanagement.persistence.ManagementState
import com.example.devicemanagement.persistence.StateRepository
import com.example.devicemanagement.trigger.DefaultTriggerEvaluator

class AppContainer(
    logger: StructuredLogger,
) {
    val stateRepository: StateRepository = InMemoryStateRepository(
        ManagementState(
            serviceAvailable = false,
            sensitiveActionsEnabled = false,
        ),
    )

    val decisionEngine: DecisionEngine = FailSafeDecisionEngine(
        triggerEvaluator = DefaultTriggerEvaluator(),
        stateRepository = stateRepository,
        logger = logger,
    )

    val actionExecutor = ActionExecutor(
        actions = setOf(SafeMockWipeAction(logger)),
        logger = logger,
    )
}
