# Checkpoint 16: wipe threat model

Checkpoint 16 is **design and threat-model hardening only**.

This document is the security contract a future Checkpoint 17
implementation would have to satisfy before any real wipe code may exist.
It does **not** authorize implementation. Sentinel remains incapable of
performing a real wipe.

Verified repository facts used by this model:

- Base SHA for this checkpoint: `31063998a8adaf14045062e977d6f363fdfd49f1`
- Production DevicePolicyManager mutators remain exactly
  `setScreenCaptureDisabled`, `setCameraDisabled`, and
  `setStatusBarDisabled`
- DeviceAdmin metadata remains exactly `disable-camera`
- The controlled registry remains exactly the six reversible commands
- `MOCK_WIPE` / `SafeMockWipeAction` remain fail-safe simulation only
- Approvals are process-local, identity-bound, and never persisted
- Durable SQLite audit is evidence, not authorization
- Recovery inspection is read-only and has no execution capability
- There is no `BOOT_COMPLETED` path
- The current DPC obtains package name, expected admin
  `ComponentName`, registered Sentinel admin components, Device Owner /
  Profile Owner flags, and active-admin facts. It does **not** obtain
  Android ID, serial, IMEI, or other hardware identifiers

Failure behavior for every threat below defaults to **DENY / NO WIPE**.
Uncertainty, exception, unavailable service, stale state, or failed
check is treated as denial. A trigger must never call a wipe executor.

See `docs/WIPE_DESIGN.md` for the future state machine, target binding,
authorization domain, arming, audit, lifecycle, and Checkpoint 17
entry criteria.

## Protected assets

| Asset | Why it is protected |
| --- | --- |
| Device userdata, adopted storage, and eUICC contents | A successful factory reset is irreversible from the application's point of view |
| Factory-reset protection / owner-binding material | Wiping FRP can enable device takeover after reset |
| Device Owner relationship and expected admin component | Loss or substitution of DO/admin is a management-plane compromise |
| Process-local destructive authorization and arming tokens | These are the only objects that may ever permit a future executor to proceed |
| Authoritative correlation / request identity | Caller-controlled IDs must never become authority |
| Durable audit evidence | Operators need a pre-execution record; the log must not become an authorization or replay source |
| Production signing and build allowlists | A widened DPM/metadata/registry boundary would create wipe capability without a reviewed design |
| Reversible policy executor and current `ApprovalAuthority` | Mixing reversible and destructive authority would let a camera/status-bar path become a wipe path |
| Recovery / lifecycle reconstruction | Restart must reconstruct services from current device state, never resume a wipe |

## Trust boundaries

```text
untrusted UI / input / intent extras
  |  Trigger fields are diagnostic input only
  v
future destructive request acceptor
  |  creates authoritative correlation ID
  |  may append durable REQUESTED evidence
  |  cannot arm, authorize, or execute
  v
destructive assessment
  |  eligibility and target-context snapshot
  |  no authorization, no DPM mutation
  v
destructive arming authority          <-- process-local, short-lived
  |  does not authorize or execute
  v
destructive authorization authority   <-- separate domain from ApprovalAuthority
  |  single-use, target-bound, process-local
  v
destructive final validator           <-- fresh DO / admin / target / freshness
  |  decision-time auth is insufficient
  v
narrow destructive DPM service        <-- isolated from reversible policy services
  |  future Checkpoint 17 only; absent now
  v
DevicePolicyManager destructive API   <-- not allowlisted; not callable now
```

Current production mutation remains on a **different** path:

```text
UI -> SensitiveActionController.submit
   -> durable REQUESTED
   -> TriggerEvaluator / DecisionEngine
   -> process-local ApprovalAuthority
   -> ActionExecutor
   -> typed reversible policy action
   -> VerifiedPolicyMutation
   -> setScreenCaptureDisabled | setCameraDisabled | setStatusBarDisabled
```

That reversible path must never gain a wipe action, wipe DPM method, or
`wipe-data` metadata.

Cross-boundary rules:

- App/UI compile classpath may request and inspect evidence only
- Assessment may read current Device Owner / admin / policy status
- Arming may create and cancel a process-local arming token only
- Authorization may issue and consume a destructive capability only
- Final validation may deny or allow the already-authorized executor
- The executor is the only future component that may call a destructive
  DPM wrapper
