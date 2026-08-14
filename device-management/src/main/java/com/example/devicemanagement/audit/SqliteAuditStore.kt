package com.example.devicemanagement.audit

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.example.devicemanagement.logging.StructuredLogger

/**
 * App-private SQLite adapter for durable audit events.
 *
 * Schema upgrades never drop or recreate the database. Corruption handling
 * never deletes the file. This is not cryptographically tamper-proof storage.
 */
internal object AuditSqliteIdentity {
    const val DATABASE_NAME = "sentinel_audit.db"
    const val TABLE_NAME = "audit_events"
}

internal class SentinelAuditOpenHelper(
    context: Context,
) : SQLiteOpenHelper(
    context,
    AuditSqliteIdentity.DATABASE_NAME,
    null,
    AuditSchema.VERSION,
    NonDestructiveAuditDatabaseErrorHandler(),
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE ${AuditSqliteIdentity.TABLE_NAME} (" +
                "sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "event_id TEXT NOT NULL UNIQUE, " +
                "correlation_id TEXT NOT NULL, " +
                "action_name TEXT NOT NULL, " +
                "phase TEXT NOT NULL, " +
                "presentation_wall_clock_millis INTEGER NOT NULL, " +
                "reason_code TEXT" +
                ")",
        )
        db.execSQL(
            "CREATE INDEX idx_audit_correlation ON ${AuditSqliteIdentity.TABLE_NAME}(correlation_id)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "audit schema upgrade from $oldVersion to $newVersion is forbidden",
        )
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "audit schema downgrade from $oldVersion to $newVersion is forbidden",
        )
    }
}

internal class NonDestructiveAuditDatabaseErrorHandler : DatabaseErrorHandler {
    override fun onCorruption(dbObj: SQLiteDatabase) {
        if (dbObj.isOpen) {
            dbObj.close()
        }
    }
}

internal class SqliteAuditRecordStore(
    context: Context,
) : AuditRecordStore {
    private val helper = SentinelAuditOpenHelper(context.applicationContext)

    override fun insert(record: NewAuditRecord): Long {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("event_id", record.eventId)
            put("correlation_id", record.correlationId)
            put("action_name", record.actionName)
            put("phase", record.phase.name)
            put("presentation_wall_clock_millis", record.presentationWallClockMillis)
            val reasonCode = record.reasonCode
            if (reasonCode == null) {
                putNull("reason_code")
            } else {
                put("reason_code", reasonCode.name)
            }
        }
        db.beginTransaction()
        try {
            val sequence = db.insertOrThrow(AuditSqliteIdentity.TABLE_NAME, null, values)
            if (sequence <= 0L) {
                throw AuditStoreException("audit insert returned invalid sequence")
            }
            db.setTransactionSuccessful()
            return sequence
        } finally {
            db.endTransaction()
        }
    }

    override fun latest(limit: Int): AuditRecordRead {
        val db = helper.readableDatabase
        val cursor = db.query(
            AuditSqliteIdentity.TABLE_NAME,
            COLUMNS,
            null,
            null,
            null,
            null,
            "sequence DESC",
            limit.coerceAtLeast(0).toString(),
        )
        cursor.use {
            val rows = ArrayList<PersistedAuditRecord>()
            var unreadable = false
            while (it.moveToNext()) {
                val decoded = readRow(it)
                if (decoded == null) {
                    unreadable = true
                } else {
                    rows += decoded
                }
            }
            return AuditRecordRead(
                records = rows,
                unreadableRecords = unreadable,
            )
        }
    }

    override fun count(): Int {
        val db = helper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${AuditSqliteIdentity.TABLE_NAME}",
            emptyArray(),
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return 0
            }
            return it.getInt(0)
        }
    }

    override fun deleteOldest(count: Int) {
        if (count <= 0) {
            return
        }
        val db = helper.writableDatabase
        db.execSQL(
            "DELETE FROM ${AuditSqliteIdentity.TABLE_NAME} WHERE sequence IN (" +
                "SELECT sequence FROM ${AuditSqliteIdentity.TABLE_NAME} ORDER BY sequence ASC LIMIT ?" +
                ")",
            arrayOf(count.toLong()),
        )
    }

    private fun readRow(cursor: android.database.Cursor): PersistedAuditRecord? {
        val phase = AuditPersistedCodec.tryDecodePhase(
            cursor.getString(cursor.getColumnIndexOrThrow("phase")),
        ) ?: return null
        val reasonName = cursor.getString(cursor.getColumnIndexOrThrow("reason_code"))
        return PersistedAuditRecord(
            sequence = cursor.getLong(cursor.getColumnIndexOrThrow("sequence")),
            eventId = cursor.getString(cursor.getColumnIndexOrThrow("event_id")),
            correlationId = cursor.getString(cursor.getColumnIndexOrThrow("correlation_id")),
            actionName = cursor.getString(cursor.getColumnIndexOrThrow("action_name")),
            phase = phase,
            presentationWallClockMillis = cursor.getLong(
                cursor.getColumnIndexOrThrow("presentation_wall_clock_millis"),
            ),
            reasonCode = parseReason(reasonName),
        )
    }

    private fun parseReason(raw: String?): AuditReasonCode? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return runCatching { AuditReasonCode.valueOf(raw) }
            .getOrDefault(AuditReasonCode.SANITIZED_UNRECOGNIZED)
    }

    private companion object {
        val COLUMNS = arrayOf(
            "sequence",
            "event_id",
            "correlation_id",
            "action_name",
            "phase",
            "presentation_wall_clock_millis",
            "reason_code",
        )
    }
}

internal object AndroidAuditPersistence {
    fun create(
        context: Context,
        logger: StructuredLogger,
    ): DurableAuditRepository {
        val store = try {
            val sqlite = SqliteAuditRecordStore(context)
            sqlite.count()
            sqlite
        } catch (_: Throwable) {
            logger.warn(
                event = "audit_store_unavailable",
                fields = mapOf("error_type" to "open_or_schema_failure"),
            )
            UnavailableAuditRecordStore()
        }
        return DurableAuditRepository(store, logger)
    }
}
