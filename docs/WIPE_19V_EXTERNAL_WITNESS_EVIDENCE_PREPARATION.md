# Checkpoint 19V: external witness evidence verification preparation

## What 19V adds

19V defines a fail-closed contract for the **structure** of external
independence evidence and of an independent review of that evidence. A
future checkpoint would need both before considering a real independent
witness enrollment. 19V adds versioned schemas, parsers, a deterministic
freshness evaluator, empty repository sources, and a CI proof that those
sources remain empty.

19V does not enroll a witness.

## Why 19U remained NOT_ENROLLED

19U prepared an enrollment **record** schema. A well-formed record is
still only a classification of a named key. 19U kept:

```text
repositoryEnrollments() = emptyList()
establishedWitnessIdentifiers() = emptySet()
witness_authority_enrolled=false
```

because no external evidence existed. 19V does not populate those
collections either. Enrollment remains a later, explicit decision.

## Enrollment record versus evidence supporting enrollment

An enrollment record (19U) would later bind a witness identifier to a
public verification-key fingerprint and a self-asserted independence
classification. External evidence (19V) is a separate artifact that
would have to exist **outside** that record: an attestation of identity,
role, or organizational separation, issued by someone other than the
witness, bound to a repository revision, and independently reviewed.

A filled 19U file is not 19V evidence. 19V evidence is not an enrollment.

## External evidence versus independent review

External evidence is the issuer's attestation about the witness.
Independent review is a second person's decision that those evidence
mechanics are well-formed and bound. 19V models them as two schemas:

```text
IndependentWitnessExternalEvidence
IndependentWitnessEnrollmentReview
```

Review decision `APPROVE_EVIDENCE_MECHANICS` is not enrollment and is
not runtime authorization.

## Why self-asserted independence is insufficient

The witness, the local operator, CI, Cursor, the 19S receipt, and the
19T report cannot attest their own independence. Reserved identifiers
from 19U are reused so those aliases cannot appear as witness, issuer,
or reviewer. The closed `evidence_type` enumeration is a label only:

```text
valid_external_evidence_format != independence
```

Software cannot prove social or organizational independence from an
enum value, a digest match, or a valid signature.

## Why a mechanically valid review is not runtime approval

A review may reach `APPROVE_EVIDENCE_MECHANICS` in a synthetic fixture
when identifiers differ, the review binds the evidence, and an injected
clock finds the evidence unexpired. That still leaves:

```text
witness_authority_enrolled=false
independent_witness_approval=false
runtime_authorization=false
evidence_authorizes_wipe=false
```

```text
valid_review_format != enrollment
external_independence_evidence_verified != independent_witness_approval
```

A later checkpoint must explicitly enroll after legitimate human
evidence exists. 19V only prepares verification mechanics.

## Why repository evidence sources remain empty

```kotlin
fun repositoryExternalWitnessEvidence(): List<...> = emptyList()
fun repositoryWitnessReviews(): List<...> = emptyList()
```

No independently established issuer, reviewer, or evidence payload
exists in this repository. Inventing names or timestamps would
fabricate independence evidence. CI proves both collections are empty
without reading a signed APK, 19S receipt, 19T report, 19U enrollment
file, or any private key.

## Remaining blocker before a future real enrollment checkpoint

Genuine external independence evidence and a genuine independent review,
neither of which is the current operator, CI, Cursor, or another file
from the same workstation, plus a later checkpoint that may populate
the empty 19U enrollment source only after that evidence is accepted.

## Why hardware and wipe remain unauthorized

Evidence-verification preparation is build-only. The candidate remains
`authority=UNTRUSTED_CANDIDATE_ONLY`. No trusted digest, device
identity, Device Owner provisioning, ADB/emulator action, production
trigger, or wipe authorization is added.

```text
CHECKPOINT_19V_EXTERNAL_WITNESS_EVIDENCE_PREPARATION = YES
19V_EXTERNAL_EVIDENCE_PRESENT = false
19V_REVIEW_ATTESTATION_PRESENT = false
19V_EXTERNAL_INDEPENDENCE_EVIDENCE_VERIFIED = false
19U_WITNESS_AUTHORITY_ENROLLED = false
19U_WITNESS_INDEPENDENCE_ESTABLISHED = false
19T_INDEPENDENT_WITNESS_APPROVAL = false
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
NO NEW WIPE SCOPE ADDED
NO HARDWARE WIPE PERFORMED
DO NOT MERGE
NO REAL EXTERNAL WITNESS EVIDENCE RECORDED
NO REAL REVIEWER RECORDED
NO REAL WITNESS ENROLLED
NO INDEPENDENT WITNESS APPROVAL
NO RUNTIME AUTHORIZATION
NO TRUSTED EXPECTATION MINTED
NO HARDWARE VALIDATION APPROVED
NO HARDWARE TEST PERFORMED
NO WIPE AUTHORIZED
```
