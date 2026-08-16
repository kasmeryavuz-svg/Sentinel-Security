# Checkpoint 19S: signed-validation local receipt preparation

## Scope

Checkpoint 19S adds one build-only, local-only receipt task for the
validation-signed `disposableValidation` APK produced in Checkpoint 19R.
It does not sign an APK, read a keystore or password, upload an artifact,
mint a trusted expectation, approve hardware, add a trigger, or execute a
wipe.

The real Windows validation-only build was observed at 19R head
`ea0c97e4c763a042c69e1e3a75c980f6e7b68fba` with:

```text
BUILD SUCCESSFUL
signed_validation_candidate_accepted=true
build_purpose_observed=DISPOSABLE_DEVICE_VALIDATION
package_matches=true
admin_matches=true
policies_match=true
min_sdk_matches=true
target_sdk_matches=true
```

That observation remains local. Independent CI did not receive the APK,
certificate, receipt, key, or password.

## Local receipt task

Task:

```text
:app:recordSignedDisposableValidationCandidateReceipt
```

Required explicit inputs:

```text
sentinel.signedValidationCandidateApk
sentinel.validationPublicCertificate
sentinel.signedValidationSourceHead
```

The task:

1. accepts only an explicitly supplied signed `disposableValidation` APK;
2. hashes an independently supplied public certificate file without reading
   the validation keystore;
3. reuses the immutable snapshot, signer, V2/V3, build-purpose, package,
   admin, DeviceAdmin policy, and SDK checks from 19R;
4. requires the observed signer to match that public certificate;
5. records the APK and certificate SHA-256 values only in
   `local/signed-validation-candidate-receipt.txt`;
6. writes the receipt once and refuses to overwrite it;
7. deletes and verifies deletion of its task-private snapshot.

The `local/` receipt is gitignored. CI proves it and the receipt snapshot are
absent. CI never runs the receipt task and never maps validation-signing
inputs.

## Authority boundary

The receipt is `UNTRUSTED_CANDIDATE_ONLY`. Its checkout SHA is an explicit
claim and does not prove APK origin. The receipt is not an independent
witness, trusted artifact expectation, production distribution, customer
device authorization, hardware-test approval, or wipe authorization.

The repository still has:

```text
expectedCertificateSha256 = null
TrustedDestructiveArtifactValidationSource.trustedExpectation() = null
TrustedPerAttemptDestructiveConfirmationRecord.current() = null
```

## Local invocation after this branch is checked out

The command uses the existing local signed APK and exported public
certificate. It does not request the keystore password and does not sign
again.

```powershell
$sourceHead="ea0c97e4c763a042c69e1e3a75c980f6e7b68fba"; $apk=@(Get-ChildItem "app\build\outputs\apk\disposableValidation" -Filter "*.apk" -Recurse | Where-Object { $_.Name -notmatch "unsigned" }) | Select-Object -First 1; .\gradlew.bat --no-daemon :app:recordSignedDisposableValidationCandidateReceipt "-Psentinel.signedValidationCandidateApk=$($apk.FullName)" "-Psentinel.validationPublicCertificate=$env:USERPROFILE\SentinelValidationKey\sentinel-validation-only.cer" "-Psentinel.signedValidationSourceHead=$sourceHead"
```

Repository state before that local invocation:

```text
CHECKPOINT_19S_SIGNED_VALIDATION_LOCAL_RECEIPT_PREPARATION = YES
19S_LOCAL_RECEIPT_TASK_PRESENT = true
19S_LOCAL_RECEIPT_RECORDED = false
19S_RECEIPT_GITIGNORED = true
19S_RECEIPT_IS_INDEPENDENT_WITNESS = false
19S_RECEIPT_AUTHORIZES_HARDWARE_TEST = false
19S_RECEIPT_AUTHORIZES_WIPE = false
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
