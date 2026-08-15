# Checkpoint 19D: real-chain assembly

Checkpoint 19D implements the **approved production real-chain assembly
path** while keeping execution fail-closed.

It does **not** enable production signing.
It does **not** record a real APK SHA-256 or certificate SHA-256.
It does **not** record a real device serial.
It does **not** perform a destructive hardware wipe or test.
It does **not** verify GrapheneOS wipe behavior.
It does **not** add a UI, command, registry, or public trigger.

**REAL-CHAIN ASSEMBLY IMPLEMENTATION IS APPROVED.**
**THE PRODUCTION ASSEMBLY PATH IS STRUCTURALLY PRESENT.**
**RUNTIME DESTRUCTIVE AVAILABILITY REMAINS FALSE.**
**NO FACTORY RESET CAN COMPLETE UNDER CURRENT REPOSITORY STATE.**
**DO NOT MERGE this assembly as signing, artifact-identity, or hardware-wipe authorization.**

Base SHA required to start from:
`167ef973eb06751ba56137c4b3fd57716da9e4b2`
(Checkpoint 19C on `main`).

Companion documents (still in force except where this checkpoint
explicitly updates live repository-reality flags):

- `docs/WIPE_19C_HARDWARE_VALIDATION_READINESS.md` — 19C readiness snapshot
- `docs/WIPE_19B_DESTRUCTIVE_IMPLEMENTATION.md` — A–D implementation snapshot
- `docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md` — architecture decision
- `docs/WIPE_17B_ENTRY_REVIEW.md` — 17B entry review (historical snapshot
  lines remain; live ENFORCED flags are updated by this checkpoint)
- `docs/RELEASE_SECURITY.md` — production signing boundary

## Separate states

These are **not** the same decision. Checkpoint 19D implements **state 2 only**.

### 1. Implementation approval

Whether a human approved creating production-reachable factory-reset
code (Checkpoint 19B) and, separately, assembling the real chain.

```text
REAL_CHAIN_ASSEMBLY_IMPLEMENTATION_APPROVED = YES
```

Recorded approval:

| Field | Value |
| --- | --- |
| Sentence | The human explicitly approved Checkpoint 19D real-chain assembly implementation. |
| Operator | Yavuz Kasmer `<kasmeryavuz@gmail.com>` |
| UTC timestamp | `2026-08-15T16:48:00Z` |
| Git revision approved to start from | `167ef973eb06751ba56137c4b3fd57716da9e4b2` |

This approval authorizes **structural production assembly** of the
existing Checkpoint 19B `wipeDevice(0)` implementation through the
trusted chain and `FutureDestructiveRealChainBoundary.assembleAndHandoff`.
It is **not** production signing, artifact recording, per-attempt
hardware confirmation, or a hardware test.

### 2. Structural assembly present

Whether production contains the approved assembly path, origin-bound
orchestrator, and fail-closed gates.

```text
REAL_CHAIN_ASSEMBLY_PATH_PRESENT = true
19D_REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE = YES
```

**This is the only state Checkpoint 19D implements.**

YES means the production assembly path is structurally complete and the
required gates are enforced. It does **not** mean a wipe can currently
execute.

Production call graph:

```text
ProductionDestructiveRealChain.retainForProduction
  -> ProductionDestructiveRealChainOrchestrator.assembleAlreadyBoundDeviceFactoryReset
       -> TrustedDestructiveArtifactValidationSource.trustedExpectation
       -> ProductionDestructiveHumanConfirmationSource.confirm
       -> FutureDestructiveRealChainBoundary.assembleAndHandoff
            -> consume capability
            -> durable PRE_EXECUTION_COMMITTED append
            -> fresh authoritative live validation
            -> single-use final permit
            -> single-use execution bundle
            -> immediate synchronous FutureDestructiveExecutorContract.execute
                 -> AndroidFutureDestructiveExecutor.onAuthorizedHandoff
                      -> AuthorizedFactoryResetPort.performAuthorizedFactoryReset
                           -> AndroidDevicePolicyFactoryResetService.performAuthorizedFactoryReset
                                -> DevicePolicyManager.wipeDevice(0)
```

There is still **no production trigger** for
`assembleAlreadyBoundDeviceFactoryReset`. Bytecode policy authorizes no
caller of that progression method.

