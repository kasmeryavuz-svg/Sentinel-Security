package com.example.devicemanagement.persistence

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.example.devicemanagement.logging.StructuredLogger

/**
 * Purpose-specific Android adapter for the deny-only cooldown marker.
 *
 * Stores one blob in a dedicated database. It is not a general file or
 * database API, not authorization, and not wired into production
 * DeviceManagement composition. Corrupt or unreadable storage fails closed.
 * Ordinary app-private SQLite is not cryptographically tamper-proof.
 */
internal object DestructiveSafetySqliteIdentity {
    const val DENY_ONLY_DATABASE_NAME = "sentinel_deny_only_cooldown.db"
    const val EVIDENCE_DATABASE_NAME = "sentinel_destructive_pre_execution_evidence.db"
    const val DENY_ONLY_SCHEMA_VERSION = 1
    const val EVIDENCE_SCHEMA_VERSION = 1
}

internal class NonDestructiveSafetyDatabaseErrorHandler : DatabaseErrorHandler {
    override fun onCorruption(dbObj: SQLiteDatabase) {
        if (dbObj.isOpen) {
            dbObj.close()
        }
    }
}

internal class DenyOnlyCooldownOpenHelper(
    context: Context,
) : SQLiteOpenHelper(
    context,
    DestructiveSafetySqliteIdentity.DENY_ONLY_DATABASE_NAME,
    null,
    DestructiveSafetySqliteIdentity.DENY_ONLY_SCHEMA_VERSION,
    NonDestructiveSafetyDatabaseErrorHandler(),
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE ${DenyOnlyCooldownStorageIdentity.TABLE_NAME} (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1) NOT NULL, " +
                "payload BLOB NOT NULL" +
                ")",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "deny-only cooldown schema upgrade from $oldVersion to $newVersion is forbidden",
        )
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "deny-only cooldown schema downgrade from $oldVersion to $newVersion is forbidden",
        )
    }
}

internal class SqliteDenyOnlyMarkerStore(
    context: Context,
) : DenyOnlyMarkerDurableMedium {
    private val helper = DenyOnlyCooldownOpenHelper(context.applicationContext)

    override fun persistEncodedMarker(encoded: ByteArray): DenyOnlyMarkerPersistResult {
        if (encoded.size > DenyOnlyCooldownStorageIdentity.MAX_PAYLOAD_BYTES) {
            return DenyOnlyMarkerPersistResult.FAILED
        }
        return try {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("id", 1)
                put("payload", encoded.copyOf())
            }
            db.beginTransaction()
            try {
                val rowId = db.insertWithOnConflict(
                    DenyOnlyCooldownStorageIdentity.TABLE_NAME,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                if (rowId <= 0L) {
                    return DenyOnlyMarkerPersistResult.FAILED
                }
                db.setTransactionSuccessful()
                DenyOnlyMarkerPersistResult.WRITTEN
            } finally {
                db.endTransaction()
            }
        } catch (_: Throwable) {
            DenyOnlyMarkerPersistResult.FAILED
        }
    }

    override fun loadEncodedMarker(): DenyOnlyMarkerLoadResult {
        return try {
            val db = helper.readableDatabase
            val cursor = db.query(
                DenyOnlyCooldownStorageIdentity.TABLE_NAME,
                arrayOf("payload"),
                "id = 1",
                null,
                null,
                null,
                null,
            )
            cursor.use {
                if (!it.moveToFirst()) {
                    return DenyOnlyMarkerLoadResult.Absent
                }
                val payload = it.getBlob(0) ?: return DenyOnlyMarkerLoadResult.Unreadable
                DenyOnlyMarkerLoadResult.Bytes(payload)
            }
        } catch (_: Throwable) {
            DenyOnlyMarkerLoadResult.Unavailable
        }
    }
}

