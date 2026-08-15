# Checkpoint 17B: entry review only

Checkpoint 17B is a **security and readiness review**. It implements only
the safe prerequisites that must exist before a later, separately approved
destructive implementation could be considered.

**Later live-flag update (Checkpoint 19D):** the production real-chain
assembly path now structurally forces cooldown, durable pre-execution
audit, artifact identity, human approval, and wipe-option policy on every
real-chain path. Live `Checkpoint17BHardBlock`
`REAL_DESTRUCTIVE_CHAIN_*_ENFORCED` flags are therefore `true`. The 17B-time
snapshot recorded below remains historically `false`. Runtime factory reset
remains impossible; trusted artifact identity and per-attempt confirmation
are still absent. See `docs/WIPE_19D_REAL_CHAIN_ASSEMBLY.md`.

**NO REAL WIPE IS IMPLEMENTED.**
**NO WIPE-DATA METADATA WAS ADDED.**
**NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED.**
**DO NOT MERGE this review as a wipe authorization.**

Base SHA: `1bc0aa1ea983e529b5f7ce543ae4b3baefb132e6` (`main` after Checkpoint 17A).

Companion documents:

- `docs/WIPE_17A_PREFLIGHT.md` — 17A simulation contract (still in force)
- `docs/WIPE_DESIGN.md` — Checkpoint 16 contract (still in force)
- `docs/WIPE_THREAT_MODEL.md` — threat table (still in force)
- `docs/WIPE_PLATFORM_PREFLIGHT.md` — Android / GrapheneOS research
- `docs/AUDIT.md`, `docs/LIFECYCLE.md`, `docs/POLICY_ARCHITECTURE.md`,
  `docs/RELEASE_SECURITY.md`

## What 17B is allowed to do

Close prerequisites that can be implemented **without** opening the
destructive Android API boundary:

1. Trusted runtime deny-only cooldown persistence
2. Real durable destructive pre-execution evidence
3. Authority-graph isolation review
4. Platform preflight update for the actual target
5. Machine-enforced release / artifact safety *requirements*
6. Lifecycle / crash / reboot proofs
7. Machine-readable 17B entry flags that match repository reality

## Absolute hard blocks (unchanged)

- Do not call `DevicePolicyManager.wipeDevice(...)`
- Do not call `DevicePolicyManager.wipeData(...)`
- Do not add `<wipe-data>` DeviceAdmin metadata
- Do not widen the production DPM mutator allowlist
- Do not create a destructive DevicePolicyManager wrapper
- Do not make destructive simulation production-reachable
- Do not perform a destructive hardware test
- Do not flip `REAL_DESTRUCTIVE_EXECUTOR_PRESENT`,
  `DESTRUCTIVE_POLICY_WRAPPER_PRESENT`, `DESTRUCTIVE_METADATA_PRESENT`, or
  `PRODUCTION_REACHABLE_SIMULATION` merely to make tests pass
- Do not weaken any Checkpoint 17A invariant

Existing production DPM writes remain exactly:

- `setScreenCaptureDisabled`
- `setCameraDisabled`
- `setStatusBarDisabled`

Existing DeviceAdmin policy remains exactly:

- `disable-camera`

## 1. Trusted runtime deny-only cooldown persistence

17A proved persistence *semantics* with a test-only reconstruction adapter
and an in-memory store. 17B adds the purpose-specific trusted runtime
adapter:

```text
TrustedRuntimeDenyOnlyCooldownMarkerStore
  -> DenyOnlyMarkerDurableMedium          (persist/load one blob only)
       JVM tests: ReconstructableDenyOnlyMarkerMedium
       Android:   SqliteDenyOnlyMarkerStore
                  database sentinel_deny_only_cooldown.db
```

Properties:

- Purpose-specific storage only. The medium has no path, query, or
  arbitrary-write API.
- The marker is deny-only and is never authorization.
- The marker cannot become an arm, lease, capability, permit, or
  counted-attempt proof.
- `recordCountedAttempt` still requires write + readback before a lease
  can be issued. There is still no marker-only admission path.
- Corrupt, malformed, unreadable, or unavailable storage fails closed.
- Process restart with a Present marker starts a **fresh full** monotonic
  cooldown. No wall-clock remaining-time calculation.
