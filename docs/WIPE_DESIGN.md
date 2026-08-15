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
  -> destructive executor (one synchronous trusted chain):
       consume / verify capability
       durable pre-execution audit append (fail closed)
       THEN live final validation
       immediately invoke the narrow DPM wrapper
```

A trigger must **never** directly call a wipe executor. Audit
commitment and live validation are distinct executor steps. Live
validation is not an operation that precedes the audit append.

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
| `PRE_EXECUTION_AUDIT` | Executor is writing durable pre-execution evidence | No wipe authority; append failure is NO WIPE |
| `EXECUTION_COMMITTED` | Durable pre-execution evidence exists | Audit commitment only; **not** permission to wipe |
| `FINAL_VALIDATION` | Authoritative live revalidation after the audit append | Last pre-call phase; immediately followed by the DPM wrapper |

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
  -- enter_executor_sync_chain_and_consume_capability --> PRE_EXECUTION_AUDIT
  -- expire / cancel --> EXPIRED or CANCELLED
  -- process_death --> OUTCOME_UNKNOWN

PRE_EXECUTION_AUDIT
  -- durable_pre_exec_append_ok --> EXECUTION_COMMITTED
  -- append_fail --> FAILED_PRE_EXECUTION
  -- process_death --> OUTCOME_UNKNOWN

EXECUTION_COMMITTED
  -- same_stack_live_final_validation --> FINAL_VALIDATION
  -- process_death --> OUTCOME_UNKNOWN

FINAL_VALIDATION
  -- same_stack_immediately_invoke --> EXECUTION_INITIATED
  -- any_mismatch_or_uncertainty --> FAILED_PRE_EXECUTION
  -- invoke_throws_before_call_or_aborted --> FAILED_PRE_EXECUTION
  -- process_death --> OUTCOME_UNKNOWN
```

These last phases are logical steps of **one synchronous trusted execution chain**
inside the destructive executor. They are not independently schedulable.
Order is mandatory:

1. Consume / verify the destructive capability
2. Write durable pre-execution evidence (`PRE_EXECUTION_AUDIT` →
   `EXECUTION_COMMITTED`). Append failure is NO WIPE
3. **After** the append, perform the final authoritative live
   revalidation (`FINAL_VALIDATION`)
4. Immediately invoke the narrow DPM wrapper in the same stack

`EXECUTION_COMMITTED` means the audit row exists. It is not a wipe
permit. No UI, async, callback, queue, or persistence boundary may
exist between step 3 and step 4. See §6.

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
- The deny-only cooldown marker survives process death and reboot and
  is never authorization

### 2.4 Interruption at each boundary

| Died / rebooted in | After restart |
| --- | --- |
| `IDLE` | Still `IDLE`. Nothing to recover. |
| `REQUESTED` | Durable REQUESTED may exist. Recovery classifies interrupted evidence. New process is `IDLE`. No assess/arm/authorize. |
| `ASSESSED` | In-memory snapshot gone. Same as interrupted REQUESTED evidence. New request required. |
| `ARMED` | Arming token gone. Disarmed by death. New request required. |
| `AUTHORIZED` | Destructive capability gone. Cannot be consumed. New request required. |
| `PRE_EXECUTION_AUDIT` | Append may or may not have landed. Interrupted evidence only. No retry. No invoke. Cooldown marker, if present, remains deny-only. |
| `EXECUTION_COMMITTED` | Pre-execution row may exist. Treat as `OUTCOME_UNKNOWN`. **Do not** treat the row as authorization or skip live validation. **Do not** invoke on restart. Cooldown marker remains deny-only. |
| `FINAL_VALIDATION` | No DPM call, or the call had not begun. `FAILED_PRE_EXECUTION` or interrupted evidence. No retry. Any in-memory permit is dead. Cooldown marker remains deny-only. |
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

At `FINAL_VALIDATION` — after the pre-execution audit append, and
immediately before the DPM wrapper — every bound field is compared to
a **fresh** read of the same facts. Any mismatch, blank package, blank
component, duplicate admin receiver, Profile Owner, or `UNAVAILABLE`
validation is NO WIPE. A successful audit append is not a substitute
for this live comparison.

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

## 6. Executor chain: audit commitment, then live validation

The destructive executor owns one synchronous trusted chain. Audit
commitment and final live validation are **distinct** steps. A SQLite
append can take time; Device Owner, admin, target, or circuit-breaker
state can change during that I/O. Therefore live validation must
happen **after** the append, not before it.

Mandatory order:

1. Consume / verify the destructive authorization capability as
   required (single-use, identity-bound, process-local).
2. Write the required durable pre-execution evidence. If the append
   fails: **NO WIPE**. This reaches `EXECUTION_COMMITTED` (audit
   commitment only).
