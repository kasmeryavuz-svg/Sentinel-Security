# Checkpoint 19R: validation-only signing path

This checkpoint adds build infrastructure that can later sign only the
`disposableValidation` APK with a separate validation-only key after
an explicit request. It does not generate a key, sign an artifact,
mint trust, or conduct a ceremony.

**A VALIDATION-ONLY PATH IS NOT AN INDEPENDENT WITNESS.**
**A VALIDATION-ONLY PATH IS NOT 19H CEREMONY READY.**
**A VALIDATION-ONLY PATH IS NOT PRODUCTION SIGNING.**
**A SIGNED VALIDATION APK IS NOT A TRUSTED ARTIFACT.**
**A SIGNED VALIDATION APK IS NOT CUSTOMER-DEVICE AUTHORIZATION.**
**THE REAL REPOSITORY STATE REMAINS NOT READY FOR SIGNING.**
**DO NOT MERGE this checkpoint as signing, artifact-identity, trigger,
or hardware-wipe authorization.**

Base SHA required to start from:

`e68be323e51274f29fe8f9997f3065d6c753a1c7`

(Checkpoint 19P on `cursor/checkpoint-19p-maintainability-cleanup`).

This stacked change must not modify draft PR #29, draft PR #30, draft
PR #31, draft PR #32, draft PR #33, draft PR #34, draft PR #35, or
draft PR #36.

Companion documents (still in force):

- `docs/WIPE_19P_MAINTAINABILITY_CLEANUP.md`
- `docs/WIPE_19H_SIGNING_CEREMONY_PREPARATION.md`
- `docs/WIPE_19J_AUDIT_FINDINGS_REPAIR.md`
- `docs/WIPE_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE.md`
- `docs/WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md`
- `docs/WIPE_19E_INDEPENDENT_CI.md`
- `docs/RELEASE_SECURITY.md`

## What 19R implements

A compact build-only `ValidationOnlySigningGate` decides whether the
dedicated task `assembleSignedDisposableValidation` may attach a
`validationOnly` signing configuration to `disposableValidation`.

Ordinary configuration does not read `SENTINEL_VALIDATION_*`.
Ordinary `assembleRelease`, `bundleRelease`, and
`assembleDisposableValidation` remain unsigned even if those inputs
exist. Production release never receives the validation key.

The dedicated task fails closed unless every validation input exists,
the keystore exists, the key is not debug/test material, the expected
certificate fingerprint is valid, signer policy is exactly one signer,
V2 and V3 signatures are present, the APK purpose is
`DISPOSABLE_DEVICE_VALIDATION`, and package/admin/policies/SDK still
match the repository contract.

A later successfully signed validation APK remains:

```text
authority=UNTRUSTED_CANDIDATE_ONLY
runtime_authorization=false
trusted_expectation_minted=false
customer_device_authorized=false
production_distribution=false
```

Independent CI refuses populated `SENTINEL_VALIDATION_*` variables and
proves ordinary `disposableValidation` remains unsigned. CI never runs
the dedicated signed-validation task.

The Checkpoint 19H witness contract is unchanged and remains
`NOT_READY`. `MISSING_INDEPENDENT_WITNESS_APPROVAL` remains an
unsatisfied 19H blocker. Repeated checks by the same operator are not
an independent witness.

## What 19R does not implement

- No private key, keystore, or certificate is generated.
- No APK or AAB is signed in this checkpoint.
- No certificate fingerprint, APK digest, key path, password, alias,
  or signed artifact is recorded in Git.
- `SENTINEL_RELEASE_*` and `SENTINEL_VALIDATION_*` stay unpopulated.
- `expectedCertificateSha256` remains null on the repository contract.
- The 19H independent-witness requirement is not removed or renamed.
- No trusted digest, device identity, trigger, or wipe is added.
- No cloned wipe-boundary test and no large runtime decision object.

## Machine-readable 19R decision

```text
CHECKPOINT_19R_VALIDATION_ONLY_SIGNING_PATH = YES
VALIDATION_SIGNING_PATH_PRESENT = true
VALIDATION_SIGNING_PERFORMED = false
ORDINARY_RELEASE_SIGNING = UNSIGNED
ORDINARY_DISPOSABLE_VALIDATION_SIGNING = UNSIGNED
PRODUCTION_SIGNING_PERFORMED = false
VALIDATION_KEY_SEPARATE_FROM_PRODUCTION = true
CUSTOMER_DEVICE_PRODUCTION_AUTHORIZED = false
TRUSTED_EXPECTATION_MINTED = false
CEREMONY_STATUS = NOT_READY
HARDWARE_ACTION_PERFORMED = false
CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
MERGE_PERFORMED = NO
DO NOT MERGE
```