- Absence on a fresh install is allowed.
- Disappearance after current-attempt admission is fail closed.
- Backup / device-transfer already exclude every database domain
  (`allowBackup=false` plus exclude-all rules). A restored well-formed
  marker can only deny. A restored malformed marker fails closed.
- No boot-triggered destructive execution.
- No replay or resume of an old attempt.
- Same-UID arbitrary code remains application compromise (T21). Ordinary
  app-private SQLite is not integrity against that attacker.

The Android store is **not** constructed by `DeviceManagement.create`.
Simulation remains non-production-reachable.

### Runtime-durable vs simulation persistence

Component existence is not the same as a future real destructive chain
being forced to use those components. 17B now separates the two:

| Surface | Who may use it | Satisfies a future real chain? |
| --- | --- | --- |
| `DenyOnlyCooldownMarkerStore` / `InMemoryDenyOnlyCooldownMarkerStore` / `ReconstructableDenyOnlyMarkerMedium` | 17A/17B simulation and tests | **No** |
| `DestructivePreExecutionDurableStore` / `InMemoryDestructivePreExecutionDurableStore` | 17A/17B simulation and tests | **No** |
| `RuntimeDenyOnlyCooldownStore` | runtime prerequisite only | **Yes**, if minted |
| `RuntimeDestructivePreExecutionStore` | runtime prerequisite only | **Yes**, if minted |
| `RuntimeDestructiveSafetyDurability` | paired runtime prerequisite | **Yes**, if minted |

`RuntimeDestructiveSafetyDurability` is an opaque paired capability.
It is not an interface. In-memory, reconstructable, unavailable, and
any other caller-supplied persistence cannot implement it or be
assigned to it. The only mint path is
`RuntimeDestructiveSafetyDurabilityMint.issueFromTrustedAndroidStores`,
which accepts only the exact Android classes
`SqliteDenyOnlyMarkerStore` and `SqliteDestructivePreExecutionStore`.
The mint is a dedicated Kotlin object with one JVM owner, not a
companion. Production bytecode allows that mint, on every JVM owner
and method-handle form, only from
`AndroidDestructiveSafetyPersistence.issueRuntimeDurability`.

That factory remains **unwired** from `DeviceManagement.create` and
production UI/composition. There is still no real destructive executor
that consumes the runtime capability, so:

```text
REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = false
```

A later real chain must take `RuntimeDestructiveSafetyDurability` (or
the two runtime store types) and must not accept the generic simulation
stores. Until that pairing exists, the ENFORCED flags stay false.

## 2. Real durable destructive pre-execution audit

17A simulation evidence was in-process and not the production durable
audit. 17B adds a **separate** durable pre-execution path that does not
migrate `sentinel_audit.db` schema v1 and does not expose destructive
authority to app/UI code.

```text
capability consume
  -> DurableDestructivePreExecutionRepository.append
       (PRE_EXECUTION_COMMITTED only; fail closed)
  -> PreExecutionEvidenceCommitAuthority issues a process-local
     single-use PreExecutionEvidenceCommitProof bound to
     correlation ID, target binding, attempt lease, and scope
  -> AFTER append: DestructiveFinalExecutionGate.validateAndIssue
  -> immediate Checkpoint17ASimulationSink handoff
```

Properties:

- Durable append must succeed before final live validation.
- Append failure makes destructive execution impossible.
- Evidence itself is never authorization.
- The final gate requires the opaque proof from the paired durable
  append authority. Caller-constructed tokens are not registered.
- Proofs are process-local, single-use, and not serializable.
- The audit record can survive process death / reboot.
- Surviving records cannot reconstruct authorization and cannot
  automatically retry or resume execution.
- Production bytecode binds store `insert` to the repository, repository
  `append` to the commit authority, and `commit` to
  `SimulatedDestructiveExecutor.execute`.
- App/UI compile classpath still cannot see the store, repository, or
  authorities.

Reusing `sentinel_audit.db` was rejected: production append is bound to
`DefaultSensitiveActionController.submit`, unknown phases are storage
corruption, and dashboard recovery must not see destructive rows as
`APPLIED`.

