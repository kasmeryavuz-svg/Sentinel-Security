# Checkpoint 19B: destructive-boundary implementation

Checkpoint 19B records the Checkpoint 19A human approval and implements
the approved A–D package for the dedicated disposable Sentinel test
device identified by the Checkpoint 19A hardware contract.

It does **not** record a disposable-device artifact hash.
It does **not** enable production signing.
It does **not** claim GrapheneOS wipe-behavior verification.
It does **not** perform a destructive hardware test.
It does **not** assemble the real destructive chain in production.

**A REAL `wipeDevice(0)` WRAPPER EXISTS.**
**`<wipe-data>` METADATA WAS ADDED.**
**NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED.**
**DO NOT MERGE this implementation as hardware-validation or production-signing authorization.**

Base SHA approved to start from:
`b2c5cafe8f06074495e66dd35885693478f4ceba`
(Checkpoint 19A HEAD).

Companion documents (still in force except where this checkpoint
explicitly updates repository-reality flags):

- `docs/WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md` — historical
  approval-request review. Its in-document flags remain the 19A-time
  snapshot (`DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false` in that file).
- `docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md` — architecture decision
- `docs/WIPE_17B_ENTRY_REVIEW.md` — 17B entry review
- `docs/DEVICE_OWNER_TEST_DEVICE.md` — disposable Device Owner test path
- `docs/RELEASE_SECURITY.md` — production signing boundary

## Recorded human approval (state 3)

The human operator issued the exact Checkpoint 19A required sentence.

```text
DESTRUCTIVE_IMPLEMENTATION_APPROVED = YES
DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = true
```

Record fields:

| Field | Value |
| --- | --- |
| Sentence | I, the human operator responsible for Sentinel Security, explicitly approve starting a separately scoped destructive-boundary implementation that will create production-reachable code capable of factory-resetting the dedicated disposable Sentinel test device identified by the Checkpoint 19A hardware contract. |
| Operator | Yavuz Kasmer `<kasmeryavuz@gmail.com>` |
| UTC timestamp | `2026-08-15T14:28:00Z` |
| Git revision approved to start from | `b2c5cafe8f06074495e66dd35885693478f4ceba` |
| Device identity | Checkpoint 19A hardware contract: dedicated operator-controlled disposable Sentinel test device; serial not identified |

No serial was identified. No certificate or artifact SHA-256 was
invented or recorded.

This recorded sentence authorizes **creation of code** capable of
factory-resetting that disposable test device. It is **not** a
per-attempt `DestructiveHumanApproval`. Runtime confirmation remains
`UnwiredDestructiveHumanConfirmationSource`.

## Implemented package (state 4, A–D only)

### A. Android API

Sole whole-device call:

```text
DevicePolicyManager.wipeDevice(0)
```

- Origin: `AndroidDevicePolicyFactoryResetService.performAuthorizedFactoryReset`
- API 34+ only; fail closed below API 34
- Additional live checks: expected admin active, running package is
  device owner
- Never `wipeData`
- Never extra flags (`WIPE_SILENTLY`, `WIPE_RESET_PROTECTION_DATA`,
  `WIPE_EUICC`, `WIPE_EXTERNAL_STORAGE`, unknown options)

The executor (`AndroidFutureDestructiveExecutor`) lives in
`:sensitive-actions` and does not spell Android destructive method
names. It calls `AuthorizedFactoryResetPort` only from
`onAuthorizedHandoff` after a registered bundle is consumed.

`SentinelDeviceAdminReceiver` remains log-only and non-mutating.

### B. DeviceAdmin metadata

`device_admin_receiver.xml` now declares:

- `disable-camera`
- `wipe-data`

`<wipe-data>` is present only together with the rest of this A–D
changeset.

### C. DPM bytecode allowlist / wrapper review

- `wipeDevice(I)V` is allowlisted from
  `AndroidDevicePolicyFactoryResetService.performAuthorizedFactoryReset`
  only
- `wipeData` remains Checkpoint 17B-blocked and non-allowlisted
- `performAuthorizedFactoryReset` is origin-bound to
  `AndroidFutureDestructiveExecutor.onAuthorizedHandoff`
- `retainForProduction` is origin-bound to
  `DeviceManagementComposition`
- Packaged DEX may contain `wipeDevice`; it must not contain `wipeData`
- Precise control remains bytecode origin, not a DEX substring denylist
  for `wipeDevice`

### D. Trusted production composition

`DeviceManagementComposition` issues runtime durability, constructs the
factory-reset port and live-facts adapter, and retains
`ProductionDestructiveRealChain.retainForProduction`.

It does **not**:

- add a wipe command to the six reversible UI actions
- call `assembleAndHandoff`
- expose the retainer on `DeviceManagementServices`
- change `AppContainer` beyond `DeviceManagement.create()`

The real chain is **not** assembled. Trusted artifact expectation is
still null, so `ProductionDestructiveRealChain` retains the executor and
port but returns a null boundary.

```text
DESTRUCTIVE_IMPLEMENTATION_PRESENT = true
REAL_DESTRUCTIVE_EXECUTOR_PRESENT = true
DESTRUCTIVE_POLICY_WRAPPER_PRESENT = true
DESTRUCTIVE_METADATA_PRESENT = true
WIPE_DATA_METADATA_REVIEW_APPROVED = true
DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = true
REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION = false
```

## Explicitly not done (E / G / H and chain assembly)

```text
DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
PRODUCTION_REACHABLE_SIMULATION = false
REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED = false
REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED = false
```

The future trusted order is unchanged and still required before any
wipe can occur:

```text
consume capability
  -> durable PRE_EXECUTION_COMMITTED append
  -> fresh live validation
  -> single-use final permit
  -> single-use execution bundle
  -> immediate synchronous executor.execute(bundle)
```

That order is not production-wired while the artifact digest is
unrecorded and per-attempt confirmation stays unwired.

## Verdict

```text
19_DESTRUCTIVE_IMPLEMENTATION_APPROVED = YES
19_DESTRUCTIVE_IMPLEMENTATION_PRESENT = true
19_DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT = false
```

YES on implementation means only: the approved disposable-device A–D
package exists in production-reachable code, scoped to
`wipeDevice(0)` for the Checkpoint 19A hardware contract.

YES does **not** mean:

- a wipe can complete today
- a hardware test was performed
- production signing is enabled
- merge is authorized

**NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED**
**NO DISPOSABLE-DEVICE ARTIFACT HASH RECORDED**
**DO NOT MERGE**