- Audit may record evidence and must never approve, arm, or execute
- Recovery may classify interrupted evidence and must never continue a
  wipe

## Attacker capabilities

This model assumes an attacker may have one or more of:

- Control of UI input, including command strings, caller `requestId`,
  and wall-clock expiry fields
- Ability to replay, duplicate, or delay Intents, button events, or
  in-process submit calls
- Ability to crash, force-stop, or kill the process at any state-machine
  boundary
- Ability to reboot the device or interrupt execution after a
  destructive API returns
- Ability to manipulate wall-clock time
- Ability, as same-UID code or a compromised local file, to alter
  app-private SQLite / preferences if such files exist
- Ability to present a stale or forged approval-shaped object
- Ability to change Device Owner / admin / package state between
  decision time and execution time
- Ability to install a non-production or downgraded artifact if signing
  and update policy are weak
- No assumed ability to break Android UID isolation, forge a production
  signing key, or call hidden APIs from this application

`SafeMockWipeAction` is not a destructive capability. It logs
`WIPE WOULD EXECUTE` and returns `ActionResult.Simulated`. Treating it
as a real wipe, or making it reachable from controlled production mode,
is itself a threat.

## Threat table

For every row: **failure behavior is DENY / NO WIPE** unless the row
explicitly describes an evidence-only terminal state after a destructive
call has already been invoked. Checkpoint 16 introduces no such call.

### T1. Malicious or untrusted trigger input

| Field | Content |
| --- | --- |
| Threat | A caller submits `mock_wipe`, an unknown command, a blank command, a caller-chosen `requestId`, or a crafted expiry. The trigger is treated as authority to wipe. |
| Required invariant | Trigger fields are untrusted input. Caller `requestId` is diagnostic only. Command strings never select a destructive executor. Authoritative correlation IDs are created inside the trusted controller. |
| Mitigation | Keep the controlled registry exactly the six reversible commands. Reject unknown commands, including `mock_wipe`, in controlled mode. Future wipe requests, if ever added, must use a dedicated destructive request type that still cannot reach an executor directly. |
| Failure behavior | `REJECTED`. No arming, no authorization, no DPM call. |

### T2. Compromised UI or input path

| Field | Content |
| --- | --- |
| Threat | Compromised dashboard, exported activity, or foreign Intent extras submit or auto-complete a wipe. |
| Required invariant | UI cannot construct decisions, approvals, arming tokens, executors, policy backends, or DPM services. MainActivity must not submit from incoming Intent extras. Provisioning activities remain mutation-free. |
| Mitigation | Preserve Checkpoint 13–15 compile-classpath isolation and exported-component policy. Future wipe UI, if any, may only request. It must not arm, authorize, or execute. No deep link or boot receiver may submit a destructive request. |
| Failure behavior | Request ignored or `REJECTED`. NO WIPE. |

### T3. Replay of a previous request or approval

| Field | Content |
| --- | --- |
| Threat | An attacker resubmits a previous trigger, Intent, correlation ID, or approval-shaped object. |
| Required invariant | Destructive authorization is single-use, identity-bound, and process-local. Persisted audit records are never replayed as authorization. A new process requires a new destructive request. |
| Mitigation | Separate destructive authorization domain. Consume the capability exactly once. Reject foreign, forged, already-consumed, or deserialized approvals. Do not reconstruct authorization from audit history. |
| Failure behavior | `REJECTED` (`approval_not_issued_or_already_consumed` or equivalent). NO WIPE. |

### T4. Forged approvals

| Field | Content |
| --- | --- |
| Threat | An attacker fabricates an `Approval` or future destructive capability, or presents an object issued by a different authority instance. |
| Required invariant | Only the issuing destructive authority can consume its own capabilities. Object identity / capability identity is not caller-supplied. |
| Mitigation | Reuse the spirit of the current `IdentityHashMap<Approval, ApprovalRecord>` pairing, in a **separate** destructive authority. The reversible `ApprovalAuthority` must never accept or issue wipe capabilities. |
| Failure behavior | `REJECTED`. NO WIPE. |

### T5. Stale approvals

