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
| Deny-only destructive cooldown marker | Process death or reboot must not reset repeated-attempt protection |
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
destructive executor                  <-- one synchronous trusted chain
  |  1 consume capability
  |  2 durable pre-execution audit append (fail closed)
  |  3 AFTER append: live FINAL_VALIDATION
  |  4 immediately invoke the narrow wrapper
  |  no Boolean allow, no UI/async/queue gap between 3 and 4
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
- Final live validation occurs only **after** the pre-execution audit
  append, inside the destructive executor’s synchronous trusted chain.
  It is not a reusable allow/deny result. An optional
  `FinalExecutionPermit` may exist only after that live check
- The executor is the only future component that may call a destructive
  DPM wrapper, and only immediately after live validation in the same
  stack
- Audit may record evidence and must never approve, arm, execute, or
  skip live validation
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
- Ability to present malformed, corrupt, or backup-restored
  app-private files (SQLite / preferences) as input to Sentinel
- Ability to present a stale or forged approval-shaped object
- Ability to change Device Owner / admin / package state between
  decision time and execution time
- Ability to install a non-production or downgraded artifact if signing
  and update policy are weak
- No assumed ability to break Android UID isolation, forge a production
  signing key, or call hidden APIs from this application

**Same-UID arbitrary code execution is application compromise.** An
attacker who can run their own code as Sentinel’s UID, or freely
rewrite Sentinel’s private files from that UID, is outside what a
purely local in-app authorization pipeline can securely contain. This
model still requires fail-closed handling of malformed, corrupt, or
restored persisted bytes. It does **not** claim ordinary app-private
persistence survives that compromised-UID attacker.

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
| Required invariant | Destructive authorization and arming cannot survive process death. A new process requires a new destructive request. Persisted records are evidence only. Process death must not shorten or clear the destructive cooldown. |
| Mitigation | Keep arming, authorization, and any `FinalExecutionPermit` strictly in memory. Persist only a deny-only cooldown-required marker (see T19). Recovery inspection classifies unmatched REQUESTED rows and has no execution capability. |
| Failure behavior | Interrupted evidence only (`OUTCOME_UNKNOWN` classification). Cooldown remains in force. NO automatic wipe. |

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
| Mitigation | Preserve Checkpoint 14/15 manifest and bytecode guards. Do not add a boot receiver for wipe. Monotonic clocks reset across reboot. The mandatory deny-only cooldown marker survives reboot and starts a fresh full monotonic cooldown; it never authorizes. |
| Failure behavior | Services reconstructed. Previous request dead. Cooldown remains in force. NO WIPE. |

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
| Mitigation | Reuse the current `DeviceOwnerMutationGuard` / `DeviceOwnerValidationProvider` pattern: obtain validation again **after** the pre-execution audit append and immediately before the destructive call. Any `UNAVAILABLE`, `CONFIGURATION_ERROR`, Profile Owner, or inconsistency is denial. |
| Failure behavior | `REJECTED` / `FAILED_PRE_EXECUTION`. NO WIPE. |

### T13. Race between decision-time and execution-time authorization

| Field | Content |
| --- | --- |
| Threat | TOCTOU: state changes after assessment/authorization and before the destructive call. A Boolean “allowed” result, cached validator output, UI callback, async task, queue, or persisted permit is reused or delayed. Separately, a durable pre-execution SQLite append after an earlier validation can take time, during which Device Owner, admin, target, arming, or circuit-breaker state can change before the DPM call. |
| Required invariant | Decision-time authorization never substitutes for execution-time validation. A successful pre-execution audit append never substitutes for live revalidation. Order is consume capability → write pre-execution evidence (fail closed) → **then** final live revalidation → immediately invoke. There is no reusable Boolean allow result, no cached or persisted final-validation result, and no UI, async, callback, queue, or persistence boundary between live validation and the destructive API invocation. If an internal permit is used, it is issued only after the append and the live check: an opaque process-local `FinalExecutionPermit`, target/scope/correlation-bound, extremely short monotonic lifetime, consumable exactly once only by the paired destructive executor, and not serializable. |
| Mitigation | Keep audit commitment, `FINAL_VALIDATION`, and the DPM wrapper call inside the destructive executor’s single trusted method, in that order. Do not validate-then-append-then-invoke. Do not return “allowed” to UI. Any mismatch, expiry, state change, exception, or unavailable service is NO WIPE. |
| Failure behavior | `FAILED_PRE_EXECUTION`. NO WIPE. |

