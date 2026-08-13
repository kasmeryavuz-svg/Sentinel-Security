package com.example.devicemanagement.ui

import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.app.AppContainer
import com.example.devicemanagement.trigger.Trigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class UiArchitectureBoundaryTest {
    @Test
    fun `UI and app container expose only the trigger-based sensitive action controller`() {
        val controllerMethods = SensitiveActionController::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
        val appSurfaceTypes = buildList {
            AppContainer::class.java.declaredMethods.forEach { method ->
                add(method.returnType.name)
                addAll(method.parameterTypes.map { it.name })
            }
            MainActivity::class.java.declaredFields.forEach { field ->
                add(field.type.name)
            }
            MainActivity::class.java.declaredMethods.forEach { method ->
                add(method.returnType.name)
                addAll(method.parameterTypes.map { it.name })
            }
        }

        assertTrue(
            controllerMethods.any {
                it.name == "submit" &&
                    it.parameterTypes.contentEquals(arrayOf(Trigger::class.java))
            },
        )
        assertFalse(
            controllerMethods.any { method ->
                method.parameterTypes.any {
                    it.name.contains("ActionDecision") ||
                        it.name.contains("ActionExecutor") ||
                        it.name.contains("DeviceAction")
                }
            },
        )
        assertFalse(
            appSurfaceTypes.any {
                it.contains("ActionDecision") ||
                    it.contains("ActionExecutor") ||
                    it.contains("DeviceAction")
            },
        )
    }
}