| Field | Content |
| --- | --- |
| Threat | A capability issued earlier is presented after its monotonic freshness window, after disarm, or after Device Owner / target context changed. |
| Required invariant | Destructive capabilities have a short monotonic lifetime. Wall-clock timestamps are not used for freshness. Execution-time validation must re-check Device Owner, admin, target binding, arming, and single-use status. |
| Mitigation | Monotonic age cap stricter than or equal to the current 5,000 ms reversible approval window. Final validator treats any age `< 0` or above the cap as stale. Decision-time authorization never substitutes for execution-time validation. |
| Failure behavior | `EXPIRED` or `REJECTED` (`approval_stale`). NO WIPE. |

### T6. Duplicate submissions and button double-taps

| Field | Content |
| --- | --- |
| Threat | Two submits for the same operator action create two live destructive requests, or a second submit races the first into execution. |
| Required invariant | At most one non-terminal destructive request may be live in a process. Duplicate in-flight submits fail closed. |
| Mitigation | Process-local in-flight latch plus destructive rate limiter. A second submit while `REQUESTED`…`FINAL_VALIDATION` is live is `REJECTED`. Caller `requestId` equality is not used as authority to merge or continue. |
| Failure behavior | `REJECTED`. The first request is not automatically executed. NO WIPE for the duplicate. |

### T7. Process death

| Field | Content |
| --- | --- |
| Threat | The process dies after REQUESTED, ASSESSED, ARMED, or AUTHORIZED. Restart reconstructs authorization from memory leftovers, audit rows, or saved instance state and continues the wipe. |
| Required invariant | Destructive authorization and arming cannot survive process death. A new process requires a new destructive request. Persisted records are evidence only. |
| Mitigation | Keep arming and authorization strictly in memory, created by trusted composition. `createControlledController` already constructs a fresh `ApprovalAuthority`; a future destructive authority must do the same and must not be persisted. Recovery inspection classifies unmatched REQUESTED rows and has no execution capability. |
| Failure behavior | Interrupted evidence only (`OUTCOME_UNKNOWN` classification). NO automatic wipe. |

### T8. Crash during the pipeline

| Field | Content |
| --- | --- |
| Threat | A crash after pre-execution audit, or during final validation, is later treated as permission to finish the wipe. |
| Required invariant | No implicit transition. No transition reconstructed from audit history. Crash is not authorization. |
| Mitigation | Same as T7. If pre-execution audit was written and the process died before the DPM call, the durable record is interrupted evidence, not `EXECUTION_INITIATED`. |
| Failure behavior | `FAILED_PRE_EXECUTION` or interrupted `OUTCOME_UNKNOWN`. NO WIPE. |

### T9. Reboot

