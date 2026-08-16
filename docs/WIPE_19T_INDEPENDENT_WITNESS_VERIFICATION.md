# Checkpoint 19T: independent witness verification contract

## Scope

Checkpoint 19T adds a fail-closed verification contract and local tooling
for a future genuinely independent witness statement. It does not treat
the current operator, the current checkout, the Checkpoint 19S receipt,
GitHub Actions, or another locally generated file as an independent
witness.

19T does not generate a key, sign an APK, mint a trusted expectation,
approve hardware, add a trigger, or execute a wipe.

## What 19T verifies

When explicit local inputs are supplied, the verification task:

1. re-inspects the signed disposable-validation APK through the existing
   immutable-snapshot envelope;
2. independently hashes the supplied validation public certificate;
3. parses the existing write-once 19S receipt without modifying it;
4. requires the APK digest, certificate digest, single reliable V2/V3
   signer, and repository identity contract to match;
5. parses a versioned witness statement, if supplied, and verifies a
   standard `SHA256withRSA` signature over documented canonical bytes;
6. compares that statement to the independently re-observed evidence.

The 19S receipt is evidence that a local receipt was recorded. It is not
trusted by itself and is not an independent witness.

## What a digital signature proves

A valid `SHA256withRSA` signature over the canonical statement bytes
proves that the corresponding private key produced that signature.

## What a digital signature does not prove

A valid signature does not prove that the key holder is organizationally
or personally independent of the operator who signed the APK, recorded
the 19S receipt, or ran verification. It does not prove that CI, a
checkout SHA, or a local file is an independent human witness. It does
not mint runtime trust or authorize hardware or wipe.

## Why cryptographic verification and witness independence are separate

19T keeps these states distinct:

```text
witness_statement_present
witness_signature_verified
witness_evidence_matches_candidate
witness_independence_established
independent_witness_approval
```

Approval requires all of: a present statement, a verified signature,
matching candidate evidence, **and** an independently established
witness authority in the repository contract. This repository currently
recognizes no such authority:

```text
establishedWitnessIdentifiers() = {}
witness_independence_established=false
independent_witness_approval=false
```

Therefore `valid_signature != independent_witness_approval`.

## Why the 19S receipt is not itself an independent witness

The receipt is written by the same local operator path that already
accepted the untrusted signed candidate. It is gitignored, local-only,
and labeled `local_receipt_is_independent_witness=false`. 19T may only
read and verify it. It never overwrites, reformats, or uploads that
file.

## Why CI is not automatically an independent witness

Independent CI proves that the 19T implementation stays fail-closed
using synthetic fixtures and a contract-check task. CI does not receive
the real signed APK, 19S receipt, validation private key, witness
private key, real witness statement, or local verification report. A
green CI job is not a human witness.

## Why 19T still gives zero runtime, hardware, or wipe authority

Verification is build-only. The candidate remains
`authority=UNTRUSTED_CANDIDATE_ONLY`. The repository contract still has
`expectedCertificateSha256 = null`. No trusted digest, device identity,
Device Owner provisioning, ADB/emulator action, production trigger, or
wipe authorization is added.

## Canonical signed bytes

The signed payload is the UTF-8 encoding of these lines in this exact
order, each terminated by a single LF (`U+000A`), with no BOM and no
signature fields:

```text
checkpoint
statement_version
candidate_apk_sha256
validation_certificate_sha256
source_head_claimed
package_name
admin_component
policies
min_sdk
target_sdk
build_purpose
witness_identifier
witness_timestamp_utc
```

`signature_algorithm` must be `SHA256withRSA`. `signature` is unwrapped
standard Base64 of the raw JCA signature bytes. Unknown, duplicate, or
trailing conflicting fields are rejected.

## Exact local-only artifacts

```text
local/signed-validation-candidate-receipt.txt
local/independent-witness-verification.txt
```

Both are gitignored. 19T never writes the 19S receipt. The 19T report
contains no digest values. Task-private snapshots live under
`app/build/tmp/independent-witness-verification-snapshot` and must be
deleted after verification.

Explicit verification inputs, never auto-discovered:

```text
sentinel.signedValidationCandidateApk
sentinel.validationPublicCertificate
sentinel.signedValidationReceipt
sentinel.signedValidationSourceHead
sentinel.independentWitnessStatement
sentinel.witnessVerificationCertificate
```

The statement and witness public key are optional as a pair. Incomplete
pairs fail closed. 19T never reads a witness private key.

CI-safe contract task:

```text
:app:checkIndependentWitnessVerificationContract
```

Local verification task, not run in CI:

```text
:app:verifyIndependentWitnessStatement
```

## Exact remaining blockers after 19T

```text
CHECKPOINT_19T_INDEPENDENT_WITNESS_VERIFICATION = YES
19T_WITNESS_STATEMENT_FORMAT_PRESENT = true
19T_VERIFICATION_TASK_PRESENT = true
19T_INDEPENDENT_AUTHORITY_ENROLLED = false
19T_WITNESS_INDEPENDENCE_ESTABLISHED = false
19T_INDEPENDENT_WITNESS_APPROVAL = false
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

The remaining blocker for `independent_witness_approval=true` is an
independently established witness-authority enrollment that is not the
current operator, not CI, and not the 19S receipt. 19T does not create
that enrollment.

DO NOT MERGE
NO RUNTIME AUTHORIZATION
NO TRUSTED EXPECTATION MINTED
NO HARDWARE VALIDATION APPROVED
NO HARDWARE TEST PERFORMED
NO WIPE AUTHORIZED
