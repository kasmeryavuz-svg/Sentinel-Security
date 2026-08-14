package attack

import com.example.devicemanagement.audit.AuditSchema

class AuditDatabaseNameAccess {
    fun databaseName(): String = AuditSchema.DATABASE_NAME
    fun tableName(): String = AuditSchema.TABLE_NAME
}