| Field | Content |
| --- | --- |
| Threat | A reboot receiver, locked-boot receiver, or startup reconstruction resumes a wipe. |
| Required invariant | No `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, or `QUICKBOOT_POWERON` wipe path. Startup reconstructs read-only status and a fresh controller only. |
| Mitigation | Preserve Checkpoint 14/15 manifest and bytecode guards. Do not add a boot receiver for wipe. Monotonic clocks reset across reboot; any future persisted cooldown must fail closed rather than authorize. |
| Failure behavior | Services reconstructed. Previous request dead. NO WIPE. |

### T10. Interrupted execution after a future destructive call

| Field | Content |
| --- | --- |
| Threat | A future `wipeDevice` / `wipeData` call returns, throws, or never returns because the process is killed by reset. Local code then claims success, or recovery retries the call. |
| Required invariant | Local code cannot prove a factory reset completed. The only honest post-call state is `EXECUTION_INITIATED` / `OUTCOME_UNKNOWN`. Recovery must not retry. |
| Mitigation | Design the executor so the DPM call is the last step after durable pre-execution evidence. Do not map a returned call to `APPLIED`. Do not implement post-wipe local success audit as a correctness requirement. See audit semantics in `docs/WIPE_DESIGN.md`. |
| Failure behavior | Evidence-only `EXECUTION_INITIATED` / `OUTCOME_UNKNOWN`. No retry. |

### T11. Wrong-device or wrong-target execution

| Field | Content |
| --- | --- |
| Threat | An authorization issued for one package, admin component, Device Owner context, or wipe scope is consumed against a different target. |
| Required invariant | Destructive authorization is bound to the exact intended target context available safely to this DPC. Caller-controlled IDs never become the target identity. |
| Mitigation | Bind action type, authoritative correlation ID, package name, expected admin component, expected Device Owner status, requested wipe scope, monotonic issuance, and a one-time capability identity. Re-validate the same fields immediately before any future DPM call. |
| Failure behavior | `REJECTED` on any mismatch. NO WIPE. |

### T12. Stale Device Owner or admin state

| Field | Content |
| --- | --- |
| Threat | Sentinel was Device Owner at decision time and is not at execution time, or the expected admin is inactive, unregistered, duplicated, or replaced. |
| Required invariant | Execution requires a fresh `VERIFIED_DEVICE_OWNER` result, expected admin registered and active, and component-set consistency. Cached decision-time validation is insufficient. |
| Mitigation | Reuse the current `DeviceOwnerMutationGuard` / `DeviceOwnerValidationProvider` pattern: obtain validation again immediately before the destructive call. Any `UNAVAILABLE`, `CONFIGURATION_ERROR`, Profile Owner, or inconsistency is denial. |
| Failure behavior | `REJECTED` / `FAILED_PRE_EXECUTION`. NO WIPE. |

### T13. Race between decision-time and execution-time authorization

| Field | Content |
| --- | --- |
| Threat | TOCTOU: state changes after assessment/authorization and before the destructive call. |
| Required invariant | Decision-time authorization never substitutes for execution-time validation. The executor must re-check Device Owner, admin, target binding, approval freshness, single-use status, arming freshness, and rate-limit state. |
| Mitigation | Explicit `FINAL_VALIDATION` state. Any mismatch, exception, or unavailable service aborts before the DPM wrapper is invoked. |
| Failure behavior | `FAILED_PRE_EXECUTION`. NO WIPE. |

### T14. Audit failure before execution

| Field | Content |
| --- | --- |
| Threat | The pipeline proceeds to wipe after a failed pre-execution audit append, leaving no durable evidence. |
| Required invariant | Durable pre-execution evidence must be written before any future destructive call. If that append fails, execution fails closed. |
| Mitigation | Same fail-closed rule as current `DefaultSensitiveActionController`: a failed REQUESTED (and, for wipe, failed pre-execution) append returns rejection and does not mutate. |
| Failure behavior | `REJECTED` (`audit_persistence_unavailable`) or `FAILED_PRE_EXECUTION`. NO WIPE. |

### T15. Audit failure or impossibility after a destructive call

| Field | Content |
| --- | --- |
| Threat | Operators treat a missing terminal audit row as “wipe did not happen,” or recovery fabricates `APPLIED`. Conversely, code claims local proof that factory reset completed. |
| Required invariant | A successful destructive call may prevent any further local audit because the process and database are destroyed. Local audit cannot prove a factory reset completed. Missing terminal rows are not authorization to retry. |
| Mitigation | Require pre-execution evidence. Represent the post-call world as `EXECUTION_INITIATED` / `OUTCOME_UNKNOWN`. Recovery must not rewrite rows or retry. |
| Failure behavior | Evidence only. No retry. No false `APPLIED`. |

### T16. Audit used as authorization or replay source

| Field | Content |
| --- | --- |
| Threat | An unmatched REQUESTED row, a future `EXECUTION_INITIATED` row, or a corrupted phase value is decoded as permission to wipe. |
| Required invariant | Audit is evidence, not authorization, and not cryptographically tamper-proof. Unknown phases are storage corruption. Recovery has no execution capability. |
| Mitigation | Preserve Checkpoint 13/14 audit and recovery guards. Future destructive phases must be added as evidence-only values. No audit writer, store, or inspector may call DPM or submit actions. |
| Failure behavior | Classification only. NO WIPE. |

### T17. Clock manipulation

| Field | Content |
| --- | --- |
| Threat | An attacker sets wall-clock backward to revive an expired trigger, or forward to skip a cooldown that was stored as wall-clock. |
| Required invariant | Wall-clock is presentation metadata only. Authorization freshness, arming freshness, and destructive cooldowns must use the existing monotonic clock (`SystemClock.elapsedRealtime()` in production). Negative monotonic deltas fail closed. |
| Mitigation | Do not use `presentationWallClockMillis` or trigger `expiresAtEpochMillis` as the sole destructive freshness control. Trigger wall-clock expiry may remain a first-pass rejection for untrusted input, but execution freshness is monotonic. Persisted cooldown, if any, must not become authorization when the clock is untrusted. |
| Failure behavior | `EXPIRED` / `REJECTED`. NO WIPE. |

### T18. Accidental operator activation

| Field | Content |
| --- | --- |
| Threat | A single tap, focus event, or mis-labeled control starts a factory reset. |
| Required invariant | A trigger never directly calls a wipe executor. Arming is a separate phase and does not authorize or execute. Accidental double activation is rejected. |
| Mitigation | Explicit arming with a short monotonic window, cancellation, and optional operator challenge. Rate limit and in-flight latch. No wipe control in the current UI. Checkpoint 16 adds none. |
| Failure behavior | Remain `IDLE` or `CANCELLED` / `REJECTED`. NO WIPE. |

### T19. Repeated wipe attempts

| Field | Content |
| --- | --- |
| Threat | Rapid repeated triggers, failed authorization loops, or scripted submits try to brute-force a race or exhaust operator attention. |
| Required invariant | Fail-closed rate limiting and cooldown. Rate-limit state is never authorization. |
| Mitigation | Process-local attempt counters and monotonic cooldown. Optional persisted cooldown may only deny, never allow. Uncertainty in limiter state denies. |
| Failure behavior | `REJECTED`. NO WIPE. |

### T20. Downgrade / rollback of a future wipe-capable artifact

| Field | Content |
| --- | --- |
| Threat | After a future Checkpoint 17 artifact exists, an older or modified APK is installed to skip arming, validation, or signing checks—or a newer artifact silently widens allowlists. |
| Required invariant | Production distribution requires the Checkpoint 15 signing boundary. Build-time DPM, metadata, and registry allowlists remain exact and reviewed. Checkpoint 16 artifacts must remain wipe-incapable even if later code is rolled back to them. |
| Mitigation | Keep this checkpoint free of destructive APIs, `wipe-data` metadata, and registry entries. Future allowlist changes require explicit human review (Checkpoint 17 entry criteria). Do not treat local unsigned release artifacts as production. |
| Failure behavior | Install / build rejected, or runtime DENY / NO WIPE. |

### T21. Malicious persisted state

| Field | Content |
| --- | --- |
| Threat | Same-UID or backup-restore attacker inserts a fake REQUESTED/AUTHORIZED row, a fake cooldown, or a serialized approval and expects execution. |
| Required invariant | No persisted reusable approval. Backup and device-to-device transfer remain disabled / excluded. Persisted rows never authorize. Corrupt or unknown persisted state fails closed. |
| Mitigation | Preserve `allowBackup=false` and extraction-exclusion rules. Do not persist arming or destructive capabilities. Treat unknown audit phases as corruption. If a future cooldown file is added, corruption denies wipe rather than skipping the limiter. |
| Failure behavior | `REJECTED` or interrupted evidence only. NO WIPE. |

### T22. Recovery-path abuse

| Field | Content |
| --- | --- |
| Threat | Recovery inspection, startup, or an unmatched audit row is used to submit, approve, or execute a wipe. |
| Required invariant | Recovery is read-only evidence classification. It cannot call `submit`, `ApprovalAuthority`, `ActionExecutor`, DPM mutators, or audit store mutation APIs. |
| Mitigation | Preserve Checkpoint 14 recovery bytecode and source guards. Do not add wipe symbols to the recovery package. Do not add a recovery “resume wipe” API. |
| Failure behavior | `UNAVAILABLE` inspection or evidence-only result. NO WIPE. |

### T23. Production signing / build-boundary abuse

| Field | Content |
| --- | --- |
| Threat | A debug, unsigned, or R8-transformed artifact exposes `wipeData` / `wipeDevice`, keeps fail-safe `MOCK_WIPE` on a production path, or widens the DPM allowlist. |
| Required invariant | Production bytecode allowlist stays exactly the three reversible mutators. Release DEX gates continue to forbid `wipeData` and `wipeDevice` tokens. Controlled production composition never calls `createFailSafeController`. |
| Mitigation | Existing `ProductionBytecodePolicyVerifier` and `ReleaseArtifactSecurityVerifier` gates, plus Checkpoint 16 freeze tests. R8 must not turn simulation into a real wipe; fail-safe types remain unkept. |
| Failure behavior | Build / release gate fails. Installed production app still has NO WIPE capability at this checkpoint. |

### T24. MOCK_WIPE promoted to production or real execution

| Field | Content |
| --- | --- |
| Threat | `SafeMockWipeAction` is registered in the controlled registry, wired by production composition, or replaced with a DPM call while keeping the MOCK_WIPE name. |
| Required invariant | `MOCK_WIPE` remains outside controlled production mode. Simulation must not call DPM. A future real wipe, if ever added, is a new dedicated action type and executor, not a promotion of MOCK_WIPE. |
| Mitigation | Runtime check in `SensitiveActionRegistry.controlled`. Source and composition guards. Freeze tests in this checkpoint. |
| Failure behavior | Registry construction fails or submit is `REJECTED`. NO WIPE. |

### T25. Widened DeviceAdmin metadata or DPM allowlist

| Field | Content |
| --- | --- |
| Threat | `wipe-data` is added to DeviceAdmin metadata, or `wipeData` / `wipeDevice` is added to the production mutator allowlist, “just to prepare” for Checkpoint 17. |
| Required invariant | Until Checkpoint 17 entry criteria are all satisfied and explicitly approved, metadata remains exactly `disable-camera` and the DPM mutator allowlist remains exactly the three reversible setters. |
| Mitigation | DeviceAdmin metadata tests, per-variant effective metadata gates, bytecode allowlist, and Checkpoint 16 freeze tests. |
| Failure behavior | Tests / assemble fail. NO WIPE. |

### T26. Direct executor call that skips the pipeline

| Field | Content |
| --- | --- |
| Threat | Future code lets assessment, UI, recovery, or audit invoke the destructive DPM service directly. |
| Required invariant | Trigger → assessment → arming → authorization → final validation → executor. No component holds more authority than required. The executor accepts only a just-consumed destructive capability plus a fresh validator result. |
| Mitigation | Isolate the future destructive DPM service from the reversible `VerifiedPolicyMutationExecutor`. Do not place the executor on the app compile classpath. Bytecode-bind the future setter the same way reversible setters are bound today. |
| Failure behavior | Build reject or runtime DENY. NO WIPE. |

### T27. Profile Owner or non-Device-Owner wipe

| Field | Content |
| --- | --- |
| Threat | A Profile Owner, ordinary app, or inconsistent management mode reaches a device-wide wipe API. Official `wipeData` semantics can wipe a user/profile rather than the device; `wipeDevice` is the device-wide API on API 34+. |
| Required invariant | Future device-wide wipe, if ever implemented, requires freshly verified Device Owner, not Profile Owner. Uncertainty is denial. |
| Mitigation | Final validator reuses current DO-only mutation guard. Do not implement profile-relinquish wipe. Unresolved API-level targeting rules are listed in `docs/WIPE_DESIGN.md` and default to NO WIPE. |
| Failure behavior | `REJECTED`. NO WIPE. |

### T28. Scope / flag confusion

| Field | Content |
| --- | --- |
| Threat | A request for “user wipe” is executed as device factory reset, or flags such as `WIPE_RESET_PROTECTION_DATA` / `WIPE_EUICC` / `WIPE_SILENTLY` are applied without being bound into the authorization. |
| Required invariant | Requested wipe scope and flags are part of the target binding. Unbound or default-zero flags are not implicit full-device wipe authority. |
| Mitigation | Explicit scope enum in the future authorization record. Final validator compares requested scope to the executor arguments. Unknown or unsupported scope fails closed. |
| Failure behavior | `REJECTED`. NO WIPE. |

## Current (Checkpoint 16) residual risk

These risks exist today and remain acceptable only because **no real wipe
exists**:

- Local SQLite audit is not cryptographically tamper-proof
- Same-UID attackers can alter app-private files
- Wall-clock trigger expiry is attacker-influenced for reversible
  actions; reversible actions still use monotonic approval age and
  execution-time Device Owner revalidation
- GrapheneOS-specific destructive API behavior is not proven in this
  repository

Those residuals become blocking for Checkpoint 17 where marked in
`docs/WIPE_DESIGN.md`. They do not justify adding wipe capability now.
