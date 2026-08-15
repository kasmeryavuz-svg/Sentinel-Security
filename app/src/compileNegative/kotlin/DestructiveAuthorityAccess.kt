package attack

import com.example.devicemanagement.destructive.Checkpoint17ASimulationSink
import com.example.devicemanagement.destructive.ConsumedDestructiveAuthorizationProof
import com.example.devicemanagement.destructive.CountedAttemptProof
import com.example.devicemanagement.destructive.PreExecutionEvidenceCommitAuthority
import com.example.devicemanagement.destructive.PreExecutionEvidenceCommitProof
import com.example.devicemanagement.destructive.DestructiveArmingAuthority
import com.example.devicemanagement.destructive.DestructiveAttemptAdmissionAuthority
import com.example.devicemanagement.destructive.DestructiveAttemptLease
import com.example.devicemanagement.destructive.DestructiveAuthorizationAuthority
import com.example.devicemanagement.destructive.DestructiveCapability
import com.example.devicemanagement.destructive.DestructiveDenyOnlyCooldown
import com.example.devicemanagement.destructive.DenyOnlyCooldownMarkerStore
import com.example.devicemanagement.destructive.DestructiveFinalExecutionGate
import com.example.devicemanagement.destructive.FinalExecutionPermit
import com.example.devicemanagement.destructive.SimulatedDestructiveExecutor
import com.example.devicemanagement.destructive.DurableDestructivePreExecutionRepository
import com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore
import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
import com.example.devicemanagement.persistence.TrustedRuntimeDenyOnlyCooldownMarkerStore
import com.example.devicemanagement.persistence.SqliteDenyOnlyMarkerStore
import com.example.devicemanagement.persistence.SqliteDestructivePreExecutionStore
import com.example.devicemanagement.persistence.AndroidDestructiveSafetyPersistence
import com.example.devicemanagement.destructive.RuntimeDenyOnlyCooldownStore
import com.example.devicemanagement.destructive.RuntimeDestructivePreExecutionStore
import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability
import com.example.devicemanagement.destructive.DestructiveArtifactIdentity
import com.example.devicemanagement.destructive.DestructiveArtifactIdentityAuthority
import com.example.devicemanagement.destructive.DestructiveHumanApproval
import com.example.devicemanagement.destructive.DestructiveHumanApprovalAuthority
import com.example.devicemanagement.destructive.DestructiveOperatorChallenge
import com.example.devicemanagement.destructive.DestructiveWipeOptionPolicy

class DestructiveAuthorityAccess(
    val arming: DestructiveArmingAuthority,
    val authorization: DestructiveAuthorizationAuthority,
    val admission: DestructiveAttemptAdmissionAuthority,
    val capability: DestructiveCapability,
    val attemptLease: DestructiveAttemptLease,
    val consumedProof: ConsumedDestructiveAuthorizationProof,
    val countedAttemptProof: CountedAttemptProof,
    val preExecutionProof: PreExecutionEvidenceCommitProof,
    val preExecutionAuthority: PreExecutionEvidenceCommitAuthority,
    val permit: FinalExecutionPermit,
    val gate: DestructiveFinalExecutionGate,
    val executor: SimulatedDestructiveExecutor,
    val cooldown: DestructiveDenyOnlyCooldown,
    val store: DenyOnlyCooldownMarkerStore,
    val sink: Checkpoint17ASimulationSink,
    val durableRepository: DurableDestructivePreExecutionRepository,
    val durableStore: DestructivePreExecutionDurableStore,
    val denyOnlyMedium: DenyOnlyMarkerDurableMedium,
    val trustedRuntimeStore: TrustedRuntimeDenyOnlyCooldownMarkerStore,
    val sqliteMarkerStore: SqliteDenyOnlyMarkerStore,
    val sqliteEvidenceStore: SqliteDestructivePreExecutionStore,
    val androidSafetyPersistence: AndroidDestructiveSafetyPersistence,
    val runtimeCooldown: RuntimeDenyOnlyCooldownStore,
    val runtimePreExecution: RuntimeDestructivePreExecutionStore,
    val runtimeDurability: RuntimeDestructiveSafetyDurability,
    val artifactIdentity: DestructiveArtifactIdentity,
    val artifactIdentityAuthority: DestructiveArtifactIdentityAuthority,
    val humanApproval: DestructiveHumanApproval,
    val humanApprovalAuthority: DestructiveHumanApprovalAuthority,
    val operatorChallenge: DestructiveOperatorChallenge,
    val wipeOptionPolicy: DestructiveWipeOptionPolicy,
)
