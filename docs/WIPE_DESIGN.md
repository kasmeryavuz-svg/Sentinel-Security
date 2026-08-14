# Checkpoint 16: wipe design

Checkpoint 16 is **design only**. This document is implementation-ready
so Checkpoint 17 can be judged against an explicit contract. It does
**not** add a wipe.

**NO REAL WIPE IS IMPLEMENTED.**

Do not call `DevicePolicyManager.wipeData`, `wipeDevice`, or any other
destructive API. Do not add `wipe-data` to DeviceAdmin metadata. Do not
widen the production DPM mutator allowlist. Do not add a real wipe
action to the controlled registry. Do not make `MOCK_WIPE` reachable
from controlled production mode. Do not add boot or recovery execution.
Do not weaken Checkpoint 13–15 guards.

Companion document: `docs/WIPE_THREAT_MODEL.md`.

## 1. Design posture

The future conceptual pipeline is:

```text
Trigger
  -> assessment
  -> arming / safety checks
  -> authorization
  -> final Device Owner + target revalidation
  -> destructive executor
```

A trigger must **never** directly call a wipe executor.

Current production mutation remains exactly:

| Boundary | Frozen value |
| --- | --- |
| DPM mutators | `setScreenCaptureDisabled`, `setCameraDisabled`, `setStatusBarDisabled` |
| DeviceAdmin metadata | `disable-camera` |
| Controlled registry | six reversible enable/disable commands |
| Fail-safe simulation | `MOCK_WIPE` / `SafeMockWipeAction` only, not production-wired |

`SafeMockWipeAction` is not a prototype executor. It is a simulation
that logs `WIPE WOULD EXECUTE` and returns `ActionResult.Simulated`.
A future real wipe, if ever added, is a new dedicated type and path,
not a promotion of `MOCK_WIPE`.

## 2. Future destructive state machine

The current reversible machine is implicit in
`DefaultSensitiveActionController`:

`submit` → durable `REQUESTED` → decide → (optional) execute → one
terminal audit phase (`APPLIED` / `REJECTED` / `FAILED` / `SIMULATED`).

That machine is **not** sufficient for destruction. Wipe needs an
explicit, process-local state machine. Names below fit this repository:
they extend the existing `REQUESTED` / fail-closed vocabulary without
pretending a wipe can be `APPLIED` the way a camera policy can.

### 2.1 Live states

These states exist only in process memory. They are never reconstructed
from audit history.

| State | Meaning | Authority held |
| --- | --- | --- |
| `IDLE` | No live destructive request | None |
| `REQUESTED` | Authoritative correlation ID created; durable REQUESTED evidence written if possible | Request identity only |
| `ASSESSED` | Eligibility and target snapshot recorded in memory | Assessment only |
| `ARMED` | Process-local arming token active | Arming only; not authorization |
| `AUTHORIZED` | Process-local destructive capability issued | Single-use capability; not yet executable |
| `FINAL_VALIDATION` | Execution-time revalidation in progress | Capability consumed or reserved; DPM not yet called |
| `EXECUTION_COMMITTED` | Pre-execution audit written; about to invoke the destructive wrapper | Last pre-call state |

### 2.2 Terminal outcomes

| Outcome | Meaning |
| --- | --- |
| `REJECTED` | Failed closed before a destructive call. Safe. |
| `EXPIRED` | Arming, capability, or monotonic window lapsed. Safe. |
| `CANCELLED` | Explicit disarm / operator cancel. Safe. |
| `FAILED_PRE_EXECUTION` | Final validation, audit precondition, rate limit, or service check failed. Safe. |
| `EXECUTION_INITIATED` | A future destructive API was invoked. Local code must not claim factory-reset success. |
| `OUTCOME_UNKNOWN` | Interrupted or post-call local evidence is incomplete. Evidence only. |

`APPLIED` is a reversible-policy terminal. It must **not** be used to
mean “wipe succeeded.” `SIMULATED` remains reserved for fail-safe
`MOCK_WIPE` and must not become a bridge to a real executor.

### 2.3 Allowed transitions

No implicit transition. Every arrow is an explicit function of a named
component. Failed checks take the denial arrow; they never skip ahead.