internal class DestructivePreExecutionOpenHelper(
    context: Context,
) : SQLiteOpenHelper(
    context,
    DestructiveSafetySqliteIdentity.EVIDENCE_DATABASE_NAME,
    null,
    DestructiveSafetySqliteIdentity.EVIDENCE_SCHEMA_VERSION,
    NonDestructiveSafetyDatabaseErrorHandler(),
) {
    override fun onCreate(db: SQLiteDatabase) {
        val identity = com.example.devicemanagement.destructive.DestructivePreExecutionStorageIdentity
        db.execSQL(
            "CREATE TABLE ${identity.TABLE_NAME} (" +
                "sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "event_id TEXT NOT NULL UNIQUE, " +
                "correlation_id TEXT NOT NULL, " +
                "action_name TEXT NOT NULL, " +
                "phase TEXT NOT NULL, " +
                "presentation_wall_clock_millis INTEGER NOT NULL, " +
                "bound_package TEXT, " +
                "bound_admin_component TEXT, " +
                "bound_scope TEXT, " +
                "reason_code TEXT" +
                ")",
        )
        db.execSQL(
            "CREATE INDEX idx_destructive_pre_exec_correlation ON ${identity.TABLE_NAME}(correlation_id)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "destructive pre-execution schema upgrade from $oldVersion to $newVersion is forbidden",
        )
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "destructive pre-execution schema downgrade from $oldVersion to $newVersion is forbidden",
        )
    }
}

internal class SqliteDestructivePreExecutionStore(
    context: Context,
) : com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore {
    private val helper = DestructivePreExecutionOpenHelper(context.applicationContext)

    override fun insert(
        record: com.example.devicemanagement.destructive.DestructivePreExecutionDurableRecord,
    ): Long {
        val identity = com.example.devicemanagement.destructive.DestructivePreExecutionStorageIdentity
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("event_id", record.eventId)
            put("correlation_id", record.correlationId)
            put("action_name", record.actionName)
            put("phase", record.phase.name)
            put("presentation_wall_clock_millis", record.presentationWallClockMillis)
            put("bound_package", record.boundPackage)
            put("bound_admin_component", record.boundAdminComponent)
            put("bound_scope", record.boundScope?.name)
            put("reason_code", record.reasonCode)
        }
        db.beginTransaction()
        try {
            val sequence = db.insertOrThrow(identity.TABLE_NAME, null, values)
            if (sequence <= 0L) {
                throw com.example.devicemanagement.destructive.DestructivePreExecutionStoreException(
                    "destructive pre-execution insert returned invalid sequence",
                )
            }
            db.setTransactionSuccessful()
            return sequence
        } finally {
            db.endTransaction()
        }
    }

    override fun latest(limit: Int): com.example.devicemanagement.destructive.DestructivePreExecutionDurableRead {
        val identity = com.example.devicemanagement.destructive.DestructivePreExecutionStorageIdentity
        val db = helper.readableDatabase
        val cursor = db.query(
            identity.TABLE_NAME,
            EVIDENCE_COLUMNS,
            null,
            null,
            null,
            null,
            "sequence DESC",
            limit.coerceAtLeast(0).toString(),
        )
        cursor.use {
            val rows = ArrayList<com.example.devicemanagement.destructive.DestructivePreExecutionDurableRecord>()
            var unreadable = false
            while (it.moveToNext()) {
                val decoded = readRow(it)
                if (decoded == null) {
                    unreadable = true
                } else {
                    rows += decoded
                }
            }
            return com.example.devicemanagement.destructive.DestructivePreExecutionDurableRead(
                records = rows,
                unreadableRecords = unreadable,
            )
        }
    }

    override fun count(): Int {
        val identity = com.example.devicemanagement.destructive.DestructivePreExecutionStorageIdentity
        val db = helper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${identity.TABLE_NAME}",
            emptyArray(),
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return 0
            }
            return it.getInt(0)
        }
    }

    private fun readRow(
        cursor: android.database.Cursor,
    ): com.example.devicemanagement.destructive.DestructivePreExecutionDurableRecord? {
        val phaseName = cursor.getString(cursor.getColumnIndexOrThrow("phase"))
        val phase = runCatching {
            com.example.devicemanagement.destructive.DestructiveEvidencePhase.valueOf(phaseName)
        }.getOrNull() ?: return null
        val scopeName = cursor.getString(cursor.getColumnIndexOrThrow("bound_scope"))
        val scope = if (scopeName.isNullOrBlank()) {
            null
        } else {
            runCatching {
                com.example.devicemanagement.destructive.DestructiveScope.valueOf(scopeName)
            }.getOrNull() ?: return null
        }
        return com.example.devicemanagement.destructive.DestructivePreExecutionDurableRecord(
            eventId = cursor.getString(cursor.getColumnIndexOrThrow("event_id")),
            correlationId = cursor.getString(cursor.getColumnIndexOrThrow("correlation_id")),
            actionName = cursor.getString(cursor.getColumnIndexOrThrow("action_name")),
            phase = phase,
            presentationWallClockMillis = cursor.getLong(
                cursor.getColumnIndexOrThrow("presentation_wall_clock_millis"),
            ),
            boundPackage = cursor.getString(cursor.getColumnIndexOrThrow("bound_package")),
            boundAdminComponent = cursor.getString(cursor.getColumnIndexOrThrow("bound_admin_component")),
            boundScope = scope,
            reasonCode = cursor.getString(cursor.getColumnIndexOrThrow("reason_code")),
        )
    }

    private companion object {
        val EVIDENCE_COLUMNS = arrayOf(
            "sequence",
            "event_id",
            "correlation_id",
            "action_name",
            "phase",
            "presentation_wall_clock_millis",
            "bound_package",
            "bound_admin_component",
            "bound_scope",
            "reason_code",
        )
    }
}

