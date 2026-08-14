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
internal class SentinelAuditOpenHelper(
    context: Context,
) : SQLiteOpenHelper(
    context,
    AuditSchema.DATABASE_NAME,
    null,
    AuditSchema.VERSION,
    NonDestructiveAuditDatabaseErrorHandler(),
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE ${AuditSchema.TABLE_NAME} (" +
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
            "CREATE INDEX idx_audit_correlation ON ${AuditSchema.TABLE_NAME}(correlation_id)",
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
            if (record.reasonCode == null) {
                putNull("reason_code")
            } else {
                put("reason_code", record.reasonCode.name)
            }
        }
        db.beginTransaction()
        try {
            val sequence = db.insertOrThrow(AuditSchema.TABLE_NAME, null, values)
            if (sequence <= 0L) {
                throw AuditStoreException("audit insert returned invalid sequence")
            }
            db.setTransactionSuccessful()
            return sequence
        } finally {
            db.endTransaction()
        }
    }

    override fun latest(limit: Int): List<PersistedAuditRecord> {
        val db = helper.readableDatabase
        val cursor = db.query(
            AuditSchema.TABLE_NAME,
            COLUMNS,
            null,
            null,
            null,
            null,
            "sequence DESC",
            limit.coerceAtLeast(0).toString(),
        )
        cursor.use {
            val rows = ArrayList<PersistedAuditRecord>(it.count)
            while (it.moveToNext()) {
                rows += readRow(it)
            }
            return rows
        }
    }

    override fun count(): Int {
        val db = helper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${AuditSchema.TABLE_NAME}",
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
            "DELETE FROM ${AuditSchema.TABLE_NAME} WHERE sequence IN (" +
                "SELECT sequence FROM ${AuditSchema.TABLE_NAME} ORDER BY sequence ASC LIMIT ?" +
                ")",
            arrayOf(count.toLong()),
        )
    }

    private fun readRow(cursor: android.database.Cursor): PersistedAuditRecord {
        val reasonName = cursor.getString(cursor.getColumnIndexOrThrow("reason_code"))
        return PersistedAuditRecord(
            sequence = cursor.getLong(cursor.getColumnIndexOrThrow("sequence")),
            eventId = cursor.getString(cursor.getColumnIndexOrThrow("event_id")),
            correlationId = cursor.getString(cursor.getColumnIndexOrThrow("correlation_id")),
            actionName = cursor.getString(cursor.getColumnIndexOrThrow("action_name")),
            phase = parsePhase(cursor.getString(cursor.getColumnIndexOrThrow("phase"))),
            presentationWallClockMillis = cursor.getLong(
                cursor.getColumnIndexOrThrow("presentation_wall_clock_millis"),
            ),
            reasonCode = parseReason(reasonName),
        )
    }

    private fun parsePhase(raw: String): AuditEventPhase {
        return runCatching { AuditEventPhase.valueOf(raw) }
            .getOrDefault(AuditEventPhase.FAILED)
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
