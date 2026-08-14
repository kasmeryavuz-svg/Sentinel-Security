# Durable local audit log

Checkpoint 13 adds an append-only security audit log in Sentinel's private
application sandbox. GrapheneOS on supported Pixel hardware remains the
production target. Persistence uses only standard Android platform SQLite
APIs. There is no Google Play, Google backup, managed Google Play, or cloud
sync dependency.

## What this provides

- Durable app-private SQLite storage (`sentinel_audit.db`)
- Architecture-controlled writers, not reachable from the UI compile classpath
- Append-oriented events: `REQUESTED`, then one terminal
  `APPLIED` / `REJECTED` / `FAILED` / `SIMULATED`
- Bounded local retention (8,000 records; oldest rows pruned first)
- Crash/restart persistence of already-committed events
- Read-only dashboard presentation, including interrupted sequences
- Read-only recovery inspection of interrupted correlation IDs after restart

## What this does not provide

Ordinary app-private SQLite is **not** cryptographically tamper-proof.

This checkpoint does **not** promise:

- cryptographic tamper evidence
- anti-rollback storage
- hardware-backed audit-chain integrity
- remote archival or cloud sync
- a public clear/delete/export API

Cryptographic audit integrity belongs to a later production-security
checkpoint. Do not treat this log as an authorization source.

## Trust boundary

Audit records originate only from `SensitiveActionController`. The Android UI
may use only:

- `AuditHistoryProvider`
- `AuditStorageStatusProvider`
- `RecoveryInspectionProvider`

The writer, SQLite helper, database filename/table identity, and database
mutation types are implementation artifacts. They are absent from the app
compile classpath. Production app/UI bytecode cannot reference SQLiteDatabase,
SQLiteOpenHelper, Context.openOrCreateDatabase, Context.deleteDatabase,
Context.getDatabasePath, Context.moveDatabaseFrom, DatabaseUtils database
creation/manipulation APIs, android.system.Os file-manipulation syscalls,
OsConstants file flags, or java.io.File APIs capable of targeting the audit
store. Production bytecode also binds `SensitiveActionAuditWriter.append` and
`DurableAuditRepository.append` to `DefaultSensitiveActionController.submit`.
`AuditRecordStore.insert`, `AuditRecordStore.deleteOldest`,
`SqliteAuditRecordStore.insert`, and `SqliteAuditRecordStore.deleteOldest` are
bound to `DurableAuditRepository.append`, so a future implementation class
cannot instantiate or cast the SQLite adapter and forge or prune durable
records while SQLite remains allowlisted inside that adapter. Read-only store
operations (`latest`, `count`) remain available to the trusted audit pipeline.
Audit records may be read only through `AuditHistoryProvider` and
`AuditStorageStatusProvider`. Audit persistence cannot authorize actions,
create approvals, replay commands, call DevicePolicyManager, or influence
Device Owner verification. This remains same-UID architectural and build-time
enforcement, not runtime cryptographic isolation.

Unknown or malformed persisted `phase` values are treated as storage
corruption. They are omitted from history and never decoded as `APPLIED`,
`REJECTED`, `FAILED`, or `SIMULATED`. A still-readable `REQUESTED` event stays
Interrupted / Outcome unknown, and audit health becomes degraded.

## Failure and crash semantics

1. A durable `REQUESTED` event is written before any policy mutation.
2. If that write fails, the controller fails closed and does not mutate policy.
3. After the trusted path returns, one terminal event is appended.
4. If the mutation was Applied and the terminal write fails, the product result
   remains Applied. Audit health becomes degraded. The unmatched `REQUESTED`
   event stays visible as Interrupted / Outcome unknown. Sentinel does not
   fabricate the missing terminal record and does not retry the action.
5. After process death, incomplete `REQUESTED` events remain incomplete.
   Restart never infers Applied. Recovery inspection classifies those rows as
   interrupted evidence and has no execution capability. Sentinel does not
   automatically retry, resume, replay, or continue the original request.
   See `docs/LIFECYCLE.md`.

Wall-clock time stored on events is presentation metadata only. It is never
used for authorization, freshness, cooldowns, or replay protection.

## Schema

- Database: `sentinel_audit.db`
- Schema version: `1`
- Table: `audit_events`
- Fields: monotonically increasing `sequence`, unique `event_id`,
  `correlation_id`, bounded `action_name`, `phase`, presentation
  `presentation_wall_clock_millis`, optional bounded `reason_code`
- Unknown or malformed `phase` values are omitted and treated as storage
  corruption. They never become `APPLIED`, `REJECTED`, `FAILED`, or
  `SIMULATED`. A readable `REQUESTED` sibling stays Interrupted.
- Incompatible or corrupt databases fail closed. The helper does not drop or
  recreate the file.
- Android backup remains disabled (`allowBackup=false`). Explicit
  full-backup and Android 12+ data-extraction rules also exclude databases,
  files, shared preferences, and device-to-device transfer. See
  `docs/RELEASE_SECURITY.md`.
