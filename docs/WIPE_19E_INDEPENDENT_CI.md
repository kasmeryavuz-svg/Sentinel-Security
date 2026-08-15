# Checkpoint 19E: independent GitHub CI

Checkpoint 19E adds **independent GitHub Actions verification** of the
existing safety and build suite. It is non-destructive infrastructure
only.

It does **not** observe a GitHub run merely because the workflow file
exists.
It does **not** configure branch protection.
It does **not** enable production signing.
It does **not** record a real APK SHA-256 or certificate SHA-256.
It does **not** record a real device serial.
It does **not** add a production trigger.
It does **not** perform a destructive hardware wipe or test.
It does **not** verify GrapheneOS wipe behavior.

**THE INDEPENDENT CI WORKFLOW IS PRESENT.**
**LOCAL VALIDATION IS RECORDED SEPARATELY FROM ANY GITHUB RUN.**
**AN OBSERVED GITHUB RUN IS NOT RECORDED IN THIS DECISION.**
**BRANCH PROTECTION IS NOT CONFIGURED.**
**RUNTIME DESTRUCTIVE AVAILABILITY REMAINS FALSE.**
**NO FACTORY RESET CAN COMPLETE UNDER CURRENT REPOSITORY STATE.**
**UNSIGNED RELEASE OUTPUTS ARE NOT DISTRIBUTABLE PRODUCTION SOFTWARE.**
**DO NOT MERGE this checkpoint as signing, artifact-identity, trigger,
or hardware-wipe authorization.**

Base SHA required to start from:

`cc0cb2c012ca11d48333f39a0b4bbd97e3fc2ac6`

(Checkpoint 19D on `cursor/checkpoint-19d-real-chain-assembly`).

This stacked change must not modify draft PR #29.

Companion documents (still in force except where this checkpoint
explicitly updates live repository-reality flags):

- `docs/WIPE_19D_REAL_CHAIN_ASSEMBLY.md` — 19D assembly snapshot
- `docs/WIPE_19C_HARDWARE_VALIDATION_READINESS.md` — 19C readiness snapshot
- `docs/WIPE_19B_DESTRUCTIVE_IMPLEMENTATION.md` — A–D implementation snapshot
- `docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md` — architecture decision
- `docs/RELEASE_SECURITY.md` — production signing boundary

## Separate states

These are **not** the same decision. They must **never** be inferred
from each other. Checkpoint 19E implements **state 1** (workflow
present) and records **local validation** from actual execution. It
does not implement states 2–8 below.

### 1. CI workflow present

Whether the repository contains the independent GitHub Actions
verification workflow that executes the complete previously local
safety/build suite.

```text
19E_INDEPENDENT_CI_WORKFLOW_PRESENT = true
```

**This is the only GitHub-side infrastructure state Checkpoint 19E
implements.**

true means `.github/workflows/checkpoint-19e-independent-ci.yml`
exists, uses `pull_request` (never `pull_request_target`), optional
`workflow_dispatch`, `permissions: contents: read`, pinned official
actions, Gradle wrapper validation, the required JDK 17 and Android
SDK packages (`platforms;android-36`, `build-tools;35.0.0`), and the
complete Gradle verification task list. It does **not** mean GitHub
has executed that workflow.

### Local validation (not a GitHub run)

Whether this agent actually executed the same suite locally before
push.

```text
19E_LOCAL_VALIDATION_PASSED = true
```

**Passed locally.** The complete required Gradle suite was executed in
this workspace and succeeded, including unsigned
`assembleRelease` / `bundleRelease` classification. Local success is
not a GitHub run, not branch protection, and not production signing.

### 2. Actual GitHub CI run observed

Whether a specific GitHub Actions run URL and conclusion have been
recorded as a repository fact.

```text
19E_GITHUB_CI_RUN_OBSERVED = false
```

**Not recorded.** Presence of the workflow file is not an observed
run. Opening a pull request is not an observed run. This flag stays
false until a later explicit record of a run URL and conclusion is
approved. Any run URL collected after opening the stacked PR is
reported separately and does not flip this flag by itself.

### 3. Branch-protection required check configured

Whether GitHub branch protection requires this workflow as a required
status check.

```text
19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false
```

**Not configured.** A green run does not configure protection.

### 4. Production signing enabled

Whether destructive/production distribution signing is enabled.

```text
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
```

