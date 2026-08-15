# Checkpoint 17A: destructive wipe preflight and simulation

Checkpoint 17A implements the **non-destructive** security machinery
required by Checkpoint 16. It does **not** make Sentinel capable of
wiping a device.

**NO REAL WIPE IS IMPLEMENTED.**
**NO WIPE-DATA METADATA WAS ADDED.**
**NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED.**

Base SHA: `d308dc7208fdd08f35f02577332a8f01479554fc`
(`cursor/checkpoint-17-real-wipe`).

Companion documents:

- `docs/WIPE_DESIGN.md` — Checkpoint 16 contract (still in force)
- `docs/WIPE_THREAT_MODEL.md` — threat table (still in force)
- `docs/WIPE_PLATFORM_PREFLIGHT.md` — Android / GrapheneOS research
- `docs/WIPE_17B_ENTRY_REVIEW.md` — 17B entry review (safe prerequisites only)

## 17A versus 17B

| Checkpoint | What it is |
| --- | --- |
| **17A (this checkpoint)** | Separate destructive security domain, arming, authorization, target binding, deny-only cooldown, simulated synchronous executor, platform research, and frozen no-wipe gates |
| **17B (entry review)** | Safe persistence + durable pre-execution evidence + authority-graph review. Real wipe, metadata, and hardware tests remain blocked. See the 17B entry review. |

17A ends at a fake sink that records `DESTRUCTIVE ACTION WOULD EXECUTE`.
There is still no Android destructive API available to that pipeline.

## Entry-criteria matrix

Every Checkpoint 17 criterion from `docs/WIPE_DESIGN.md` §12:

| Requirement | Current status | What 17A implements | What remains blocked for 17B | Evidence |
| --- | --- | --- | --- | --- |
| Threat model reviewed | **17A reviewed; not a wipe approval** | Threat model remains the contract; 17A implements mitigations that do not call destructive APIs | Human approval that the model is satisfied for a real wipe | `docs/WIPE_THREAT_MODEL.md`, this matrix |
| Target binding using only proven DPC facts | **Implemented (simulation)** | Package, expected admin, registered Sentinel admin set, Device Owner expectation, Profile Owner must be false, active-admin facts, management validation, explicit scope, authoritative correlation ID | Signing-certificate digest and any hardware-unique ID remain unresolved and unused | `DestructiveTargetBindingTest`, `DestructiveTargetRules` |
| Separate anti-replay authorization domain | **Implemented (simulation)** | `DestructiveAuthorizationAuthority` + opaque `DestructiveCapability`; one arm mints at most one authorization; post-append freshness uses an opaque consumed-authorization proof | Must not be reused as a real-wipe ticket without 17B review | `DestructiveAuthorizationAuthorityTest` |
| Non-executing process-local arming | **Implemented** | `DestructiveArmingAuthority`; requires a live attempt/admission lease; cannot reach a policy service | Operator challenge remains optional / unimplemented | `DestructiveArmingAuthorityTest` |
| Executor order: consume → pre-exec audit → live validation → immediate sink | **Implemented with simulation sink** | `SimulatedDestructiveExecutor` + `FinalExecutionPermit`; final validation requires and consumes a pre-execution commit proof issued only after `PRE_EXECUTION_COMMITTED`; rechecks original capability issuance age after the append | Immediate **DPM wrapper** call is 17B-only | `SimulatedDestructiveExecutorTest`, `FinalExecutionPermitTest` |
| Deny-only cooldown / circuit breaker | **Implemented (state machine + TESTED PERSISTENCE SEMANTICS)** | Core deny-only codec/state machine; `recordCountedAttempt` issues an opaque `CountedAttemptProof` only after write + readback; `issueLease` consumes that proof and has no marker-only path; persisted marker may only deny and is never the lease or the proof; persistence semantics exercised with a test-only reconstruction adapter | Purpose-specific trusted RUNTIME PERSISTENCE IMPLEMENTATION is a 17B blocker. Same-UID arbitrary code remains application compromise | `DestructiveDenyOnlyCooldownTest`, `DestructiveAttemptAdmissionAuthorityTest` |
| Audit semantics: pre-exec before live validation; no false APPLIED | **Implemented for ordering / fail-closed simulation evidence only** | `DestructiveEvidencePhase` is not production schema v1; APPLIED is never used; in-process writer is not durable | Real durable destructive pre-execution evidence and any additive production schema (`EXECUTION_COMMITTED`, `EXECUTION_INITIATED`) remain 17B blockers. No production durable destructive audit exists in 17A | `SimulatedDestructiveExecutorTest`, `docs/AUDIT.md` |
| Lifecycle: no boot path, no recovery execution, no persisted authority | **Preserved and tested** | Reconstruction cannot resume arm/capability/permit | Still no boot receiver | `DestructiveLifecycleRestartTest`, Checkpoint 14 guards |
| Destructive API semantics verified on intended OS | **Research only; not complete** | Documented Android facts + GrapheneOS primary-source facts | GrapheneOS/device behavior and disposable-hardware tests | `docs/WIPE_PLATFORM_PREFLIGHT.md` |
| Intended path = verified DO + reviewed wipe-data metadata; no privileged-permission dependency | **Unchanged; metadata not added** | Research confirms `USES_POLICY_WIPE_DATA` / `<wipe-data>` would be required for a future real call | Metadata change and API wiring | DeviceAdmin remains `disable-camera` |
| Build-time DPM allowlist change explicitly reviewed | **Not changed; 17B-blocked** | Allowlist still the three reversible setters plus existing reads | Adding destructive methods requires deleting the 17B hard block | `Checkpoint16DpmAllowlistFreezeTest`, `Checkpoint17BHardBlockTest` |
| DeviceAdmin `wipe-data` explicitly reviewed | **Not added; 17B-blocked** | Effective metadata gates reject `wipe-data` even if the approved set is later widened | Metadata XML + 17B forbidden-policy set | `DeviceAdminMetadataGuardTest`, `app/build.gradle.kts` |
| Simulation tests without destructive APIs | **Complete for 17A** | Dedicated sink, not `SafeMockWipeAction` | Must not be promoted to a real executor | `SimulatedDestructiveExecutorTest`, isolation tests |
| Destructive tests on disposable hardware | **Not started** | No hardware test | Required before any real wipe | `docs/DEVICE_OWNER_TEST_DEVICE.md` |
| Test-artifact certificate verification on disposable device | **Not started** | Not a production signing-key exercise | 17B disposable-device install | Checkpoint 16 §12 |
| Production distribution still requires Checkpoint 15 `PRODUCTION_SIGNED` | **Unchanged** | No wipe capability to ship | Still required if 17B ever ships | `docs/RELEASE_SECURITY.md` |
| Explicit human approval before destructive hardware test | **Not requested; not performed** | — | Required | This document |
| Controlled registry still excludes `MOCK_WIPE` | **Unchanged** | Six reversible commands only | Must stay excluded | `Checkpoint17AFreezeTest` |
| Reversible `ApprovalAuthority` isolated from destructive executor | **Implemented** | Separate types; compile-negative + source guards | Must remain isolated | `DestructiveDomainIsolationTest`, `checkAppApiCompileNegative` |
| Checkpoint 13–15 guards still pass | **Required at 17A exit** | Gates unchanged except added 17B hard block | Must still pass | Gradle gate list below |

Do **not** treat destructive API, metadata, or hardware-test rows as complete.

## Architecture implemented

```text
untrusted DestructiveSimulationRequest
  -> DestructiveSimulationPipeline
       creates authoritative correlation ID
       deny-only cooldown may only deny
       REQUESTED simulation evidence
       recordCountedAttempt after marker write + readback
         -> opaque single-use CountedAttemptProof
       issueLease consumes that proof
         -> opaque process-local DestructiveAttemptLease
  -> assessment / DestructiveTargetBinding (defensive collection snapshot)
  -> bind lease to target
  -> DestructiveArmingAuthority          (requires live lease; non-executing)
  -> DestructiveAuthorizationAuthority   (one arm -> at most one capability)
  -> SimulatedDestructiveExecutor
       1 consume DestructiveCapability -> opaque consumption proof
       2 pre-execution simulation evidence (fail closed; not durable)
            -> opaque PreExecutionEvidenceCommitProof after PRE_EXECUTION_COMMITTED
       3 AFTER append: DestructiveFinalExecutionGate.validateAndIssue
            (requires and consumes the pre-execution proof; live facts +
             lease + arm + original capability freshness + current-attempt
             marker Present; then opaque permit)
       4 immediately invoke Checkpoint17ASimulationSink
            (paired to the concrete DestructiveFinalExecutionGate only)
  -> sink records DESTRUCTIVE ACTION WOULD EXECUTE
```

