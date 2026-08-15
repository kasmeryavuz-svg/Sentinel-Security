# Checkpoint 19A: destructive implementation approval review

Checkpoint 19A is an **approval-request readiness** checkpoint.

It prepares the explicit destructive-implementation approval decision.
It does **not** grant that approval.
It does **not** implement a real wipe.
It does **not** perform a destructive hardware test.

**NO REAL WIPE IS IMPLEMENTED.**
**NO WIPE-DATA METADATA WAS ADDED.**
**NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED.**
**NO DESTRUCTIVE APPROVAL RECORDED.**
**DO NOT MERGE this review as a wipe authorization.**

Base SHA: `bca591d6faf88a5f3373b9f89dd5633926e8faf4` (`main` after Checkpoint 18).

Companion documents (still in force):

- `docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md` — architecture decision
- `docs/WIPE_17B_ENTRY_REVIEW.md` — 17B entry review (verdict remains NO)
- `docs/WIPE_17A_PREFLIGHT.md` — 17A simulation contract
- `docs/WIPE_DESIGN.md` — Checkpoint 16 contract
- `docs/WIPE_THREAT_MODEL.md` — threat table
- `docs/WIPE_PLATFORM_PREFLIGHT.md` — Android / GrapheneOS research
- `docs/DEVICE_OWNER_TEST_DEVICE.md` — disposable Device Owner test path
- `docs/GRAPHENEOS_ENROLLMENT.md` — GrapheneOS enrollment status
- `docs/RELEASE_SECURITY.md` — production signing boundary

## Five different states

These are **not** the same decision. A YES on one does not grant the next.

### 1. Architecture readiness

Whether the non-Android authority graph and future executor **type
boundary** are structurally ready to *request* a new explicit approval.

Closed by Checkpoint 18:

```text
18_ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL = YES
19A_ARCHITECTURE_READY_RECONFIRMED = YES
```

### 2. Approval request readiness

Whether every **safe** prerequisite for asking a human for a fresh
explicit destructive-boundary approval is complete.

This is the only decision Checkpoint 19A is allowed to close.

```text
19_DESTRUCTIVE_IMPLEMENTATION_APPROVAL_REQUEST_READY = YES
```

YES means **only**:

> all safe prerequisites are complete and the human may now be asked
> for a fresh explicit destructive-boundary approval

YES does **not** mean destructive approval has been granted.
YES does **not** authorize creating wipe-capable code.
YES does **not** authorize `<wipe-data>`, DPM allowlist widening,
production signing, hardware testing, or merge.

### 3. Actual destructive approval

Whether a human has issued the exact required approval sentence and
that issuance has been recorded.

```text
DESTRUCTIVE_IMPLEMENTATION_APPROVED = NO
DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false
```

**Not recorded in this checkpoint.** Holding the required sentence in
this document is the contract to obtain later, not a recorded answer.

### 4. Destructive implementation

Whether production-reachable code capable of factory-resetting a device
exists.

```text
DESTRUCTIVE_IMPLEMENTATION_PRESENT = false
REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false
DESTRUCTIVE_POLICY_WRAPPER_PRESENT = false
DESTRUCTIVE_METADATA_PRESENT = false
```

**Not started.** Must not start until state 3 is recorded.

### 5. Destructive hardware validation

Whether an approved disposable-device wipe test has been performed
against an exact signed artifact.

```text
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
```

**Not performed.** Requires states 3 and 4 plus the hardware contract
below. This checkpoint does not invent or record an artifact hash.

## Absolute hard blocks (unchanged)

- Do not call `DevicePolicyManager.wipeDevice(...)`
- Do not call `DevicePolicyManager.wipeData(...)`
- Do not add `<wipe-data>` DeviceAdmin metadata
- Do not add a destructive DevicePolicyManager wrapper
- Do not widen the production DPM mutator allowlist
- Do not wire a real destructive executor
- Do not enable destructive production composition
- Do not enable destructive production signing
- Do not make destructive simulation production-reachable
- Do not perform a destructive hardware test
- Do not record a fake human approval
- Do not record a fake disposable-device artifact hash
- Do not merge