```text
IDLE
  -- accept_request --> REQUESTED
  -- invalid_trigger / audit_unavailable --> REJECTED

REQUESTED
  -- assess_ok --> ASSESSED
  -- assess_fail / rate_limited --> REJECTED
  -- process_death --> OUTCOME_UNKNOWN (evidence only; new process is IDLE)

ASSESSED
  -- arm_ok --> ARMED
  -- arm_fail / cancel --> REJECTED or CANCELLED
  -- process_death --> OUTCOME_UNKNOWN

ARMED
  -- authorize_ok --> AUTHORIZED
  -- disarm / expire / cancel --> EXPIRED or CANCELLED
  -- process_death --> OUTCOME_UNKNOWN

AUTHORIZED
  -- begin_final_validation --> FINAL_VALIDATION
  -- expire / cancel --> EXPIRED or CANCELLED
  -- process_death --> OUTCOME_UNKNOWN

FINAL_VALIDATION
  -- all_checks_ok_and_pre_exec_audit_ok --> EXECUTION_COMMITTED
  -- any_mismatch_or_uncertainty --> FAILED_PRE_EXECUTION
  -- process_death --> OUTCOME_UNKNOWN

EXECUTION_COMMITTED
  -- destructive_api_invoked --> EXECUTION_INITIATED
  -- invoke_throws_before_call_or_aborted --> FAILED_PRE_EXECUTION
  -- process_death_before_invoke --> OUTCOME_UNKNOWN
```

Rules:

- No transition is inferred from SQLite rows, UI state, or Intent extras
- No automatic continuation after process death, crash, force-stop, or
  reboot
- Persisted records are evidence, never authorization
- No recovery replay
- Destructive authorization cannot survive process death unless a later
  design **proves** a stronger secure mechanism. This design requires
  process-local authorization. A new process requires a new destructive
  request
- `EXECUTION_INITIATED` is not success. It is “the call was made; local
  outcome cannot be proven”

### 2.4 Interruption at each boundary

| Died / rebooted in | After restart |
| --- | --- |
| `IDLE` | Still `IDLE`. Nothing to recover. |
| `REQUESTED` | Durable REQUESTED may exist. Recovery classifies interrupted evidence. New process is `IDLE`. No assess/arm/authorize. |
| `ASSESSED` | In-memory snapshot gone. Same as interrupted REQUESTED evidence. New request required. |
| `ARMED` | Arming token gone. Disarmed by death. New request required. |
| `AUTHORIZED` | Destructive capability gone. Cannot be consumed. New request required. |
| `FINAL_VALIDATION` | No DPM call. `FAILED_PRE_EXECUTION` or interrupted evidence. No retry. |
| `EXECUTION_COMMITTED` before invoke | Pre-execution row may exist. Treat as `OUTCOME_UNKNOWN`. **Do not** invoke on restart. |
| After invoke | Device may be resetting. If the app still runs, state is `EXECUTION_INITIATED` / `OUTCOME_UNKNOWN`. Never retry. |

## 3. Explicit target binding

A future wipe authorization must be bound to the exact intended target
context. Caller-controlled IDs are never authority.

### 3.1 Fields that must be bound

| Binding field | Source of authority | Notes |
| --- | --- | --- |
| Action type | Trusted pipeline, not the raw command string alone | Dedicated future destructive type. Not `MOCK_WIPE`. Not a reversible `DeviceActionType`. |
| Authoritative correlation / request identity | Created inside the trusted controller (`UUID` today) | `Trigger.requestId` remains diagnostic input only. |
| Package name | `context.packageName` already obtained by `AndroidDevicePolicyPlatform` | Must equal the running DPC package. |
| Expected admin component | `ComponentName(context, SentinelDeviceAdminReceiver).flattenToString()` | Must match the single registered Sentinel admin. |
| Expected Device Owner status | `DevicePolicyManager.isDeviceOwnerApp(packageName)` plus current validation | Must be `VERIFIED_DEVICE_OWNER` at issue and at execution. |
| Active-admin / registration facts | `isAdminActive`, `activeAdmins`, receiver scan | Bound so a substituted receiver cannot inherit the capability. |
| Requested wipe scope / type | Explicit future enum chosen during assessment | See §10. Unspecified scope is denial. |
| Issuance time / monotonic freshness | `MonotonicTimeSource` / `SystemClock.elapsedRealtime()` | Wall-clock is not a binding input. |
| One-time nonce / capability identity | Opaque object issued by the destructive authority | Same spirit as current `Approval`; not a caller string. |

