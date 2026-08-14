package attack;

import android.content.Context;
import com.example.devicemanagement.audit.SentinelAuditOpenHelper;
import com.example.devicemanagement.audit.SqliteAuditRecordStore;

final class AuditDatabaseAccess {
    SentinelAuditOpenHelper helper(Context context) {
        return new SentinelAuditOpenHelper(context);
    }

    SqliteAuditRecordStore store(Context context) {
        return new SqliteAuditRecordStore(context);
    }
}
