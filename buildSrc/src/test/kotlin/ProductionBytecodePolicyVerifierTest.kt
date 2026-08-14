import java.io.File
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertTrue

class ProductionBytecodePolicyVerifierTest {
    @Test
    fun `authorized infrastructure can invoke only approved DPM status bar setter`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService.java" to
                """
                package com.example.devicemanagement.management;
                import android.app.admin.DevicePolicyManager;
                import android.content.ComponentName;
                public final class AndroidDevicePolicyStatusBarService {
                    private final DevicePolicyManager manager;
                    private final ComponentName adminComponent;
                    AndroidDevicePolicyStatusBarService(
                        DevicePolicyManager manager,
                        ComponentName adminComponent
                    ) {
                        this.manager = manager;
                        this.adminComponent = adminComponent;
                    }
                    boolean setStatusBarDisabled(boolean disabled) {
                        return manager.setStatusBarDisabled(adminComponent, disabled);
                    }
                    boolean isStatusBarDisabled() {
                        return manager.isStatusBarDisabled();
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `authorized infrastructure can invoke only approved DPM setter`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService.java" to
                """
                package com.example.devicemanagement.management;
                import android.app.admin.DevicePolicyManager;
                import android.content.ComponentName;
                public final class AndroidDevicePolicyCameraService {
                    private final DevicePolicyManager manager;
                    private final ComponentName adminComponent;
                    AndroidDevicePolicyCameraService(
                        DevicePolicyManager manager,
                        ComponentName adminComponent
                    ) {
                        this.manager = manager;
                        this.adminComponent = adminComponent;
                    }
                    void setCameraDisabled(boolean disabled) {
                        manager.setCameraDisabled(adminComponent, disabled);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `Java unicode escaped DPM helper is rejected from app bytecode`() {
        val classes = compileJava(
            "attack/UnicodeHelper.java" to
                """
                package attack;
                import android.app.admin.DevicePolicyManag\u0065r;
                import android.content.ComponentName;
                public final class UnicodeHelper {
                    void apply(DevicePolicyManager policy, ComponentName admin) {
                        policy.setCameraDisabled(admin, true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside the explicitly authorized")
    }

    @Test
    fun `inferred DPM alias is resolved and rejected from app bytecode`() {
        val classes = compileJava(
            "attack/AliasHelper.java" to
                """
                package attack;
                import android.app.admin.DevicePolicyManager;
                import android.content.ComponentName;
                public final class AliasHelper {
                    void apply(DevicePolicyManager supplied, ComponentName admin) {
                        var completelyUnrelatedName = supplied;
                        completelyUnrelatedName.setScreenCaptureDisabled(admin, true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "setScreenCaptureDisabled")
    }

    @Test
    fun `fully qualified DPM call is rejected from Java app helper`() {
        val classes = compileJava(
            "attack/FullyQualifiedHelper.java" to
                """
                package attack;
                public final class FullyQualifiedHelper {
                    void apply(
                        android.app.admin.DevicePolicyManager manager,
                        android.content.ComponentName admin
                    ) {
                        manager.setCameraDisabled(admin, true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "setCameraDisabled")
    }

    @Test
    fun `non-allowlisted DPM method fails even in authorized class`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService.java" to
                """
                package com.example.devicemanagement.management;
                import android.app.admin.DevicePolicyManager;
                public final class AndroidDevicePolicyCameraService {
                    void apply(DevicePolicyManager manager) {
                        manager.wipeData(0);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "non-allowlisted" in it && "wipeData" in it })
    }

    @Test
    fun `allowlisted setter fails from an extra infrastructure method`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService.java" to
                """
                package com.example.devicemanagement.management;
                import android.app.admin.DevicePolicyManager;
                import android.content.ComponentName;
                public final class AndroidDevicePolicyCameraService {
                    void bypass(DevicePolicyManager manager, ComponentName admin) {
                        manager.setCameraDisabled(admin, true);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "authorized implementation method" in it })
    }

    @Test
    fun `fragmented reflection target fails by actual Class forName owner`() {
        val classes = compileJava(
            "attack/FragmentedReflection.java" to
                """
                package attack;
                public final class FragmentedReflection {
                    Class<?> reach() throws Exception {
                        String first = "android.app.admin.";
                        String second = "DevicePolicy" + "Manager";
                        return Class.forName(first + second);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "java/lang/Class.forName")
    }

    @Test
    fun `reflective lookup and invocation fail by method owners`() {
        val classes = compileJava(
            "attack/ReflectiveInvoke.java" to
                """
                package attack;
                public final class ReflectiveInvoke {
                    Object reach(Class<?> target, Object receiver) throws Exception {
                        return target.getDeclaredMethod("setCameraDisabled").invoke(receiver);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":app", classes)
        assertTrue(violations.any { "reflective lookup" in it })
        assertTrue(violations.any { "Java reflection" in it })
    }

    @Test
    fun `native Java entry point is rejected from production bytecode`() {
        val classes = compileJava(
            "attack/NativeBridge.java" to
                """
                package attack;
                public final class NativeBridge {
                    public native void applyPolicy();
                }
                """.trimIndent(),
        )

        assertRejected(classes, "native/JNI entry point")
    }

    @Test
    fun `method handles are rejected by actual invocation owner`() {
        val classes = compileJava(
            "attack/MethodHandleAccess.java" to
                """
                package attack;
                import java.lang.invoke.MethodHandles;
                public final class MethodHandleAccess {
                    Object reach() {
                        return MethodHandles.lookup();
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "method handles")
    }

    @Test
    fun `dynamic class loaders are rejected by actual invocation owner`() {
        val classes = compileJava(
            "attack/DynamicLoader.java" to
                """
                package attack;
                import java.net.URL;
                import java.net.URLClassLoader;
                public final class DynamicLoader {
                    Class<?> reach(URL location) throws Exception {
                        return new URLClassLoader(new URL[] {location}).loadClass("policy.Target");
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "dynamic class loading")
    }

    @Test
    fun `system and runtime native loads are rejected by actual owners`() {
        val classes = compileJava(
            "attack/NativeLoad.java" to
                """
                package attack;
                public final class NativeLoad {
                    void reach(String path) {
                        System.loadLibrary("policy");
                        Runtime.getRuntime().load(path);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":app", classes)
        assertTrue(violations.any { "java/lang/System.loadLibrary" in it })
        assertTrue(violations.any { "java/lang/Runtime.load" in it })
    }

    @Test
    fun `compiler intrinsic string concatenation is not treated as dynamic policy access`() {
        val classes = compileJava(
            "safe/StringDescription.java" to
                """
                package safe;
                public final class StringDescription {
                    String describe(String value, int count) {
                        return "value=" + value + ", count=" + count;
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":app", classes)
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `lambda invokedynamic remains rejected`() {
        val classes = compileJava(
            "attack/DynamicLambda.java" to
                """
                package attack;
                public final class DynamicLambda {
                    Runnable create() {
                        return () -> System.out.println("dynamic");
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "uses invokedynamic")
    }

    @Test
    fun `legacy Class newInstance is rejected`() {
        val classes = compileJava(
            "attack/LegacyReflection.java" to
                """
                package attack;
                public final class LegacyReflection {
                    Object reach(Class<?> type) throws Exception {
                        return type.newInstance();
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "reflective lookup")
    }

    @Test
    fun `DexFile loading is rejected`() {
        val classes = compileJava(
            "dalvik/system/DexFile.java" to
                """
                package dalvik.system;
                public final class DexFile {
                    public DexFile(String path) {}
                    public Class<?> loadClass(String name, ClassLoader loader) { return null; }
                }
                """.trimIndent(),
            "attack/DexFileLoading.java" to
                """
                package attack;
                import dalvik.system.DexFile;
                public final class DexFileLoading {
                    Object reach(String path) throws Exception {
                        return new DexFile(path).loadClass("policy.Target", getClass().getClassLoader());
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "dynamic class loading")
    }

    @Test
    fun `narrow policy setter cannot bypass verified mutation executor`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/DevicePolicyCameraService.java" to
                """
                package com.example.devicemanagement.management;
                public interface DevicePolicyCameraService {
                    void setCameraDisabled(boolean disabled);
                }
                """.trimIndent(),
            "attack/NarrowServiceBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.management.DevicePolicyCameraService;
                public final class NarrowServiceBypass {
                    void reach(DevicePolicyCameraService service) {
                        service.setCameraDisabled(true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside VerifiedPolicyMutationExecutor")
    }

    @Test
    fun `direct concrete camera service setter bypass fails production verifier`() {
        val classes = compileJava(
            concreteCameraServiceStub(),
            "attack/ConcreteCameraSetterBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.management.AndroidDevicePolicyCameraService;
                public final class ConcreteCameraSetterBypass {
                    void reach(AndroidDevicePolicyCameraService service) {
                        service.setCameraDisabled(true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside VerifiedPolicyMutationExecutor")
        assertRejected(classes, "AndroidDevicePolicyCameraService.setCameraDisabled")
    }

    @Test
    fun `direct concrete screen capture service setter bypass fails production verifier`() {
        val classes = compileJava(
            concreteScreenCaptureServiceStub(),
            "attack/ConcreteScreenCaptureSetterBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.management.AndroidDevicePolicyScreenCaptureService;
                public final class ConcreteScreenCaptureSetterBypass {
                    void reach(AndroidDevicePolicyScreenCaptureService service) {
                        service.setScreenCaptureDisabled(true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside VerifiedPolicyMutationExecutor")
        assertRejected(classes, "AndroidDevicePolicyScreenCaptureService.setScreenCaptureDisabled")
    }

    @Test
    fun `cast from interface to concrete camera setter bypass fails production verifier`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/DevicePolicyCameraService.java" to
                """
                package com.example.devicemanagement.management;
                public interface DevicePolicyCameraService {
                    void setCameraDisabled(boolean disabled);
                }
                """.trimIndent(),
            concreteCameraServiceStub(implementInterface = true),
            "attack/CastConcreteCameraSetterBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.management.AndroidDevicePolicyCameraService;
                import com.example.devicemanagement.management.DevicePolicyCameraService;
                public final class CastConcreteCameraSetterBypass {
                    void reach(DevicePolicyCameraService service) {
                        ((AndroidDevicePolicyCameraService) service).setCameraDisabled(true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside VerifiedPolicyMutationExecutor")
        assertRejected(classes, "AndroidDevicePolicyCameraService.setCameraDisabled")
    }

    @Test
    fun `cast from interface to concrete screen capture setter bypass fails production verifier`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/DevicePolicyScreenCaptureService.java" to
                """
                package com.example.devicemanagement.management;
                public interface DevicePolicyScreenCaptureService {
                    void setScreenCaptureDisabled(boolean disabled);
                }
                """.trimIndent(),
            concreteScreenCaptureServiceStub(implementInterface = true),
            "attack/CastConcreteScreenCaptureSetterBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.management.AndroidDevicePolicyScreenCaptureService;
                import com.example.devicemanagement.management.DevicePolicyScreenCaptureService;
                public final class CastConcreteScreenCaptureSetterBypass {
                    void reach(DevicePolicyScreenCaptureService service) {
                        ((AndroidDevicePolicyScreenCaptureService) service)
                            .setScreenCaptureDisabled(true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside VerifiedPolicyMutationExecutor")
        assertRejected(
            classes,
            "AndroidDevicePolicyScreenCaptureService.setScreenCaptureDisabled",
        )
    }

    @Test
    fun `authorized verified mutation executor may invoke interface setters`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/DevicePolicyCameraService.java" to
                """
                package com.example.devicemanagement.management;
                public interface DevicePolicyCameraService {
                    void setCameraDisabled(boolean disabled);
                }
                """.trimIndent(),
            "com/example/devicemanagement/management/DevicePolicyScreenCaptureService.java" to
                """
                package com.example.devicemanagement.management;
                public interface DevicePolicyScreenCaptureService {
                    void setScreenCaptureDisabled(boolean disabled);
                }
                """.trimIndent(),
            "com/example/devicemanagement/management/DevicePolicyStatusBarService.java" to
                """
                package com.example.devicemanagement.management;
                public interface DevicePolicyStatusBarService {
                    boolean setStatusBarDisabled(boolean disabled);
                }
                """.trimIndent(),
            "com/example/devicemanagement/management/VerifiedPolicyMutation.java" to
                """
                package com.example.devicemanagement.management;
                public abstract class VerifiedPolicyMutation {
                    public static final class Camera extends VerifiedPolicyMutation {
                        public final boolean disabled;
                        public Camera(boolean disabled) { this.disabled = disabled; }
                    }
                    public static final class ScreenCapture extends VerifiedPolicyMutation {
                        public final boolean disabled;
                        public ScreenCapture(boolean disabled) { this.disabled = disabled; }
                    }
                    public static final class StatusBar extends VerifiedPolicyMutation {
                        public final boolean disabled;
                        public StatusBar(boolean disabled) { this.disabled = disabled; }
                    }
                }
                """.trimIndent(),
            "com/example/devicemanagement/management/PolicyMutation.java" to
                """
                package com.example.devicemanagement.management;
                public abstract class PolicyMutation {}
                """.trimIndent(),
            "com/example/devicemanagement/management/VerifiedPolicyMutationExecutor.java" to
                """
                package com.example.devicemanagement.management;
                public final class VerifiedPolicyMutationExecutor {
                    PolicyMutation executeCamera(
                        VerifiedPolicyMutation.Camera mutation,
                        String correlationId
                    ) {
                        DevicePolicyCameraService service = null;
                        service.setCameraDisabled(mutation.disabled);
                        return null;
                    }
                    PolicyMutation executeScreenCapture(
                        VerifiedPolicyMutation.ScreenCapture mutation,
                        String correlationId
                    ) {
                        DevicePolicyScreenCaptureService service = null;
                        service.setScreenCaptureDisabled(mutation.disabled);
                        return null;
                    }
                    PolicyMutation executeStatusBar(
                        VerifiedPolicyMutation.StatusBar mutation,
                        String correlationId
                    ) {
                        DevicePolicyStatusBarService service = null;
                        service.setStatusBarDisabled(mutation.disabled);
                        return null;
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(
            violations.none { "outside VerifiedPolicyMutationExecutor" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `direct concrete status bar service setter bypass fails production verifier`() {
        val classes = compileJava(
            concreteStatusBarServiceStub(),
            "attack/ConcreteStatusBarSetterBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.management.AndroidDevicePolicyStatusBarService;
                public final class ConcreteStatusBarSetterBypass {
                    void reach(AndroidDevicePolicyStatusBarService service) {
                        service.setStatusBarDisabled(true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside VerifiedPolicyMutationExecutor")
        assertRejected(classes, "AndroidDevicePolicyStatusBarService.setStatusBarDisabled")
    }

    @Test
    fun `cast from interface to concrete status bar setter bypass fails production verifier`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/DevicePolicyStatusBarService.java" to
                """
                package com.example.devicemanagement.management;
                public interface DevicePolicyStatusBarService {
                    boolean setStatusBarDisabled(boolean disabled);
                }
                """.trimIndent(),
            concreteStatusBarServiceStub(implementInterface = true),
            "attack/CastConcreteStatusBarSetterBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.management.AndroidDevicePolicyStatusBarService;
                import com.example.devicemanagement.management.DevicePolicyStatusBarService;
                public final class CastConcreteStatusBarSetterBypass {
                    void reach(DevicePolicyStatusBarService service) {
                        ((AndroidDevicePolicyStatusBarService) service).setStatusBarDisabled(true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside VerifiedPolicyMutationExecutor")
        assertRejected(classes, "AndroidDevicePolicyStatusBarService.setStatusBarDisabled")
    }

    @Test
    fun `narrow status bar setter cannot bypass verified mutation executor`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/DevicePolicyStatusBarService.java" to
                """
                package com.example.devicemanagement.management;
                public interface DevicePolicyStatusBarService {
                    boolean setStatusBarDisabled(boolean disabled);
                }
                """.trimIndent(),
            "attack/NarrowStatusBarServiceBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.management.DevicePolicyStatusBarService;
                public final class NarrowStatusBarServiceBypass {
                    void reach(DevicePolicyStatusBarService service) {
                        service.setStatusBarDisabled(true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "outside VerifiedPolicyMutationExecutor")
    }

    @Test
    fun `raw DPM setStatusBarDisabled is rejected from unauthorized class`() {
        val classes = compileJava(
            "attack/RawStatusBarDpmBypass.java" to
                """
                package attack;
                import android.app.admin.DevicePolicyManager;
                import android.content.ComponentName;
                public final class RawStatusBarDpmBypass {
                    void apply(DevicePolicyManager manager, ComponentName admin) {
                        manager.setStatusBarDisabled(admin, true);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "setStatusBarDisabled")
        assertRejected(classes, "outside the explicitly authorized")
    }

    @Test
    fun `app bytecode cannot open the audit database through SQLiteDatabase`() {
        val classes = compileJava(
            "attack/SqliteDatabaseBypass.java" to
                """
                package attack;
                import android.database.sqlite.SQLiteDatabase;
                public final class SqliteDatabaseBypass {
                    SQLiteDatabase open(String path) {
                        return SQLiteDatabase.openOrCreateDatabase(path, null);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "SQLiteDatabase")
        assertRejected(classes, "trusted audit SQLite implementation")
    }

    @Test
    fun `app bytecode cannot subclass SQLiteOpenHelper`() {
        val classes = compileJava(
            "attack/RogueAuditHelper.java" to
                """
                package attack;
                import android.content.Context;
                import android.database.sqlite.SQLiteDatabase;
                import android.database.sqlite.SQLiteOpenHelper;
                public final class RogueAuditHelper extends SQLiteOpenHelper {
                    public RogueAuditHelper(Context context) {
                        super(context, "sentinel_audit.db", null, 1);
                    }
                    public void onCreate(SQLiteDatabase db) {}
                    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
                }
                """.trimIndent(),
        )

        assertRejected(classes, "SQLiteOpenHelper")
        assertRejected(classes, "sentinel_audit.db")
    }

    @Test
    fun `app bytecode cannot call Context openOrCreateDatabase or deleteDatabase`() {
        val classes = compileJava(
            "attack/ContextDatabaseBypass.java" to
                """
                package attack;
                import android.content.Context;
                public final class ContextDatabaseBypass {
                    void reach(Context context) {
                        context.openOrCreateDatabase("sentinel_audit.db", 0, null);
                        context.deleteDatabase("sentinel_audit.db");
                        context.getDatabasePath("sentinel_audit.db");
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "openOrCreateDatabase")
        assertRejected(classes, "deleteDatabase")
        assertRejected(classes, "getDatabasePath")
        assertRejected(classes, "sentinel_audit.db")
    }

    @Test
    fun `app bytecode cannot open the audit database file directly`() {
        val classes = compileJava(
            "attack/AuditFileBypass.java" to
                """
                package attack;
                import java.io.File;
                public final class AuditFileBypass {
                    File reach() {
                        return new File("sentinel_audit.db");
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "java/io/File")
        assertRejected(classes, "sentinel_audit.db")
    }

    @Test
    fun `unicode escaped audit database filename is still rejected from app bytecode`() {
        val classes = compileJava(
            "attack/UnicodeAuditFile.java" to
                """
                package attack;
                public final class UnicodeAuditFile {
                    String name() {
                        return "sentinel_audi\u0074.db";
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "sentinel_audit.db")
    }

    @Test
    fun `authorized audit SQLite helper may use SQLiteOpenHelper in device-management-impl`() {
        val classes = compileJava(
            "com/example/devicemanagement/audit/SentinelAuditOpenHelper.java" to
                """
                package com.example.devicemanagement.audit;
                import android.content.Context;
                import android.database.sqlite.SQLiteDatabase;
                import android.database.sqlite.SQLiteOpenHelper;
                public final class SentinelAuditOpenHelper extends SQLiteOpenHelper {
                    public SentinelAuditOpenHelper(Context context) {
                        super(context, "sentinel_audit.db", null, 1);
                    }
                    public void onCreate(SQLiteDatabase db) {}
                    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(
            violations.none { "trusted audit SQLite implementation" in it },
            violations.joinToString("\n"),
        )
        assertTrue(
            violations.none { "sentinel_audit.db" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `SQLite helper outside the trusted audit classes is rejected even in impl`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/RogueSqliteHelper.java" to
                """
                package com.example.devicemanagement.management;
                import android.content.Context;
                import android.database.sqlite.SQLiteDatabase;
                import android.database.sqlite.SQLiteOpenHelper;
                public final class RogueSqliteHelper extends SQLiteOpenHelper {
                    public RogueSqliteHelper(Context context) {
                        super(context, "sentinel_audit.db", null, 1);
                    }
                    public void onCreate(SQLiteDatabase db) {}
                    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "trusted audit SQLite implementation" in it })
    }

    private fun concreteCameraServiceStub(
        implementInterface: Boolean = false,
    ): Pair<String, String> {
        val implementsClause =
            if (implementInterface) " implements DevicePolicyCameraService" else ""
        return "com/example/devicemanagement/management/AndroidDevicePolicyCameraService.java" to
            """
            package com.example.devicemanagement.management;
            import android.app.admin.DevicePolicyManager;
            import android.content.ComponentName;
            public final class AndroidDevicePolicyCameraService$implementsClause {
                private final DevicePolicyManager manager;
                private final ComponentName adminComponent;
                public AndroidDevicePolicyCameraService(
                    DevicePolicyManager manager,
                    ComponentName adminComponent
                ) {
                    this.manager = manager;
                    this.adminComponent = adminComponent;
                }
                public boolean isCameraDisabled() {
                    return manager.getCameraDisabled(adminComponent);
                }
                public void setCameraDisabled(boolean disabled) {
                    manager.setCameraDisabled(adminComponent, disabled);
                }
            }
            """.trimIndent()
    }

    private fun concreteScreenCaptureServiceStub(
        implementInterface: Boolean = false,
    ): Pair<String, String> {
        val implementsClause =
            if (implementInterface) " implements DevicePolicyScreenCaptureService" else ""
        return "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService.java" to
            """
            package com.example.devicemanagement.management;
            import android.app.admin.DevicePolicyManager;
            import android.content.ComponentName;
            public final class AndroidDevicePolicyScreenCaptureService$implementsClause {
                private final DevicePolicyManager manager;
                private final ComponentName adminComponent;
                public AndroidDevicePolicyScreenCaptureService(
                    DevicePolicyManager manager,
                    ComponentName adminComponent
                ) {
                    this.manager = manager;
                    this.adminComponent = adminComponent;
                }
                public boolean isScreenCaptureDisabled() {
                    return manager.getScreenCaptureDisabled(adminComponent);
                }
                public void setScreenCaptureDisabled(boolean disabled) {
                    manager.setScreenCaptureDisabled(adminComponent, disabled);
                }
            }
            """.trimIndent()
    }

    private fun concreteStatusBarServiceStub(
        implementInterface: Boolean = false,
    ): Pair<String, String> {
        val implementsClause =
            if (implementInterface) " implements DevicePolicyStatusBarService" else ""
        return "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService.java" to
            """
            package com.example.devicemanagement.management;
            import android.app.admin.DevicePolicyManager;
            import android.content.ComponentName;
            public final class AndroidDevicePolicyStatusBarService$implementsClause {
                private final DevicePolicyManager manager;
                private final ComponentName adminComponent;
                public AndroidDevicePolicyStatusBarService(
                    DevicePolicyManager manager,
                    ComponentName adminComponent
                ) {
                    this.manager = manager;
                    this.adminComponent = adminComponent;
                }
                public boolean isStatusBarDisabled() {
                    return manager.isStatusBarDisabled();
                }
                public boolean setStatusBarDisabled(boolean disabled) {
                    return manager.setStatusBarDisabled(adminComponent, disabled);
                }
            }
            """.trimIndent()
    }

    private fun assertRejected(classes: File, expected: String) {
        val violations = verify(":app", classes)
        assertTrue(
            violations.any { expected in it },
            "Expected '$expected' in:\n${violations.joinToString("\n")}",
        )
    }

    private fun verify(artifact: String, classes: File): List<String> {
        return ProductionBytecodePolicyVerifier.verify(
            ProductionBytecodePolicyVerifier.classTargets(artifact, listOf(classes)),
        )
    }

    private fun compileJava(vararg sources: Pair<String, String>): File {
        val root = Files.createTempDirectory("policy-bytecode-fixture").toFile()
        val sourceRoot = File(root, "src").apply { mkdirs() }
        val classes = File(root, "classes").apply { mkdirs() }
        val fixtures = sources.toList() + platformStubs()
        val sourceFiles = fixtures.map { (path, source) ->
            File(sourceRoot, path).apply {
                parentFile.mkdirs()
                writeText(source)
            }
        }
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler())
        val result = compiler.run(
            null,
            null,
            null,
            "-source",
            "17",
            "-target",
            "17",
            "-d",
            classes.absolutePath,
            *sourceFiles.map(File::getAbsolutePath).toTypedArray(),
        )
        check(result == 0) { "Fixture javac failed with exit code $result" }
        return classes
    }

    private fun platformStubs(): List<Pair<String, String>> = listOf(
        "android/content/ComponentName.java" to
            """
            package android.content;
            public final class ComponentName {}
            """.trimIndent(),
        "android/content/Context.java" to
            """
            package android.content;
            import android.database.sqlite.SQLiteDatabase;
            import java.io.File;
            public class Context {
                public SQLiteDatabase openOrCreateDatabase(
                    String name,
                    int mode,
                    SQLiteDatabase.CursorFactory factory
                ) {
                    return null;
                }
                public boolean deleteDatabase(String name) {
                    return false;
                }
                public File getDatabasePath(String name) {
                    return new File(name);
                }
            }
            """.trimIndent(),
        "android/database/sqlite/SQLiteDatabase.java" to
            """
            package android.database.sqlite;
            public class SQLiteDatabase {
                public interface CursorFactory {}
                public static SQLiteDatabase openOrCreateDatabase(
                    String path,
                    CursorFactory factory
                ) {
                    return null;
                }
            }
            """.trimIndent(),
        "android/database/sqlite/SQLiteOpenHelper.java" to
            """
            package android.database.sqlite;
            import android.content.Context;
            public abstract class SQLiteOpenHelper {
                public SQLiteOpenHelper(
                    Context context,
                    String name,
                    SQLiteDatabase.CursorFactory factory,
                    int version
                ) {}
                public abstract void onCreate(SQLiteDatabase db);
                public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);
            }
            """.trimIndent(),
        "android/app/admin/DevicePolicyManager.java" to
            """
            package android.app.admin;
            import android.content.ComponentName;
            public class DevicePolicyManager {
                public void setCameraDisabled(ComponentName admin, boolean disabled) {}
                public void setScreenCaptureDisabled(ComponentName admin, boolean disabled) {}
                public boolean setStatusBarDisabled(ComponentName admin, boolean disabled) {
                    return true;
                }
                public boolean getCameraDisabled(ComponentName admin) { return false; }
                public boolean getScreenCaptureDisabled(ComponentName admin) { return false; }
                public boolean isStatusBarDisabled() { return false; }
                public void wipeData(int flags) {}
            }
            """.trimIndent(),
    )
}
