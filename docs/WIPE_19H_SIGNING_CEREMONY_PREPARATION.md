# Checkpoint 19H: signing-ceremony preparation

This checkpoint prepares and enforces the contract for a future offline
signing ceremony. It does not conduct that ceremony.

**PREPARATION IS NOT KEY GENERATION.**
**PREPARATION IS NOT CERTIFICATE APPROVAL.**
**PREPARATION IS NOT PRODUCTION SIGNING.**
**PREPARATION IS NOT SIGNED-CANDIDATE CREATION.**
**PREPARATION IS NOT TRUSTED ARTIFACT ENROLLMENT.**
**PREPARATION IS NOT RUNTIME AUTHORIZATION.**
**PREPARATION IS NOT HARDWARE-TEST APPROVAL.**
**PREPARATION IS NOT A PRODUCTION DISTRIBUTION.**
**THE REAL REPOSITORY STATE REMAINS NOT READY FOR SIGNING.**
**DO NOT MERGE this checkpoint as signing, artifact-identity, trigger,
or hardware-wipe authorization.**

Base SHA required to start from:

`ab959084ab425823d5c7c8a7e8ce1d1b832c3291`

(Checkpoint 19G on `cursor/checkpoint-19g-validation-build-purpose-provenance`).

This stacked change must not modify draft PR #29, draft PR #30, draft
PR #31, or draft PR #32.

Companion documents (still in force):

- `docs/WIPE_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE.md`
- `docs/WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md`
- `docs/WIPE_19E_INDEPENDENT_CI.md`
- `docs/WIPE_19D_REAL_CHAIN_ASSEMBLY.md`
- `docs/RELEASE_SECURITY.md`

The blank later-ceremony schema lives at
`docs/templates/DESTRUCTIVE_SIGNING_CEREMONY_RECORD.template.txt`.
It contains field names and placeholders only.

## What 19H implements

A build-only signing-ceremony preparation contract in `buildSrc`
defines the evidence required before a later signing ceremony may be
approved. It is completely separate from production runtime
authorization types.

The repository default source keeps every real ceremony value absent
or false. The build-only validator returns explicit fail-closed
blockers. The real evaluation is `ceremony_status=NOT_READY`.

`:app:checkDestructiveSigningCeremonyPreparation` succeeds only by
proving that the real repository state is still safely `NOT_READY`.
It writes only a gitignored local preparation report and deletes its
temporary directory.

The `disposableValidation` variant remains explicitly unsigned, even
if production-signing environment variables are populated. Normal
release signing behavior is unchanged. The repository contract still
contains `expectedCertificateSha256 = null`.

## What 19H does not implement

- No private key, keystore, or real signing certificate is generated
  or imported.
- No signing password is read.
- Gradle production signing is not enabled.
- No APK or AAB is signed.
- No certificate fingerprint is committed.
- `expectedCertificateSha256` remains null.
- No trusted artifact digest is recorded or minted.
- No device serial or real hardware identity is recorded.
- No production trigger is added.
- The destructive runtime remains unavailable.
- Checkout provenance still does not prove APK origin.
- A later signed validation candidate would still not be a production
  distribution and still could not mint runtime trust.

## Local proof

```text
:app:checkDestructiveSigningCeremonyPreparation
```

The task must prove:

```text
ceremony_status=NOT_READY
offline_key_generated=false
public_certificate_supplied=false
expected_certificate_recorded=false
operator_approval_available=false
witness_approval_available=false
key_custody_approved=false
recovery_backup_verified=false
branch_protection_required_check_verified=false
production_artifact_signed=false
runtime_authorization=false
trusted_expectation_minted=false
```

The report is gitignored and is never uploaded. No digest is printed
in normal CI lifecycle output. No filled ceremony record is written.

## Machine-readable 19H decision

```text
CHECKPOINT_19H_SIGNING_CEREMONY_PREPARATION = YES
19H_SIGNING_CEREMONY_CONTRACT_PRESENT = true
19H_SIGNING_CEREMONY_READY = false
19H_OFFLINE_KEY_GENERATED = false
19H_PUBLIC_CERTIFICATE_SUPPLIED = false
19H_EXPECTED_CERTIFICATE_RECORDED = false
19H_OPERATOR_APPROVAL_AVAILABLE = false
19H_WITNESS_APPROVAL_AVAILABLE = false
19H_KEY_CUSTODY_APPROVED = false
19H_RECOVERY_BACKUP_VERIFIED = false
19H_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED = false
19H_PRODUCTION_ARTIFACT_SIGNED = false
19H_SIGNED_VALIDATION_CANDIDATE_PRODUCED = false
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
