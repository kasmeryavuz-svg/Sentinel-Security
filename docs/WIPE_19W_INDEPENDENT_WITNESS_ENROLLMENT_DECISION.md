# Checkpoint 19W: independent witness enrollment decision contract

## 19T proves cryptographic witness-statement mechanics

Checkpoint 19T can re-inspect a signed disposable-validation APK and
verify a `SHA256withRSA` witness statement. A valid signature proves
control of a key. It does not prove independence and does not enroll
anyone.

```text
valid_signature != independence
```

## 19U defines enrollment structure but enrolls nobody

Checkpoint 19U prepared a versioned enrollment-record schema and kept:

```text
repositoryEnrollments() = emptyList()
establishedWitnessIdentifiers() = emptySet()
witness_authority_enrolled=false
```

A well-formed record is a classification of a named key, not authority.

## 19V defines external independence evidence and review mechanics

Checkpoint 19V prepared external-evidence and independent-review
schemas. Format validity is not independence. A review decision of
`APPROVE_EVIDENCE_MECHANICS` is not enrollment.

```text
valid_external_evidence_format != independence
valid_review_format != enrollment
external_independence_evidence_verified != independent_witness_approval
```

## 19W combines those signals into a fail-closed enrollment decision

19W reads the 19T, 19U, and 19V mechanical flags as **inputs** and
derives:

```text
enrollment_candidate_mechanics_satisfied
witness_enrollment_decision
```

plus a stable, duplicate-free blocker list. No single earlier boolean
can bypass the full contract. The evaluator is pure and read-only. It
does not write enrollment sources, Gradle properties, environment
variables, or production state.

## Decision eligibility is not enrollment

```text
enrollment_candidate_mechanics_satisfied != witness_authority_enrolled
```

A synthetic fixture may set every mechanical input true. 19W still
records `NO_REPOSITORY_AUTHORITY_ENROLLMENT` and
`witness_enrollment_decision=BLOCKED` because the repository authority
sources remain empty.

## Enrollment is not runtime authorization

```text
witness_authority_enrolled != runtime_authorization
```

Even a later enrollment checkpoint would still have to mint trust,
hardware approval, and wipe authority separately. 19W mints none of
those.

## Current repository remains BLOCKED / NOT_ENROLLED

```text
repositoryEnrollments() = emptyList()
repositoryExternalWitnessEvidence() = emptyList()
repositoryWitnessReviews() = emptyList()
establishedWitnessIdentifiers() = emptySet()
witness_enrollment_decision=BLOCKED
witness_authority_enrolled=false
independent_witness_approval=false
```

CI proves that result without reading a signed APK, 19S receipt, 19T
report, 19U enrollment file, 19V evidence or review, certificate, or
private key.

## No hardware validation or wipe is authorized

```text
authority=UNTRUSTED_CANDIDATE_ONLY
runtime_authorization=false
trusted_expectation_minted=false
hardware_validation_approved=false
hardware_test_performed=false
decision_authorizes_hardware_test=false
decision_authorizes_wipe=false
```

## Remaining blocker before legitimate enrollment

Genuine external independence evidence and a genuine independent review,
neither of which is the current operator, CI, Cursor, or another file
from the same workstation, plus a later checkpoint that may populate
the empty 19U enrollment source only after that evidence is accepted.
19W does not populate that source.

```text
CHECKPOINT_19W_INDEPENDENT_WITNESS_ENROLLMENT_DECISION = YES
19W_WITNESS_ENROLLMENT_DECISION = BLOCKED
19W_ENROLLMENT_CANDIDATE_MECHANICS_SATISFIED = false
19U_WITNESS_AUTHORITY_ENROLLED = false
19T_INDEPENDENT_WITNESS_APPROVAL = false
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
NO NEW WIPE SCOPE ADDED
NO HARDWARE WIPE PERFORMED
DO NOT MERGE
NO REAL WITNESS ENROLLED
NO REAL EXTERNAL EVIDENCE RECORDED
NO REAL REVIEWER RECORDED
NO INDEPENDENT WITNESS APPROVAL
NO RUNTIME AUTHORIZATION
NO TRUSTED EXPECTATION MINTED
NO HARDWARE VALIDATION APPROVED
NO HARDWARE TEST PERFORMED
NO WIPE AUTHORIZED
```
