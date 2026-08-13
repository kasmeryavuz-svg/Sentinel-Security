package com.example.devicemanagement.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.devicemanagement.action.SensitiveActionResult
import com.example.devicemanagement.app.DeviceManagementApp
import com.example.devicemanagement.trigger.Trigger
import java.util.UUID

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resultView = TextView(this).apply {
            gravity = Gravity.CENTER
            text = "No simulation request submitted."
            textSize = 16f
        }
        val submitButton = Button(this).apply {
            text = "Submit simulated wipe request"
            setOnClickListener {
                val correlationId = UUID.randomUUID().toString()
                val result = (application as DeviceManagementApp)
                    .container
                    .sensitiveActions
                    .submit(
                        Trigger(
                            command = "mock_wipe",
                            requestId = correlationId,
                            expiresAtEpochMillis = System.currentTimeMillis() + REQUEST_TTL_MILLIS,
                        ),
                    )
                resultView.text = result.toDisplayText()
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
                addView(
                    TextView(context).apply {
                        gravity = Gravity.CENTER
                        text = "Sensitive Action Simulation\nNo device data will be changed."
                        textSize = 20f
                    },
                )
                addView(submitButton)
                addView(resultView)
            },
        )
    }

    private fun SensitiveActionResult.toDisplayText(): String {
        return when (this) {
            is SensitiveActionResult.Approved ->
                "APPROVED — simulation only\n$message\nCorrelation ID: $correlationId"
            is SensitiveActionResult.Denied ->
                "DENIED\n$reason\nCorrelation ID: $correlationId"
        }
    }

    private companion object {
        const val REQUEST_TTL_MILLIS = 60_000L
    }
}