### 3.2 Identifiers that are safe and actually available today

The current DPC already obtains, and therefore may bind:

- Application package name
- Expected admin component flatten-string
- Registered Sentinel admin component set
- Device Owner boolean
- Profile Owner boolean (must be false)
- Expected admin active boolean
- Active admin component set
- Management mode / validation result
  (`VERIFIED_DEVICE_OWNER`, `NOT_DEVICE_OWNER`,
  `CONFIGURATION_ERROR`, `UNAVAILABLE`)

These are the only device-identity inputs this design treats as
available.

### 3.3 Identifiers this design does **not** invent

The repository does not read Android ID, serial number, IMEI,
advertising ID, or hardware attestation IDs. This design does not
assume those APIs, permissions, or values exist for Sentinel.

Optional future read-only bindings that would require **new** proven
code, and are therefore **unresolved** until Checkpoint 17 researches
them:

- Signing-certificate digest via `PackageManager` signing info
- A platform device-unique ID, if one is lawfully available to a Device
  Owner on GrapheneOS without adding new dangerous permissions

Until those are proven, target binding uses the §3.2 set plus
process-local capability identity.

### 3.4 Binding comparison

At `FINAL_VALIDATION`, every bound field is compared to a **fresh**
read of the same facts. Any mismatch, blank package, blank component,
duplicate admin receiver, Profile Owner, or `UNAVAILABLE` validation
is NO WIPE.

## 4. Anti-replay and single-use authorization

### 4.1 Assessment of the current `ApprovalAuthority`

Current reversible approvals are:

- Issued into an in-memory `IdentityHashMap<Approval, ApprovalRecord>`
- Single-use (`remove` on consume)
- Rejected if missing, already consumed, wall-clock expired, or
  monotonically stale (`MAX_APPROVAL_AGE_MILLIS = 5_000`)
- Process-local; a new `ApprovalAuthority` after restart cannot consume
  a pre-restart `Approval`
- Paired: the same instance is given to `FailSafeDecisionEngine` and
  `ActionExecutor`
- Free of an explicit nonce field; object identity is the capability
- Shared across camera, screen-capture, and status-bar actions

That design is appropriate for **reversible** policy. It is **not**
automatically sufficient for destruction.

Gaps versus wipe:

- No target / package / admin / Device Owner binding inside the
  capability
- No wipe-scope binding
- No separate arming prerequisite
- Same domain as reversible actions, so a registry mistake could in
  principle reuse the same issuer/consumer for a destructive type
- Wall-clock trigger expiry is still consulted at consume time
- No rate-limit or in-flight latch
- No distinct audit phase for “about to do something irreversible”

### 4.2 Required destructive authorization domain

Checkpoint 17 must add a **separate** destructive authorization
capability/domain, for example `DestructiveAuthorizationAuthority`.

It should reuse the *spirit* of `ApprovalAuthority`:

- Opaque capability object
- Identity-bound map
- Single-use consume
- Process-local only
- Paired issuer and consumer
- Monotonic freshness with negative-delta denial

It must add:

- Explicit target binding record (§3)
- Explicit wipe scope
- Authoritative correlation ID copied from the controller, not from
  the trigger
- Arming-token identity that must still be live at issue time
- Executor pairing that reversible `ActionExecutor` cannot satisfy
- No persist, serialize, or parcel path

The current generic `Approval` type must not become a wipe ticket.

### 4.3 Anti-replay rules

| Event | Behavior |
| --- | --- |
| Consume unknown capability | `REJECTED` |
| Consume already-consumed capability | `REJECTED` |
| Consume capability from another authority instance | `REJECTED` |
| Consume after monotonic expiry | `EXPIRED` |
| Consume after disarm | `CANCELLED` or `REJECTED` |
| Replay caller `requestId` | New correlation ID; no inherited authority; rate limiter may deny |
| Replay durable audit row | Impossible by construction; recovery cannot submit |
| Process death | All capabilities die; new process needs a new request |
| Duplicate in-flight submit | `REJECTED` |