### Crash windows

| Window | Surviving state | Required behavior |
| --- | --- | --- |
| Before durable PRE_EXECUTION append | No durable pre-exec row. Process-local authority dead. | New process is IDLE. Cooldown marker, if written at admission, only denies. No invoke. |
| After durable PRE_EXECUTION append, before live validation / permit | Durable evidence row exists. Proof / capability / lease / permit are dead. | `OUTCOME_UNKNOWN` evidence only. Do not treat the row as authorization. Do not invoke. |
| After permit issue, before sink / future wrapper | Permit is process-local and dead. Durable row remains evidence. | No invoke. No second permit. |
| After sink / future invocation request | Local process may be dying. | Never automatically invoke a second time. Honest outcome is `EXECUTION_INITIATED` / `OUTCOME_UNKNOWN` if a real call is ever added later. 17B has no real call. |

## 3. Final execution chain / authority graph

Reviewed 17A chain for a future real sink. There is still **no**
structural route equivalent to:

- raw permit minting
- fake permit consumer
- fake evidence proof
- marker-only admission
- reusable Boolean allow result
- capability replay
- arm replay
- lease banking
- recovery replay
- cross-process authority restoration
- production UI direct invocation
- arbitrary app-module access to destructive authorities

Compile-negative snippets and isolation tests cover the new persistence
and durable-evidence types. Recovery bytecode now also forbids those
types.

The live sink remains `Checkpoint17ASimulationSink`
(`DESTRUCTIVE ACTION WOULD EXECUTE`). That sink is not
production-reachable.

## 4. Android platform entry criteria

Repository SDK coordinates (`REPO_PROVEN`):

| Coordinate | Value |
| --- | --- |
| `compileSdk` | 36 |
| `minSdk` | 26 |
| `targetSdk` | 36 |

Device Owner: a future device-wide wipe, if ever implemented, still
requires freshly verified Device Owner, expected admin active, and
explicitly reviewed `<wipe-data>` / `USES_POLICY_WIPE_DATA`. Sentinel is
not a Profile Owner product. Privileged
`MANAGE_DEVICE_POLICY_WIPE_DATA` / `MASTER_CLEAR` paths remain refused.

`wipeDevice` (API 34+): documented whole-device factory reset for apps
targeting API 34+. Sentinel targets 36, so this is the only documented
device-wide API that would be in scope later. It is **not** invoked.

`wipeData` vs `wipeDevice`: calling `wipeData` from the primary / last
full user as an app targeting API 34+ throws `IllegalStateException`.
`wipeData` is therefore not a future Sentinel device-wide path.

Required future DeviceAdmin metadata for a real implementation:
`<wipe-data>` under `uses-policies`. **Not added.** Current metadata
remains exactly `disable-camera`.

Expected whole-device scope: `DEVICE_FACTORY_RESET` only.
`USER_SCOPED_WIPE` remains a deny-only comparison value.

Stock Android / Pixel: documented API 34+ `wipeDevice` behavior applies
to stock Android. This is `VERIFIED_ANDROID`, not a hardware test.

GrapheneOS: remains `UNRESOLVED_REQUIRES_DEVICE_TEST`. This repository
still has no verified `wipeDevice` / `wipeData` evidence on GrapheneOS
Pixels. Do not guess.

See `docs/WIPE_PLATFORM_PREFLIGHT.md`.

## 5. Release / artifact safety

Before a future destructive build can exist, the following must be
machine-enforced. **None of them are enabled now.**

| Requirement | Current enforcement | 17B flag |
| --- | --- | --- |
| Exact production certificate verification | Checkpoint 15 `PRODUCTION_SIGNED` / `SENTINEL_RELEASE_CERT_SHA256` | `DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false` |
| Exact APK/artifact hash recording for any disposable-device test | Not recorded | `DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false` |
| Production vs local-test signing separation | Unsigned local release when secrets are absent; no debug-key fallback | unchanged |
| Debug / test build rejection for destructive capability | DEX denylist still rejects `wipeData` / `wipeDevice`; debug is not a wipe build | `REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false` |
| Disposable-device-only destructive validation | Not performed | `DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false` |
| Explicit human approval | Not recorded | `DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false` |
| No accidental destructive invocation in ordinary debug/release | Production composition does not wire the simulation pipeline; bytecode and metadata gates remain closed | `PRODUCTION_REACHABLE_SIMULATION = false` |