Existing production DPM writes remain exactly:

- `setScreenCaptureDisabled`
- `setCameraDisabled`
- `setStatusBarDisabled`

Existing DeviceAdmin policy remains exactly:

- `disable-camera`

## 1. Checkpoint 18 audit on main

Re-audited at required baseline
`bca591d6faf88a5f3373b9f89dd5633926e8faf4`. Current branch HEAD and
`origin/main` both equal that SHA before this checkpoint’s
documentation.

The future trusted order remains:

```text
consume capability
  -> durable PRE_EXECUTION_COMMITTED append
  -> fresh live validation
  -> single-use final permit
  -> single-use execution bundle
  -> immediate synchronous executor handoff
```

`FutureDestructiveRealChainBoundary.assembleAndHandoff` still encodes
that tail as one non-`suspend` method. It does not return the bundle or
the permit. There is no coroutine hop, `Handler.post`, WorkManager,
service queue, database write after permit, UI confirmation after
permit, callback delay, persisted permit, retry, or reboot recovery.

Authority, replay, restart, persistence, and origin-binding invariants
still hold:

| Invariant | Status |
| --- | --- |
| No caller-created positive authority | Sealed handoff types; file-private issued implementations; `HandoffRegistry` |
| No Boolean / string / reversible `Approval` authorization | `assembleAndHandoff` rejects those types |
| No reusable approval / arm / capability / lease / proof / permit | Single-use consume on each authority |
| No stored or reconstructed positive authority | Types are not `Serializable` / `Parcelable`; process death destroys them |
| No recovery / boot resume | Recovery, UI, `DeviceManagementComposition`, and `SensitiveActionController` do not host the boundary; no `BOOT_COMPLETED` receiver |
| No fake durable evidence | Runtime append is paired to consumed authorization through `RuntimeDestructiveSafetyDurability`; caller-supplied proofs are not inputs |
| No generic in-memory persistence for the real chain | Constructor requires `RuntimeDestructiveSafetyDurability`; in-memory stores are not assignable |
| No artifact-identity self-trust | Observed identity cannot become a trusted expectation; `TrustedDestructiveArtifactValidationSource.trustedExpectation()` is null |
| No self-confirming human approval | Challenge cannot mint confirmation; confirmation mint is unwired |
| Unknown wipe options default deny | Empty extra-option set only; `USER_SCOPED_WIPE` denied |
| No asynchronous gap after final validation | Immediate `executor.execute(bundle)` on the same call stack |
| No queued or persisted final permit | Permit never returned; registry consume is single-use |
| No second execution after crash / restart | Surviving durable row is evidence only and cannot reconstruct authorization |
| Origin-bound executor entry | Production bytecode allows `execute` only from `assembleAndHandoff` and `onAuthorizedHandoff` only from `execute` |

There is still **no** production implementation of
`FutureDestructiveExecutorContract`. `UnwiredFutureDestructiveExecutor`
is not an executor. DeviceManagement does not construct the boundary.

Checkpoint 18 structural flags remain true; implementation flags remain
false. 17B `ENFORCED` flags remain false because production is not
wired.

```text
18_ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL = YES
```

## 2. Exact future Android changeset

**Do not implement any of A–H in this checkpoint.** The lists below are
the change surface a later approved implementation checkpoint would
have to touch for the disposable test-device path.

### A. Android destructive API implementation

Future whole-device candidate remains `DevicePolicyManager.wipeDevice`
(API 34+). `wipeData` is **not** a Sentinel whole-device route:
`targetSdk` is 36, so `wipeData` from the primary / last full user
throws `IllegalStateException`.

A later approved implementation would need:

| File / type | Change |
| --- | --- |
| New `device-management/.../AndroidFutureDestructiveExecutor.kt` | Sole production class extending `FutureDestructiveRealChainBoundary.FutureDestructiveExecutorContract` |
| That class’s `onAuthorizedHandoff()` | Sole authorized Android invocation site; flags `0` only; fail closed below API 34 |
| New `device-management/.../AndroidDevicePolicyFactoryResetService.kt` | Narrow DPM wrapper, if the later review keeps mutators out of the existing reversible services |
| `AndroidDeviceManagementInfrastructure.kt` / `AndroidDevicePolicyPlatform` | Optional platform accessor for the new wrapper; must not add wipe to camera / screen-capture / status-bar services |
| `FutureDestructiveExecutorContract.kt` | Boundary order must remain consume → append → live validation → permit → bundle → `execute`; do not return the permit or bundle |
| `SentinelDeviceAdminReceiver.kt` | Must remain log-only / non-mutating. Wipe must not be invoked from the receiver |

Must remain absent until then:

- any `wipeDevice` / `wipeData` token in production mutation sources
- any class named `DestructiveDevicePolicy`
- any `fun wipe...` production method
- any `FutureDestructiveExecutorContract` implementor other than the
  later-approved executor

### B. DeviceAdmin metadata `<wipe-data>`

| File / type | Change |
| --- | --- |
| `device-management/src/main/res/xml/device_admin_receiver.xml` | Add `<wipe-data />` under `uses-policies` only after metadata review |
| `DeviceAdminMetadataGuardTest.kt` | Expected capability set would become `disable-camera` plus `wipe-data` |
| `app/build.gradle.kts` `approvedPolicies` | Same effective set |
| `app/build.gradle.kts` `checkpoint17BForbiddenPolicies` | Would have to stop treating `wipe-data` as an absolute forbidden policy |
| Per-variant `check*EffectiveDeviceAdminMetadata` | Effective metadata gate |

Current metadata remains exactly `disable-camera`.
`WIPE_DATA_METADATA_REVIEW_APPROVED` remains false.

### C. DPM bytecode allowlist / wrapper review

| File / type | Change |
| --- | --- |
| `ProductionBytecodePolicyVerifier.kt` `checkpoint17BForbiddenDpmMethodNames` | Today unconditionally blocks `wipeData` and `wipeDevice` |
| `ProductionBytecodePolicyVerifier.kt` `allowedDpmInvocations` | Today the only mutators are the three reversible `set*` methods |
| `ProductionBytecodePolicyVerifier.kt` `authorizedDpmCallers` | Would need the new wrapper class as the sole authorized origin |
| `ReleaseArtifactSecurityVerifier.kt` `forbiddenDexTokens` | Today packaged DEX still rejects `wipeData` / `wipeDevice` |
| `Checkpoint16DpmAllowlistFreezeTest.kt` | Exact complete key set freeze |
| `Checkpoint17BHardBlockTest.kt` / `Checkpoint18DecisionTest.kt` | Independent hard-block proofs |

`DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED` remains false. This
checkpoint does not widen the allowlist.

### D. Trusted production composition

| File / type | Change |
| --- | --- |
| `DeviceManagementComposition` in `DeviceManagementSensitiveActions.kt` | Today does not call `AndroidDestructiveSafetyPersistence.issueRuntimeDurability`, does not construct `FutureDestructiveRealChainBoundary`, and does not hand off |
| `DeviceManagementImplementation` / `DeviceManagement.create` | Facade linkage; must not grow a UI-reachable wipe command |
| `AppContainer.kt` | Must remain `DeviceManagement.create` only; no direct real-chain types |
| `SensitiveActionController` / controlled registry | Must remain the six reversible commands; wipe must not join that registry |
| `AndroidDestructiveSafetyPersistence.issueRuntimeDurability` | Already exists and is unwired; a later composition would mint `RuntimeDestructiveSafetyDurability` here |
| `Checkpoint17BHardBlock` / `Checkpoint18Decision` `ENFORCED` flags | May become true only after a real chain is structurally forced to use those components |