3. **After** the audit append, perform the final authoritative live
   revalidation (`FINAL_VALIDATION` / `DestructiveFinalValidator`):
   - `VERIFIED_DEVICE_OWNER`
   - expected admin component
   - active admin
   - package / receiver consistency
   - target / scope binding
   - capability freshness and single-use status
   - arming freshness
   - deny-only cooldown / circuit-breaker
   - DPM / policy-service availability
4. Immediately invoke the narrow DPM wrapper in the same synchronous
   trusted chain.

This is **not** a standalone allow/deny API that other components may
call and later honor. Decision-time authorization never substitutes
for step 3. A successful step-2 append never substitutes for step 3.

Any mismatch, uncertainty, exception, unavailable service, stale
state, expiry, or failed check at step 3: **NO WIPE**
(`FAILED_PRE_EXECUTION`).

### 6.1 No TOCTOU gap after live validation

Forbidden:

- Validating, then appending audit, then invoking (the append is a
  delay window)
- A reusable Boolean “allowed” / `true` result
- A cached or persisted final-validation result
- Returning validation success to UI, an async callback, a work queue,
  or another process
- Any UI, async, callback, queue, persistence, Intent, or Binder
  boundary between step 3 and step 4

Required: step 3 and step 4 occur back-to-back in the same stack
inside the destructive executor.

### 6.2 Optional internal `FinalExecutionPermit`

If Checkpoint 17 splits helper functions inside that chain, the only
acceptable hand-off **after step 3** is an opaque
`FinalExecutionPermit` that:

- is process-local
- is bound to action type, authoritative correlation ID, target
  package/admin/Device Owner expectation, and wipe scope
- has an extremely short monotonic lifetime (far shorter than arming
  or authorization; recommended starting point: a few milliseconds,
  reviewed in Checkpoint 17)
- is consumable exactly once, and only by the paired destructive
  executor
- cannot be serialized, parceled, persisted, or reconstructed
- is never accepted from UI or another authority instance

The executor must consume this permit immediately before calling the
narrow DPM wrapper. Any state mismatch, expiry, or change after issue
is NO WIPE. A Boolean is not a permit. The permit is not issued until
the pre-execution audit append has already succeeded.

Unlike reversible policies, there is **no** post-write read-back that
can prove a factory reset. The current
`VerifiedPolicyMutationExecutor` pattern (setter then getter) does not
apply. Absence of read-back is why audit commitment, then live
validation, then immediate invocation are mandatory, and why success
must not be claimed.

## 7. Audit semantics

Current durable audit (`sentinel_audit.db`, schema version 1) is
append-only evidence. It is not cryptographically tamper-proof, not
anti-rollback, and not remotely archived. Wall-clock on events is
presentation only.

### 7.1 What must be recorded before a future destructive call

Minimum durable pre-execution evidence, written **before** final live
validation (see §6 step 2):

- Authoritative correlation ID
- Canonical action name for the future destructive type (not
  `mock_wipe`)
- Phase that means “pre-execution evidence committed” (design name:
  `EXECUTION_COMMITTED` / pre-execution audit commitment)
- Bound package, expected admin component, and scope as requested
  context (do not persist secrets or capabilities)
- Presentation wall-clock for operators (not for authorization)

If this append fails: **NO WIPE**.

`EXECUTION_COMMITTED` is evidence that the pipeline intended to
proceed. It is **not** authorization, not a substitute for live
revalidation, and not a resume token. After the append, the same
synchronous chain must still pass `FINAL_VALIDATION` and then invoke
immediately. If the process dies after the append, recovery classifies
interrupted evidence and must not invoke.

The existing fail-closed REQUESTED-before-mutation rule remains. For
wipe, REQUESTED alone is not enough; the committed pre-execution
record is also required because the process may never run again.

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
- Process death and reboot must not shorten or clear the deny-only
  destructive cooldown (see §9)

See §2.4 for per-state interruption behavior.

## 9. Rate limiting / cooldown

No rate limiter exists today. Checkpoint 16 does **not** implement one.
Checkpoint 17 **must** add fail-closed protection against:

- Rapid repeated attempts
- Button double-taps
- Duplicated Intents / events
- Repeated malicious triggers
- Repeated failed authorization or arming attempts
- Repeated crash, force-stop, or reboot intended to reset in-memory
  counters (T19)

Process-local counters alone are insufficient. The threat model
permits an attacker to kill or reboot the app and thereby clear
in-memory state. A **mandatory** cross-process / reboot deny-only
circuit breaker is therefore a Checkpoint 17 entry criterion.

### 9.1 Process-local state (required, not sufficient)

Safe to keep in memory only:

- In-flight latch (one live destructive request)
- Failed-attempt counter
- Last-attempt monotonic timestamp
- Armed / authorized / permit identity for the live request

This state dies with the process. That is intended for authorization
and arming. It is **not** acceptable as the only cooldown.

