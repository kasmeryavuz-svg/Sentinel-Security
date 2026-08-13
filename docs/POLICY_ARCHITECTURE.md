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
  -> typed ScreenCapturePolicyAction or CameraPolicyAction
  -> typed SensitiveActionPolicyBackend method
  -> typed ScreenCapturePolicy or CameraPolicy
  -> closed VerifiedPolicyMutation
  -> capability-specific DevicePolicyManager service
  -> DevicePolicyManager
```

The controlled registry is fixed in source and contains exactly four commands:
enable/disable screen capture and enable/disable camera. The `MOCK_WIPE`
simulation exists only in the separate fail-safe registry. Registries are
immutable, reject duplicate commands and action types, and expose no runtime
registration API.

The app compiles only against the submit-only `sensitive-actions-api` module.
Controlled construction and the backend contract live in the separate
`sensitive-actions` implementation module, which is an implementation-only
dependency of device-management and is rejected from the app compile classpath.
Device-management owns the production factory and returns an already-composed
controller. Tests inside the implementation module use internal factories for
deterministic time.

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
- `setCameraDisabled`, verified by `getCameraDisabled(expectedAdmin)`.

Direct `DevicePolicyManager` access is confined to
`AndroidDeviceManagementInfrastructure.kt`. Build and unit-test guards reject
references from every Android production source set outside that exact boundary,
including fully-qualified references, and reject reflection, method handles, and
dynamic class loading in production. Compiled class-file verification resolves
actual DevicePolicyManager method owners, so aliases and inferred receiver names
cannot evade the exact method allowlist.

DeviceAdmin metadata declares exactly `disable-camera`. Screen-capture control
does not require a `uses-policies` declaration. Metadata tests reject every
other capability, including `wipe-data`, `reset-password`, and `force-lock`.
In addition to the source XML test, Android Components registers verification
for every application variant. Each variant's assemble, unit-test, and check
lifecycle validates the merged manifest and decodes the linked resource. New
flavors and build types inherit the guard automatically; an app or variant
override must still resolve to exactly the approved metadata.

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
