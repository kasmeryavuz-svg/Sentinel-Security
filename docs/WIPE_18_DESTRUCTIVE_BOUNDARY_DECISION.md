# Checkpoint 18: destructive boundary decision

Checkpoint 18 is a **decision / architecture** checkpoint.

It implements only the safe, non-Android type boundary that a later,
separately approved destructive implementation would have to satisfy.

**NO REAL WIPE IS IMPLEMENTED.**
**NO WIPE-DATA METADATA WAS ADDED.**
**NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED.**
**DO NOT MERGE this decision as a wipe authorization.**

Base SHA: `2d04e41d6153d675d3fc2e001a7b4c41d9fefd4a` (`main` after Checkpoint 17B).

Companion documents:

- `docs/WIPE_17B_ENTRY_REVIEW.md` — 17B entry review (still in force; verdict remains NO)
- `docs/WIPE_17A_PREFLIGHT.md` — 17A simulation contract (still in force)
- `docs/WIPE_DESIGN.md` — Checkpoint 16 contract (still in force)
- `docs/WIPE_THREAT_MODEL.md` — threat table (still in force)
- `docs/WIPE_PLATFORM_PREFLIGHT.md` — Android / GrapheneOS research

## Separate decisions

These are **not** the same decision. A YES on A does not grant B, C, D, or E.

### A. architecture readiness

Whether the non-Android authority graph and future executor **type
boundary** are structurally ready to *request* a new explicit approval.

This is the only decision Checkpoint 18 is allowed to close.

### B. Android API implementation approval

Whether production code may call `DevicePolicyManager.wipeDevice` (or
any other destructive Android API). **Not approved.**

### C. metadata approval

Whether DeviceAdmin metadata may add `<wipe-data>` /
`USES_POLICY_WIPE_DATA`. **Not approved.** Current metadata remains
exactly `disable-camera`.

### D. production signing approval

Whether a destructive-capable artifact may be production-signed.
**Not approved.** `DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED` remains false.

### E. disposable hardware test approval

Whether a disposable-device wipe test may be performed, and whether a
real disposable artifact hash / human approval may be recorded.
**Not approved.** No hardware test was performed.

## Absolute hard blocks (unchanged)

- Do not call `DevicePolicyManager.wipeDevice(...)`
- Do not call `DevicePolicyManager.wipeData(...)`
- Do not add `<wipe-data>` DeviceAdmin metadata
- Do not add a destructive DevicePolicyManager wrapper
- Do not widen the production DPM mutator allowlist
- Do not make destructive simulation production-reachable
- Do not perform a destructive hardware test
- Do not record a fake human approval
- Do not record a fake disposable-device artifact hash
- Do not enable destructive production signing
- Do not merge

Existing production DPM writes remain exactly:

- `setScreenCaptureDisabled`
- `setCameraDisabled`
- `setStatusBarDisabled`

Existing DeviceAdmin policy remains exactly:

- `disable-camera`

## Android platform assumptions

| Assumption | Status |
| --- | --- |
| `targetSdk` | 36 (`REPO_PROVEN`) |
| `compileSdk` | 36 |
| `minSdk` | 26 |
| Future whole-device candidate | `wipeDevice` (API 34+) |
| wipeData as Sentinel whole-device route | **No.** Apps targeting API 34+ throw `IllegalStateException` when calling `wipeData` from the primary / last full user |
| `<wipe-data>` | still absent |
| GrapheneOS `wipeDevice` / `wipeData` behavior | `UNRESOLVED_REQUIRES_DEVICE_TEST` |
| Hardware validation | unperformed |

See `docs/WIPE_PLATFORM_PREFLIGHT.md`.

## 1. Post-17B authority graph

Required future **synchronous** chain:

```text
trigger/request
  -> assessment
  -> counted attempt / deny-only cooldown
  -> arming
  -> destructive authorization
  -> artifact identity admission
  -> explicit human approval
  -> capability consume
  -> durable PRE_EXECUTION_COMMITTED append
  -> final LIVE validation
  -> short-lived final permit
  -> immediate future executor handoff
```

`FutureDestructiveRealChainBoundary.assembleAndHandoff` encodes that
tail as one non-`suspend` method. It does not return the bundle or the
permit. There is no coroutine hop, `Handler.post`, WorkManager, service
queue, database write after permit, UI confirmation after permit,
callback delay, persisted permit, retry, or reboot recovery.

Machine-visible tests prove there is still **no** route equivalent to:

- caller-created positive authority
- Boolean authorization
- reusable approval
- replayed arm / capability / lease / proof / permit
- stored or reconstructed positive authority
- recovery / boot resume
- UI direct mint
- fake durable evidence
- generic in-memory persistence satisfying runtime destructive prerequisites
- artifact identity self-trust
- self-confirming human approval
- unknown wipe-option default allow
- asynchronous gap after final validation
- queued or persisted final permit
- second execution after crash / restart

Simulation remains a separate chain (`SimulatedDestructiveExecutor` +
`PreExecutionEvidenceCommitProof` + `FinalExecutionPermit`). Those
simulation types are not accepted by the future executor contract.

## 2. Real-chain interface — without implementing wipe

New types (no Android DevicePolicyManager calls):

| Type | Role |
| --- | --- |
| `FutureDestructiveExecutorContract` | abstract contract; `execute(bundle)` consumes a registered bundle then calls `onAuthorizedHandoff` |
| `FutureDestructiveExecutionBundle` | sealed interface; file-private issued implementation; no companion `create()` |
| `FutureDestructiveRealChainBoundary` | sole mint of permit/bundle and sole production origin of `execute` |
| `RuntimeDurablePreExecutionCommitProof` | nested private-constructor proof; issued only after consumed authorization |
| `RealChainFinalLiveValidationPermit` | sealed interface; file-private issued implementation; no companion `create()` |
| `DestructiveWipeOptionPolicyProof` | default-deny option-policy proof |

The bundle can only be assembled after:

- exact target binding
- exact `DEVICE_FACTORY_RESET` scope
- live attempt lease
- artifact identity match proof
- destructive human approval
- consumed destructive authorization / capability
- runtime-durable PRE_EXECUTION_COMMITTED append invoked after that consume
- final live validation permit minted only after that append and live validation
- approved wipe-option policy proof
- `RuntimeDestructiveSafetyDurability` at construction

The executor boundary does **not** accept:

- `Boolean approved`
- raw digest strings
- generic reversible `Approval`
- generic persistence interfaces
- caller-selected artifact hashes
- recovered / persisted authority
- simulation-only proof types (`PreExecutionEvidenceCommitProof`, `FinalExecutionPermit`)
- a caller-supplied or pre-banked `RuntimeDurablePreExecutionCommitProof`

There is **no** production implementation of
`FutureDestructiveExecutorContract`. `UnwiredFutureDestructiveExecutor`
is not an executor. DeviceManagement does not construct the boundary.

`REAL_DESTRUCTIVE_EXECUTOR_PRESENT` remains false because no Android
wipe executor exists.

The handoff bundle and final permit have **no** general `create()` /
companion mint and **no** public nested mint object. The exported types
are sealed interfaces. Only file-private issued implementations in the
same file as `FutureDestructiveRealChainBoundary` can be constructed.
Same-module Kotlin in any other file cannot name or mint those
implementations. Issued instances are registered in a file-private
`HandoffRegistry` (`IdentityHashMap`, single-use). The boundary mints a
permit only after live validation, assembles the bundle only from that
exact live permit, and production bytecode allows `execute` only from
`assembleAndHandoff`. A reflected or caller-constructed bundle is not
registered and cannot reach `onAuthorizedHandoff`.

```text
REAL_CHAIN_UNFORGEABLE_HANDOFF_PRESENT = true
```

Checkpoint 18 pairs the runtime-durable pre-execution append with the
exact pending consumed authorization. The authority reaches the store
only through `RuntimeDestructiveSafetyDurability`, writes an exact-
binding `PRE_EXECUTION_COMMITTED` row, and only then registers a
single-use process-local proof. A caller cannot supply or bank that
proof. Append failure means no permit, no bundle, and no executor call.
A surviving durable row remains evidence only and cannot reconstruct
authorization after restart.

```text
REAL_CHAIN_PRE_EXECUTION_APPEND_AFTER_CONSUME_REQUIRED = true
REAL_CHAIN_RUNTIME_DURABLE_APPEND_PAIRED = true
```

The append path is structurally paired. Production composition still
does not construct the boundary or a future executor.

## 3. Runtime durability pairing

17B still has:

```text
TRUSTED_RUNTIME_COOLDOWN_PERSISTENCE_ADAPTER_PRESENT = true
REAL_DURABLE_DESTRUCTIVE_PRE_EXECUTION_AUDIT_PRESENT = true
REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = false
```

The future real-chain boundary constructor now requires
`RuntimeDestructiveSafetyDurability`. Generic
`DenyOnlyCooldownMarkerStore` / `DestructivePreExecutionDurableStore`
and in-memory stores cannot be assigned to that type.

```text
REAL_CHAIN_RUNTIME_DURABILITY_REQUIRED = true
```

