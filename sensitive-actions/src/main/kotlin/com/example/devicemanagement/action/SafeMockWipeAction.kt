package com.example.devicemanagement.action

import com.example.devicemanagement.logging.StructuredLogger

internal class SafeMockWipeAction(
    private val logger: StructuredLogger,
) : DeviceAction {
    override val type: DeviceActionType = DeviceActionType.MOCK_WIPE

    override fun execute(request: ActionRequest): ActionResult {
        logger.info(
            event = WIPE_LOG_MESSAGE,
            fields = mapOf(
                "action" to type.name,
                "mode" to "simulation_only",
                "correlation_id" to request.correlationId,
                "caller_request_id" to request.callerRequestId,
            ),
        )
        return ActionResult.Simulated(WIPE_LOG_MESSAGE, request.correlationId)
    }

    companion object {
        const val WIPE_LOG_MESSAGE = "WIPE WOULD EXECUTE"
    }
}
