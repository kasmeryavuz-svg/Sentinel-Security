# Checkpoint 19U: witness-authority enrollment preparation

## Why 19T did not establish independence

Checkpoint 19T can re-inspect a signed disposable-validation APK, read
the 19S receipt, and verify a standard `SHA256withRSA` witness
statement. A valid signature proves control of a key. It does not prove
that the key holder is organizationally or personally independent of the
operator, CI, Cursor, or the workstation that produced the APK and
receipt.

19T therefore kept:

```text
establishedWitnessIdentifiers() = emptySet()
witness_independence_established=false
independent_witness_approval=false
```

## What 19U prepares

19U adds a fail-closed enrollment schema, parser, evaluator, empty
repository source, and CI proof for a **future** genuine independent
witness. It does not enroll anyone.

A future enrollment record must bind:

```text
checkpoint
enrollment_version
witness_identifier
witness_display_name
witness_verification_key_sha256
witness_role
independence_basis
enrollment_timestamp_utc
enrollment_repository_revision
operator_identifier
review_identifier
```

Supported independence classifications:

```text
SEPARATE_NATURAL_PERSON
SEPARATE_ORGANIZATION
EXTERNAL_SECURITY_REVIEWER
```

A classification string in a file is not evidence. Software cannot prove
social or organizational independence from a self-asserted value.

## What qualifies conceptually as an independent witness

An independent witness is a separate natural person or organization,
with a review outside the operator's own statement, whose public
verification key is bound only after those external facts exist. The
current operator, Cursor, GitHub Actions, a GitHub username, a locally
generated certificate, the 19S receipt, and the 19T report do not
qualify.

## Key ownership versus independence

A SHA-256 fingerprint of public-key or certificate bytes can later name
an expected cryptographic identity. It never names an independent
person. `valid_signature != independent_witness_approval` remains true.

## Why the repository remains NOT_ENROLLED

```kotlin
fun repositoryEnrollments(): List<Record> = emptyList()
fun repositoryAcceptsEnrollment(): Boolean = false
fun establishedWitnessIdentifiers(): Set<String> = emptySet()
```

19U does not populate those collections. CI proves
`witness_authority_enrollment_status=NOT_ENROLLED` and
`established_witness_count=0` without reading a signed APK, receipt,
statement, or key.

## Why no real witness is added in this checkpoint

No independently established person, organization, or review record
exists in this repository. Inventing a name, generating a key, or
copying a fingerprint would fabricate independence evidence.

## What evidence a future enrollment requires

All of the following, none of which 19U supplies:

1. a well-formed versioned enrollment record;
2. a witness identifier that is not reserved for CI, operator, Cursor,
   the 19S receipt, or the 19T report;
3. operator and witness identifiers that differ;
4. a SHA-256 fingerprint of stable public verification-key bytes;
5. independence evidence **outside** the enrollment file and witness
   statement;
6. an independent review identifier distinct from operator and witness;
7. a repository revision binding that a later checkpoint can accept;
8. an explicit later decision to set `repositoryAcceptsEnrollment()`.

## Why CI and the current operator cannot self-enroll

CI is an automated check, not a human witness. The local operator is
the party who already signed or recorded the candidate. Reserved
identifiers `ci`, `github-actions`, `cursor`, `local-operator`,
`19s-receipt`, and `19t-report` are rejected.

## Remaining blocker before real witness enrollment

External independence and review evidence that is not the current
operator, not CI, not Cursor, and not another file from the same
workstation, plus a later checkpoint that may populate the empty
enrollment source only after that evidence exists.

## Why hardware and wipe remain unauthorized

Enrollment preparation is build-only. The candidate remains
`authority=UNTRUSTED_CANDIDATE_ONLY`. No trusted digest, device
identity, Device Owner provisioning, ADB/emulator action, production
trigger, or wipe authorization is added.

```text
CHECKPOINT_19U_WITNESS_AUTHORITY_ENROLLMENT_PREPARATION = YES
19U_ENROLLMENT_SCHEMA_PRESENT = true
19U_REPOSITORY_ENROLLMENT_PRESENT = false
19U_WITNESS_AUTHORITY_ENROLLED = false
19U_WITNESS_INDEPENDENCE_ESTABLISHED = false
19U_INDEPENDENT_WITNESS_APPROVAL = false
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
NO NEW WIPE SCOPE ADDED
NO HARDWARE WIPE PERFORMED
DO NOT MERGE
NO REAL WITNESS ENROLLED
NO INDEPENDENT WITNESS APPROVAL
NO RUNTIME AUTHORIZATION
NO TRUSTED EXPECTATION MINTED
NO HARDWARE VALIDATION APPROVED
NO HARDWARE TEST PERFORMED
NO WIPE AUTHORIZED
```
