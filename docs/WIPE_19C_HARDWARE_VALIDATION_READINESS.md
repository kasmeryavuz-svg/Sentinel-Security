# Checkpoint 19C: hardware-validation / real-chain readiness

Checkpoint 19C is a **readiness** checkpoint.

It determines whether all **safe** prerequisites are complete for later,
**separately approved**:

1. real-chain assembly
2. signing / artifact identity
3. destructive disposable-device hardware validation

It does **not** assemble the real destructive chain.
It does **not** wire per-attempt human confirmation into production execution.
It does **not** enable production signing.
It does **not** record or invent an APK/AAB SHA-256.
It does **not** perform a destructive hardware test.

**IMPLEMENTATION IS PRESENT (Checkpoint 19B A–D).**
**THE REAL DESTRUCTIVE CHAIN IS NOT ASSEMBLED.**
**NO PER-ATTEMPT HUMAN CONFIRMATION WAS MINTED.**
**NO ARTIFACT DIGEST WAS RECORDED.**
**NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED.**
**DO NOT MERGE this readiness review as assembly, signing, or hardware-wipe authorization.**

Base SHA required to start from:
`b490fd115d09a2c9545f704a705bcc19d8680527`
(Checkpoint 19B A–D on `main`).

Companion documents (still in force except where this checkpoint
explicitly updates repository-reality flags):

- `docs/WIPE_19B_DESTRUCTIVE_IMPLEMENTATION.md` — A–D implementation
- `docs/WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md` — historical
  approval-request review (in-document flags remain the 19A-time snapshot)
- `docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md` — architecture decision
- `docs/WIPE_17B_ENTRY_REVIEW.md` — 17B entry review
- `docs/DEVICE_OWNER_TEST_DEVICE.md` — disposable Device Owner test path
  (reversible-policy workflow only)
- `docs/RELEASE_SECURITY.md` — production signing boundary
- `docs/GRAPHENEOS_ENROLLMENT.md` — GrapheneOS enrollment status
- `docs/WIPE_PLATFORM_PREFLIGHT.md` — GrapheneOS wipe behavior remains
  unresolved pending a device test

## Separate states

These are **not** the same decision. A YES on one does not grant the next.

### 1. Implementation present

Whether production-reachable code capable of factory-resetting the
dedicated disposable Sentinel test device exists.

Closed by Checkpoint 19B A–D:

```text
DESTRUCTIVE_IMPLEMENTATION_PRESENT = true
REAL_DESTRUCTIVE_EXECUTOR_PRESENT = true
DESTRUCTIVE_POLICY_WRAPPER_PRESENT = true
DESTRUCTIVE_METADATA_PRESENT = true
WIPE_ZERO_BYTECODE_ENFORCED = true
FACTORY_RESET_ORIGIN_EXACT = true
DEX_CONTROL_FLOW_ZERO_PROOF = true
```

Present does **not** mean a wipe can complete today. The retained
executor and port stay unreachable from an assembled real chain.

### 2. Real-chain assembly approval

Whether a human has issued a fresh explicit approval to assemble the
retained implementation into a production-reachable
`assembleAndHandoff` path.

```text
REAL_CHAIN_ASSEMBLY_APPROVED = NO
```

**Not granted.** Checkpoint 19C only asks whether that approval may now
be requested.

### 3. Real-chain assembly

Whether production actually constructs the real-chain boundary and can
call `assembleAndHandoff`.

```text
REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = false
REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION = false
```

**Not assembled.** `ProductionDestructiveRealChain.retainForProduction`
keeps the executor and port. `assembleIfPossible` returns a null
boundary because the trusted artifact expectation is null.
`DeviceManagementComposition` does not call `assembleAndHandoff`.

### 4. Artifact / signing approval

Whether a human has approved enabling destructive production signing
and treating one exact APK as the disposable-validation artifact.

```text
ARTIFACT_SIGNING_APPROVAL_GRANTED = NO
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
```

**Not granted.**

### 5. Artifact identity recording

Whether the exact APK SHA-256 and signing-certificate SHA-256 of that
approved immutable APK are recorded as the trusted expectation.

```text
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256 = null
RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256 = null
```

**Not recorded.** Do not invent hashes.

### 6. Destructive hardware-test approval

Whether a human has approved performing one disposable-device factory
reset against the approved artifact.