`PRODUCTION_REACHABLE_SIMULATION` must stay false unless a later review
explicitly replaces simulation with the approved real executor.

### E. Production signing / artifact identity

| File / type | Change |
| --- | --- |
| `TrustedDestructiveArtifactValidationSource.trustedExpectation()` | Today returns null; later would mint `DISPOSABLE_DEVICE_VALIDATION` through `TrustedDestructiveArtifactExpectationMint` |
| `UnwiredDestructiveArtifactIdentitySource` | Same null today |
| `SENTINEL_RELEASE_CERT_SHA256` / `assembleProductionRelease` | Checkpoint 15 production signing; destructive production signing is a separate flag |
| `ReleaseArtifactSecurityVerifier` | Must keep debug/test certificates from becoming destructive-eligible |
| Recorded disposable artifact digest | Must be the exact approved APK SHA-256; **not recorded here** |

```text
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
```

### F. Explicit human approval recording

| File / type | Change |
| --- | --- |
| This document / `Checkpoint19ADecision.kt` | Flip `DESTRUCTIVE_HUMAN_APPROVAL_RECORDED` and `DESTRUCTIVE_IMPLEMENTATION_APPROVED` only after the exact sentence below is issued by a human |
| Runtime `DestructiveHumanApprovalAuthority` | Process-local per-attempt approval; **not** the Checkpoint 19A implementation-approval record |

Recording the runtime type is not a substitute for the implementation-
approval sentence. This checkpoint records neither.

### G. Disposable-device hardware validation

| File / type | Change |
| --- | --- |
| `docs/DEVICE_OWNER_TEST_DEVICE.md` | Later add the approved destructive procedure; current doc is reversible-policy only |
| This document’s hardware contract | Record device identity, artifact hash, and outcome only after an approved test |
| `DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED` | Remains false until that test exists |

No destructive hardware test is performed here.

### H. GrapheneOS validation

| File / type | Change |
| --- | --- |
| `docs/WIPE_PLATFORM_PREFLIGHT.md` | GrapheneOS `wipeDevice` / `wipeData` behavior is `UNRESOLVED_REQUIRES_DEVICE_TEST` |
| `docs/GRAPHENEOS_ENROLLMENT.md` | QR enrollment is not yet confirmed; ADB Device Owner remains the test assignment method |
| `GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED` | Remains false |

Forum posts are not verification. Do not guess.

## 3. Scope

Future destructive scope remains:

```text
DEVICE_FACTORY_RESET only
```

The intended later Android call, if ever approved, is
`DevicePolicyManager.wipeDevice(0)` — no extra flag bits.

Explicitly deny unless a later separate review changes policy:

| Name | Decision |
| --- | --- |
| `USER_SCOPED_WIPE` | denied |
| `WIPE_SILENTLY` | `FORBIDDEN` |
| `WIPE_RESET_PROTECTION_DATA` | `UNRESOLVED_DENY` |
| `WIPE_EUICC` | `UNRESOLVED_DENY` |
| `WIPE_EXTERNAL_STORAGE` and any other extra flag | denied (extra-option set must be empty) |
| unknown future wipe options | `DENY_UNKNOWN` |
| `wipeData` as a whole-device route | denied |
| Profile Owner wipe | out of scope |
| `MASTER_CLEAR` / `MANAGE_DEVICE_POLICY_*` | refused |

`DestructiveWipeOptionPolicyAuthority.verifyDefaultDeny` already issues
a proof only for `DEVICE_FACTORY_RESET` with an empty extra-option set.

## 4. Human approval contract

A later human must issue **exactly** the following sentence, in writing,
before any destructive-boundary implementation starts.

Required sentence (also in `Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE`):