Ordinary `assembleDebug` / `assembleRelease` / `bundleRelease` must remain
incapable of wiping a device. Production signing tasks retain fail-closed
behavior when production secrets are absent.

### Artifact identity precondition

17B adds a safe, unwired artifact-identity snapshot so a later disposable-
device validation can be bound to exact values:

- signing certificate SHA-256
- APK / artifact SHA-256
- expected package name
- expected admin component
- build-purpose classification (`ORDINARY_NON_DESTRUCTIVE` vs
  `DISPOSABLE_DEVICE_VALIDATION`)

Missing, malformed, all-zero, or mismatched values fail closed. Observed
identity is a separate type from trusted expected identity. A caller-
created `DestructiveArtifactIdentity` cannot become a trusted
expectation. The only mint path is
`TrustedDestructiveArtifactExpectationMint.issueFromTrustedValidationSource`,
which does not accept an observed identity. The mint is a dedicated
Kotlin object with one JVM owner, not a companion. Production bytecode
allows that mint, on every JVM owner and method-handle form, only from
`TrustedDestructiveArtifactValidationSource`, which
returns null because no disposable-device artifact hash is recorded.
`UnwiredDestructiveArtifactIdentitySource` also returns no expectation.
Callers cannot select the trusted digest at admit time. Ordinary
debug/release purpose cannot become future-validation eligible. There is
no debug-key fallback. Identity is evidence / admission data only and
cannot reconstruct arm, capability, or permit.

```text
DESTRUCTIVE_ARTIFACT_IDENTITY_PRECONDITION_PRESENT = true
REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED = false
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
```

### Destructive human approval / operator challenge

17B adds a separate destructive human-approval domain. It is not the
reversible `Approval` type. `issueChallenge` returns challenge material
only. It does not return a response, confirmation, token, or approval.
Redeem requires a distinct `DestructiveHumanConfirmation` minted by
`DestructiveHumanConfirmationAuthority`, not by
`DestructiveHumanApprovalAuthority`. Confirmation is bound to
correlation ID, target binding, requested scope, artifact identity, the
pending attempt lease, and challenge identity. It is explicit,
short-lived, single-use, and process-local. The operator challenge is
authority-issued unpredictable material, not a fixed magic string. The
challenge alone cannot mint approval. Holding the approval authority
cannot manufacture the successful confirmation. Replay, restoration
after process death, Boolean `approved=true`, and reversible `Approval`
cannot authorize. Production confirmation/mint remains unwired
(`UnwiredDestructiveHumanConfirmationSource` returns null). No actual
human destructive approval has been recorded.

```text
DESTRUCTIVE_HUMAN_APPROVAL_AUTHORITY_PRESENT = true
REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED = false
DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false
```

### Future wipe-option defaults

Decision-domain only. No Android policy-manager call.

| Option / scope | Default |
| --- | --- |
| `DEVICE_FACTORY_RESET` | intended scope |
| `USER_SCOPED_WIPE` | denied |
| `WIPE_SILENTLY` | `FORBIDDEN` |
| `WIPE_RESET_PROTECTION_DATA` | `UNRESOLVED_DENY` |
| `WIPE_EUICC` | `UNRESOLVED_DENY` |
| unknown future names | `DENY_UNKNOWN` |

```text
DESTRUCTIVE_WIPE_OPTION_POLICY_PRESENT = true
REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED = false
```

## 6. Lifecycle / crash / reboot

Proven and still required:

- Process death destroys all positive authority (arm, capability, lease,
  counted-attempt proof, pre-execution proof, permit).
- Reboot destroys all positive authority. Monotonic clocks reset. A
  Present deny-only marker starts a fresh full cooldown.
- Persisted data can only deny (cooldown marker) or provide evidence
  (durable pre-execution row).
- No `BOOT_COMPLETED` / locked-boot / quickboot receiver can resume a
  destructive flow. None exists.
