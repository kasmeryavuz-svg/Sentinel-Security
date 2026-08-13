# Sentinel Security

Minimal, fail-safe Android device-management skeleton.

## Safety

This project contains no real wipe, factory-reset, deletion, Device Admin,
Device Owner provisioning, accessibility, shell, or ADB functionality. The
only wipe-shaped action is `SafeMockWipeAction`; it logs
`WIPE WOULD EXECUTE` and returns a simulated result.

All sensitive-action requests follow one controlled path:

```text
Trigger -> TriggerEvaluator -> DecisionEngine -> ActionDecision -> ActionExecutor
```

Invalid, missing, expired, unavailable, disabled, or exceptional states are
denied. Triggers cannot access device actions directly.

## Packages

- `ui`: status-only Android UI.
- `trigger`: trigger input parsing and validation.
- `decision`: centralized, fail-safe sensitive-action decisions.
- `action`: controlled execution boundary and safe mock action.
- `policy`: non-destructive device-policy boundary.
- `persistence`: state storage boundary and in-memory implementation.
- `logging`: structured logging abstraction and Android implementation.
- `app`: dependency wiring and application entry point.

## Build

```bash
./gradlew test
./gradlew assembleDebug
```