```text
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
HARDWARE_TEST_APPROVAL_GRANTED = NO
```

**Not granted.**

### 7. Destructive hardware test

Whether that approved wipe was actually performed.

```text
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT = false
```

**Not performed.**

### 8. GrapheneOS validation

Whether GrapheneOS `wipeDevice(0)` behavior has been verified on the
named disposable device.

```text
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
```

**Not verified.** Forum posts are not verification.

## Absolute hard blocks (this checkpoint)

- Do not call `assembleAndHandoff` from production
- Do not make the real destructive chain executable
- Do not wire per-attempt human confirmation into production execution
- Do not enable production signing
- Do not record or invent an APK/AAB SHA-256
- Do not perform a destructive hardware test
- Do not invoke `wipeDevice` during tests or tooling
- Do not add `wipeData`
- Do not add any wipe flag other than literal `0`
- Do not add DeviceAdmin policies beyond `disable-camera` + `wipe-data`
- Do not add UI / command / trigger access to wipe
- Do not merge

Existing Checkpoint 19B `wipeDevice(0)` may remain, and must stay
unreachable from an assembled real chain.

## 1. Re-audit of Checkpoint 19B

Re-audited at required baseline
`b490fd115d09a2c9545f704a705bcc19d8680527`. Current branch HEAD and
`origin/main` both equal that SHA before this checkpoint’s
documentation.

| Invariant | Status |
| --- | --- |
| Exactly one production `DevicePolicyManager.wipeDevice(I)V` | Confirmed: `AndroidDevicePolicyFactoryResetService.performAuthorizedFactoryReset` |
| Exact argument constant `0` | Confirmed in source, bytecode gate, and DEX CFG proof |
| Exact origin | `AndroidDevicePolicyFactoryResetService` only |
| `wipeData` forbidden | Confirmed: 17B hard-block; not allowlisted; DEX denylist still rejects it |
| DEX CFG proof still enforced | `DEX_CONTROL_FLOW_ZERO_PROOF = true`; packaged DEX requires fall-through from constant-zero assignment |
| DeviceAdmin metadata | Exactly `disable-camera` and `wipe-data` |
| No UI wipe command | Controlled registry remains the six reversible commands |
| No `assembleAndHandoff` production call | Composition retains only; does not assemble |
| No production-reachable destructive trigger | Retainer is a private unused field on `ComposedDeviceManagementServices`; not on `DeviceManagementServices` |
| Production signing disabled | `DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false` |
| Trusted artifact digest null | `TrustedDestructiveArtifactValidationSource.trustedExpectation()` returns null |
| Hardware validation absent | `DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false` |

The required runtime order is still:

```text
consume capability
  -> durable PRE_EXECUTION_COMMITTED append
  -> fresh live validation
  -> single-use final permit
  -> single-use execution bundle
  -> immediate synchronous executor handoff
  -> AndroidFutureDestructiveExecutor
  -> AuthorizedFactoryResetPort
  -> AndroidDevicePolicyFactoryResetService
  -> wipeDevice(0)
```

That order is encoded by `FutureDestructiveRealChainBoundary.assembleAndHandoff`.
Production does not call it.

## 2. Real-chain assembly contract (do not implement)

Moving from **retained implementation** to an **assembled destructive
chain** requires a later, separately approved checkpoint. The exact
future surface is:

| Class / function | Today | Future assembly would need |
| --- | --- | --- |
| `TrustedDestructiveArtifactValidationSource.trustedExpectation()` | Always null | Mint a `DISPOSABLE_DEVICE_VALIDATION` expectation from a separately approved recorded digest. Without this, `assembleIfPossible` must keep returning null. |
| `DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint.issueFromTrustedValidationSource` | Unreachable from production | Sole mint path for that expectation. Observed identity must never become expected identity. |
| `ProductionDestructiveRealChain.assembleIfPossible` | Returns null (no trusted expectation) | May construct `FutureDestructiveRealChainBoundary` only after expectation + durability exist. Must still fail closed if either is missing. Must not call `assembleAndHandoff`. |
| `ProductionDestructiveRealChain.retainForProduction` | Constructs `AndroidFutureDestructiveExecutor` and retains the port; `boundary` is null | Must keep origin-bound to `DeviceManagementComposition.retainProductionDestructiveImplementation`. Holding the retainer must not itself execute. |
| `DeviceManagementComposition.retainProductionDestructiveImplementation` | Calls `retainForProduction` only | Must not grow a UI command. The only later production caller of `assembleAndHandoff` may be a dedicated orchestrator invoked from this composition, never from `SensitiveActionController` or `AppContainer`. |
| `UnwiredDestructiveHumanConfirmationSource.confirm` | Always returns null | Later wiring would call `DestructiveHumanConfirmationAuthority.confirm`, which is the only bytecode-authorized mint of `DestructiveHumanConfirmation`. Composition must not call confirm today. |
| `DestructiveHumanApprovalAuthority.issueChallenge` / `redeem` | Exist; unused by production | Challenge must never return a confirmation. Redeem requires the distinct confirmation bound to the attempt. |
| `FutureDestructiveRealChainBoundary.assembleAndHandoff` | Exists; unused by production | Sole production progression method. Must remain non-`suspend`, must not return the permit or bundle, must call `executor.execute(bundle)` on the same call stack. |
| `FutureDestructiveExecutorContract.execute` | Origin-bound to `assembleAndHandoff` | Consumes the registered bundle, then `onAuthorizedHandoff`. |
| `AndroidFutureDestructiveExecutor.onAuthorizedHandoff` | Calls `AuthorizedFactoryResetPort` | Must remain the only port caller. |
| `AndroidDevicePolicyFactoryResetService.performAuthorizedFactoryReset` | `wipeDevice(0)` after API 34 / admin / Device Owner checks | Must remain the only `wipeDevice` origin. Flags stay literal `0`. |
| `ComposedDeviceManagementServices` | Private unused retainer | Must not expose the retainer on `DeviceManagementServices`. |
| `SensitiveActionRegistry.controlled` | Six reversible commands | Wipe must not join. `MOCK_WIPE` stays fail-safe-only. |
| `AppContainer` | `DeviceManagement.create()` only | Must not import real-chain types. |
| `Checkpoint17BHardBlock` `ENFORCED` flags | All false | May become true only after a production-wired chain is structurally forced to use cooldown, durable audit, artifact identity, human approval, and wipe-option policy. |

Do **not** assemble it in this checkpoint.

### Fail-closed requirements (must hold after any later assembly)

| Failure | Required behavior |
| --- | --- |
| Process restart | Process-local capability, lease, arm, confirmation, approval, permit, and bundle die. They are not `Serializable` / `Parcelable`. A surviving durable `PRE_EXECUTION_COMMITTED` row is evidence only and cannot reconstruct a second invoke. No `BOOT_COMPLETED` resume. |
| Stale lease | `DestructiveAttemptAdmissionAuthority.requireLive` and `DestructiveArmingAuthority.requireLive` fail closed. Freshness windows on approval / artifact match fail closed on negative or expired monotonic delta. |
| Replay | Every authority is single-use consume: capability, confirmation, approval, artifact proof, wipe-option proof, permit, bundle. Caller-constructed or reconstructed instances are unregistered and cannot authorize. |
| Wrong target | `DestructiveTargetRules.denyReason` and binding equality reject package, admin, Device Owner, Profile Owner, and active-admin mismatches. |
| Missing Device Owner | Live facts and `AndroidDevicePolicyFactoryResetService` refuse with `not_device_owner`. Profile Owner is not a Sentinel whole-device route. |
| Inactive admin | Service refuses with `device_admin_inactive`. Expected admin must be the live active component. |
| API &lt; 34 | Service refuses with `factory_reset_requires_api_34`. No `wipeData` fallback. |
| Missing trusted artifact expectation | `assembleIfPossible` returns null. `DestructiveArtifactIdentityAuthority` cannot admit. Ordinary debug/release purpose cannot become eligible. No debug-key fallback. |
| Missing per-attempt human confirmation | `UnwiredDestructiveHumanConfirmationSource` returns null. `assembleAndHandoff` cannot consume a live `DestructiveHumanApproval`. Boolean / string / reversible `Approval` cannot satisfy this type. Checkpoint 19A/19B implementation-approval sentences are not per-attempt confirmation. |
| Durable append failure | `commitAfterConsumedAuthorization` failure invalidates the consumed proof, mints no permit, and does not call the executor. |
| Live validation failure | Unavailable live facts, target mismatch, consumed-authorization not fresh, dead lease/arm, or missing cooldown marker fail closed before permit mint. |