- No unmatched audit entry can execute or retry.
- Crash after durable audit but before invocation produces
  evidence / `OUTCOME_UNKNOWN` semantics, never automatic replay.
- Crash after an invocation request must never cause an automatic second
  invocation. 17B has no real invocation.

## Machine-readable 17B entry gate

`Checkpoint17BHardBlock` flags that must remain false until a later
approved destructive implementation actually exists:

```text
REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false
DESTRUCTIVE_POLICY_WRAPPER_PRESENT = false
DESTRUCTIVE_METADATA_PRESENT = false
PRODUCTION_REACHABLE_SIMULATION = false
REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
WIPE_DATA_METADATA_REVIEW_APPROVED = false
DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = false
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
```

Flags that 17B may set true because the safe prerequisites are
implemented and tested:

```text
TRUSTED_RUNTIME_COOLDOWN_PERSISTENCE_ADAPTER_PRESENT = true
REAL_DURABLE_DESTRUCTIVE_PRE_EXECUTION_AUDIT_PRESENT = true
DESTRUCTIVE_ARTIFACT_IDENTITY_PRECONDITION_PRESENT = true
DESTRUCTIVE_HUMAN_APPROVAL_AUTHORITY_PRESENT = true
DESTRUCTIVE_WIPE_OPTION_POLICY_PRESENT = true
```

PRESENT flags prove **component existence** only. They do not prove that
a future real destructive chain is forced to use those components. The
ENFORCED flags stay false until that structural pairing exists.

Do not change any flag just because this document says the architecture
is closer. Tests prove the flags match reality.

## Verdict

```text
17B_DESTRUCTIVE_BOUNDARY_READY = NO
```

Remaining blockers:

1. `REAL_DESTRUCTIVE_EXECUTOR_PRESENT` is false — no real wipe executor.
2. `DESTRUCTIVE_POLICY_WRAPPER_PRESENT` is false — no DPM wipe wrapper.
3. `DESTRUCTIVE_METADATA_PRESENT` is false — DeviceAdmin remains
   `disable-camera`; `<wipe-data>` is not reviewed or added.
4. `PRODUCTION_REACHABLE_SIMULATION` is false — required, and must stay
   false until a later approved composition exists.
5. `GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED` is false — GrapheneOS
   `wipeDevice` / `wipeData` behavior is unresolved. Do not guess.
6. `WIPE_RESET_PROTECTION_DATA` interaction with GrapheneOS remains
   unresolved.
7. `WIPE_EUICC` appropriateness / effectiveness on the production Pixel
   target remains unresolved.
8. `WIPE_SILENTLY` remains an unapproved product policy; default is
   forbid.
9. Artifact-identity architecture exists, but trusted expectation is
    opaque, no disposable-device artifact hash is recorded, and the real
    chain is not enforced.
10. Hardware-backed confirmation availability remains unresolved.
11. `DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED` is false — no disposable
    hardware test.
12. `DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED` is false.
13. `DESTRUCTIVE_HUMAN_APPROVAL_RECORDED` is false.
14. `WIPE_DATA_METADATA_REVIEW_APPROVED` is false.
15. `DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED` is false.
16. `DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED` is false — and must not be
    enabled by this checkpoint.
17. Destructive human-approval architecture exists, but challenge
    issuance cannot self-redeem, no human destructive approval has been
    recorded, and the real chain is not enforced.
18. Same-UID arbitrary code remains out of scope for local persistence
    integrity.
19. `REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED` is false — no
    real destructive chain requires `RuntimeDenyOnlyCooldownStore`.
20. `REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED` is false — no real
    destructive chain requires `RuntimeDestructivePreExecutionStore`.
21. `REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED` is false.
22. `REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED` is false.
23. `REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED` is false.

A later YES would mean only:

> architecture is ready for a separately approved destructive
> implementation

It would **not** authorize `wipeDevice`, `<wipe-data>`, destructive
hardware testing, or merge.

**NO REAL WIPE IMPLEMENTED**
**NO WIPE-DATA METADATA ADDED**
**NO DESTRUCTIVE HARDWARE TEST PERFORMED**
**DO NOT MERGE**