> I, the human operator responsible for Sentinel Security, explicitly approve starting a separately scoped destructive-boundary implementation that will create production-reachable code capable of factory-resetting the dedicated disposable Sentinel test device identified by the Checkpoint 19A hardware contract.

The recorded approval must also make all of the following explicit:

1. Approval authorizes **creation of code** capable of factory-resetting
   that disposable test device.
2. The only in-scope Android API is `DevicePolicyManager.wipeDevice`
   with flags `0` (`DEVICE_FACTORY_RESET` only).
3. Approval is **not** an authorization to call `wipeData`, add
   `<wipe-data>` by itself without the rest of the approved changeset,
   perform a destructive hardware test, ship to any non-disposable
   device, or merge without the later implementation review.
4. Approval does **not** authorize `USER_SCOPED_WIPE`, `WIPE_SILENTLY`,
   `WIPE_RESET_PROTECTION_DATA`, `WIPE_EUICC`, or unknown wipe options.
5. Runtime `DestructiveHumanApproval` for a later attempt is a separate
   per-attempt control and does not replace this sentence.

Required record fields, when a later checkpoint actually records
approval:

- the exact sentence above
- operator identity
- UTC timestamp
- git revision being approved to *start implementing from*
- confirmation that the device in the hardware contract is disposable

This checkpoint stores those fields as null / false:

```text
DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false
DESTRUCTIVE_IMPLEMENTATION_APPROVED = NO
RECORDED_APPROVAL_OPERATOR = null
RECORDED_APPROVAL_TIMESTAMP = null
RECORDED_APPROVAL_SENTENCE = null
```

**Do not treat the presence of the required sentence in this file as a
recorded approval.**

## 5. Artifact / hardware contract

The following must exist **before destructive hardware validation**
(state 5). They are **not** all required merely to *ask* for
implementation approval (state 2). This checkpoint defines the
contract. It does not invent values.

| Requirement | Current status |
| --- | --- |
| Disposable dedicated device (not personal, employee, customer, or production) | Not identified in this checkpoint |
| Exact package / application identity | `com.example.devicemanagement` (`REPO_PROVEN`) |
| Exact admin component | `com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver` (`REPO_PROVEN`) |
| Build purpose | `DISPOSABLE_DEVICE_VALIDATION` only; ordinary debug/release cannot become eligible |
| Exact signing certificate SHA-256 | **Not recorded** |
| Exact approved APK artifact SHA-256 | **Not recorded** |
| Device Owner confirmed (expected admin active; not Profile Owner) | Procedure exists for reversible tests only |
| Approved destructive test procedure | **Not written as an executable procedure**; reversible-only workflow remains in `docs/DEVICE_OWNER_TEST_DEVICE.md` |
| Explicit human approval (state 3) | **Not recorded** |
| Fail-closed abort criteria | Defined below; not executed |

```text
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256 = null
RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256 = null
```

Do not use the Android debug key. Do not treat an unsigned local
`assembleRelease` artifact as the disposable validation artifact.
Trusted expectation mint remains fail-closed until a later checkpoint
records the real digests.

### Fail-closed abort criteria

Abort the later hardware validation, and do not retry, if any of:

- the device is not the dedicated disposable device named in the later
  recorded contract
- package name, admin component, certificate digest, or artifact digest
  mismatches the recorded identity
- build purpose is not `DISPOSABLE_DEVICE_VALIDATION`
- Device Owner is not freshly verified, expected admin is inactive, or
  Profile Owner is present
- API level is below 34 (`wipeDevice` unsupported; fail closed)
- human implementation approval is not recorded
- any extra wipe option / flag is requested
- live validation, lease, arm, cooldown marker, artifact match, or
  per-attempt human approval fails
- durable `PRE_EXECUTION_COMMITTED` append fails
- the process dies after append or after handoff; surviving evidence
  must not reconstruct a second invoke
