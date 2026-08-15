# Checkpoint 19J: audit-findings repair

This checkpoint repairs three LOW findings from the separate
read-only Checkpoint 19I aggregate audit. It does not enable
production signing, mint trust, add a trigger, or perform a wipe.

Checkpoint 19I itself is not a committed repository decision.

**ISOLATED TASK PATHS ARE NOT CANDIDATE ELIGIBILITY.**
**ENFORCED SNAPSHOT CLEANUP IS NOT TRUSTED-ARTIFACT ENROLLMENT.**
**AN UNSIGNED ORDINARY RELEASE IS NOT A PRODUCTION DISTRIBUTION.**
**POPULATED SIGNING VARIABLES ARE NOT A SIGNING CEREMONY.**
**AN EXPLICIT PRODUCTION-DISTRIBUTION REQUEST IS NOT A PERFORMED
SIGNING.**
**THE REAL REPOSITORY STATE REMAINS UNABLE TO COMPLETE A FACTORY
RESET.**
**DO NOT MERGE this checkpoint as signing, artifact-identity, trigger,
or hardware-wipe authorization.**

Base SHA required to start from:

`8572ad0a2bb84aa3de2a7ce8b0028ed5486dd842`

(Checkpoint 19H on `cursor/checkpoint-19h-signing-ceremony-preparation`).

This stacked change must not modify draft PR #29, draft PR #30, draft
PR #31, draft PR #32, or draft PR #33.

Companion documents (still in force):

- `docs/WIPE_19H_SIGNING_CEREMONY_PREPARATION.md`
- `docs/WIPE_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE.md`
- `docs/WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md`
- `docs/WIPE_19E_INDEPENDENT_CI.md`
- `docs/WIPE_19D_REAL_CHAIN_ASSEMBLY.md`
- `docs/RELEASE_SECURITY.md`

## What 19J repairs

1. Each candidate-evidence task now has a unique task-private snapshot
   directory and a unique report path. The 19F unsigned-proof report
   used by CI remains
   `app/build/reports/destructive-validation-candidate.txt`.
2. Every candidate-evidence task fails if its private snapshot remains
   after inspection, report writing, or inspection failure. Cleanup
   deletes only the exact task-private snapshot file and then the
   empty task-private directory.
3. Ordinary `assembleRelease` / `bundleRelease` remain unsigned unless
   production distribution is explicitly requested. Merely populating
   `SENTINEL_RELEASE_*` values does not attach a signing configuration.

## What 19J does not implement

- No private key, keystore, or real signing certificate is generated
  or imported.
- No APK or AAB is signed.
- No certificate fingerprint or trusted digest is recorded.
- `expectedCertificateSha256` remains null.
- No production trigger or confirmation source is added.
- Wipe scope and wipe options are unchanged.
- The 19H real ceremony state remains `NOT_READY`.
- The destructive runtime remains unavailable.

## Isolated candidate-evidence paths

```text
generateDestructiveValidationCandidateEvidence
  snapshot: app/build/tmp/destructive-validation-explicit-candidate-snapshot
  report:   app/build/reports/destructive-validation-explicit-candidate.txt

checkUnsignedDestructiveValidationCandidateEvidence
  snapshot: app/build/tmp/destructive-validation-unsigned-release-snapshot
  report:   app/build/reports/destructive-validation-candidate.txt

checkUnsignedDisposableValidationBuildPurposeEvidence
  snapshot: app/build/tmp/destructive-validation-disposable-purpose-snapshot
  report:   app/build/reports/destructive-validation-disposable-purpose.txt
```

The general generate task still requires
`sentinel.destructiveValidationCandidateApk`. The 19F and 19G proof
tasks still inspect only their AGP-provided APK artifacts. No
automatic trust or candidate selection is introduced.

## Machine-readable 19J decision

```text
CHECKPOINT_19J_AUDIT_FINDINGS_REPAIRED = YES
19J_CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED = true
19J_CANDIDATE_TASK_REPORT_PATHS_ISOLATED = true
19J_SNAPSHOT_CLEANUP_ENFORCED = true
19J_ORDINARY_RELEASE_REMAINS_UNSIGNED = true
19J_PRODUCTION_SIGNING_REQUIRES_EXPLICIT_DISTRIBUTION_REQUEST = true
19J_PRODUCTION_SIGNING_PERFORMED = false
19J_SIGNED_VALIDATION_CANDIDATE_PRODUCED = false
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
