# Checkpoint 19F: disposable-device evidence contract

This file is an **unfilled contract template only**.

It is not a filled hardware-validation record.
It is not a trusted artifact identity.
It is not production-signing approval.
It is not a hardware-test approval.
It is not a per-attempt confirmation.
It is not authorization to wipe a device.

Checkpoint 19F adds **build-only candidate-artifact evidence tooling**
and this **unfilled** disposable-device contract. It does **not** enable
production signing, trust an artifact, record a real device serial, add
a trigger, approve a hardware test, or perform a wipe.

**CANDIDATE EVIDENCE TOOLING IS PRESENT.**
**THIS CONTRACT TEMPLATE IS UNFILLED.**
**NO REAL DEVICE IDENTITY IS RECORDED.**
**NO TRUSTED ARTIFACT DIGEST IS RECORDED.**
**NO HARDWARE-VALIDATION PREPARATION IS READY.**
**RUNTIME DESTRUCTIVE AVAILABILITY REMAINS FALSE.**
**NO FACTORY RESET CAN COMPLETE UNDER CURRENT REPOSITORY STATE.**
**DO NOT MERGE this checkpoint as signing, artifact-identity, trigger,
or hardware-wipe authorization.**

Base SHA required to start from:

`7a542156c0848c53950b74157e23e9bc3d833df4`

(Checkpoint 19E on `cursor/checkpoint-19e-independent-ci`).

This stacked change must not modify draft PR #29 or draft PR #30.

Companion documents (still in force except where this checkpoint
explicitly updates live repository-reality flags):

- `docs/WIPE_19E_INDEPENDENT_CI.md` — 19E independent CI snapshot
- `docs/WIPE_19D_REAL_CHAIN_ASSEMBLY.md` — 19D assembly snapshot
- `docs/WIPE_19C_HARDWARE_VALIDATION_READINESS.md` — 19C readiness snapshot
- `docs/WIPE_19B_DESTRUCTIVE_IMPLEMENTATION.md` — A–D implementation snapshot
- `docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md` — architecture decision
- `docs/RELEASE_SECURITY.md` — production signing boundary

## Separate states

These are **not** the same decision. They must **never** be inferred
from each other. Checkpoint 19F implements **state 1** (candidate
evidence tooling present) and adds this unfilled contract template.
It does not implement states 2–12 below.

### 1. Candidate evidence tooling present

Whether the repository contains build/Gradle-only tooling that can
inspect one explicitly supplied APK as an untrusted candidate.

```text
19F_ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT = true
```

**This is the only evidence-tooling state Checkpoint 19F implements.**

true means `:app:generateDestructiveValidationCandidateEvidence` exists,
requires `sentinel.destructiveValidationCandidateApk`, accepts APK files
only, and writes a gitignored untrusted report. It does **not** mean a
report was generated, that a candidate is eligible, or that any digest
is trusted.

### 2. Candidate report generated

Whether a specific candidate report is recorded as a trusted repository
fact.

```text
19F_CANDIDATE_REPORT_GENERATED = false
```

**Not recorded.** A gitignored build report is not a repository fact.
Tooling presence is not a generated report. This flag stays false.

### 3. Candidate eligible

Whether an inspected candidate satisfied every required property and
may be treated as eligible for later explicit trust work.

```text
19F_CANDIDATE_ARTIFACT_ELIGIBLE = false
```

**Not eligible.** The current unsigned release APK remains
`candidate_status=INELIGIBLE` and `signing=UNSIGNED`. Eligibility is
not inferred from tooling presence or from an untrusted report.

### 4. Production signing approved

Whether production distribution signing has been separately approved.

```text
DESTRUCTIVE_PRODUCTION_SIGNING_APPROVED = false
```

**Not approved.** Candidate inspection does not approve signing.

### 5. Production signing enabled

Whether destructive/production distribution signing is enabled.

```text
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
```

**Not enabled.** Independent CI still must not run
`checkProductionDistributionSigning`, `assembleProductionRelease`, or
`bundleProductionRelease`.

### 6. Exact artifact frozen and trusted

Whether one exact signed APK digest and signing-certificate digest are
recorded as a trusted expectation.

```text
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
```

**Not recorded.** A SHA-256 that appears only inside a gitignored
candidate report is not a trusted digest. This contract must not be
filled with a real APK or certificate digest.

### 7. Disposable device identified

Whether one disposable device has been identified by exact serial and
matching hardware/OS fields in a filled contract.

```text
19F_REAL_DEVICE_IDENTITY_RECORDED = false
```

**Not recorded.** This template must not contain a real serial.

### 8. Hardware-validation preparation ready

Whether every 19C/19F preparation blocker is closed.

```text
19F_HARDWARE_VALIDATION_PREPARATION_READY = false
```

**Not ready.** An unfilled contract is not preparation.

### 9. Hardware-test approval granted

Whether hardware validation is approved.

```text
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
```

**Not approved.**

### 10. Per-attempt confirmation available

Whether a real per-attempt confirmation record exists.

```text
PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
```

**Not available.**

### 11. Hardware test performed

Whether a destructive hardware test was performed.

```text
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
```

**Not performed.**

### 12. GrapheneOS behavior verified

Whether GrapheneOS factory-reset behavior was verified on hardware.

```text
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
```

