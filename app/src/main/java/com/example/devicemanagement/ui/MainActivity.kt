package com.example.devicemanagement.ui

import android.app.Activity
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.devicemanagement.R
import com.example.devicemanagement.app.DeviceManagementApp
import com.example.devicemanagement.audit.AuditActionNames
import com.example.devicemanagement.audit.AuditStorageHealth
import java.util.Date

class MainActivity : Activity() {
    private lateinit var presenter: DashboardPresenter
    private lateinit var headerSubtitle: TextView
    private lateinit var headerVerification: TextView
    private lateinit var managementMode: TextView
    private lateinit var managementAdmin: TextView
    private lateinit var managementValidation: TextView
    private lateinit var managementDeviceOwnerProvisioning: TextView
    private lateinit var managementProfileOwnerProvisioning: TextView
    private lateinit var managementDetailsToggle: Button
    private lateinit var managementDetails: View
    private lateinit var managementDetailsText: TextView
    private lateinit var screenCaptureCard: PolicyCardViews
    private lateinit var cameraCard: PolicyCardViews
    private lateinit var statusBarCard: PolicyCardViews
    private lateinit var auditStatus: TextView
    private lateinit var auditEmpty: TextView
    private lateinit var auditList: LinearLayout
    private var detailsVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val container = (application as DeviceManagementApp).container
        presenter = DashboardPresenter(
            readSnapshot = {
                DashboardSnapshot(
                    validation = container.deviceOwnerValidation.currentValidation(),
                    managementStatus = container.deviceManagementStatus.currentStatus(),
                    provisioningReadiness = container.provisioningReadiness.currentReadiness(),
                    screenCapture = container.screenCapturePolicyStatus.currentStatus(),
                    camera = container.cameraPolicyStatus.currentStatus(),
                    statusBar = container.statusBarPolicyStatus.currentStatus(),
                )
            },
            sensitiveActions = container.sensitiveActions,
            auditHistory = container.auditHistory,
            auditStorageStatus = container.auditStorageStatus,
        )
        bindViews()
        bindState(presenter.currentState())
    }

    private fun bindViews() {
        headerSubtitle = findViewById(R.id.header_subtitle)
        headerVerification = findViewById(R.id.header_verification)
        managementMode = findViewById(R.id.management_mode)
        managementAdmin = findViewById(R.id.management_admin)
        managementValidation = findViewById(R.id.management_validation)
        managementDeviceOwnerProvisioning =
            findViewById(R.id.management_device_owner_provisioning)
        managementProfileOwnerProvisioning =
            findViewById(R.id.management_profile_owner_provisioning)
        managementDetailsToggle = findViewById(R.id.management_details_toggle)
        managementDetails = findViewById(R.id.management_details)
        managementDetailsText = findViewById(R.id.management_details_text)
        screenCaptureCard = PolicyCardViews(findViewById(R.id.card_screen_capture))
        cameraCard = PolicyCardViews(findViewById(R.id.card_camera))
        statusBarCard = PolicyCardViews(findViewById(R.id.card_status_bar))
        auditStatus = findViewById(R.id.audit_status)
        auditEmpty = findViewById(R.id.audit_empty)
        auditList = findViewById(R.id.audit_list)

        managementDetailsToggle.setOnClickListener {
            detailsVisible = !detailsVisible
            renderDetailsVisibility()
        }
        bindCardActions(screenCaptureCard, PolicyCapability.SCREEN_CAPTURE)
        bindCardActions(cameraCard, PolicyCapability.CAMERA)
        bindCardActions(statusBarCard, PolicyCapability.STATUS_BAR)
    }

    private fun bindCardActions(card: PolicyCardViews, capability: PolicyCapability) {
        card.disable.setOnClickListener {
            submit(capability, disable = true)
        }
        card.enable.setOnClickListener {
            submit(capability, disable = false)
        }
    }

    private fun submit(capability: PolicyCapability, disable: Boolean) {
        val completed = presenter.submitAction(capability, disable) { pending ->
            bindState(pending)
        }
        bindState(completed)
    }

    private fun bindState(state: DashboardViewState) {
        bindHeader(state.header)
        bindManagement(state.management)
        bindPolicyCard(
            views = screenCaptureCard,
            card = state.screenCapture,
            title = getString(R.string.policy_screen_capture_title),
            explanation = getString(R.string.policy_screen_capture_explanation),
            disableDescription = getString(R.string.action_disable_screen_capture),
            enableDescription = getString(R.string.action_enable_screen_capture),
        )
        bindPolicyCard(
            views = cameraCard,
            card = state.camera,
            title = getString(R.string.policy_camera_title),
            explanation = getString(R.string.policy_camera_explanation),
            disableDescription = getString(R.string.action_disable_camera),
            enableDescription = getString(R.string.action_enable_camera),
        )
        bindPolicyCard(
            views = statusBarCard,
            card = state.statusBar,
            title = getString(R.string.policy_status_bar_title),
            explanation = getString(R.string.policy_status_bar_explanation),
            disableDescription = getString(R.string.action_disable_status_bar),
            enableDescription = getString(R.string.action_enable_status_bar),
        )
        bindAuditLog(state)
    }

    private fun bindHeader(header: HeaderViewState) {
        val bannerText = verificationBanner(header.verification)
        headerSubtitle.text = when (header.verification) {
            VerificationPresentation.VERIFIED_DEVICE_OWNER ->
                getString(R.string.dashboard_subtitle_verified)
            VerificationPresentation.NOT_DEVICE_OWNER ->
                getString(R.string.dashboard_subtitle_not_owner)
            VerificationPresentation.CONFIGURATION_ERROR ->
                getString(R.string.dashboard_subtitle_configuration)
            VerificationPresentation.UNAVAILABLE ->
                getString(R.string.dashboard_subtitle_unavailable)
        }
        headerVerification.text = bannerText
        headerVerification.setTextColor(verificationColor(header.verification))
        headerVerification.setBackgroundResource(verificationBackground(header.verification))
        headerVerification.contentDescription =
            getString(R.string.banner_content_description, bannerText)
    }

    private fun bindManagement(management: ManagementStatusViewState) {
        val validationLabel = verificationLabel(management.verification)
        managementMode.text = getString(
            R.string.label_management_mode,
            managementModeLabel(management.mode),
        )
        managementAdmin.text = getString(
            R.string.label_expected_admin,
            management.expectedAdminReceiver.ifBlank { getString(R.string.value_none) },
        )
        managementValidation.text = getString(
            R.string.label_owner_validation,
            validationLabel,
        )
        managementDeviceOwnerProvisioning.text = getString(
            R.string.label_device_owner_provisioning,
            provisioningLabel(management.deviceOwnerProvisioning),
        )
        managementProfileOwnerProvisioning.text = getString(
            R.string.label_profile_owner_provisioning,
            provisioningLabel(management.profileOwnerProvisioning),
        )
        managementDetailsText.text = technicalDetails(management)
        renderDetailsVisibility()
    }

    private fun bindPolicyCard(
        views: PolicyCardViews,
        card: PolicyCardViewState,
        title: String,
        explanation: String,
        disableDescription: String,
        enableDescription: String,
    ) {
        val stateLabel = policyStateLabel(card.state)
        views.title.text = title
        views.state.text = stateLabel
        views.explanation.text = explanation
        views.root.contentDescription =
            getString(R.string.card_content_description, title, stateLabel)
        if (card.requiresApi34Notice) {
            views.unavailable.visibility = View.VISIBLE
            views.unavailable.text = getString(R.string.policy_status_bar_api_notice)
        } else if (card.state == PolicyPresentationState.UNAVAILABLE && card.reasons.isNotEmpty()) {
            views.unavailable.visibility = View.VISIBLE
            views.unavailable.text = card.reasons.joinToString(separator = "\n")
        } else {
            views.unavailable.visibility = View.GONE
        }
        views.disable.isEnabled = card.actionsEnabled
        views.enable.isEnabled = card.actionsEnabled
        views.disable.contentDescription = disableDescription
        views.enable.contentDescription = enableDescription
        views.outcome.text = outcomeText(card)
        views.outcome.setTextColor(outcomeColor(card.latestOutcome))
    }

    private fun bindAuditLog(state: DashboardViewState) {
        auditStatus.text = auditStatusText(state.auditStorageHealth)
        auditStatus.setTextColor(auditStatusColor(state.auditStorageHealth))
        auditStatus.contentDescription = auditStatus.text
        auditList.removeAllViews()
        if (state.auditLog.isEmpty()) {
            auditEmpty.visibility = View.VISIBLE
            auditList.visibility = View.GONE
            return
        }
        auditEmpty.visibility = View.GONE
        auditList.visibility = View.VISIBLE
        val timeFormat = DateFormat.getTimeFormat(this)
        state.auditLog.forEach { entry ->
            val row = TextView(this).apply {
                text = auditEntryText(entry, timeFormat.format(Date(entry.timestampMillis)))
                setTextColor(resources.getColor(R.color.dashboard_text_primary, theme))
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.text_secondary),
                )
                setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.spacing_m))
                setTextIsSelectable(true)
                contentDescription = text
            }
            auditList.addView(row)
        }
    }

    private fun auditEntryText(entry: AuditLogRow, time: String): String {
        val action = auditActionLabel(entry.actionName)
        val status = auditStatusLabel(entry.status)
        val reason = friendlyReason(entry.reasonCode)
        return if (reason == null) {
            getString(
                R.string.audit_entry,
                action,
                status,
                time,
                entry.correlationId,
            )
        } else {
            getString(
                R.string.audit_entry_with_reason,
                action,
                status,
                time,
                reason,
                entry.correlationId,
            )
        }
    }

    private fun auditStatusText(health: AuditStorageHealth): String {
        return when (health) {
            AuditStorageHealth.HEALTHY ->
                getString(R.string.audit_notice)
            AuditStorageHealth.DEGRADED ->
                getString(R.string.audit_degraded)
            AuditStorageHealth.UNAVAILABLE ->
                getString(R.string.audit_unavailable)
        }
    }

    private fun auditStatusColor(health: AuditStorageHealth): Int {
        val color = when (health) {
            AuditStorageHealth.HEALTHY ->
                R.color.dashboard_text_secondary
            AuditStorageHealth.DEGRADED ->
                R.color.dashboard_warning_text
            AuditStorageHealth.UNAVAILABLE ->
                R.color.dashboard_danger_text
        }
        return resources.getColor(color, theme)
    }

    private fun outcomeText(card: PolicyCardViewState): String {
        val label = outcomeLabel(card.latestOutcome)
        val detail = friendlyReason(card.latestOutcomeDetail)
        val withReason = if (detail == null) {
            label
        } else {
            getString(R.string.outcome_with_reason, label, detail)
        }
        val correlationId = card.latestCorrelationId
        return if (correlationId.isNullOrBlank()) {
            withReason
        } else {
            getString(R.string.outcome_with_correlation, withReason, correlationId)
        }
    }

    private fun technicalDetails(management: ManagementStatusViewState): String {
        val registered = management.registeredAdminComponents
            .ifEmpty { listOf(getString(R.string.value_none)) }
            .joinToString()
        return buildString {
            appendLine(
                getString(
                    R.string.label_package,
                    management.packageName.ifBlank { getString(R.string.value_none) },
                ),
            )
            appendLine(getString(R.string.label_registered_admins, registered))
            appendLine(
                getString(R.string.label_policy_service, yesNo(management.isPolicyServiceAvailable)),
            )
            appendLine(
                getString(
                    R.string.label_admin_registered,
                    yesNo(management.isExpectedAdminReceiverRegistered),
                ),
            )
            appendLine(getString(R.string.label_admin_active, yesNo(management.isAdminActive)))
            appendLine(getString(R.string.label_is_device_owner, yesNo(management.isDeviceOwner)))
            appendLine(getString(R.string.label_is_profile_owner, yesNo(management.isProfileOwner)))
            appendLine(
                getString(R.string.label_diagnostics, bulletList(management.diagnostics)),
            )
            appendLine(
                getString(R.string.label_validation_reasons, bulletList(management.validationReasons)),
            )
            append(
                getString(
                    R.string.label_device_owner_provisioning_reasons,
                    bulletList(management.deviceOwnerProvisioningReasons),
                ),
            )
            appendLine()
            append(
                getString(
                    R.string.label_profile_owner_provisioning_reasons,
                    bulletList(management.profileOwnerProvisioningReasons),
                ),
            )
        }
    }

    private fun renderDetailsVisibility() {
        managementDetails.visibility = if (detailsVisible) View.VISIBLE else View.GONE
        managementDetailsToggle.text = if (detailsVisible) {
            getString(R.string.hide_technical_details)
        } else {
            getString(R.string.show_technical_details)
        }
    }

    private fun verificationBanner(verification: VerificationPresentation): String {
        return when (verification) {
            VerificationPresentation.VERIFIED_DEVICE_OWNER ->
                getString(R.string.banner_verified)
            VerificationPresentation.NOT_DEVICE_OWNER ->
                getString(R.string.banner_not_owner)
            VerificationPresentation.CONFIGURATION_ERROR ->
                getString(R.string.banner_configuration)
            VerificationPresentation.UNAVAILABLE ->
                getString(R.string.banner_unavailable)
        }
    }

    private fun verificationLabel(verification: VerificationPresentation): String {
        return when (verification) {
            VerificationPresentation.VERIFIED_DEVICE_OWNER ->
                getString(R.string.validation_verified)
            VerificationPresentation.NOT_DEVICE_OWNER ->
                getString(R.string.validation_not_owner)
            VerificationPresentation.CONFIGURATION_ERROR ->
                getString(R.string.validation_configuration)
            VerificationPresentation.UNAVAILABLE ->
                getString(R.string.validation_unavailable)
        }
    }

    private fun verificationColor(verification: VerificationPresentation): Int {
        val color = when (verification) {
            VerificationPresentation.VERIFIED_DEVICE_OWNER -> R.color.dashboard_verified_text
            VerificationPresentation.NOT_DEVICE_OWNER,
            VerificationPresentation.CONFIGURATION_ERROR,
            -> R.color.dashboard_warning_text
            VerificationPresentation.UNAVAILABLE -> R.color.dashboard_danger_text
        }
        return resources.getColor(color, theme)
    }

    private fun verificationBackground(verification: VerificationPresentation): Int {
        return when (verification) {
            VerificationPresentation.VERIFIED_DEVICE_OWNER -> R.drawable.banner_verified
            VerificationPresentation.NOT_DEVICE_OWNER,
            VerificationPresentation.CONFIGURATION_ERROR,
            -> R.drawable.banner_warning
            VerificationPresentation.UNAVAILABLE -> R.drawable.banner_danger
        }
    }

    private fun managementModeLabel(mode: ManagementModePresentation): String {
        return when (mode) {
            ManagementModePresentation.DEVICE_OWNER -> getString(R.string.mode_device_owner)
            ManagementModePresentation.PROFILE_OWNER -> getString(R.string.mode_profile_owner)
            ManagementModePresentation.ORDINARY_APP -> getString(R.string.mode_ordinary_app)
            ManagementModePresentation.UNAVAILABLE -> getString(R.string.mode_unavailable)
        }
    }

    private fun provisioningLabel(presentation: ProvisioningPresentation): String {
        return when (presentation) {
            ProvisioningPresentation.ALLOWED -> getString(R.string.provisioning_allowed)
            ProvisioningPresentation.NOT_ALLOWED -> getString(R.string.provisioning_not_allowed)
            ProvisioningPresentation.UNAVAILABLE -> getString(R.string.provisioning_unavailable)
        }
    }

    private fun policyStateLabel(state: PolicyPresentationState): String {
        return when (state) {
            PolicyPresentationState.DISABLED -> getString(R.string.policy_state_disabled)
            PolicyPresentationState.ENABLED -> getString(R.string.policy_state_enabled)
            PolicyPresentationState.UNAVAILABLE -> getString(R.string.policy_state_unavailable)
        }
    }

    private fun outcomeLabel(outcome: OperationOutcomePresentation): String {
        return when (outcome) {
            OperationOutcomePresentation.NONE -> getString(R.string.outcome_none)
            OperationOutcomePresentation.PENDING -> getString(R.string.outcome_pending)
            OperationOutcomePresentation.APPLIED -> getString(R.string.outcome_applied)
            OperationOutcomePresentation.DENIED -> getString(R.string.outcome_denied)
            OperationOutcomePresentation.FAILED -> getString(R.string.outcome_failed)
            OperationOutcomePresentation.SIMULATED -> getString(R.string.outcome_simulated)
            OperationOutcomePresentation.INTERRUPTED -> getString(R.string.outcome_interrupted)
        }
    }

    private fun outcomeColor(outcome: OperationOutcomePresentation): Int {
        val color = when (outcome) {
            OperationOutcomePresentation.APPLIED -> R.color.dashboard_verified_text
            OperationOutcomePresentation.DENIED,
            OperationOutcomePresentation.FAILED,
            OperationOutcomePresentation.INTERRUPTED,
            -> R.color.dashboard_danger_text
            OperationOutcomePresentation.SIMULATED -> R.color.dashboard_warning_text
            OperationOutcomePresentation.PENDING,
            OperationOutcomePresentation.NONE,
            -> R.color.dashboard_text_secondary
        }
        return resources.getColor(color, theme)
    }

    private fun auditActionLabel(actionName: String): String {
        return when (actionName) {
            AuditActionNames.DISABLE_SCREEN_CAPTURE ->
                getString(R.string.action_disable_screen_capture)
            AuditActionNames.ENABLE_SCREEN_CAPTURE ->
                getString(R.string.action_enable_screen_capture)
            AuditActionNames.DISABLE_CAMERA ->
                getString(R.string.action_disable_camera)
            AuditActionNames.ENABLE_CAMERA ->
                getString(R.string.action_enable_camera)
            AuditActionNames.DISABLE_STATUS_BAR ->
                getString(R.string.action_disable_status_bar)
            AuditActionNames.ENABLE_STATUS_BAR ->
                getString(R.string.action_enable_status_bar)
            else -> actionName
        }
    }

    private fun auditStatusLabel(status: AuditLogStatus): String {
        return when (status) {
            AuditLogStatus.APPLIED -> getString(R.string.outcome_applied)
            AuditLogStatus.REJECTED -> getString(R.string.outcome_denied)
            AuditLogStatus.FAILED -> getString(R.string.outcome_failed)
            AuditLogStatus.SIMULATED -> getString(R.string.outcome_simulated)
            AuditLogStatus.INTERRUPTED -> getString(R.string.outcome_interrupted)
        }
    }

    private fun friendlyReason(reason: String?): String? {
        return when (reason) {
            null, "" -> null
            "post_write_read_back_mismatch",
            "POST_WRITE_READ_BACK_MISMATCH",
            -> getString(R.string.reason_post_write_mismatch)
            "AUDIT_PERSISTENCE_UNAVAILABLE" -> getString(R.string.reason_audit_unavailable)
            else -> reason
        }
    }

    private fun yesNo(value: Boolean): String {
        return if (value) getString(R.string.value_yes) else getString(R.string.value_no)
    }

    private fun bulletList(values: List<String>): String {
        val items = values.ifEmpty { listOf(getString(R.string.value_none)) }
        return items.joinToString(separator = "\n") { "• $it" }
    }

    private class PolicyCardViews(val root: View) {
        val title: TextView = root.findViewById(R.id.policy_title)
        val state: TextView = root.findViewById(R.id.policy_state)
        val explanation: TextView = root.findViewById(R.id.policy_explanation)
        val unavailable: TextView = root.findViewById(R.id.policy_unavailable)
        val disable: Button = root.findViewById(R.id.policy_disable)
        val enable: Button = root.findViewById(R.id.policy_enable)
        val outcome: TextView = root.findViewById(R.id.policy_outcome)
    }
}
