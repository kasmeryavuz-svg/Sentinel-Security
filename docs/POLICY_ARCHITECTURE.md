# Controlled policy architecture

## Trust boundaries

Sentinel exposes one mutation entry point: `SensitiveActionController.submit`.
The UI can submit untrusted trigger fields, but it cannot construct decisions,
approvals, actions, registries, executors, policy writers, or device-management
backends.

```text
untrusted UI trigger
  -> SensitiveActionController (creates authoritative correlation ID)
  -> immutable SensitiveActionRegistry / TriggerEvaluator
  -> DecisionEngine
  -> identity-bound ApprovalAuthority
  -> ActionExecutor (consumes approval once)
  -> typed ScreenCapturePolicyAction, CameraPolicyAction, or StatusBarPolicyAction
  -> typed SensitiveActionPolicyBackend method
  -> typed ScreenCapturePolicy, CameraPolicy, or StatusBarPolicy
  -> closed VerifiedPolicyMutation
  -> capability-specific DevicePolicyManager service
  -> DevicePolicyManager
```

The controlled registry is fixed in source and contains exactly six commands:
enable/disable screen capture, enable/disable camera, and enable/disable status
bar. The `MOCK_WIPE`
simulation exists only in the separate fail-safe registry. Registries are
immutable, reject duplicate commands and action types, and expose no runtime
registration API.

## Presentation dashboard

The Android UI is a presentation layer only. It reads Device Owner,
management, provisioning, and policy status through the public read-only
providers and submits the six trusted commands through
`SensitiveActionController`. It does not receive or construct
`DevicePolicyManager`, policy writers, mutation executors, approval
authorities, action executors, policy backends, or authorization clocks.

Session activity shown on the dashboard is **NON-PERSISTENT** in-memory
history for the current process. It is cleared when the app restarts and
is not durable audit storage. `StructuredLogger` remains a logging
abstraction and is not an audit subsystem.

The app has one direct project dependency: the `device-management` facade.
That facade exposes contracts from `device-management-api` and
`sensitive-actions-api`, while depending on `device-management-impl` with
Gradle `implementation`. The implementation artifact in turn owns the Android
policy infrastructure and has an implementation-only dependency on
`sensitive-actions`. Neither implementation artifact is present on any app
compile classpath; both remain on the runtime classpath for Android packaging.
The facade returns an already-composed `DeviceManagementServices` interface.
Its only mutation surface is the submit-only `SensitiveActionController`.

## Approval lifecycle

The controller creates an authoritative correlation ID inside the trusted
pipeline. `Trigger.requestId` is caller-controlled diagnostic input only.
Approval identity does not depend on either identifier.

The decision engine validates the trigger and current authorization state, then
asks its paired `ApprovalAuthority` to issue an identity-bound capability. The
authority retains the authoritative action request. The executor consumes that
capability exactly once and rejects forged, foreign, replayed, expired, or
monotonically stale approvals.

## Final mutation authorization

Decision-time authorization is not cached for mutation. For every real write:

1. The executor consumes a valid approval.
2. The typed policy obtains its required service reference.
3. Device Owner, expected-admin, active-admin, and consistency validation is
   obtained again.
4. Any denial, uncertainty, or exception stops execution.
5. The capability-specific setter is called.
6. Its matching getter is called immediately.
7. Success is returned only when observed state equals requested state.

`VerifiedPolicyMutation` is a sealed allowlist with only `ScreenCapture` and
`Camera` variants. Its exhaustive dispatch contains explicit typed setter and
getter calls. It accepts no callbacks, lambdas, method names, reflection, or
generic argument maps.

## DevicePolicyManager and metadata allowlists

The only permitted `DevicePolicyManager` mutators are:

- `setScreenCaptureDisabled`, verified by
  `getScreenCaptureDisabled(expectedAdmin)`;
- `setCameraDisabled`, verified by `getCameraDisabled(expectedAdmin)`;
- `setStatusBarDisabled`, verified by `isStatusBarDisabled()` on API 34+.

Direct `DevicePolicyManager` access is confined to five explicitly named classes
compiled from `AndroidDeviceManagementInfrastructure.kt` in
`device-management-impl`. An Android Components guard consumes the final project
class artifacts for every discovered production variant. JVM production source
sets are also registered dynamically, and a repository coverage task fails when
a production module has no compiled-output guard. ASM verification resolves
actual class, method, field, descriptor, and method-handle owners; source names,
imports, aliases, filenames, and token spelling are irrelevant.

The same compiled-output gate rejects Java/Kotlin reflection, method handles,
non-string-concatenation `invokedynamic`, class loaders, runtime compilation,
process execution, native load calls, and native/JNI methods. Kotlin lambdas and
SAM conversions are compiled as classes rather than LambdaMetafactory call sites.
Android variants also inspect merged native libraries, while production source
inputs reject native code and libraries.

DeviceAdmin metadata declares exactly `disable-camera`. Screen-capture and
status-bar control do not require a `uses-policies` declaration. Metadata tests
reject every other capability, including `wipe-data`, `reset-password`, and
`force-lock`.
In addition to the source XML test, Android Components registers verification
for every application variant. Each variant's assemble, unit-test, and check
lifecycle validates the merged manifest, requires one exact DeviceAdmin receiver,
and decodes the linked resource. New flavors and build types inherit the guard
automatically; alternate receivers and manifest or resource overrides must still
resolve to exactly the approved metadata.

## Status-bar policy notes

Status-bar enable/disable is available only on Android API 34+, because verified
mutation requires `setStatusBarDisabled` followed immediately by
`isStatusBarDisabled()`. On older SDKs the capability is reported unavailable and
mutations fail closed without calling the setter. Status-bar disabling does not
apply on the lock screen. LockTask is a separate Android capability and is not
part of this policy surface.

## Adding a future capability safely

A future capability is not added by registering a name at runtime. It requires
all of the following explicit source changes:

1. Add a command and `DeviceActionType`.
2. Add a concrete typed action and an explicit controlled-registry entry.
3. Add a capability-specific backend and policy method.
4. Add a narrow typed DevicePolicyManager service.
5. Add a sealed `VerifiedPolicyMutation` variant and exhaustive dispatch branch
   with an explicit setter and matching getter.
6. Update the exact DPM and, only if Android requires it, metadata allowlists.
7. Add registry completeness, duplicate-registration, final authorization,
   exception, write/read-back, mismatch, boundary, and end-to-end tests.

If any registry, exhaustive dispatch, verification pair, DPM allowlist, or
metadata allowlist is incomplete, tests or the build must fail closed.
The verified-mutation completeness test reflects the sealed variant set and
behaviorally exercises final validation, setter, getter, successful equality,
and mismatch failure for every variant.
