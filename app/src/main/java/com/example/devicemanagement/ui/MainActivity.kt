package com.example.devicemanagement.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                gravity = Gravity.CENTER
                text = "Device management skeleton\nSensitive actions are disabled"
                textSize = 18f
            },
        )
    }
}