ENFORCED stays false: production does not wire the boundary and no real
executor consumes the capability. Structural append pairing alone does
not flip an ENFORCED flag.

## 4. Artifact identity pairing

The boundary requires `DestructiveArtifactIdentityMatchProof` plus the
observed identity. It does not accept a caller-selected hash string.
Trusted expectation mint remains fail-closed /
`TrustedDestructiveArtifactValidationSource.trustedExpectation() == null`.

```text
REAL_CHAIN_ARTIFACT_IDENTITY_REQUIRED = true
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
```

No disposable artifact hash was recorded.

## 5. Human approval pairing

The boundary requires `DestructiveHumanApproval`. It does not accept a
challenge, confirmation, reversible `Approval`, or Boolean.

Production confirmation remains unwired
(`UnwiredDestructiveHumanConfirmationSource` returns null).

```text
REAL_CHAIN_HUMAN_APPROVAL_REQUIRED = true
DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false
```

Tests prove:

- challenge alone cannot progress the executor boundary
- approval authority alone cannot manufacture confirmation
- confirmation / approval are single-attempt and single-use
- process restart destroys positive approval
- no generic UI/API Boolean can satisfy the future executor boundary

## 6. Wipe-option policy pairing

Future scope: `DEVICE_FACTORY_RESET` only.

Remain deny:

- `USER_SCOPED_WIPE`
- `WIPE_SILENTLY`
- `WIPE_RESET_PROTECTION_DATA`
- `WIPE_EUICC`
- unknown future option names

`DestructiveWipeOptionPolicyAuthority.verifyDefaultDeny` issues a
process-local proof only for `DEVICE_FACTORY_RESET` with an empty extra
option set. No Android flag constants are passed to a policy manager.

```text
REAL_CHAIN_WIPE_OPTION_POLICY_REQUIRED = true
```

## 7. Final live validation contract

Required order inside `assembleAndHandoff`:

```text
consume capability
  -> invoke runtime-durable PRE_EXECUTION_COMMITTED append
     (commitAfterConsumedAuthorization; bound to consumed auth)
  -> obtain paired single-use proof
  -> consume that proof
  -> AFTER that, fresh live validation
  -> mint RealChainFinalLiveValidationPermit
  -> assemble bundle from that exact live permit
  -> immediate executor.execute(bundle)
```

The method is not `suspend`. Source tests forbid Handler, WorkManager,
coroutine launch/async, service queues, persisted permits, retries, and
boot recovery. The permit and bundle are never returned to the caller.

```text
REAL_CHAIN_FINAL_LIVE_VALIDATION_REQUIRED = true
```

The runtime-durable proof is issued only after the append succeeds.
A pre-created or pre-banked proof cannot satisfy the boundary because
the proof is not an input and unregistered implementations are rejected.

## Machine-readable Checkpoint 18 flags

Structurally proven (true):

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
```

Remain false:

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
REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED = false
```

## Verdict

```text
18_ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL = YES
```

YES because the non-Android boundary now structurally pairs consumed
authorization, the trusted runtime-durable append, a single-use proof,
fresh live validation, the final permit, and the immediate executor
handoff. YES means **only**:

> the non-Android architecture is ready to request a new explicit
> approval

YES would **not** authorize:

- `wipeDevice`
- `<wipe-data>`
- DPM allowlist widening
- hardware testing
- merge into a destructive implementation

Remaining blockers for any later destructive implementation (decisions
B–E, plus production wiring):

1. `REAL_DESTRUCTIVE_EXECUTOR_PRESENT` is false — no Android wipe executor.
2. `DESTRUCTIVE_POLICY_WRAPPER_PRESENT` is false — no DPM wipe wrapper.
3. `DESTRUCTIVE_METADATA_PRESENT` is false — DeviceAdmin remains `disable-camera`.
4. `PRODUCTION_REACHABLE_SIMULATION` must stay false until a later approved composition exists.
5. `GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED` is false.
6. `WIPE_RESET_PROTECTION_DATA` / `WIPE_EUICC` remain unresolved-deny.
7. `WIPE_SILENTLY` remains forbidden.
8. No disposable-device artifact hash is recorded.
9. No destructive human approval has been recorded.
10. `DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED` is false.
11. `WIPE_DATA_METADATA_REVIEW_APPROVED` is false.
12. `DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED` is false.
13. `DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED` is false.
14. 17B ENFORCED flags remain false because production is not wired.
15. Same-UID arbitrary code remains out of scope for local persistence integrity.

**NO REAL WIPE IMPLEMENTED**
**NO WIPE-DATA METADATA ADDED**
**NO DESTRUCTIVE HARDWARE TEST PERFORMED**
**DO NOT MERGE**