**Not verified.**

## Unfilled contract fields

Every field below is **blank / UNRECORDED**. Do not write a real
serial, artifact digest, certificate digest, or approval into this
file. Filling any field requires a later explicit checkpoint.

```text
operator_identity = UNRECORDED
confirmation_utc_issuance_time = UNRECORDED
confirmation_expiry = UNRECORDED
one_attempt_identifier = UNRECORDED
disposable_device_manufacturer = UNRECORDED
disposable_device_model = UNRECORDED
exact_device_serial = UNRECORDED
grapheneos_version = UNRECORDED
android_api_level = UNRECORDED
os_build_fingerprint = UNRECORDED
expected_package = UNRECORDED
expected_device_admin_component = UNRECORDED
device_owner_status = UNRECORDED
active_admin_status = UNRECORDED
profile_owner_absence = UNRECORDED
exact_signed_apk_filename = UNRECORDED
apk_sha256 = UNRECORDED
signing_certificate_sha256 = UNRECORDED
git_revision = UNRECORDED
clean_dirty_build_provenance = UNRECORDED
build_purpose = UNRECORDED
flags_literal = UNRECORDED
battery_percentage = UNRECORDED
charging_state = UNRECORDED
usb_state = UNRECORDED
adb_state = UNRECORDED
no_valuable_data_attestation = UNRECORDED
recovery_reprovisioning_procedure = UNRECORDED
artifact_frozen_acknowledgement = UNRECORDED
factory_reset_consequence_acknowledgement = UNRECORDED
hardware_validation_approval = UNRECORDED
per_attempt_confirmation = UNRECORDED
actual_execution_result = UNRECORDED
post_reset_grapheneos_observations = UNRECORDED
```

## Machine-readable 19F decision

```text
19F_ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT = true
19F_DISPOSABLE_DEVICE_CONTRACT_TEMPLATE_PRESENT = true
19F_UNSIGNED_CANDIDATE_PROOF_PASSED = true
19F_CANDIDATE_ARTIFACT_ELIGIBLE = false
19F_REAL_DEVICE_IDENTITY_RECORDED = false
19F_HARDWARE_VALIDATION_PREPARATION_READY = false
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
```

`19F_UNSIGNED_CANDIDATE_PROOF_PASSED` is recorded from actual
execution of `:app:checkUnsignedDestructiveValidationCandidateEvidence`.
**Passed locally.** The unsigned release APK was classified
`candidate_status=INELIGIBLE` and `signing=UNSIGNED` with
`trusted_expectation_minted=false` and `runtime_authorization=false`.
This is not candidate eligibility, not a trusted digest, and not
runtime authorization.

## Candidate evidence rules

- Input is supplied only through `sentinel.destructiveValidationCandidateApk`.
- The generator never auto-selects or trusts an arbitrary build output.
- APK files only. AAB, ZIP, directory, missing, unreadable, symlink,
  non-regular, and changing files are rejected.
- The input is read-only. The tooling never generates, signs, re-signs,
  aligns, or modifies an APK.
- SHA-256 is computed from the exact immutable APK bytes before and
  after external inspection.
- Signature verification uses official Android SDK `apksigner`.
- Expected package, DeviceAdmin component, and policies are the
  repository contract. Observed identity is never copied into expected
  identity.
- Declared DeviceAdmin policies must remain exactly `disable-camera`
  and `wipe-data`.
- The tooling never mints a trusted artifact expectation, never writes
  a digest into production source, and never uses signing passwords or
  keystore paths.
- Reports are written only under the gitignored path
  `app/build/reports/destructive-validation-candidate.txt`.
- Every report includes:

```text
authority=UNTRUSTED_CANDIDATE_ONLY
runtime_authorization=false
trusted_expectation_minted=false
production_signing_enabled=false
hardware_validation_approved=false
```

## Isolation

- Candidate tooling exists only in build/Gradle infrastructure.
- App/runtime code cannot read or consume the candidate report.
- sensitive-actions cannot mint trust from candidate evidence.
- Candidate evidence cannot populate production null sources.
- No candidate-report parser exists in production runtime modules.
- No artifact path, digest, signer, or report is an authorization input.
- Independent CI remains read-only and must not upload the report,
  APK, AAB, or a keystore.
- No emulator, ADB, device provisioning, or hardware test is added.
- No new production trigger is added.
- The wipe boundary remains exactly one production `wipeDevice(I)V`,
  literal `0`, same origin. No `wipeData`.

## Local unsigned-candidate proof

```text
:app:checkUnsignedDestructiveValidationCandidateEvidence
```

This task inspects the temporary unsigned release APK and must prove:

```text
candidate_status=INELIGIBLE
signing=UNSIGNED
runtime_authorization=false
trusted_expectation_minted=false
```

Its digest may exist only inside the generated build report. Do not
commit or upload that digest.

```text
CHECKPOINT_19F_VALIDATION_EVIDENCE_PREPARATION = YES
19F_CANDIDATE_ARTIFACT_ELIGIBLE = false
19F_REAL_DEVICE_IDENTITY_RECORDED = false
19F_HARDWARE_VALIDATION_PREPARATION_READY = false
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
NO NEW WIPE SCOPE ADDED
NO HARDWARE WIPE PERFORMED
DO NOT MERGE
```