There is no persisted reusable approval.

## 5. Arming model

Arming is a separate phase. **Arming does not authorize and does not
execute.**

### 5.1 How arming becomes active

1. A destructive request is in `ASSESSED`
2. Current validation is `VERIFIED_DEVICE_OWNER`
3. Target snapshot is complete and internally consistent
4. Rate-limit / cooldown allows an arm
5. Optional operator confirmation / challenge succeeds
6. `DestructiveArmingAuthority` issues a process-local arming token
   bound to correlation ID, target snapshot, and scope
7. State becomes `ARMED` for a short monotonic window

### 5.2 Properties

| Property | Requirement |
| --- | --- |
| Validity window | Short monotonic window. Recommended starting point: ≤ 15,000 ms, reviewed in Checkpoint 17. Negative monotonic delta invalidates. |
| Cancellation | Explicit disarm returns `CANCELLED` and destroys the token. |
| Process death / reboot | Token is gone. New process is not armed. |
| Device Owner / admin | Must be currently verified. Stale assessment is not enough. |
| Target / scope | Token names the exact package, admin component, DO expectation, and wipe scope. |
| Operator challenge | Optional but recommended: a second distinct confirmation that is not the original trigger. Not implemented in Checkpoint 16. |
| Rate limit | Failed or repeated arm attempts increment the limiter. |
| Double activation | A second arm while one is live is `REJECTED`. No silent replace. |
| Persistence | None. |

Arming success is not permission to call DPM. Authorization and final
validation still follow.

Checkpoint 16 does **not** implement an arming executor.

## 6. Final pre-wipe validation

Immediately before any future destructive call, a dedicated
`DestructiveFinalValidator` must freshly re-check:

1. Device Owner: `VERIFIED_DEVICE_OWNER`
2. Expected admin component equals the bound component
3. Expected admin is active
4. Package / context consistency (running package, registered receiver
   set size == 1, expected member of that set)
5. Target / scope binding equality
6. Destructive capability freshness (monotonic)
7. Single-use status (consume succeeds exactly once)
8. Arming freshness and identity
9. Cooldown / rate-limit state (deny if locked or uncertain)
10. Audit precondition: required pre-execution append succeeded
11. Policy service / DPM availability; any exception is denial

Any mismatch, uncertainty, exception, unavailable service, stale state,
or failed check: **NO WIPE** (`FAILED_PRE_EXECUTION`).

Decision-time authorization never substitutes for this step.

Unlike reversible policies, there is **no** post-write read-back that
can prove a factory reset. The current
`VerifiedPolicyMutationExecutor` pattern (setter then getter) does not
apply. Absence of read-back is why final validation and pre-execution
audit are mandatory, and why success must not be claimed.

## 7. Audit semantics

Current durable audit (`sentinel_audit.db`, schema version 1) is
append-only evidence. It is not cryptographically tamper-proof, not
anti-rollback, and not remotely archived. Wall-clock on events is
presentation only.

### 7.1 What must be recorded before a future destructive call

Minimum durable pre-execution evidence:

- Authoritative correlation ID
- Canonical action name for the future destructive type (not
  `mock_wipe`)
- Phase that means “about to invoke” (design name:
  `EXECUTION_COMMITTED` evidence / pre-execution record)
- Bound package, expected admin component, and scope **or** a reason
  code that those bindings were validated (do not persist secrets)
- Presentation wall-clock for operators (not for authorization)

If this append fails: **NO WIPE**.

The existing fail-closed REQUESTED-before-mutation rule remains. For
wipe, REQUESTED alone is not enough; the committed pre-execution record
is also required because the process may never run again.

### 7.2 After a destructive call

A returned, thrown, or never-returning destructive API may prevent any
terminal local audit event: the userdata partition, including
`sentinel_audit.db`, may be erased.

Therefore:

- Do not require a local `APPLIED` row to call the API “successful”
- Do not claim local audit can prove a factory reset completed
- If the process still exists, the honest phase is
  `EXECUTION_INITIATED` or interrupted `OUTCOME_UNKNOWN`
