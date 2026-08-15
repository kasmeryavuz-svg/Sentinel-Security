# Checkpoint 19G: validation build-purpose provenance

This checkpoint adds a **dedicated unsigned disposable-device
validation variant** whose build purpose can be independently observed
from the resulting APK. It closes only the
`build_purpose_observed=UNAVAILABLE` preparation gap left by
Checkpoint 19F.

It is not production-signing approval.
It is not a trusted artifact identity.
It is not a recorded disposable-device serial.
It is not hardware-validation preparation.
It is not authorization to wipe a device.

**THE DEDICATED VARIANT IS UNSIGNED AND NON-DISTRIBUTABLE.**
**BUILD-PURPOSE OBSERVATION IS EVIDENCE, NOT TRUST.**
**CHECKOUT PROVENANCE STILL DOES NOT PROVE APK ORIGIN.**
**A MATCHING BUILD PURPOSE ALONE CANNOT MAKE A CANDIDATE ELIGIBLE.**
**PRODUCTION SIGNING, TRUSTED CERTIFICATE EXPECTATION, DEVICE
IDENTITY, HARDWARE APPROVAL, AND RUNTIME AUTHORIZATION REMAIN ABSENT.**
**DO NOT MERGE this checkpoint as signing, artifact-identity, trigger,
or hardware-wipe authorization.**

Base SHA required to start from:

`dd0114715ced5928e8b4bee69aeebd9731e6ea70`

(Checkpoint 19F on `cursor/checkpoint-19f-validation-evidence-preparation`).

This stacked change must not modify draft PR #29, draft PR #30, or
draft PR #31.

Companion documents (still in force):

- `docs/WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md`
- `docs/WIPE_19E_INDEPENDENT_CI.md`
- `docs/WIPE_19D_REAL_CHAIN_ASSEMBLY.md`
- `docs/RELEASE_SECURITY.md`

## What 19G implements

The `disposableValidation` build type is release-derived and remains
explicitly unsigned even if production-signing environment variables
are present. It does not use the Android debug signing key. It is not
a production distribution.

A variant-specific manifest overlay declares exactly one metadata
entry whose value is `DISPOSABLE_DEVICE_VALIDATION`. Official `aapt2`
manifest inspection reads that entry from the APK. The expected
contract is never copied into the observed field.

Normal debug and release variants stay unchanged and must not claim
this build purpose.

The metadata is build-evidence only. Production runtime code must not
read or trust it. This checkpoint adds no runtime trigger,
permission, or authorization source.

## What 19G does not implement

- Production signing remains disabled. No keystore is configured or
  generated for this variant.
- The repository contract still has `expectedCertificateSha256 = null`.
- No trusted artifact digest is recorded or minted.
- No real device identity or serial is recorded.
- Checkout git revision remains inspection/build-environment
  provenance. It does not prove which source produced an arbitrary
  external APK.
- Matching package, admin, policies, SDK values, and build purpose
  still leave an unsigned candidate `INELIGIBLE`.
- Hardware approval, per-attempt confirmation, and factory-reset
  completion remain closed.

## Local proof

```text
:app:checkUnsignedDisposableValidationBuildPurposeEvidence
```

This task consumes only the dedicated unsigned validation APK, uses
the existing immutable private snapshot path, and must prove:

```text
candidate_status=INELIGIBLE
signing=UNSIGNED
build_purpose_observed=DISPOSABLE_DEVICE_VALIDATION
authority=UNTRUSTED_CANDIDATE_ONLY
runtime_authorization=false
trusted_expectation_minted=false
expected_certificate_configured=false
production_signing_enabled=false
hardware_validation_approved=false
```

The snapshot is deleted after success and failure. The report is
gitignored and is never uploaded.

The general explicit-candidate task still refuses to run without
`sentinel.destructiveValidationCandidateApk`.

## Machine-readable 19G decision

```text
CHECKPOINT_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE = YES
19G_DISPOSABLE_VALIDATION_VARIANT_PRESENT = true
19G_BUILD_PURPOSE_OBSERVABLE = true
19G_CANDIDATE_ARTIFACT_ELIGIBLE = false
19G_REAL_DEVICE_IDENTITY_RECORDED = false
19G_HARDWARE_VALIDATION_PREPARATION_READY = false
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
