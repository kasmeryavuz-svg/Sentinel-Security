package com.example.devicemanagement.provisioning

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.example.devicemanagement.R
import com.example.devicemanagement.app.DeviceManagementApp
import com.example.devicemanagement.management.DeviceOwnerValidationResult

/**
 * Android 12+ ADMIN_POLICY_COMPLIANCE handler.
 *
 * Verifies the current Sentinel Device Owner relationship through the existing
 * read-only validation API. It does not mutate policy and does not claim
 * success unless Device Owner verification succeeds.
 */
class AdminPolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provisioning_compliance)
        val title = findViewById<TextView>(R.id.compliance_title)
        val status = findViewById<TextView>(R.id.compliance_status)
        val detail = findViewById<TextView>(R.id.compliance_detail)
        title.setText(R.string.compliance_title)

        if (intent?.action != FullyManagedProvisioningContract.ACTION_ADMIN_POLICY_COMPLIANCE) {
            status.setText(R.string.compliance_failure)
            detail.setText(R.string.compliance_unavailable_detail)
            Log.w(TAG, "provisioning_compliance_failed result=unexpected_action")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val decision = evaluateCurrentValidation()
        when (decision) {
            is ProvisioningComplianceDecision.Succeed -> {
                status.setText(R.string.compliance_success)
                detail.setText(R.string.compliance_success_detail)
                Log.i(TAG, "provisioning_compliance result=VERIFIED_DEVICE_OWNER")
                setResult(RESULT_OK)
            }
            is ProvisioningComplianceDecision.FailClosed -> {
                status.setText(R.string.compliance_failure)
                detail.text = failureDetail(decision.result)
                Log.w(TAG, "provisioning_compliance_failed result=${decision.result.name}")
                setResult(RESULT_CANCELED)
            }
        }
        finish()
    }

    private fun evaluateCurrentValidation(): ProvisioningComplianceDecision {
        return try {
            val application = application
            if (application !is DeviceManagementApp) {
                return ProvisioningComplianceDecision.FailClosed(
                    DeviceOwnerValidationResult.UNAVAILABLE,
                )
            }
            val validation = application.container.deviceOwnerValidation.currentValidation()
            ProvisioningComplianceEvaluator.evaluate(validation.result)
        } catch (_: Throwable) {
            ProvisioningComplianceDecision.FailClosed(DeviceOwnerValidationResult.UNAVAILABLE)
        }
    }

    private fun failureDetail(result: DeviceOwnerValidationResult): String {
        return when (result) {
            DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER ->
                getString(R.string.compliance_success_detail)
            DeviceOwnerValidationResult.NOT_DEVICE_OWNER ->
                getString(R.string.compliance_not_owner_detail)
            DeviceOwnerValidationResult.CONFIGURATION_ERROR ->
                getString(R.string.compliance_configuration_detail)
            DeviceOwnerValidationResult.UNAVAILABLE ->
                getString(R.string.compliance_unavailable_detail)
        }
    }

    private companion object {
        const val TAG = "SentinelProvisioning"
    }
}