- If the terminal append fails after a call that returned, do not
  retry the wipe to “finish the log”

### 7.3 Recovery and audit

- No recovery code may turn an interrupted event into authorization
- No audit record may trigger or replay a wipe
- Unknown persisted phases remain storage corruption, as today
- Future destructive phases, if added to `AuditEventPhase`, need an
  explicit schema review; they must not be decoded as `APPLIED`

### 7.4 Suggested future phases (not implemented)

Additive, evidence-only names for a later schema review:

- `REQUESTED` (already exists)
- `REJECTED` / `FAILED` (already exist; remain safe terminals)
- `EXECUTION_INITIATED` (new; not success)
- Do **not** add `APPLIED` meaning for wipe
- Do **not** treat `SIMULATED` as a real-wipe outcome

Until a schema change is approved, Checkpoint 17 must not overload
`APPLIED` for destruction.

## 8. Crash / reboot / lifecycle semantics

Checkpoint 14 remains in force:

- No automatic replay
- No `BOOT_COMPLETED` wipe path
- No recovery execution
- No persisted approval resurrection
- Interrupted state is evidence only

Additional wipe rules:

- No locked-boot or quickboot receiver
- `DeviceManagementApp` / `AppContainer` continue to reconstruct
  services from current device state and a fresh controller
- `SentinelDeviceAdminReceiver.onProfileProvisioningComplete` remains
  log-only
- A future read-only boot receiver, if ever proposed, still cannot
  submit, arm, authorize, or execute

See §2.4 for per-state interruption behavior.

## 9. Rate limiting / cooldown

No rate limiter exists today. Checkpoint 17 must add fail-closed
protection against:

- Rapid repeated attempts
- Button double-taps
- Duplicated Intents / events
- Repeated malicious triggers
- Repeated failed authorization or arming attempts

### 9.1 Process-local state (required)

Safe to keep in memory only:

- In-flight latch (one live destructive request)
- Failed-attempt counter
- Last-attempt monotonic timestamp
- Armed / authorized identity for the live request

This state dies with the process. That is intended.

### 9.2 Persisted state (optional, deny-only)

If a persisted cooldown is added later:

- It may only cause **denial**
- It must never be read as authorization, arming, or a capability
- Corruption, missing file, or clock uncertainty → deny wipe, or ignore
  the persist and still apply process-local limits; never “fail open”
- Do not store approvals, nonces, or “resume wipe” flags
- Do not key cooldown by caller `requestId`

Wall-clock persisted cooldowns are attacker-influenced (T17). Prefer
monotonic process-local limits. A persisted “recent attempt” marker is
evidence for operators, not a permit.

## 10. Android destructive API boundary (research only)

Research sources: this repository’s SDK coordinates (`compileSdk = 36`,
`minSdk = 26`, `targetSdk = 36`) and the public
`DevicePolicyManager` / `DeviceAdminInfo` reference. These APIs are
**not** referenced from executable production mutation code in this
checkpoint.

### 10.1 Candidate APIs

| API | Added in | Role |
| --- | --- | --- |
| `DevicePolicyManager.wipeData(int flags)` | API 8 | Ask that user data be wiped. See targeting trap below. |
| `DevicePolicyManager.wipeData(int flags, CharSequence reason)` | API 28 | Same, with a user-visible reason. `WIPE_SILENTLY` is illegal with this overload. |
| `DevicePolicyManager.wipeDevice(int flags)` | API 34 | Ask that the **device** be wiped and factory reset. |

`clearDeviceOwnerApp` is deprecated and is not a wipe design. The
platform text advises factory reset instead. It remains forbidden.

### 10.2 Targeting and Device Owner constraints (documented)

Public documentation states:

- Calling `wipeData` from the primary / last full user as an app
  **targeting API 34+** throws `IllegalStateException`. Sentinel
  `targetSdk` is 36, so a future device-wide wipe cannot use
  `wipeData` on the primary user.