### 9.2 Mandatory persisted deny-only circuit breaker

Checkpoint 17 must persist a cooldown-required marker after a
destructive attempt (including failed authorization or arming
attempts that should count). This marker is mandatory against
**untrusted trigger / input attackers** who crash, force-stop, or
reboot the process to reset in-memory counters (T19).

Requirements:

- Process death or reboot must **never shorten or clear** destructive
  cooldown for that untrusted-input attacker
- Persisted state may **only deny**. It can never arm, authorize,
  resume, or execute
- Persist **no** approvals, capabilities, nonces, executor permits,
  `FinalExecutionPermit`s, or resume flags
- Do not rely on attacker-controlled wall clock for authorization or
  remaining cooldown
- A safe accepted design: the persisted value is only
  “cooldown required.” After every process start or reboot, a present
  marker causes a **fresh full** monotonic cooldown before any new
  destructive request may proceed
- Malformed, corrupt, unreadable, or backup-restored cooldown state
  must **fail closed** (NO WIPE)
- In-process: if the executor just wrote a marker and cannot read it
  back, fail closed
- Do not key the marker by caller `requestId`

The marker is not a permit to continue a previous request. After
restart the process is `IDLE` and, if a valid marker is present, still
denied until the new monotonic window elapses.

### 9.3 Trust boundary: this is not same-UID code containment

Ordinary app-private persistence is **not** cryptographically
tamper-proof and has no independent anti-rollback memory after
process death. Do not claim ordinary app-private persistence solves
arbitrary same-UID code compromise. A local marker does not survive
an attacker who can run arbitrary code as Sentinel’s UID or freely
rewrite Sentinel’s private files.

Distinguish:

| Attacker | In scope for the marker? |
| --- | --- |
| Untrusted trigger / UI / Intent / crash / reboot | Yes. The marker is mandatory so killing the process cannot clear cooldown. |
| Malformed, corrupt, or backup-restored cooldown bytes | Yes. Fail closed. Absence of a well-formed marker after restart is treated as “no cooldown recorded,” not as authorization. |
| Arbitrary same-UID code execution (application compromise) | **Out of scope** for a purely local in-app authorization pipeline. That attacker can delete the marker, call DPM if the allowlist later includes wipe, or disable the app. Do not invent a hardware-backed or server integrity scheme here. |

Checkpoint 17 must not treat “we wrote a file” as integrity against
same-UID compromise. If a later review brings same-UID arbitrary
writes into scope, integrity / anti-rollback of the cooldown becomes
a blocking unresolved requirement and the default remains NO WIPE
until a trustworthy mechanism is proven. That mechanism is not
specified here.

### 9.4 What must remain process-local

Arming tokens, destructive capabilities, and `FinalExecutionPermit`
objects remain process-local and die with the process. Only the
deny-only cooldown marker may persist, and only as a deny signal.

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
- The public API also documents alternative privileged permission
  paths (`MASTER_CLEAR`, or `MANAGE_DEVICE_POLICY_WIPE_DATA` together
  with `MANAGE_DEVICE_POLICY_ACROSS_USERS`). Those are **not**
  Sentinel’s intended authorization path and must not be depended on.
- Sentinel is a fully-managed Device Owner DPC, not a Profile Owner.
  Profile-owner “relinquish device” wipe is out of scope.
- `USES_POLICY_WIPE_DATA` is declared by a `wipe-data` tag under
  `uses-policies`. Current metadata must stay exactly `disable-camera`
  until Checkpoint 17 explicitly reviews a metadata change.

**Intended future Sentinel path (frozen):**

1. Freshly verified active Device Owner
2. Explicitly reviewed DeviceAdmin `USES_POLICY_WIPE_DATA` /
   `<wipe-data>` metadata
3. The documented Device Owner + `wipe-data` API contract

Do not add, request, or rely on `MANAGE_DEVICE_POLICY_*` or
`MASTER_CLEAR` grants.

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
   Not proven in this repository. Keep this unresolved until verified
   on disposable GrapheneOS Pixel hardware.
2. Exact interaction of `WIPE_RESET_PROTECTION_DATA` with GrapheneOS
   owner-binding / installer verification.
3. Whether `WIPE_EUICC` is appropriate or even effective on the
   production Pixel target.
4. Whether any Sentinel product policy should ever allow
   `WIPE_SILENTLY`. This design recommends **forbidding** silent wipe
   unless a later review explicitly accepts it.
5. Whether a user-scoped wipe (secondary user) is ever in scope.
   Current product is fully-managed Device Owner. Default: out of scope.
6. Behavior on API 26–33 devices if a wipe were ever attempted there.
   `wipeDevice` does not exist before API 34. Default: unsupported,
   fail closed.
7. Whether PackageManager signing-info binding should be added to the
   target record.