**Not enabled.** `assembleRelease` / `bundleRelease` may produce
temporary unsigned verification outputs inside a runner or local
workspace. `checkReleaseProductionSecurity` and
`checkReleaseBundleProductionSecurity` must classify those outputs as
`UNSIGNED`. `checkProductionDistributionSigning` is intentionally not
invoked because production signing is unavailable. Unsigned success is
not distributable production software.

### 5. Hardware-validation preparation ready

Whether Checkpoint 19C hardware-validation preparation is ready.

```text
HARDWARE_VALIDATION_PREPARATION_READY = false
```

**Not ready.** CI does not prepare a disposable device, record a
serial, or acknowledge factory-reset consequences.

### 6. Hardware-test approval granted

Whether a human approved a destructive hardware test.

```text
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
HARDWARE_TEST_APPROVAL_GRANTED = false
```

**Not granted.**

### 7. Hardware test performed

Whether a destructive hardware test was performed.

```text
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
```

**Not performed.** This workflow must not start an emulator, talk to a
physical device, run a connected-device command, provision Device
Owner, or invoke a destructive API.

### 8. GrapheneOS behavior verified

Whether GrapheneOS factory-reset behavior was verified on hardware.

```text
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
```

**Not verified.**

## Required Gradle verification tasks

The workflow must keep executing this exact suite:

```text
:buildSrc:test
test
checkProductionBytecodePolicy
:app:checkAppApiCompileNegative
:app:checkAppDependencyIsolation
:app:checkDebugEffectiveDeviceAdminMetadata
:app:checkReleaseEffectiveDeviceAdminMetadata
:app:checkDebugProductionBytecodePolicy
:app:checkReleaseProductionBytecodePolicy
assembleDebug
assembleRelease
bundleRelease
checkReleaseProductionSecurity
checkReleaseBundleProductionSecurity
:sensitive-actions:test
:sensitive-actions:checkMainProductionBytecodePolicy
```

Ordinary `assembleDebug` / `assembleRelease` remain incapable of
completing a factory reset.

Forbidden in this workflow:

- `checkProductionDistributionSigning`
- `assembleProductionRelease`
- `bundleProductionRelease`
- connected-device / virtual-device / instrumentation suites

## Workflow security boundary

- trigger: `pull_request` only, plus optional `workflow_dispatch`
- never `pull_request_target`
- no scheduled, boot, deployment, release, repository-dispatch, or
  other external trigger
- explicit permissions: `contents: read` only; no write, packages,
  deployments, issues, pull-requests, actions, or OIDC grant
- no production-signing secret mapping
- no keystore generation
- no Android debug key used as production signing
- no upload of APK, AAB, signing files, databases, or device
  identifiers
- runner workspace only; delete nothing outside it
- job timeout and concurrency cancellation for superseded PR runs
- do not print environment variables or secret-like configuration

## Supply chain

- official GitHub (`actions/*`) and Gradle (`gradle/actions/*`)
  actions only
- every `uses:` pin is a complete 40-character commit SHA
- the corresponding human-readable tag is documented in a comment
- Gradle wrapper validation runs before `./gradlew`
- the repository wrapper is the only Gradle used
- JDK 17, compile SDK `android-36`, build-tools `35.0.0`
- install only the SDK platform and build-tools packages required by
  that configuration

## Runtime availability

```text
REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
```

Independent CI does not assemble, authorize, or execute the
production real chain. Checkpoint 19D remain fail-closed.

## Wipe boundary

Unchanged:

- exactly one production whole-device call with literal flags `0`
- origin `AndroidDevicePolicyFactoryResetService`
- no extra wipe flags
- DeviceAdmin metadata remains exactly `disable-camera` + `wipe-data`
- no additional DeviceAdmin policy

## No trigger

This checkpoint adds GitHub verification only. There is still no:

- UI button
- command / registry action
- public facade method
- AppContainer destructive authority
- Intent / BroadcastReceiver / boot / notification / accessibility /
  connected-device / deep-link trigger
- scheduled worker/job
- async destructive queue
- persisted/resumable positive authority
- production artifact expectation
- production confirmation record
- approved build revision
- real device serial

## Verdict

```text
19E_INDEPENDENT_CI_WORKFLOW_PRESENT = true
19E_LOCAL_VALIDATION_PASSED = true
19E_GITHUB_CI_RUN_OBSERVED = false
19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false
REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO
```

**NO NEW WIPE SCOPE ADDED**
**NO HARDWARE WIPE PERFORMED**
**DO NOT MERGE**