Not present:

- DevicePolicyManager destructive wrapper
- `<wipe-data>`
- production composition wiring
- UI submit path
- `BOOT_COMPLETED` / recovery execution

App/UI compile classpath may see only the request/status/evidence types in
`sensitive-actions-api`. Authorities, capabilities, permits, the executor,
cooldown mutation, and the sink remain in `:sensitive-actions`
(implementation-only).

## Target binding

Bound fields (exact comparison at simulated final validation):

- dedicated action type `FACTORY_RESET_SIMULATION` (not `MOCK_WIPE`)
- authoritative correlation ID generated inside trusted code
- running package
- expected admin component
- registered Sentinel admin set
- Device Owner expectation (`true`)
- Profile Owner must be false
- active-admin expectation and active admin set
- management validation `VERIFIED_DEVICE_OWNER`
- explicit `DestructiveScope`

Not invented: IMEI, serial, Android ID, hardware attestation IDs.

Signing-certificate binding remains an unresolved optional improvement.

`USER_SCOPED_WIPE` exists only so wrong-scope comparison can fail closed.
It is never authorized.

## Production boundary after 17A (unchanged)

DevicePolicyManager mutators:

- `setScreenCaptureDisabled`
- `setCameraDisabled`
- `setStatusBarDisabled`

DeviceAdmin metadata:

- `disable-camera`

Controlled registry: the six reversible enable/disable commands.
`MOCK_WIPE` remains fail-safe only and is not production-wired.

## 17B hard block

Until these are **explicitly** changed in review, a later 17B change
cannot “accidentally” add a real wipe and still pass:

1. `ProductionBytecodePolicyVerifier.checkpoint17BForbiddenDpmMethodNames`
   rejects `wipeData` / `wipeDevice` even if someone allowlists them
2. `ReleaseArtifactSecurityVerifier.forbiddenDexTokens` still lists both
3. `allowedDpmInvocations` freeze tests still require the exact key set
4. DeviceAdmin source + effective metadata must stay `disable-camera`
5. `app/build.gradle.kts` `checkpoint17BForbiddenPolicies` contains
   `wipe-data` independently of the approved set
6. `Checkpoint17BHardBlock.REAL_DESTRUCTIVE_EXECUTOR_PRESENT` is `false`

See `Checkpoint17BHardBlock.gatesRequiringExplicitModification`.

## Persistence honesty (17A)

Distinguish these two contracts:

| Contract | 17A status |
| --- | --- |
| **TESTED PERSISTENCE SEMANTICS** | Implemented. The deny-only marker codec and state machine are real. Write / readback / reboot-equivalent reconstruction / fail-closed corrupt bytes are exercised with a **test-only reconstruction adapter**. |
| **RUNTIME PERSISTENCE IMPLEMENTATION** | **Not implemented in 17A.** 17A uses an in-memory deny-only store and an in-memory simulation evidence writer. The 17B entry review adds the purpose-specific trusted runtime adapter. There is still no generic filesystem write primitive in `sensitive-actions` main sources. |

17A left those adapters unimplemented. The 17B entry review implements
them as safe prerequisites and may set the two advisory PRESENT flags
true only when the adapters exist and are tested. Simulation and test
stores remain on a separate generic persistence surface and cannot
satisfy the runtime-durable capability types. The ENFORCED flags stay
false until a future real chain is structurally paired to those
runtime types. See `docs/WIPE_17B_ENTRY_REVIEW.md`.
17A simulation evidence still proves ordering and fail-closed behavior.
Do not treat the 17B adapters as a wipe authorization.

The attempt/admission lease is process-local, never serialized, and never
persisted. Process death destroys the lease. A surviving deny-only marker
can only deny.

## Reserved production audit schema (17B)

Production `sentinel_audit.db` remains schema version 1 with phases
`REQUESTED` / `APPLIED` / `REJECTED` / `FAILED` / `SIMULATED`.

17A uses a **separate** simulation evidence store. A later 17B additive
schema review may add evidence-only phases such as
`EXECUTION_COMMITTED` and `EXECUTION_INITIATED`. Those must not decode
as `APPLIED`. 17A does not migrate production schema.

## Same-UID residual

Ordinary app-private persistence is not integrity against arbitrary
same-UID code. The deny-only marker is for untrusted-trigger
crash/restart/reboot bypass and fail-closed corrupt bytes. Application
compromise remains out of scope, as in Checkpoint 16 T21.