- Apps that want to wipe the entire device should use `wipeDevice`.
- `wipeDevice` requires the caller to be a Device Owner or
  organization-owned Profile Owner that requested
  `DeviceAdminInfo.USES_POLICY_WIPE_DATA` (`wipe-data` metadata), or
  to hold `MASTER_CLEAR`, or both `MANAGE_DEVICE_POLICY_WIPE_DATA` and
  `MANAGE_DEVICE_POLICY_ACROSS_USERS`. Otherwise `SecurityException`.
- Sentinel is a fully-managed Device Owner DPC, not a Profile Owner.
  Profile-owner “relinquish device” wipe is out of scope.
- `USES_POLICY_WIPE_DATA` is declared by a `wipe-data` tag under
  `uses-policies`. Current metadata must stay exactly `disable-camera`
  until Checkpoint 17 explicitly reviews a metadata change.

### 10.3 Flags / scope (documented)

| Flag | Added | Documented on | Notes |
| --- | --- | --- | --- |
| `WIPE_EXTERNAL_STORAGE` | API 9 | `wipeData` | Also listed for `wipeDevice` flags. Adopted external storage. |
| `WIPE_RESET_PROTECTION_DATA` | API 22 | `wipeData` | FRP data. Device Owner only; other admins get `SecurityException`. |
| `WIPE_EUICC` | API 28 | `wipeDevice` | eUICC data. |
| `WIPE_SILENTLY` | API 29 | `wipeData` | Hides the reason. Illegal on the reason-bearing `wipeData` overload. |

`wipeDevice` on API 34+ targeting API 34+ is documented to attempt a
device factory reset and to respect supported flags regardless of
calling user. For apps targeting API 33 or below, a non-system-user
`wipeDevice` may fall back to a user wipe and ignore some flags.
Sentinel targets API 36; the API 33 fallback is still recorded because
minSdk is 26.

### 10.4 Limitations that are already proven enough to design against

- There is no getter that can prove a factory reset completed
- The calling process and local audit database may be destroyed
- DeviceAdmin `wipe-data` is a **separate** allowlist from DPM bytecode
  policy; both would have to change in Checkpoint 17
- Current production code must not mention these APIs in executable
  mutation paths

### 10.5 Unresolved items (do not guess)

Mark these explicitly for Checkpoint 17. Default until resolved: **NO
WIPE**.

1. **GrapheneOS behavior** of `wipeDevice` / `wipeData`, including any
   additional policy, FRP, or eUICC restrictions on supported Pixels.
   Not proven in this repository.
2. Whether a Device Owner on GrapheneOS is automatically granted
   `MANAGE_DEVICE_POLICY_WIPE_DATA` / `MANAGE_DEVICE_POLICY_ACROSS_USERS`,
   or whether `wipe-data` metadata is the only supported path.
3. Exact interaction of `WIPE_RESET_PROTECTION_DATA` with GrapheneOS
   owner-binding / installer verification.
4. Whether `WIPE_EUICC` is appropriate or even effective on the
   production Pixel target.
5. Whether any Sentinel product policy should ever allow
   `WIPE_SILENTLY`. This design recommends **forbidding** silent wipe
   unless a later review explicitly accepts it.
6. Whether a user-scoped wipe (secondary user) is ever in scope.
   Current product is fully-managed Device Owner. Default: out of scope.
7. Behavior on API 26–33 devices if a wipe were ever attempted there.
   `wipeDevice` does not exist before API 34. Default: unsupported,
   fail closed.
8. Whether PackageManager signing-info binding should be added to the
   target record.
9. Whether any hardware-backed confirmation (for example a future
   system confirmation dialog) exists and is available without inventing
   APIs this project does not use today.

## 11. Architecture boundary for Checkpoint 17

Propose these **future** source boundaries. None of them are added now.
None may be exposed on the UI/app compile classpath in a way that
grants execution capability.