## 3. Per-attempt human confirmation contract (do not mint)

A later hardware attempt requires a **new** confirmation record issued
immediately before that one attempt. It is not the Checkpoint 19A
implementation-approval sentence. It is not the existing opaque
process-local `DestructiveHumanConfirmation` object by itself.

The future record must bind **at minimum**:

| Field | Requirement |
| --- | --- |
| Operator identity | Named human operator responsible for that attempt |
| UTC timestamp | Issuance time in UTC, not only a process-local monotonic clock |
| Exact device serial | The named disposable device; `DestructiveTargetBinding` does not currently carry a hardware serial and must not invent one |
| Exact package | `com.example.devicemanagement` |
| Exact DeviceAdmin component | `com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver` |
| Exact signing certificate SHA-256 | Taken from the approved APK, not guessed |
| Exact APK SHA-256 | SHA-256 of the final immutable APK bytes |
| Exact scope | `DEVICE_FACTORY_RESET` only |
| Exact flags | Literal `0` only |
| Exact build / revision | Git revision and build provenance of that APK |
| One attempt only | Single-use; consume destroys the record |
| Short validity window | Stale confirmation cannot redeem or assemble |
| Non-replayable attempt identifier | Distinct from reversible correlation IDs supplied by UI callers |

Existing process-local challenge / confirmation / approval types remain
necessary after assembly, but they do **not** currently bind operator
identity, UTC time, device serial, flags `0`, or git revision. Those
fields are a future contract. This checkpoint does not mint or record a real confirmation.

```text
PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED = false
RECORDED_PER_ATTEMPT_CONFIRMATION = null
```

## 4. Artifact identity contract (do not invent hashes)

Future artifact verification, after a separate signing/artifact
approval, must be:

1. Build the **exact** APK that will be installed on the disposable device.
2. Calculate SHA-256 from the **final immutable APK bytes**.
3. Extract the signing-certificate SHA-256 from **that same APK**.
4. Verify package identity is `com.example.devicemanagement`.
5. Verify DeviceAdmin receiver identity is the expected component.
6. Record git revision and build provenance with those digests.
7. Mint `TrustedDestructiveArtifactValidationSource.trustedExpectation()`
   only from those recorded values, purpose
   `DISPOSABLE_DEVICE_VALIDATION`.
8. Freeze the artifact: any rebuild, re-sign, or byte change invalidates
   approval and the trusted expectation.

Must remain false / null until that later approval records real values:

```text
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256 = null
RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256 = null
```

Do not use the Android debug key.
Do not treat an unsigned local `assembleRelease` artifact as the
disposable validation artifact.
`SENTINEL_RELEASE_CERT_SHA256` is Checkpoint 15 production-distribution
signing and is not a destructive-validation digest.

## 5. Disposable-device contract (do not run the test)

Pre-hardware checklist. Every item must be true before a later hardware
test may be requested. Status now:

| Checklist item | Status |
| --- | --- |
| Dedicated disposable Android device | Contract exists; specific device not identified |
| Device serial explicitly identified | **Not identified** (`RECORDED_DISPOSABLE_DEVICE_SERIAL = null`) |
| Expected OS / build recorded | **Not recorded** |
| Factory-reset consequence acknowledged | **Not acknowledged** for a named device |
| Device Owner confirmed | Reversible procedure exists; not confirmed for a named destructive attempt |
| Active expected DeviceAdmin confirmed | Same |
| Package matches approved artifact | Package identity is repo-proven; approved artifact does not exist |
| Signing certificate matches approved artifact | **No recorded certificate digest** |
| Artifact SHA-256 matches approval | **No recorded artifact digest** |
| Battery / USB / ADB state recorded | **Not recorded** |
| No valuable user data | Required; not attested for a named device |
| Recovery / provisioning procedure prepared | `docs/DEVICE_OWNER_TEST_DEVICE.md` is reversible-only; destructive recovery procedure is **not** written as an executable procedure |
| Abort criteria defined | Defined below; not executed |

Abort the later hardware validation, and do not retry, if any of:

- the device is not the dedicated disposable device named in the later recorded contract
- serial, package, admin component, certificate digest, or artifact digest mismatches
- build purpose is not `DISPOSABLE_DEVICE_VALIDATION`
- Device Owner is not freshly verified, expected admin is inactive, or Profile Owner is present
- API level is below 34
- per-attempt confirmation is missing, stale, replayed, or unbound
- trusted artifact expectation is missing or mismatched
- any extra wipe option / flag is requested
- durable `PRE_EXECUTION_COMMITTED` append fails
- live validation, lease, arm, or cooldown marker fails
- the process dies after append or after handoff
- GrapheneOS behavior is unexpected; do not guess and do not retry with different flags
- the artifact is debug-signed, test-signed, unsigned, rebuilt, or not the approved bytes

Do **not** run the hardware test in this checkpoint.

## 6. Machine-readable Checkpoint 19C flags

Preserved true 19B facts:

```text
DESTRUCTIVE_IMPLEMENTATION_PRESENT = true
REAL_DESTRUCTIVE_EXECUTOR_PRESENT = true
DESTRUCTIVE_POLICY_WRAPPER_PRESENT = true
DESTRUCTIVE_METADATA_PRESENT = true
WIPE_ZERO_BYTECODE_ENFORCED = true
FACTORY_RESET_ORIGIN_EXACT = true
DEX_CONTROL_FLOW_ZERO_PROOF = true
WIPE_DATA_METADATA_REVIEW_APPROVED = true
DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = true
wipeDevice(0) exact-zero enforcement = true
```

Kept false:

```text
REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = false
PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED = false
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
PRODUCTION_REACHABLE_SIMULATION = false
```

Readiness decisions:

```text
19C_READINESS_MODEL_NON_CIRCULAR = YES
19C_REAL_CHAIN_ASSEMBLY_APPROVAL_REQUEST_READY = YES
19C_HARDWARE_VALIDATION_PREPARATION_READY = NO
```

YES on assembly-approval-request readiness means **only**:

> Checkpoint 19B A–D is present and still unassembled, the exact future
> assembly surface and fail-closed requirements are documented, and a
> human may now be asked for a separate real-chain assembly approval.

YES does **not** mean assembly is approved.
YES does **not** mean signing is approved.
YES does **not** mean a hardware wipe is approved.

`19C_HARDWARE_VALIDATION_PREPARATION_READY` means **only** that all
technical, device, and artifact prerequisites are complete so a human
may then be asked for the **separate** destructive hardware-test
approval. It is not that approval. It is not a performed-test claim.
It is not a GrapheneOS wipe-behavior claim.

NO on hardware-validation preparation readiness because genuine
preparation prerequisites are still missing today. The next
hardware-test approval **must not** be requested yet. Current genuine
preparation blockers:

1. Device serial is not identified.
2. Expected OS / build is not recorded.
3. Trusted artifact digest is not recorded.
4. Destructive production signing is disabled.
5. Real chain is not assembled.
6. Per-attempt confirmation is not wired.
7. Battery / USB / ADB state is not recorded.
8. Destructive recovery / provisioning procedure is not prepared.
9. Factory-reset consequence is not acknowledged for a named device.
10. 17B `REAL_DESTRUCTIVE_CHAIN_*_ENFORCED` runtime gates remain false
    because production is not wired.

Those missing items are the **current** blockers. They are not an
invitation to assemble, sign, record a digest, or wipe.

Later states that are **not** preparation blockers. Approval, execution, and result verification happen **after** preparation readiness:

```text
HARDWARE_TEST_APPROVAL_GRANTED = NO
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
```

The readiness model is not circular
(`19C_READINESS_MODEL_NON_CIRCULAR = YES`): a future
`PREPARATION_READY = YES` must be logically possible while those four
later flags remain `NO` / `false`. Hardware-test approval, a performed
test, and GrapheneOS wipe-behavior verification are therefore not
prerequisites of preparation readiness. The current verdict stays
`19C_HARDWARE_VALIDATION_PREPARATION_READY = NO` only because the
genuine preparation list above is still incomplete.

## Verdict

```text
19C_READINESS_MODEL_NON_CIRCULAR = YES
19C_REAL_CHAIN_ASSEMBLY_APPROVAL_REQUEST_READY = YES
19C_HARDWARE_VALIDATION_PREPARATION_READY = NO
REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
```

**NO NEW DESTRUCTIVE SCOPE ADDED**
**NO REAL CHAIN ASSEMBLED**
**NO ARTIFACT HASH RECORDED**
**NO DESTRUCTIVE HARDWARE TEST PERFORMED**
**DO NOT MERGE**
