# Production / release security

Checkpoint 15 is **release hardening only**. It does not add Device Owner
capabilities, destructive DevicePolicyManager operations, remote command
infrastructure, or a real wipe.

GrapheneOS on supported Pixel hardware remains the production target. Stock
Android is a reference/test environment only.

## Production / release threat model

A future production APK/AAB can be installed as a fully-managed Device Owner
on a real device. The hardened release artifact must not:

- ship debuggable or test-only
- restore audit/state from cloud backup or device-to-device transfer as
  trusted authorization
- expose extra exported components, boot receivers, or deep links that can
  submit sensitive actions
- include an INTERNET control plane or cleartext network trust
- silently sign a “production” artifact with the Android debug key
- leak approval material, signing secrets, intent extras, or database
  contents through diagnostic logcat
- allow R8 to drop DeviceAdmin, provisioning, facade composition, or
  recovery types, or to keep a path that turns `MOCK_WIPE` into a real wipe

Existing Checkpoint 13/14 boundaries remain in force: no persisted approval,
no approval replay, no startup replay, no `BOOT_COMPLETED` execution, recovery
is read-only evidence, and audit records never authorize.

## Debug vs release

| Property | Debug | Release |
| --- | --- | --- |
| `debuggable` | true | **false** |
| R8 / minify | disabled | **enabled** |
| Resource shrinking | disabled | **enabled** |
| `testOnly` | not set | **must not be true** |
| `profileable` | absent | **absent** |
| Backup / extraction | disabled + exclude-all rules | same |
| Network | no INTERNET; cleartext denied | same |
| Signing | Android debug key | production key **only** when secrets are supplied; otherwise an **unsigned** local verification artifact that must not be distributed as production |

Debug remains a developer build. It is not a production distribution.

## R8 / minification

Release sets `isMinifyEnabled = true` and `isShrinkResources = true` with
`proguard-android-optimize.txt` plus `app/proguard-rules.pro`.

Keep rules are **minimum**:

- manifest-instantiated Application, activities, and `SentinelDeviceAdminReceiver`
- `DeviceManagement` / `DeviceManagementImplementation` composition linkage
- public `DeviceManagementServices`, `AppContainer`, submit-only controller
- read-only recovery and audit provider contracts
- `AndroidStructuredLogger` / `StructuredLogger`

Optimization and obfuscation stay enabled. Fail-safe `MOCK_WIPE` types are
intentionally **not** kept so R8 may strip them from the controlled production
call graph.

Post-R8 APK/AAB DEX gates prove the DeviceAdmin receiver, provisioning
activities, facade/implementation linkage, and recovery types still exist, and
that forbidden destructive API tokens are absent.

## Backup / data migration

- `android:allowBackup="false"`
- `android:fullBackupContent="@xml/backup_rules"` — exclude root, file,
  database, sharedpref, external, and the device-protected domains
  `device_root`, `device_file`, `device_database`, and
  `device_sharedpref` (legacy Auto Backup)
- `android:dataExtractionRules="@xml/data_extraction_rules"` — exclude the
  same credential-protected and device-protected domains from
  **cloud-backup** and **device-transfer** (API 31+)

Do not rely on `allowBackup=false` alone. Android 12+ device-to-device
transfer is independent of that flag. Sentinel audit SQLite
(`sentinel_audit.db`), preferences, and private files are not restorable
authorization state. There is no backup or export feature.

## Network policy

Sentinel has **no** production network control plane.

- `android.permission.INTERNET` is absent; the build fails if it appears
- Network Security Config denies cleartext (`cleartextTrafficPermitted="false"`)
- system CAs only; **no** `debug-overrides` and no user-CA trust
- `android:usesCleartextTraffic="false"`
- no HTTP clients, analytics, telemetry, crash-reporting, ads, or remote
  command SDKs

## Exported component policy

The only exported app activities are:

- `MainActivity` — launcher (`MAIN` / `LAUNCHER` only; no `VIEW`, no data URIs)
- `GetProvisioningModeActivity` — `GET_PROVISIONING_MODE`, protected by
  `BIND_DEVICE_ADMIN`
- `AdminPolicyComplianceActivity` — `ADMIN_POLICY_COMPLIANCE`, protected by
  `BIND_DEVICE_ADMIN`

The only exported receiver is `SentinelDeviceAdminReceiver`
(`DEVICE_ADMIN_ENABLED` and log-only `PROFILE_PROVISIONING_COMPLETE`).

Not present:

