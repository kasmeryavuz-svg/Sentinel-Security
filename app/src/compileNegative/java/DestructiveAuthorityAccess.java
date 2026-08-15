package attack;

import com.example.devicemanagement.destructive.Checkpoint17ASimulationSink;
import com.example.devicemanagement.destructive.ConsumedDestructiveAuthorizationProof;
import com.example.devicemanagement.destructive.DestructiveArmingAuthority;
import com.example.devicemanagement.destructive.DestructiveAttemptAdmissionAuthority;
import com.example.devicemanagement.destructive.DestructiveAttemptLease;
import com.example.devicemanagement.destructive.DestructiveAuthorizationAuthority;
import com.example.devicemanagement.destructive.DestructiveCapability;
import com.example.devicemanagement.destructive.DestructiveDenyOnlyCooldown;
import com.example.devicemanagement.destructive.DenyOnlyCooldownMarkerStore;
import com.example.devicemanagement.destructive.FinalExecutionPermit;
import com.example.devicemanagement.destructive.SimulatedDestructiveExecutor;

final class DestructiveAuthorityAccess {
    DestructiveArmingAuthority arming;
    DestructiveAuthorizationAuthority authorization;
    DestructiveAttemptAdmissionAuthority admission;
    DestructiveCapability capability;
    DestructiveAttemptLease attemptLease;
    ConsumedDestructiveAuthorizationProof consumedProof;
    FinalExecutionPermit permit;
    SimulatedDestructiveExecutor executor;
    DestructiveDenyOnlyCooldown cooldown;
    DenyOnlyCooldownMarkerStore store;
    Checkpoint17ASimulationSink sink;
}