8. Whether any hardware-backed confirmation (for example a future
   system confirmation dialog) exists and is available without inventing
   APIs this project does not use today.

Privileged `MANAGE_DEVICE_POLICY_*` / `MASTER_CLEAR` grants are
**not** an unresolved Sentinel requirement. They are alternative
platform paths that this design refuses to depend on.

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
| `DestructiveFinalValidator` | Run live DO/admin/target/freshness/cooldown/DPM checks **after** pre-execution audit commitment, inside the executor chain | Return a reusable Boolean allow; run before the audit append as the last check; cache or persist a result; expose an API to UI |
| `FinalExecutionPermit` | Optional opaque in-chain hand-off **after** live validation | Be serialized, delayed, queued, issued before audit commitment, or accepted from UI |
| Narrow destructive DPM service | The single future wrapper method, bytecode-bound | Be reachable from UI, recovery, audit, or reversible executor |
| Sealed destructive mutation type | Isolated from `VerifiedPolicyMutation` | Share dispatch with camera/screen/status-bar |
| Destructive executor | Consume capability, append pre-execution audit, live-validate, then immediately invoke in one synchronous trusted chain | Validate then append then invoke; accept a Boolean allow, a delayed callback, or a persisted permit; be the reversible `ActionExecutor` or `VerifiedPolicyMutationExecutor` |
| Audit writer | Append REQUESTED and pre-execution evidence | Authorize, replay, execute, or skip live validation |
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
- [ ] Executor order implemented exactly: consume capability → durable
      pre-execution audit append (fail closed) → **then** final live
      revalidation → immediate DPM wrapper call in the same
      synchronous chain. No Boolean allow result. No
      UI/async/queue/persistence gap between live validation and
      invocation
- [ ] Mandatory deny-only cooldown / circuit breaker against
      untrusted-trigger crash/restart/reboot bypass: process death and
      reboot never shorten or clear a well-formed marker; persisted
      state denies only; malformed/corrupt/restored state fails
      closed. Do not claim the marker securely contains arbitrary
      same-UID code execution
- [ ] Audit semantics implemented: pre-execution append required
      **before** live validation; append is not authorization; no
      false `APPLIED`; no audit replay
- [ ] Lifecycle behavior complete: no boot path, no recovery execution,
      no persisted-approval resurrection
- [ ] Destructive API semantics verified on the intended OS
      (GrapheneOS Pixel and documented API 34+ `wipeDevice` rules),
      with every §10.5 item resolved or explicitly accepted as
      out-of-scope fail-closed
- [ ] Intended path remains verified Device Owner plus explicitly
      reviewed `wipe-data` metadata; no `MANAGE_DEVICE_POLICY_*` or
      `MASTER_CLEAR` dependency
- [ ] Build-time DPM allowlist change explicitly reviewed
- [ ] DeviceAdmin metadata change (`wipe-data`) explicitly reviewed if
      required by the chosen API
- [ ] Simulation tests complete without calling destructive APIs on
      production paths
- [ ] Destructive tests restricted to disposable dedicated test
      hardware (`docs/DEVICE_OWNER_TEST_DEVICE.md`)
- [ ] Exact **test-artifact** certificate / hash verification for the
      APK installed on that disposable device. This is not the
      Checkpoint 15 production-distribution signing gate and must not
      require exposing or using the production signing key
- [ ] Production distribution, if ever shipped with wipe capability,
      still requires the separate Checkpoint 15 `PRODUCTION_SIGNED`
      gate
- [ ] Explicit human approval before any destructive hardware test
- [ ] Controlled production registry still does not contain
      `MOCK_WIPE`
- [ ] Reversible `VerifiedPolicyMutation` / `ApprovalAuthority` path
      remains isolated from the destructive executor
- [ ] Checkpoint 13–15 guards still pass

Until that checklist is complete, the application must remain
**incapable** of performing a real wipe.

## 14. Checkpoint 17A status

Checkpoint 17A implements the non-destructive machinery above and a
simulated executor whose final sink records
`DESTRUCTIVE ACTION WOULD EXECUTE`. It does **not** complete the
destructive-API, metadata, or hardware-test rows.

17A implements the deny-only cooldown **state machine/codec** and
**TESTED PERSISTENCE SEMANTICS** via a test-only reconstruction adapter.
It does **not** ship a trusted **RUNTIME PERSISTENCE IMPLEMENTATION**.
17A simulation evidence proves ordering and fail-closed behavior only.
Real durable destructive pre-execution evidence remains a 17B blocker.

See `docs/WIPE_17A_PREFLIGHT.md` and `docs/WIPE_PLATFORM_PREFLIGHT.md`.

**NO REAL WIPE IS IMPLEMENTED.** A later Checkpoint 17B would still have
to change the explicit 17B hard-block gates before any real DPM wipe
wrapper or `<wipe-data>` metadata may exist.

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