| Future component | May | Must not |
| --- | --- | --- |
| UI / `MainActivity` / dashboard | Request (submit a dedicated command) and display evidence | Assess with authority, arm, authorize, validate for execution, execute, write audit, inspect via implementation types |
| Destructive request acceptor (controller-like) | Create correlation IDs; append REQUESTED; start assessment | Call DPM; issue reversible `Approval`; skip arming |
| Destructive assessor | Read current DO/admin/status; build target snapshot | Arm, authorize, execute |
| `DestructiveArmingAuthority` | Issue / cancel process-local arming tokens | Authorize or execute |
| `DestructiveAuthorizationAuthority` | Issue / consume target-bound capabilities | Call DPM; accept reversible `Approval` |
| `DestructiveFinalValidator` | Fresh DO/admin/target/freshness/rate-limit/audit checks | Call DPM on success beyond returning allow/deny |
| Narrow destructive DPM service | The single future wrapper method, bytecode-bound | Be reachable from UI, recovery, audit, or reversible executor |
| Sealed destructive mutation type | Isolated from `VerifiedPolicyMutation` | Share dispatch with camera/screen/status-bar |
| Destructive executor | Invoke the wrapper only after consume + final allow | Be the reversible `ActionExecutor` or `VerifiedPolicyMutationExecutor` |
| Audit writer | Append evidence from the trusted controller | Authorize, replay, or execute |
| `RecoveryInspectionProvider` | Classify interrupted evidence | Submit, approve, retry, or mutate |

`VerifiedPolicyMutation` stays `{ScreenCapture, Camera, StatusBar}`.
A wipe variant must **not** be added to that sealed type.

`SensitiveActionRegistry.controlled` stays the six reversible commands.
A future destructive command is a new dedicated registration surface,
not an extra entry in the reversible registry, unless a later review
proves a safer composition. The safer default is a separate destructive
registry that production composition does not wire until every
Checkpoint 17 criterion is met.

`MOCK_WIPE` stays in `SensitiveActionRegistry.failSafe` only.

## 12. Mandatory Checkpoint 17 entry criteria

All of the following must be satisfied before real wipe implementation
is allowed. Partial completion is not permission to add
`wipeData` / `wipeDevice`, `wipe-data` metadata, or a registry entry.

- [ ] Threat model in `docs/WIPE_THREAT_MODEL.md` reviewed and approved
- [ ] Target binding complete using only proven DPC-available
      identifiers, plus any newly proven read-only facts
- [ ] Anti-replay complete in a **separate** destructive authorization
      domain
- [ ] Arming design implemented as a non-executing, process-local phase
- [ ] Final validation complete and fail-closed
- [ ] Audit semantics implemented: pre-execution append required;
      no false `APPLIED`; no audit replay
- [ ] Lifecycle behavior complete: no boot path, no recovery execution,
      no persisted-approval resurrection
- [ ] Destructive API semantics verified on the intended OS
      (GrapheneOS Pixel and documented API 34+ `wipeDevice` rules),
      with every §10.5 item resolved or explicitly accepted as
      out-of-scope fail-closed
- [ ] Build-time DPM allowlist change explicitly reviewed
- [ ] DeviceAdmin metadata change (`wipe-data`) explicitly reviewed if
      required by the chosen API
- [ ] Simulation tests complete without calling destructive APIs on
      production paths
- [ ] Destructive tests restricted to disposable dedicated test
      hardware (`docs/DEVICE_OWNER_TEST_DEVICE.md`)
- [ ] Exact signed artifact verification
      (`PRODUCTION_SIGNED` / Checkpoint 15 signing boundary)
- [ ] Explicit human approval before any destructive hardware test
- [ ] Controlled production registry still does not contain
      `MOCK_WIPE`
- [ ] Reversible `VerifiedPolicyMutation` / `ApprovalAuthority` path
      remains isolated from the destructive executor
- [ ] Checkpoint 13–15 guards still pass

Until that checklist is complete, the application must remain
**incapable** of performing a real wipe.

## 13. Checkpoint 16 freeze

After this checkpoint the production security boundary is unchanged:

- DPM mutators: `setScreenCaptureDisabled`, `setCameraDisabled`,
  `setStatusBarDisabled`
- DeviceAdmin metadata: `disable-camera`
- Controlled commands: `disable_screen_capture`,
  `enable_screen_capture`, `disable_camera`, `enable_camera`,
  `disable_status_bar`, `enable_status_bar`
- `MOCK_WIPE` outside controlled production mode
- No `wipeData` / `wipeDevice` invocation in production bytecode