/**
 * Android factory for Checkpoint 17B runtime-durable safety persistence.
 * Not invoked by DeviceManagement composition. Issuing the runtime
 * capability does not arm, authorize, or execute anything.
 *
 * This is the only production mint path for
 * [com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability].
 * Open or schema failure returns null (fail closed). Unavailable test
 * stand-ins are not runtime durability.
 */
internal object AndroidDestructiveSafetyPersistence {
    fun issueRuntimeDurability(
        context: Context,
        logger: StructuredLogger,
    ): com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability? {
        val medium = openDenyOnlyMedium(context, logger) ?: return null
        val store = openPreExecutionStore(context, logger) ?: return null
        return com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability
            .RuntimeDestructiveSafetyDurabilityMint
            .issueFromTrustedAndroidStores(medium, store)
    }

    private fun openDenyOnlyMedium(
        context: Context,
        logger: StructuredLogger,
    ): SqliteDenyOnlyMarkerStore? {
        return try {
            val store = SqliteDenyOnlyMarkerStore(context)
            store.loadEncodedMarker()
            store
        } catch (_: Throwable) {
            logger.warn(
                event = "deny_only_cooldown_store_unavailable",
                fields = mapOf("error_type" to "open_or_schema_failure"),
            )
            null
        }
    }

    private fun openPreExecutionStore(
        context: Context,
        logger: StructuredLogger,
    ): SqliteDestructivePreExecutionStore? {
        return try {
            val store = SqliteDestructivePreExecutionStore(context)
            store.count()
            store
        } catch (_: Throwable) {
            logger.warn(
                event = "destructive_pre_execution_store_unavailable",
                fields = mapOf("error_type" to "open_or_schema_failure"),
            )
            null
        }
    }
}

internal object UnavailableDenyOnlyMarkerMedium : DenyOnlyMarkerDurableMedium {
    override fun persistEncodedMarker(encoded: ByteArray): DenyOnlyMarkerPersistResult {
        return DenyOnlyMarkerPersistResult.FAILED
    }

    override fun loadEncodedMarker(): DenyOnlyMarkerLoadResult {
        return DenyOnlyMarkerLoadResult.Unavailable
    }
}
