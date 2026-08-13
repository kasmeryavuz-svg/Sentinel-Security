package com.example.devicemanagement.action

import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.trigger.SensitiveActionCommands
import java.util.Collections

/**
 * Immutable command-to-action registry shared by trigger evaluation and execution.
 *
 * Registries are created only by the explicit controlled and fail-safe factories.
 * There is no runtime registration surface.
 */
internal class SensitiveActionRegistry internal constructor(
    registrations: List<SensitiveActionRegistration>,
) {
    private val registrationsByCommand: Map<String, SensitiveActionRegistration>
    private val actionsByType: Map<DeviceActionType, DeviceAction>

    init {
        require(registrations.isNotEmpty()) { "sensitive action registry must not be empty" }
        require(registrations.none { it.command.isBlank() }) {
            "sensitive action commands must not be blank"
        }

        val duplicateCommands = registrations
            .groupingBy(SensitiveActionRegistration::command)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateCommands.isEmpty()) {
            "duplicate sensitive action commands: ${duplicateCommands.sorted()}"
        }

        val duplicateTypes = registrations
            .groupingBy { it.action.type }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateTypes.isEmpty()) {
            "duplicate sensitive action types: ${duplicateTypes.map { it.name }.sorted()}"
        }

        registrationsByCommand = Collections.unmodifiableMap(
            registrations.associateBy(SensitiveActionRegistration::command),
        )
        actionsByType = Collections.unmodifiableMap(
            registrations.associate { it.action.type to it.action },
        )
    }

    fun actionTypeForCommand(command: String): DeviceActionType? {
        return registrationsByCommand[command]?.action?.type
    }

    fun actionForType(type: DeviceActionType): DeviceAction? = actionsByType[type]

    internal fun registeredCommands(): Set<String> = registrationsByCommand.keys

    internal fun registeredTypes(): Set<DeviceActionType> = actionsByType.keys

    companion object {
        fun controlled(backend: SensitiveActionPolicyBackend): SensitiveActionRegistry {
            return create(
                listOf(
                    SensitiveActionRegistration(
                        command = SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
                        action = ScreenCapturePolicyAction(
                            type = DeviceActionType.DISABLE_SCREEN_CAPTURE,
                            disabled = true,
                            backend = backend,
                        ),
                    ),
                    SensitiveActionRegistration(
                        command = SensitiveActionCommands.ENABLE_SCREEN_CAPTURE,
                        action = ScreenCapturePolicyAction(
                            type = DeviceActionType.ENABLE_SCREEN_CAPTURE,
                            disabled = false,
                            backend = backend,
                        ),
                    ),
                    SensitiveActionRegistration(
                        command = SensitiveActionCommands.DISABLE_CAMERA,
                        action = CameraPolicyAction(
                            type = DeviceActionType.DISABLE_CAMERA,
                            disabled = true,
                            backend = backend,
                        ),
                    ),
                    SensitiveActionRegistration(
                        command = SensitiveActionCommands.ENABLE_CAMERA,
                        action = CameraPolicyAction(
                            type = DeviceActionType.ENABLE_CAMERA,
                            disabled = false,
                            backend = backend,
                        ),
                    ),
                ),
            ).also { registry ->
                check(DeviceActionType.MOCK_WIPE !in registry.registeredTypes()) {
                    "MOCK_WIPE must never be registered in controlled mode"
                }
            }
        }

        fun failSafe(logger: StructuredLogger): SensitiveActionRegistry {
            return create(
                listOf(
                    SensitiveActionRegistration(
                        command = SensitiveActionCommands.MOCK_WIPE_SIMULATION,
                        action = SafeMockWipeAction(logger),
                    ),
                ),
            )
        }

        private fun create(
            registrations: List<SensitiveActionRegistration>,
        ): SensitiveActionRegistry = SensitiveActionRegistry(registrations.toList())
    }
}

internal data class SensitiveActionRegistration(
    val command: String,
    val action: DeviceAction,
)