- `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `QUICKBOOT_POWERON`
- exported services
- any ContentProvider
- browsable / deep-link intent filters

`MainActivity` does not read extras, data, or foreign actions and cannot
submit a sensitive action from an incoming Intent.

## Signing boundary

**Do not commit a keystore, passwords, or local keystore paths.**

Production signing secrets, when used, come only from:

- environment variables, or
- gitignored `local.properties`

Required names:

- `SENTINEL_RELEASE_STORE_FILE`
- `SENTINEL_RELEASE_STORE_PASSWORD`
- `SENTINEL_RELEASE_KEY_ALIAS`
- `SENTINEL_RELEASE_KEY_PASSWORD`
- `SENTINEL_RELEASE_CERT_SHA256` — expected production signing certificate
  SHA-256 fingerprint (colons, spaces, and case are normalized)

Behavior:

- all four keystore secrets absent: `assembleRelease` / `bundleRelease`
  produce an **unsigned local verification** artifact. Release is never
  assigned the Android debug signing configuration. That artifact is
  **not** a production distribution.
- any but not all four keystore secrets present: configuration **fails
  closed**. There is no silent debug-key fallback.
- all four present: release uses the `production` signing config, never the
  debug signing config.
- a random developer or test certificate is **never** classified as
  production merely because it is not the Android debug certificate.

Classification is fail-closed:

- unsigned → `UNSIGNED`
- known Android debug/test certificate → `TEST_SIGNED`
- signed but no expected production fingerprint configured → `UNKNOWN`
- signed and fingerprint does not match the configured production
  identity → `TEST_SIGNED` or `UNKNOWN`, never `PRODUCTION_SIGNED`
- signed and fingerprint exactly matches the configured production
  fingerprint → `PRODUCTION_SIGNED` only after the artifact signature
  itself cryptographically verifies. For AABs this is JAR signature
  verification (`JarFile` verify=true). Certificate bytes or META-INF
  signature-file presence alone are never treated as a valid signature.
  A tampered archive that keeps the original signature block is
  `UNKNOWN`, never `PRODUCTION_SIGNED`.

Production distribution (`assembleProductionRelease` /
`bundleProductionRelease`) fails unless classification is exactly
`PRODUCTION_SIGNED`. Those tasks inspect the produced APK/AAB, require
the keystore secrets and `SENTINEL_RELEASE_CERT_SHA256`, and require a
usable `apksigner` or `apksigner.bat` for APK verification. A missing
verifier is not acceptable for a production distribution.

Tasks:

- `./gradlew :app:checkReleaseProductionSecurity` — classifies the local
  verification APK (`UNSIGNED` / `TEST_SIGNED` / `UNKNOWN` /
  `PRODUCTION_SIGNED`) into `app/build/reports/release-signing-boundary.txt`
- `./gradlew :app:checkReleaseBundleProductionSecurity` — same for the AAB
- `./gradlew :app:checkProductionDistributionSigning` — **fails** unless
  production secrets and the expected certificate fingerprint are present
- `./gradlew :app:assembleProductionRelease` — fail-closed production APK
  path; runs `checkReleaseProductionSecurity` with production distribution
  requested
- `./gradlew :app:bundleProductionRelease` — fail-closed production AAB path

This repository does **not** generate a production signing key and does
**not** hardcode a production certificate fingerprint.

## Logging / information disclosure

Production logcat goes through `AndroidStructuredLogger` and
`ProductionLogSanitizer`. Sensitive keys (passwords, keystore material,
approval/token fields, intent extras, SQL/database dumps) are redacted.
Errors record `exception_class` only; they do not dump stack traces or raw
throwables to logcat.

Provisioning and DeviceAdmin `Log` calls use fixed event strings. They do
not print raw extras.

The durable audit SQLite trail is **unchanged** and remains evidence, not
authorization. It is not written through logcat sanitization.

## Audit limitations

Ordinary app-private SQLite is not cryptographically tamper-proof. Checkpoint
15 does **not** add:

- cryptographic tamper evidence
- anti-rollback audit
- hardware-backed audit
- remote archive

See `docs/AUDIT.md`.

## Lifecycle / recovery guarantees

Unchanged from Checkpoint 14. See `docs/LIFECYCLE.md`.

- no persisted approval
- no approval replay
- no startup or reboot execution
- recovery inspection is read-only
- audit is never an authorization source

## MOCK_WIPE limitations

`SafeMockWipeAction` is fail-safe **simulation only**. It logs
`WIPE WOULD EXECUTE` and returns `ActionResult.Simulated`. It does not call
DevicePolicyManager.

The controlled production registry never contains `MOCK_WIPE`. Submitting
`mock_wipe` through the controlled controller is rejected. Production
composition never calls `createFailSafeController`.

## Exact DevicePolicyManager mutator allowlist

The only allowed DevicePolicyManager mutators remain exactly:

- `setScreenCaptureDisabled`
- `setCameraDisabled`
- `setStatusBarDisabled`

## Exact DeviceAdmin policy

DeviceAdmin metadata remains exactly:

- `disable-camera`

## What Checkpoint 15 does **not** implement

- real wipe, `wipeData`, `wipeDevice`, factory reset
- `resetPassword`, `lockNow`, reboot policy execution
- keyguard disabling, package install/uninstall, account or user removal
- new DPM mutators of any kind
- remote command / network control plane
- production keystore generation
- cryptographic, anti-rollback, hardware-backed, or remote audit
- backup/export functionality

Future destructive-operation work remains **explicitly deferred**.
Checkpoint 17A adds only non-destructive simulation machinery and keeps
this release boundary frozen. Checkpoint 17B defines machine-enforced
requirements that must exist before a future destructive build can
exist, and keeps them **disabled**:

- exact production certificate verification remains the Checkpoint 15
  `PRODUCTION_SIGNED` gate; destructive production signing is not enabled
- exact APK/artifact hash recording for disposable-device validation is
  not recorded
- production vs local-test signing separation is unchanged
- debug/test builds cannot gain wipe capability; DEX denylist still
  rejects `wipeData` / `wipeDevice`
- disposable-device-only destructive validation is not approved
- explicit human approval is not recorded
- ordinary `assembleDebug` / `assembleRelease` / `bundleRelease` remain
  incapable of wiping a device

See `docs/WIPE_17A_PREFLIGHT.md` and `docs/WIPE_17B_ENTRY_REVIEW.md`.
