package attack

import com.example.devicemanagement.destructive.Checkpoint17ASimulationSink
import com.example.devicemanagement.destructive.DestructiveArmingAuthority
import com.example.devicemanagement.destructive.DestructiveAuthorizationAuthority
import com.example.devicemanagement.destructive.DestructiveCapability
import com.example.devicemanagement.destructive.DestructiveDenyOnlyCooldown
import com.example.devicemanagement.destructive.DenyOnlyCooldownMarkerStore
import com.example.devicemanagement.destructive.FinalExecutionPermit
import com.example.devicemanagement.destructive.SimulatedDestructiveExecutor

class DestructiveAuthorityAccess(
    val arming: DestructiveArmingAuthority,
    val authorization: DestructiveAuthorizationAuthority,
    val capability: DestructiveCapability,
    val permit: FinalExecutionPermit,
    val executor: SimulatedDestructiveExecutor,
    val cooldown: DestructiveDenyOnlyCooldown,
    val store: DenyOnlyCooldownMarkerStore,
    val sink: Checkpoint17ASimulationSink,
)
