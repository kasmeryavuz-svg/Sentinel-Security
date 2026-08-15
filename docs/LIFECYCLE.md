# Process lifecycle and recovery

Checkpoint 14 hardens Sentinel against process death, crash, force-stop, and
reboot. After any of those events, Sentinel must never automatically retry,
resume, replay, or execute a previously requested sensitive action.

## Process death semantics

Approvals are process-local, identity-bound, and single-use. They live only in
an in-memory `ApprovalAuthority` created by trusted composition
(`createControlledController`). They are never written to disk, SharedPreferences,
or SQLite.

When the process dies, every outstanding approval dies with it. The next
`DeviceManagement.create` / `createControlledController` call constructs a
brand-new authority. A stale pre-restart approval object cannot be consumed by
the new executor.

Durable audit is the only security-relevant state that survives. A `REQUESTED`
event that never received a terminal sibling remains in the log as evidence.

## Interrupted audit records

Presentation-time classification is sufficient. Recovery does not rewrite or
delete an unmatched `REQUESTED` record, and it does not append a completion
marker that would imply the original action finished.

A correlation ID is **interrupted** when durable history still contains
`REQUESTED` and does not contain `APPLIED`, `REJECTED`, `FAILED`, or
`SIMULATED` for that ID. The dashboard already shows that as Interrupted /
Outcome unknown. `RecoveryInspectionProvider.inspect()` exposes the same
classification as interrupted correlation IDs, count, and inspection health.

Interrupted records are evidence only. They are never converted into an
`ActionRequest`, approval, trigger, policy mutation, retry, replay, or
continuation.

## No automatic replay

Recovery code has no execution capability. It can read `AuditHistoryProvider`
and classify events. It cannot call:

- `SensitiveActionController.submit`
- `ApprovalAuthority`
- `ActionExecutor`
- DevicePolicyManager mutators
- audit store insert/delete APIs

Build-time bytecode and source guards reject those calls from the recovery
package. App/UI compile classpaths receive only the read-only
`RecoveryInspectionProvider` contract.

## No persisted approvals

Authorization is re-read from `SensitiveActionPolicyBackend` through
`PolicyBackendStateRepository` on every decision. Device Owner / admin /
backend state is not cached in persistent storage. If ownership or admin
state changes between submissions, the next submit sees the new state and
fails closed when it is no longer authorized.

A new submission after restart works only through the normal trusted
controller path: a fresh user/trigger, a fresh correlation ID, a fresh
decision, and a fresh approval.

## Reboot behavior

There is no `BOOT_COMPLETED` (or locked-boot) receiver. Sentinel does not
execute policy at boot. `DeviceManagementApp` lazily reconstructs
`AppContainer` from current device state via `DeviceManagement.create`.
`SentinelDeviceAdminReceiver.onProfileProvisioningComplete` remains log-only.

If a future read-only boot receiver were ever required, it would have to be
non-exported where possible, perform no policy mutation, issue no approval,
and submit no trigger. That receiver is not present now.

## Recovery is evidence and state reconstruction only

Startup reconstructs:

- read-only Device Owner / management / policy status providers from the
  current Android device state
- the submit-only `SensitiveActionController` with a fresh
  `ApprovalAuthority`
- durable audit history and storage-health providers
- read-only `RecoveryInspectionProvider`

Inspection failure is fail-safe: `inspect()` returns `UNAVAILABLE` and empty
interrupted sets. It does not execute anything and does not fabricate
terminal audit events.

Audit remains evidence, never authorization. See `docs/AUDIT.md`.

## Checkpoint 17A destructive simulation

Arming tokens, destructive capabilities, attempt/admission leases,
consumed-authorization proofs, and `FinalExecutionPermit` objects are
process-local and die with the process. Reconstruction cannot resume an
armed or authorized simulation request. The only persisted
destructive-adjacent state is a deny-only cooldown marker, which can
never authorize, arm, resume, execute, or become a lease. 17A tests
those persistence semantics with a test-only reconstruction adapter.
Checkpoint 17B adds the trusted runtime deny-only adapter and a
separate durable pre-execution evidence path. Persisted data can only
deny or provide evidence. Process death and reboot still destroy every
positive authority. Crash after a durable pre-execution row and before
invocation is evidence / outcome-unknown only; it never automatically
replays or invokes. There is still no `BOOT_COMPLETED` path and no
recovery execution. Runtime-durable capabilities are a separate type
from simulation/test stores and are not wired into production
composition. See `docs/WIPE_17A_PREFLIGHT.md` and
`docs/WIPE_17B_ENTRY_REVIEW.md`.
