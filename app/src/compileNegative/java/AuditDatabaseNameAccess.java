package attack;

import com.example.devicemanagement.audit.AuditSchema;

final class AuditDatabaseNameAccess {
    String databaseName() {
        return AuditSchema.DATABASE_NAME;
    }

    String tableName() {
        return AuditSchema.TABLE_NAME;
    }
}