### 3. Runtime destructive availability

Whether the current repository can complete a factory reset.

```text
REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
19D_CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
```

**Unavailable.** Trusted artifact expectation is null. Per-attempt
confirmation is null. Orchestration exists; execution cannot progress.

### 4. Trusted artifact / signing readiness

```text
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256 = null
RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256 = null
```

**Not recorded. Not enabled.** Observed artifact identity cannot become
the trusted expectation. No digest is hard-coded, derived from
observation, or taken from a debug certificate.

### 5. Per-attempt hardware confirmation readiness

```text
PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
RECORDED_PER_ATTEMPT_CONFIRMATION = null
```

**Unavailable.** `ProductionDestructiveHumanConfirmationSource` is
structurally wired and returns null until a separately approved real
trusted confirmation record exists. There is no Boolean/string
"confirmed" shortcut.

A later real record must bind operator identity, UTC timestamp, exact
disposable-device serial, exact package, exact DeviceAdmin component,
exact cert SHA-256, exact APK SHA-256, `DEVICE_FACTORY_RESET`, flags
literal `0`, exact build/revision, one attempt only, a short validity
window, and a non-replayable attempt id.

### 6. Hardware-test approval

```text
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
```

**Not granted.**

### 7. Hardware test performed

```text
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
```

**Not performed.**

### 8. GrapheneOS result verification

```text
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
```

**Not verified.**

## Origin allowlists

Callers are bound by exact JVM owner, exact method name, and exact JVM
descriptor. Overloads, alternate owners, method handles, method
references, reflection, and lambda/synthetic bypasses are rejected.

| Callee | Only allowed caller |
| --- | --- |
| `FutureDestructiveRealChainBoundary.assembleAndHandoff` | `ProductionDestructiveRealChainOrchestrator.assembleAlreadyBoundDeviceFactoryReset` |
| `FutureDestructiveExecutorContract.execute` | `FutureDestructiveRealChainBoundary.assembleAndHandoff` |
| `AuthorizedFactoryResetPort.performAuthorizedFactoryReset` | `AndroidFutureDestructiveExecutor.onAuthorizedHandoff` |
| `DevicePolicyManager.wipeDevice(I)V` | `AndroidDevicePolicyFactoryResetService.performAuthorizedFactoryReset` with literal `0` |
| `DestructiveHumanConfirmationAuthority.confirm` | `ProductionDestructiveHumanConfirmationSource.confirm` |
| `ProductionDestructiveHumanConfirmationSource.confirm` | `ProductionDestructiveRealChainOrchestrator.assembleAlreadyBoundDeviceFactoryReset` |
| `assembleAlreadyBoundDeviceFactoryReset` | **none** — no production trigger origin |

## Checkpoint 17B ENFORCED flags

Live flags are true because every production real-chain path is forced
through these protections. They are not true merely because the
components exist. Runtime execution still cannot complete.

```text
REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = true
REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = true
REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED = true
REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED = true
REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED = true
```

Historical 17B/18/19A/19B/19C documents remain snapshots of those
checkpoints and may still print the older `= false` lines.

## Wipe boundary

Unchanged:

- exactly one production `DevicePolicyManager.wipeDevice(I)V`
- exact integer constant `0`
- origin `AndroidDevicePolicyFactoryResetService`
- DEX CFG zero proof intact
- no `wipeData`
- no extra wipe flags
- DeviceAdmin metadata remains exactly `disable-camera` + `wipe-data`

## No trigger

This checkpoint assembles trusted infrastructure only. There is still no:

- UI button
- command / registry action
- public facade method
- AppContainer destructive authority
- Intent / BroadcastReceiver / boot / notification / accessibility /
  ADB / deep-link trigger
- scheduled worker/job
- async destructive queue
- persisted/resumable positive authority

A later hardware-validation checkpoint will define the exact one-attempt
test trigger only after separate approval.

## Verdict

```text
REAL_CHAIN_ASSEMBLY_IMPLEMENTATION_APPROVED = YES
REAL_CHAIN_ASSEMBLY_PATH_PRESENT = true
19D_REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE = YES
REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
19D_CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
```

**NO NEW WIPE SCOPE ADDED**
**NO HARDWARE WIPE PERFORMED**
**DO NOT MERGE**