### T14. Audit failure before execution

| Field | Content |
| --- | --- |
| Threat | The pipeline proceeds to wipe after a failed pre-execution audit append, leaving no durable evidence. |
| Required invariant | Durable pre-execution evidence must be written before live final validation and before any future destructive call. If that append fails, execution fails closed. A successful append is evidence only and is not authorization to skip live validation. |
| Mitigation | Same fail-closed rule as current `DefaultSensitiveActionController` for the append itself. After a successful wipe pre-execution append, the same stack still performs live revalidation and only then may invoke. |
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
| Mitigation | Do not use `presentationWallClockMillis` or trigger `expiresAtEpochMillis` as the sole destructive freshness control. Trigger wall-clock expiry may remain a first-pass rejection for untrusted input, but execution freshness is monotonic. The mandatory persisted cooldown marker must not encode attacker-controlled wall-clock remaining time as authorization. After process start, a present marker starts a fresh full monotonic cooldown. |
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
| Threat | Rapid repeated triggers, failed authorization loops, or scripted submits try to brute-force a race or exhaust operator attention. The attacker also crashes, force-stops, or reboots the app to reset process-local counters and immediately retry. |
| Required invariant | Fail-closed rate limiting and a **mandatory** cross-process / reboot deny-only circuit breaker against this untrusted-trigger attacker. Process death or reboot must never shorten or clear a well-formed destructive cooldown. Rate-limit and cooldown state is never authorization, arming, resume, or an executor permit. Ordinary app-private persistence is not claimed to survive arbitrary same-UID code execution (see T21). |
| Mitigation | Keep an in-process latch and monotonic counter for double-taps. Persist only a cooldown-required marker with no approvals, capabilities, nonces, executor permits, or resume flags. After every process start or reboot, a present well-formed marker starts a **fresh full** monotonic cooldown. Do not rely on attacker-controlled wall clock. Malformed, corrupt, or restored cooldown bytes fail closed. Checkpoint 16 does not implement the limiter. |
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
| Threat | Backup-restore or malformed local files insert a fake REQUESTED/AUTHORIZED row, a fake cooldown, or a serialized approval and expect execution. Separately, arbitrary code running as Sentinel’s UID can delete or replace the cooldown marker. |
| Required invariant | No persisted reusable approval, capability, nonce, or `FinalExecutionPermit`. Backup and device-to-device transfer remain disabled / excluded. Persisted rows never authorize. Malformed, corrupt, or restored persisted state fails closed. Arbitrary same-UID code execution is application compromise and is outside what a purely local in-app pipeline can securely contain; do not claim the marker solves that. |
| Mitigation | Preserve `allowBackup=false` and extraction-exclusion rules. Do not persist arming or destructive capabilities. Treat unknown audit phases as corruption. A corrupt or unreadable cooldown marker denies wipe. A well-formed marker only denies. Absence of a marker after restart is not authorization; it also cannot be proven to be “deleted by an attacker” without independent trusted memory, which this design does not invent. |
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
| Required invariant | Trigger → assessment → arming → authorization → executor-owned pre-execution audit → executor-owned live final validation → immediate DPM wrapper call. No component holds more authority than required. The executor does not accept a Boolean allow flag or a delayed validator result from UI, a queue, or persistence. |
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
- Ordinary app-private files are not anti-rollback. The deny-only
  cooldown marker is for untrusted-trigger crash/reboot bypass and
  fail-closed corrupt/restored bytes. It does not contain arbitrary
  same-UID code execution (application compromise)
- Wall-clock trigger expiry is attacker-influenced for reversible
  actions; reversible actions still use monotonic approval age and
  execution-time Device Owner revalidation
- GrapheneOS-specific destructive API behavior is not proven in this
  repository

Those residuals become blocking for Checkpoint 17 where marked in
`docs/WIPE_DESIGN.md`. They do not justify adding wipe capability now.
