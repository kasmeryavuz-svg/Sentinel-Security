package com.example.devicemanagement.action

import com.example.devicemanagement.logging.StructuredLogger

class SafeMockWipeAction(
    private val logger: StructuredLogger,
) : DeviceAction {
    override val type: DeviceActionType = DeviceActionType.MOCK_WIPE

    override fun execute(request: ActionRequest): ActionResult {
        logger.info(
            event = WIPE_LOG_MESSAGE,
            fields = mapOf(
                "action" to type.name,
                "mode" to "simulation_only",
                "request_id" to request.requestId,
            ),
        )
        return ActionResult.Simulated(WIPE_LOG_MESSAGE)
    }

    companion object {
        const val WIPE_LOG_MESSAGE = "WIPE WOULD EXECUTE"
    }
}
