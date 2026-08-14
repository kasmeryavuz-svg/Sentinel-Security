package attack;

import com.example.devicemanagement.audit.AuditPersistedCodec;
import com.example.devicemanagement.audit.AuditSqliteIdentity;

final class AuditSqliteBypassAccess {
    String databaseName() {
        return AuditSqliteIdentity.DATABASE_NAME;
    }

    Object codec() {
        return AuditPersistedCodec.INSTANCE;
    }
}