- GrapheneOS behavior is unexpected; do not guess and do not retry
  with different flags
- the artifact is debug-signed, test-signed, unsigned, or not
  `PRODUCTION_SIGNED` under the later destructive-signing rules

## Machine-readable Checkpoint 19A flags

Structurally reconfirmed from Checkpoint 18 (true / YES):

```text
DESTRUCTIVE_EXECUTOR_CONTRACT_PRESENT = true
REAL_CHAIN_UNFORGEABLE_HANDOFF_PRESENT = true
REAL_CHAIN_RUNTIME_DURABILITY_REQUIRED = true
REAL_CHAIN_ARTIFACT_IDENTITY_REQUIRED = true
REAL_CHAIN_HUMAN_APPROVAL_REQUIRED = true
REAL_CHAIN_WIPE_OPTION_POLICY_REQUIRED = true
REAL_CHAIN_FINAL_LIVE_VALIDATION_REQUIRED = true
REAL_CHAIN_PRE_EXECUTION_APPEND_AFTER_CONSUME_REQUIRED = true
REAL_CHAIN_RUNTIME_DURABLE_APPEND_PAIRED = true
18_ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL = YES
19A_ARCHITECTURE_READY_RECONFIRMED = YES
FUTURE_SCOPE_DEVICE_FACTORY_RESET_ONLY = true
```

Remain false / NO:

```text
REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false
DESTRUCTIVE_POLICY_WRAPPER_PRESENT = false
DESTRUCTIVE_METADATA_PRESENT = false
PRODUCTION_REACHABLE_SIMULATION = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
WIPE_DATA_METADATA_REVIEW_APPROVED = false
DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = false
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
DESTRUCTIVE_IMPLEMENTATION_PRESENT = false
DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT = false
DESTRUCTIVE_IMPLEMENTATION_APPROVED = NO
REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED = false
```

## Verdict

```text
19_DESTRUCTIVE_IMPLEMENTATION_APPROVAL_REQUEST_READY = YES
```

YES because Checkpoint 18 architecture readiness still holds, the
future trusted order and invariants still hold, the exact future
change surface A–H is documented without being implemented, scope is
frozen to `DEVICE_FACTORY_RESET`, the exact human approval sentence is
defined and not recorded, and the hardware/artifact contract is defined
without a fake hash.

YES would **not** authorize:

- `wipeDevice`
- `<wipe-data>`
- DPM allowlist widening
- a real executor or wrapper
- production signing
- hardware testing
- merge into a destructive implementation

Remaining blockers for actual approval, implementation, and hardware
validation (states 3–5):

1. `DESTRUCTIVE_IMPLEMENTATION_APPROVED` is NO — the human has not
   issued the required sentence.
2. `DESTRUCTIVE_HUMAN_APPROVAL_RECORDED` is false.
3. `REAL_DESTRUCTIVE_EXECUTOR_PRESENT` is false — no Android wipe
   executor.
4. `DESTRUCTIVE_POLICY_WRAPPER_PRESENT` is false — no DPM wipe wrapper.
5. `DESTRUCTIVE_METADATA_PRESENT` is false — DeviceAdmin remains
   `disable-camera`.
6. `WIPE_DATA_METADATA_REVIEW_APPROVED` is false.
7. `DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED` is false.
8. `DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED` is false.
9. `DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED` is false.
10. `DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED` is false.
11. `GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED` is false.
12. 17B `ENFORCED` flags remain false because production is not wired.
13. `PRODUCTION_REACHABLE_SIMULATION` must stay false until a later
    approved composition exists.
14. Same-UID arbitrary code remains out of scope for local persistence
    integrity.

**NO REAL WIPE IMPLEMENTED**
**NO WIPE-DATA METADATA ADDED**
**NO DESTRUCTIVE HARDWARE TEST PERFORMED**
**NO DESTRUCTIVE APPROVAL RECORDED**
**DO NOT MERGE**
