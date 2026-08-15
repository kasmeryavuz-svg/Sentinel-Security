import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(violations.any { "Checkpoint 17B-blocked" in it && "wipeData" in it })
    }

    @Test
    fun `wipeDevice remains non-allowlisted even in an authorized DPM class`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService.java" to
                """
                package com.example.devicemanagement.management;
                import android.app.admin.DevicePolicyManager;
                public final class AndroidDevicePolicyCameraService {
                    void apply(DevicePolicyManager manager) {
                        manager.wipeDevice(0);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "non-allowlisted" in it && "wipeDevice" in it })
        assertTrue(violations.any { "Checkpoint 17B-blocked" in it && "wipeDevice" in it })
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
    fun `System out diagnostic stream is rejected from production bytecode`() {
        val classes = compileJava(
            "attack/PrintlnLeak.java" to
                """
                package attack;
                public final class PrintlnLeak {
                    void leak(String value) {
                        System.out.println(value);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "System.out")
    }

    @Test
    fun `printStackTrace is rejected from production bytecode`() {
        val classes = compileJava(
            "attack/TraceLeak.java" to
                """
                package attack;
                public final class TraceLeak {
                    void leak(Exception error) {
                        error.printStackTrace();
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "printStackTrace")
    }

    @Test
    fun `workstation QR tooling may still use diagnostic streams`() {
        val classes = compileJava(
            "tool/QrPrinter.java" to
                """
                package tool;
                public final class QrPrinter {
                    void print(String payload) {
                        System.out.println(payload);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":provisioning-qr", classes)
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
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
    fun `custom ContextWrapper subclass inherited deleteDatabase is rejected`() {
        val classes = compileJava(
            customContextWrapperSubclass(
                className = "CustomDeleteDatabaseContext",
                methodName = "reach",
                statement = "deleteDatabase(\"sentinel_audit.db\");",
            ),
        )

        assertInvocationOwner(
            classes = classes,
            callerClass = "attack/CustomDeleteDatabaseContext",
            callerMethod = "reach",
            invokedName = "deleteDatabase",
            expectedOwner = "attack/CustomDeleteDatabaseContext",
        )
        assertRejected(classes, "attack/CustomDeleteDatabaseContext.deleteDatabase")
        assertRejected(classes, "trusted audit pipeline")
    }

    @Test
    fun `custom ContextWrapper subclass inherited getDatabasePath is rejected`() {
        val classes = compileJava(
            customContextWrapperSubclass(
                className = "CustomGetDatabasePathContext",
                methodName = "reach",
                statement = "getDatabasePath(\"sentinel_audit.db\");",
            ),
        )

        assertInvocationOwner(
            classes = classes,
            callerClass = "attack/CustomGetDatabasePathContext",
            callerMethod = "reach",
            invokedName = "getDatabasePath",
            expectedOwner = "attack/CustomGetDatabasePathContext",
        )
        assertRejected(classes, "attack/CustomGetDatabasePathContext.getDatabasePath")
        assertRejected(classes, "trusted audit pipeline")
    }

    @Test
    fun `custom ContextWrapper subclass inherited openOrCreateDatabase is rejected`() {
        val classes = compileJava(
            customContextWrapperSubclass(
                className = "CustomOpenOrCreateDatabaseContext",
                methodName = "reach",
                statement = "openOrCreateDatabase(\"sentinel_audit.db\", 0, null);",
            ),
        )

        assertInvocationOwner(
            classes = classes,
            callerClass = "attack/CustomOpenOrCreateDatabaseContext",
            callerMethod = "reach",
            invokedName = "openOrCreateDatabase",
            expectedOwner = "attack/CustomOpenOrCreateDatabaseContext",
        )
        assertRejected(classes, "attack/CustomOpenOrCreateDatabaseContext.openOrCreateDatabase")
        assertRejected(classes, "trusted audit pipeline")
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

    @Test
    fun `trusted audit identity class may embed the audit database filename`() {
        val classes = compileJava(
            "com/example/devicemanagement/audit/AuditSqliteIdentity.java" to
                """
                package com.example.devicemanagement.audit;
                public final class AuditSqliteIdentity {
                    public static final String DATABASE_NAME = "sentinel_audit.db";
                    public static final String TABLE_NAME = "audit_events";
                    private AuditSqliteIdentity() {}
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(
            violations.none { "sentinel_audit.db" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `app bytecode cannot call Context moveDatabaseFrom`() {
        val classes = compileJava(
            "attack/ContextMoveDatabaseBypass.java" to
                """
                package attack;
                import android.content.Context;
                public final class ContextMoveDatabaseBypass {
                    void reach(Context context, Context source) {
                        context.moveDatabaseFrom(source, "sentinel_audit.db");
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "moveDatabaseFrom")
        assertRejected(classes, "trusted audit pipeline")
        assertRejected(classes, "sentinel_audit.db")
    }

    @Test
    fun `custom ContextWrapper subclass inherited moveDatabaseFrom is rejected`() {
        val classes = compileJava(
            customContextWrapperSubclass(
                className = "CustomMoveDatabaseFromContext",
                methodName = "reach",
                statement = "moveDatabaseFrom(this, \"sentinel_audit.db\");",
            ),
        )

        assertInvocationOwner(
            classes = classes,
            callerClass = "attack/CustomMoveDatabaseFromContext",
            callerMethod = "reach",
            invokedName = "moveDatabaseFrom",
            expectedOwner = "attack/CustomMoveDatabaseFromContext",
        )
        assertRejected(classes, "attack/CustomMoveDatabaseFromContext.moveDatabaseFrom")
        assertRejected(classes, "trusted audit pipeline")
    }

    @Test
    fun `app bytecode cannot create the audit database through DatabaseUtils`() {
        val classes = compileJava(
            "attack/DatabaseUtilsCreateBypass.java" to
                """
                package attack;
                import android.content.Context;
                import android.database.DatabaseUtils;
                public final class DatabaseUtilsCreateBypass {
                    void reach(Context context) {
                        DatabaseUtils.createDbFromSqlStatements(
                            context,
                            "other.db",
                            1,
                            "CREATE TABLE audit_events (sequence INTEGER);"
                        );
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "DatabaseUtils")
        assertRejected(classes, "create or populate")
    }

    @Test
    fun `DatabaseUtils InsertHelper is rejected outside trusted audit classes`() {
        val classes = compileJava(
            "attack/DatabaseUtilsInsertHelperBypass.java" to
                """
                package attack;
                import android.database.DatabaseUtils;
                import android.database.sqlite.SQLiteDatabase;
                public final class DatabaseUtilsInsertHelperBypass {
                    Object reach(SQLiteDatabase db) {
                        return new DatabaseUtils.InsertHelper(db, "audit_events");
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "DatabaseUtils" in it && "create or populate" in it })
    }

    @Test
    fun `app bytecode cannot open audit files through android system Os`() {
        val classes = compileJava(
            "attack/OsOpenBypass.java" to
                """
                package attack;
                import android.system.Os;
                import java.io.FileDescriptor;
                public final class OsOpenBypass {
                    FileDescriptor reach(String path) throws Exception {
                        return Os.open(path, 2, 0600);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "android/system/Os.open")
        assertRejected(classes, "open, unlink, rename")
    }

    @Test
    fun `app bytecode cannot unlink rename truncate or chmod through Os`() {
        val classes = compileJava(
            "attack/OsMutateBypass.java" to
                """
                package attack;
                import android.system.Os;
                import java.io.FileDescriptor;
                public final class OsMutateBypass {
                    void reach(String path, FileDescriptor fd) throws Exception {
                        Os.unlink(path);
                        Os.rename(path, path + ".bak");
                        Os.truncate(path, 0L);
                        Os.ftruncate(fd, 0L);
                        Os.chmod(path, 0600);
                        Os.chown(path, 0, 0);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "android/system/Os.unlink")
        assertRejected(classes, "android/system/Os.rename")
        assertRejected(classes, "android/system/Os.truncate")
        assertRejected(classes, "android/system/Os.ftruncate")
        assertRejected(classes, "android/system/Os.chmod")
        assertRejected(classes, "android/system/Os.chown")
    }

    @Test
    fun `OsConstants file flags are rejected outside trusted audit classes`() {
        val classes = compileJava(
            "attack/OsConstantsBypass.java" to
                """
                package attack;
                import android.system.Os;
                import android.system.OsConstants;
                public final class OsConstantsBypass {
                    Object reach(String path) throws Exception {
                        return Os.open(path, OsConstants.O_RDWR | OsConstants.O_CREAT, 0600);
                    }
                }
                """.trimIndent(),
        )

        assertRejected(classes, "android/system/OsConstants")
        assertRejected(classes, "android/system/Os.open")
    }

    @Test
    fun `Os file syscalls are rejected from a future implementation class`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/RogueOsAuditMutator.java" to
                """
                package com.example.devicemanagement.management;
                import android.system.Os;
                public final class RogueOsAuditMutator {
                    void reach(String path) throws Exception {
                        Os.unlink(path);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "android/system/Os.unlink" in it })
    }

    @Test
    fun `unrelated Os getpid remains accepted from app bytecode`() {
        val classes = compileJava(
            "safe/OsGetpid.java" to
                """
                package safe;
                import android.system.Os;
                public final class OsGetpid {
                    int pid() {
                        return Os.getpid();
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":app", classes)
        assertTrue(
            violations.none { "android/system/Os" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `direct SensitiveActionAuditWriter append outside controller is rejected`() {
        val classes = compileJava(
            auditWriterStub(),
            *auditAppendTypes(),
            "attack/DirectAuditWriterAppendBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.audit.AuditAppendRequest;
                import com.example.devicemanagement.audit.SensitiveActionAuditWriter;
                public final class DirectAuditWriterAppendBypass {
                    void reach(SensitiveActionAuditWriter writer, AuditAppendRequest request) {
                        writer.append(request);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "outside DefaultSensitiveActionController" in it })
        assertTrue(violations.any { "SensitiveActionAuditWriter.append" in it })
    }

    @Test
    fun `direct DurableAuditRepository append outside controller is rejected`() {
        val classes = compileJava(
            auditWriterStub(),
            durableAuditRepositoryStub(),
            *auditAppendTypes(),
            "attack/DirectDurableRepositoryAppendBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.audit.AuditAppendRequest;
                import com.example.devicemanagement.audit.DurableAuditRepository;
                public final class DirectDurableRepositoryAppendBypass {
                    void reach(DurableAuditRepository repository, AuditAppendRequest request) {
                        repository.append(request);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "outside DefaultSensitiveActionController" in it })
        assertTrue(violations.any { "DurableAuditRepository.append" in it })
    }

    @Test
    fun `cast from audit writer interface to concrete repository append is rejected`() {
        val classes = compileJava(
            auditWriterStub(),
            durableAuditRepositoryStub(implementInterface = true),
            *auditAppendTypes(),
            "attack/CastDurableRepositoryAppendBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.audit.AuditAppendRequest;
                import com.example.devicemanagement.audit.DurableAuditRepository;
                import com.example.devicemanagement.audit.SensitiveActionAuditWriter;
                public final class CastDurableRepositoryAppendBypass {
                    void reach(SensitiveActionAuditWriter writer, AuditAppendRequest request) {
                        ((DurableAuditRepository) writer).append(request);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "outside DefaultSensitiveActionController" in it })
        assertTrue(violations.any { "DurableAuditRepository.append" in it })
    }

    @Test
    fun `authorized DefaultSensitiveActionController may append through the writer`() {
        val classes = compileJava(
            auditWriterStub(),
            *auditAppendTypes(),
            "com/example/devicemanagement/trigger/Trigger.java" to
                """
                package com.example.devicemanagement.trigger;
                public final class Trigger {}
                """.trimIndent(),
            "com/example/devicemanagement/action/ActionResult.java" to
                """
                package com.example.devicemanagement.action;
                public abstract class ActionResult {}
                """.trimIndent(),
            "com/example/devicemanagement/action/DefaultSensitiveActionController.java" to
                """
                package com.example.devicemanagement.action;
                import com.example.devicemanagement.audit.SensitiveActionAuditWriter;
                import com.example.devicemanagement.trigger.Trigger;
                public final class DefaultSensitiveActionController {
                    private final SensitiveActionAuditWriter auditWriter;
                    public DefaultSensitiveActionController(SensitiveActionAuditWriter auditWriter) {
                        this.auditWriter = auditWriter;
                    }
                    public ActionResult submit(Trigger trigger) {
                        auditWriter.append(null);
                        auditWriter.append(null);
                        return null;
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.none { "outside DefaultSensitiveActionController" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `recovery class cannot submit through SensitiveActionController`() {
        val classes = compileJava(
            "com/example/devicemanagement/action/SensitiveActionController.java" to
                """
                package com.example.devicemanagement.action;
                import com.example.devicemanagement.trigger.Trigger;
                public interface SensitiveActionController {
                    Object submit(Trigger trigger);
                }
                """.trimIndent(),
            "com/example/devicemanagement/trigger/Trigger.java" to
                """
                package com.example.devicemanagement.trigger;
                public final class Trigger {}
                """.trimIndent(),
            "com/example/devicemanagement/recovery/RogueRecoverySubmit.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.action.SensitiveActionController;
                import com.example.devicemanagement.trigger.Trigger;
                public final class RogueRecoverySubmit {
                    void replay(SensitiveActionController controller, Trigger trigger) {
                        controller.submit(trigger);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it })
        assertTrue(violations.any { "submit" in it })
    }

    @Test
    fun `recovery class cannot issue an ApprovalAuthority capability`() {
        val classes = compileJava(
            "com/example/devicemanagement/action/ApprovalAuthority.java" to
                """
                package com.example.devicemanagement.action;
                public final class ApprovalAuthority {
                    public Object issue(Object request, long issuedAt) {
                        return request;
                    }
                }
                """.trimIndent(),
            "com/example/devicemanagement/recovery/RogueRecoveryApproval.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.action.ApprovalAuthority;
                public final class RogueRecoveryApproval {
                    Object replay(ApprovalAuthority authority) {
                        return authority.issue(null, 0L);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it })
        assertTrue(violations.any { "ApprovalAuthority" in it })
    }

    @Test
    fun `recovery class cannot call ActionExecutor`() {
        val classes = compileJava(
            "com/example/devicemanagement/action/ActionExecutor.java" to
                """
                package com.example.devicemanagement.action;
                public final class ActionExecutor {
                    public Object execute(Object decision) {
                        return decision;
                    }
                }
                """.trimIndent(),
            "com/example/devicemanagement/recovery/RogueRecoveryExecutor.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.action.ActionExecutor;
                public final class RogueRecoveryExecutor {
                    Object replay(ActionExecutor executor) {
                        return executor.execute(null);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it })
        assertTrue(violations.any { "ActionExecutor" in it })
    }

    @Test
    fun `recovery class cannot mutate DevicePolicyManager`() {
        val classes = compileJava(
            "com/example/devicemanagement/recovery/RogueRecoveryDpm.java" to
                """
                package com.example.devicemanagement.recovery;
                import android.app.admin.DevicePolicyManager;
                import android.content.ComponentName;
                public final class RogueRecoveryDpm {
                    void replay(DevicePolicyManager manager, ComponentName admin) {
                        manager.setCameraDisabled(admin, true);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it || "DevicePolicyManager" in it })
        assertTrue(violations.any { "setCameraDisabled" in it })
    }

    @Test
    fun `recovery class cannot insert audit store records`() {
        val classes = compileJava(
            auditRecordStoreStub(),
            newAuditRecordStub(),
            "com/example/devicemanagement/recovery/RogueRecoveryAuditInsert.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.audit.AuditRecordStore;
                import com.example.devicemanagement.audit.NewAuditRecord;
                public final class RogueRecoveryAuditInsert {
                    void forge(AuditRecordStore store, NewAuditRecord record) {
                        store.insert(record);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it || "outside DurableAuditRepository" in it })
        assertTrue(violations.any { "insert" in it })
    }

    @Test
    fun `recovery class may read AuditHistoryProvider latest`() {
        val classes = compileJava(
            "com/example/devicemanagement/audit/AuditHistory.java" to
                """
                package com.example.devicemanagement.audit;
                public final class AuditHistory {}
                """.trimIndent(),
            "com/example/devicemanagement/audit/AuditHistoryProvider.java" to
                """
                package com.example.devicemanagement.audit;
                public interface AuditHistoryProvider {
                    AuditHistory latest(int limit);
                }
                """.trimIndent(),
            "com/example/devicemanagement/recovery/SafeRecoveryInspect.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.audit.AuditHistoryProvider;
                public final class SafeRecoveryInspect {
                    Object inspect(AuditHistoryProvider history) {
                        return history.latest(20);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.none { "recovery code" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `controller append from a non-submit method is rejected`() {
        val classes = compileJava(
            auditWriterStub(),
            *auditAppendTypes(),
            "com/example/devicemanagement/action/DefaultSensitiveActionController.java" to
                """
                package com.example.devicemanagement.action;
                import com.example.devicemanagement.audit.SensitiveActionAuditWriter;
                public final class DefaultSensitiveActionController {
                    void bypass(SensitiveActionAuditWriter writer) {
                        writer.append(null);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "outside DefaultSensitiveActionController" in it })
    }

    @Test
    fun `direct SqliteAuditRecordStore insert from rogue implementation class is rejected`() {
        val classes = compileJava(
            auditRecordStoreStub(),
            sqliteAuditRecordStoreStub(),
            newAuditRecordStub(),
            "attack/RogueSqliteInsertBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.audit.NewAuditRecord;
                import com.example.devicemanagement.audit.SqliteAuditRecordStore;
                public final class RogueSqliteInsertBypass {
                    void forge(SqliteAuditRecordStore store, NewAuditRecord record) {
                        store.insert(record);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "outside DurableAuditRepository" in it })
        assertTrue(violations.any { "SqliteAuditRecordStore.insert" in it })
    }

    @Test
    fun `AuditRecordStore interface insert outside DurableAuditRepository is rejected`() {
        val classes = compileJava(
            auditRecordStoreStub(),
            newAuditRecordStub(),
            "attack/RogueAuditRecordStoreInsertBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.audit.AuditRecordStore;
                import com.example.devicemanagement.audit.NewAuditRecord;
                public final class RogueAuditRecordStoreInsertBypass {
                    void forge(AuditRecordStore store, NewAuditRecord record) {
                        store.insert(record);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "outside DurableAuditRepository" in it })
        assertTrue(violations.any { "AuditRecordStore.insert" in it })
    }

    @Test
    fun `cast from audit store interface to concrete SqliteAuditRecordStore insert is rejected`() {
        val classes = compileJava(
            auditRecordStoreStub(),
            sqliteAuditRecordStoreStub(implementInterface = true),
            newAuditRecordStub(),
            "attack/CastSqliteInsertBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.audit.AuditRecordStore;
                import com.example.devicemanagement.audit.NewAuditRecord;
                import com.example.devicemanagement.audit.SqliteAuditRecordStore;
                public final class CastSqliteInsertBypass {
                    void forge(AuditRecordStore store, NewAuditRecord record) {
                        ((SqliteAuditRecordStore) store).insert(record);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "outside DurableAuditRepository" in it })
        assertTrue(violations.any { "SqliteAuditRecordStore.insert" in it })
    }

    @Test
    fun `direct SqliteAuditRecordStore deleteOldest outside DurableAuditRepository is rejected`() {
        val classes = compileJava(
            auditRecordStoreStub(),
            sqliteAuditRecordStoreStub(),
            newAuditRecordStub(),
            "attack/RogueSqliteDeleteOldestBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.audit.SqliteAuditRecordStore;
                public final class RogueSqliteDeleteOldestBypass {
                    void prune(SqliteAuditRecordStore store) {
                        store.deleteOldest(1);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "outside DurableAuditRepository" in it })
        assertTrue(violations.any { "SqliteAuditRecordStore.deleteOldest" in it })
    }

    @Test
    fun `AuditRecordStore interface deleteOldest outside DurableAuditRepository is rejected`() {
        val classes = compileJava(
            auditRecordStoreStub(),
            newAuditRecordStub(),
            "attack/RogueAuditRecordStoreDeleteOldestBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.audit.AuditRecordStore;
                public final class RogueAuditRecordStoreDeleteOldestBypass {
                    void prune(AuditRecordStore store) {
                        store.deleteOldest(1);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "outside DurableAuditRepository" in it })
        assertTrue(violations.any { "AuditRecordStore.deleteOldest" in it })
    }

    @Test
    fun `authorized DurableAuditRepository append may insert and prune through the store`() {
        val classes = compileJava(
            auditRecordStoreStub(),
            newAuditRecordStub(),
            *auditAppendTypes(),
            "com/example/devicemanagement/audit/DurableAuditRepository.java" to
                """
                package com.example.devicemanagement.audit;
                public final class DurableAuditRepository {
                    private final AuditRecordStore records;
                    public DurableAuditRepository(AuditRecordStore records) {
                        this.records = records;
                    }
                    public AuditAppendResult append(AuditAppendRequest request) {
                        records.insert(null);
                        records.count();
                        records.deleteOldest(1);
                        return null;
                    }
                    public void latest(int limit) {
                        records.latest(limit);
                        records.count();
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.none { "outside DurableAuditRepository" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `repository store mutation from a non-append method is rejected`() {
        val classes = compileJava(
            auditRecordStoreStub(),
            newAuditRecordStub(),
            "com/example/devicemanagement/audit/DurableAuditRepository.java" to
                """
                package com.example.devicemanagement.audit;
                public final class DurableAuditRepository {
                    void bypass(AuditRecordStore store, NewAuditRecord record) {
                        store.insert(record);
                        store.deleteOldest(1);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "outside DurableAuditRepository" in it })
        assertTrue(violations.any { "AuditRecordStore.insert" in it })
        assertTrue(violations.any { "AuditRecordStore.deleteOldest" in it })
    }

    @Test
    fun `direct SqliteDenyOnlyMarkerStore persist from rogue class is rejected`() {
        val classes = compileJava(
            denyOnlyMarkerMediumStub(),
            sqliteDenyOnlyMarkerStoreStub(),
            "attack/RogueDenyOnlyMarkerPersistBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.persistence.SqliteDenyOnlyMarkerStore;
                public final class RogueDenyOnlyMarkerPersistBypass {
                    void forge(SqliteDenyOnlyMarkerStore store) {
                        store.persistEncodedMarker(new byte[0]);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "destructive-safety persistence mutation" in it })
        assertTrue(violations.any { "SqliteDenyOnlyMarkerStore.persistEncodedMarker" in it })
    }

    @Test
    fun `direct SqliteDestructivePreExecutionStore insert from rogue class is rejected`() {
        val classes = compileJava(
            destructivePreExecutionStoreStub(),
            sqliteDestructivePreExecutionStoreStub(),
            destructivePreExecutionRecordStub(),
            "attack/RogueDestructiveEvidenceInsertBypass.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableRecord;
                import com.example.devicemanagement.persistence.SqliteDestructivePreExecutionStore;
                public final class RogueDestructiveEvidenceInsertBypass {
                    void forge(SqliteDestructivePreExecutionStore store, DestructivePreExecutionDurableRecord record) {
                        store.insert(record);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(violations.any { "destructive-safety persistence mutation" in it })
        assertTrue(violations.any { "SqliteDestructivePreExecutionStore.insert" in it })
    }

    @Test
    fun `recovery class cannot reference durable destructive evidence store`() {
        val classes = compileJava(
            destructivePreExecutionStoreStub(),
            destructivePreExecutionRecordStub(),
            "com/example/devicemanagement/recovery/RogueRecoveryDestructiveEvidence.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore;
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableRecord;
                public final class RogueRecoveryDestructiveEvidence {
                    void forge(DestructivePreExecutionDurableStore store, DestructivePreExecutionDurableRecord record) {
                        store.insert(record);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it || "destructive-safety persistence mutation" in it })
    }

    @Test
    fun `rogue caller cannot mint runtime durability`() {
        val classes = compileJava(
            denyOnlyMarkerMediumStub(),
            destructivePreExecutionStoreStub(),
            destructivePreExecutionRecordStub(),
            runtimeDestructiveSafetyDurabilityStub(),
            "attack/RogueRuntimeDurabilityMint.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore;
                import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability;
                import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium;
                public final class RogueRuntimeDurabilityMint {
                    void forge(DenyOnlyMarkerDurableMedium medium, DestructivePreExecutionDurableStore store) {
                        RuntimeDestructiveSafetyDurability.issueFromTrustedAndroidStores(medium, store);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "runtime durability issuance" in it })
        assertTrue(violations.any { "issueFromTrustedAndroidStores" in it })
    }

    @Test
    fun `authorized Android factory may mint runtime durability`() {
        val classes = compileJava(
            denyOnlyMarkerMediumStub(),
            destructivePreExecutionStoreStub(),
            destructivePreExecutionRecordStub(),
            runtimeDestructiveSafetyDurabilityStub(),
            structuredLoggerStub(),
            "com/example/devicemanagement/persistence/AndroidDestructiveSafetyPersistence.java" to
                """
                package com.example.devicemanagement.persistence;
                import android.content.Context;
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore;
                import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability;
                import com.example.devicemanagement.logging.StructuredLogger;
                public final class AndroidDestructiveSafetyPersistence {
                    public RuntimeDestructiveSafetyDurability issueRuntimeDurability(
                        Context context,
                        StructuredLogger logger
                    ) {
                        DenyOnlyMarkerDurableMedium medium = null;
                        DestructivePreExecutionDurableStore store = null;
                        return RuntimeDestructiveSafetyDurability.issueFromTrustedAndroidStores(medium, store);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":device-management-impl", classes)
        assertTrue(
            violations.none { "runtime durability issuance" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `recovery class cannot reference runtime durability capability`() {
        val classes = compileJava(
            denyOnlyMarkerMediumStub(),
            destructivePreExecutionStoreStub(),
            destructivePreExecutionRecordStub(),
            runtimeDestructiveSafetyDurabilityStub(),
            "com/example/devicemanagement/recovery/RogueRecoveryRuntimeDurability.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability;
                public final class RogueRecoveryRuntimeDurability {
                    RuntimeDestructiveSafetyDurability capability;
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it })
        assertTrue(violations.any { "RuntimeDestructiveSafetyDurability" in it })
    }

    @Test
    fun `recovery class cannot reference destructive human approval`() {
        val classes = compileJava(
            "com/example/devicemanagement/destructive/DestructiveHumanApprovalAuthority.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveHumanApprovalAuthority {}
                """.trimIndent(),
            "com/example/devicemanagement/recovery/RogueRecoveryHumanApproval.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.destructive.DestructiveHumanApprovalAuthority;
                public final class RogueRecoveryHumanApproval {
                    DestructiveHumanApprovalAuthority authority;
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it })
        assertTrue(violations.any { "DestructiveHumanApprovalAuthority" in it })
    }

    @Test
    fun `rogue caller cannot mint trusted artifact expectation`() {
        val classes = compileJava(
            *trustedArtifactExpectationStubs(),
            "attack/RogueTrustedArtifactExpectationMint.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.DestructiveArtifactBuildPurpose;
                import com.example.devicemanagement.destructive.DestructiveArtifactIdentityExpectation;
                public final class RogueTrustedArtifactExpectationMint {
                    void forge() {
                        DestructiveArtifactIdentityExpectation.issueFromTrustedValidationSource(
                            "aa", "bb", "com.example.app", "com.example.app/Admin",
                            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION
                        );
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "trusted artifact expectation issuance" in it })
        assertTrue(violations.any { "issueFromTrustedValidationSource" in it })
    }

    @Test
    fun `authorized validation source may mint trusted artifact expectation`() {
        val classes = compileJava(
            *trustedArtifactExpectationStubs(),
            "com/example/devicemanagement/destructive/TrustedDestructiveArtifactValidationSource.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class TrustedDestructiveArtifactValidationSource {
                    public DestructiveArtifactIdentityExpectation trustedExpectation() {
                        return DestructiveArtifactIdentityExpectation.issueFromTrustedValidationSource(
                            "aa", "bb", "com.example.app", "com.example.app/Admin",
                            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION
                        );
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.none { "trusted artifact expectation issuance" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `recovery class cannot reference trusted artifact expectation factory`() {
        val classes = compileJava(
            *trustedArtifactExpectationStubs(),
            "com/example/devicemanagement/recovery/RogueRecoveryArtifactExpectation.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.destructive.DestructiveArtifactIdentityExpectation;
                public final class RogueRecoveryArtifactExpectation {
                    DestructiveArtifactIdentityExpectation expectation;
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it })
        assertTrue(violations.any { "DestructiveArtifactIdentityExpectation" in it })
    }

    @Test
    fun `rogue caller cannot mint human confirmation`() {
        val classes = compileJava(
            *humanConfirmationMintStubs(),
            "attack/RogueHumanConfirmationMint.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.DestructiveHumanConfirmationMint;
                import com.example.devicemanagement.destructive.DestructiveOperatorChallenge;
                public final class RogueHumanConfirmationMint {
                    void forge(DestructiveOperatorChallenge challenge) {
                        DestructiveHumanConfirmationMint.issueFromTrustedConfirmationSource(
                            challenge, null, null, null, null, null, 0L
                        );
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "human confirmation issuance" in it })
        assertTrue(violations.any { "issueFromTrustedConfirmationSource" in it })
    }

    @Test
    fun `confirmation authority may mint human confirmation`() {
        val classes = compileJava(
            *humanConfirmationMintStubs(),
            "com/example/devicemanagement/destructive/DestructiveHumanConfirmationAuthority.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveHumanConfirmationAuthority {
                    public DestructiveHumanConfirmationResult confirm(
                        DestructiveOperatorChallenge challenge
                    ) {
                        DestructiveHumanConfirmationMint.issueFromTrustedConfirmationSource(
                            challenge, null, null, null, null, null, 0L
                        );
                        return null;
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.none { "human confirmation issuance" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `rogue caller cannot invoke human confirmation authority`() {
        val classes = compileJava(
            *humanConfirmationMintStubs(),
            "com/example/devicemanagement/destructive/DestructiveHumanConfirmationAuthority.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveHumanConfirmationAuthority {
                    public DestructiveHumanConfirmationResult confirm(
                        DestructiveOperatorChallenge challenge
                    ) {
                        return null;
                    }
                }
                """.trimIndent(),
            "attack/RogueHumanConfirmationConfirm.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.DestructiveHumanConfirmationAuthority;
                import com.example.devicemanagement.destructive.DestructiveOperatorChallenge;
                public final class RogueHumanConfirmationConfirm {
                    void forge(
                        DestructiveHumanConfirmationAuthority authority,
                        DestructiveOperatorChallenge challenge
                    ) {
                        authority.confirm(challenge);
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "human confirmation" in it })
        assertTrue(violations.any { "outside an unwired" in it })
    }

    @Test
    fun `rogue Kotlin caller cannot mint trusted artifact expectation`() {
        val classes = compileKotlin(
            *trustedArtifactExpectationKotlinStubs(),
            "attack/RogueKotlinArtifactExpectationMint.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.DestructiveArtifactBuildPurpose
                import com.example.devicemanagement.destructive.DestructiveArtifactIdentityExpectation
                class RogueKotlinArtifactExpectationMint {
                    fun forge() {
                        DestructiveArtifactIdentityExpectation.issueFromTrustedValidationSource(
                            "aa", "bb", "com.example.app", "com.example.app/Admin",
                            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
                        )
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "trusted artifact expectation issuance" in it })
        assertTrue(violations.any { "issueFromTrustedValidationSource" in it })
    }

    @Test
    fun `rogue Kotlin explicit companion cannot mint trusted artifact expectation`() {
        val classes = compileKotlin(
            *trustedArtifactExpectationKotlinStubs(),
            "attack/RogueKotlinNamedCompanionArtifactMint.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.DestructiveArtifactBuildPurpose
                import com.example.devicemanagement.destructive.DestructiveArtifactIdentityExpectation
                class RogueKotlinNamedCompanionArtifactMint {
                    fun forge() {
                        DestructiveArtifactIdentityExpectation
                            .TrustedDestructiveArtifactExpectationFactory
                            .issueFromTrustedValidationSource(
                                "aa", "bb", "com.example.app", "com.example.app/Admin",
                                DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
                            )
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "trusted artifact expectation issuance" in it })
        assertTrue(violations.any { "issueFromTrustedValidationSource" in it })
    }

    @Test
    fun `rogue Kotlin caller cannot mint runtime durability`() {
        val classes = compileKotlin(
            *runtimeDurabilityKotlinStubs(),
            "attack/RogueKotlinRuntimeDurabilityMint.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore
                import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability
                import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
                class RogueKotlinRuntimeDurabilityMint {
                    fun forge(
                        medium: DenyOnlyMarkerDurableMedium,
                        store: DestructivePreExecutionDurableStore,
                    ) {
                        RuntimeDestructiveSafetyDurability.issueFromTrustedAndroidStores(medium, store)
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "runtime durability issuance" in it })
        assertTrue(violations.any { "issueFromTrustedAndroidStores" in it })
    }

    @Test
    fun `rogue Kotlin explicit Companion cannot mint runtime durability`() {
        val classes = compileKotlin(
            *runtimeDurabilityKotlinStubs(),
            "attack/RogueKotlinCompanionRuntimeDurabilityMint.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore
                import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability
                import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
                class RogueKotlinCompanionRuntimeDurabilityMint {
                    fun forge(
                        medium: DenyOnlyMarkerDurableMedium,
                        store: DestructivePreExecutionDurableStore,
                    ) {
                        RuntimeDestructiveSafetyDurability.Companion
                            .issueFromTrustedAndroidStores(medium, store)
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "runtime durability issuance" in it })
        assertTrue(violations.any { "issueFromTrustedAndroidStores" in it })
    }

    @Test
    fun `rogue caller cannot invoke future executor execute`() {
        val classes = compileJava(
            *realChainHandoffStubs(),
            "attack/RogueFutureExecutorExecute.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary;
                public final class RogueFutureExecutorExecute {
                    void forge(
                        FutureDestructiveRealChainBoundary.FutureDestructiveExecutorContract executor,
                        FutureDestructiveRealChainBoundary.FutureDestructiveExecutionBundle bundle
                    ) {
                        executor.execute(bundle);
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "future executor" in it })
        assertTrue(violations.any { "assembleAndHandoff" in it })
    }

    @Test
    fun `authorized assembleAndHandoff may invoke future executor execute`() {
        val classes = compileJava(
            "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class FutureDestructiveRealChainBoundary {
                    public static class FutureDestructiveExecutionBundle {}
                    public static abstract class FutureDestructiveExecutorContract {
                        public Object execute(FutureDestructiveExecutionBundle bundle) { return null; }
                    }
                    public Object assembleAndHandoff(
                        FutureDestructiveExecutorContract executor,
                        FutureDestructiveExecutionBundle bundle
                    ) {
                        return executor.execute(bundle);
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.none { "future executor" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `rogue caller cannot mint final live validation permit`() {
        val classes = compileJava(
            *realChainHandoffStubs(),
            "attack/RogueFinalPermitMint.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary;
                public final class RogueFinalPermitMint {
                    Object forge(FutureDestructiveRealChainBoundary boundary) {
                        return boundary.mintFinalLiveValidationPermit();
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "real-chain handoff mint" in it })
        assertTrue(violations.any { "mintFinalLiveValidationPermit" in it })
    }

    @Test
    fun `rogue caller cannot construct a future execution bundle`() {
        val classes = compileJava(
            *realChainHandoffStubs(),
            "attack/RogueFutureBundleInit.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary;
                public final class RogueFutureBundleInit {
                    Object forge() {
                        return new FutureDestructiveRealChainBoundary.FutureDestructiveExecutionBundle();
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "constructs real-chain handoff material" in it })
    }

    @Test
    fun `rogue companion create cannot mint a real-chain bundle`() {
        val classes = compileJava(
            *realChainHandoffStubs(),
            "attack/RogueBundleCompanionCreate.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary;
                public final class RogueBundleCompanionCreate {
                    Object forge() {
                        return FutureDestructiveRealChainBoundary.FutureDestructiveExecutionBundle.Companion.create();
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.any { "forbidden real-chain companion mint" in it || "constructs real-chain handoff material" in it },
        )
    }

    @Test
    fun `method handle lookup of future executor execute is rejected`() {
        val classes = compileJava(
            *realChainHandoffStubs(),
            "attack/RogueFutureExecutorHandle.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary;
                import java.lang.invoke.MethodHandles;
                import java.lang.invoke.MethodType;
                public final class RogueFutureExecutorHandle {
                    Object forge() throws Exception {
                        return MethodHandles.lookup().findVirtual(
                            FutureDestructiveRealChainBoundary.FutureDestructiveExecutorContract.class,
                            "execute",
                            MethodType.methodType(
                                Object.class,
                                FutureDestructiveRealChainBoundary.FutureDestructiveExecutionBundle.class
                            )
                        );
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.any { "method handles" in it || "future executor" in it },
        )
    }

    @Test
    fun `rogue caller cannot invoke onAuthorizedHandoff`() {
        val classes = compileJava(
            *realChainHandoffStubs(),
            "attack/RogueOnAuthorizedHandoff.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary;
                public final class RogueOnAuthorizedHandoff {
                    Object forge(
                        FutureDestructiveRealChainBoundary.FutureDestructiveExecutorContract executor
                    ) {
                        return executor.onAuthorizedHandoff();
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "onAuthorizedHandoff" in it })
        assertTrue(violations.any { "FutureDestructiveExecutorContract.execute" in it })
    }

    @Test
    fun `rogue caller cannot register a forged handoff bundle`() {
        val classes = compileJava(
            *realChainHandoffStubs(),
            "attack/RogueRegisterIssuedBundle.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary;
                public final class RogueRegisterIssuedBundle {
                    void forge(
                        FutureDestructiveRealChainBoundary boundary,
                        FutureDestructiveRealChainBoundary.FutureDestructiveExecutionBundle bundle
                    ) {
                        boundary.registerIssuedBundle(bundle);
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "real-chain handoff mint" in it })
        assertTrue(violations.any { "registerIssuedBundle" in it })
    }

    @Test
    fun `rogue caller cannot construct a top-level future execution bundle`() {
        val classes = compileJava(
            *realChainHandoffStubs(),
            "attack/RogueTopLevelBundleInit.java" to
                """
                package attack;
                import com.example.devicemanagement.destructive.FutureDestructiveExecutionBundle;
                public final class RogueTopLevelBundleInit {
                    Object forge() {
                        return new FutureDestructiveExecutionBundle();
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "constructs real-chain handoff material" in it })
    }

    @Test
    fun `rogue Kotlin live-validation mint object cannot mint a permit`() {
        val classes = compileKotlin(
            *realChainHandoffKotlinStubs(),
            "attack/RogueKotlinLiveValidationMint.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary
                class RogueKotlinLiveValidationMint {
                    fun forge() =
                        FutureDestructiveRealChainBoundary.RealChainFinalLiveValidationPermit
                            .LiveValidationMint.mintFinalLiveValidationPermit()
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "real-chain handoff mint" in it || "mintFinalLiveValidationPermit" in it })
    }

    @Test
    fun `rogue Kotlin method reference cannot mint a final live validation permit`() {
        val classes = compileKotlin(
            *realChainHandoffKotlinStubs(),
            "attack/RogueKotlinPermitMintHandle.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary
                class RogueKotlinPermitMintHandle {
                    fun forge(
                        boundary: FutureDestructiveRealChainBoundary,
                    ): () -> Any? {
                        return boundary::mintFinalLiveValidationPermit
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.any {
                "real-chain handoff mint" in it ||
                    "mintFinalLiveValidationPermit" in it ||
                    "invokedynamic" in it ||
                    "method handle" in it
            },
        )
    }

    @Test
    fun `rogue Kotlin method reference cannot invoke future executor execute`() {
        val classes = compileKotlin(
            *realChainHandoffKotlinStubs(),
            "attack/RogueKotlinFutureExecutorHandle.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.FutureDestructiveRealChainBoundary
                class RogueKotlinFutureExecutorHandle {
                    fun forge(
                        executor: FutureDestructiveRealChainBoundary.FutureDestructiveExecutorContract,
                    ): (
                        FutureDestructiveRealChainBoundary.FutureDestructiveExecutionBundle
                    ) -> Any? {
                        return executor::execute
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.any { "future executor" in it || "invokedynamic" in it || "method handle" in it },
        )
    }

    @Test
    fun `rogue Kotlin method references cannot mint trusted artifact expectation`() {
        val classes = compileKotlin(
            *trustedArtifactExpectationKotlinStubs(),
            "attack/RogueKotlinArtifactExpectationHandle.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.DestructiveArtifactBuildPurpose
                import com.example.devicemanagement.destructive.DestructiveArtifactIdentityExpectation
                class RogueKotlinArtifactExpectationHandle {
                    fun forge() {
                        val outer = DestructiveArtifactIdentityExpectation::issueFromTrustedValidationSource
                        outer(
                            "aa", "bb", "com.example.app", "com.example.app/Admin",
                            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
                        )
                        val named = DestructiveArtifactIdentityExpectation
                            .TrustedDestructiveArtifactExpectationFactory::issueFromTrustedValidationSource
                        named(
                            "aa", "bb", "com.example.app", "com.example.app/Admin",
                            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
                        )
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "trusted artifact expectation issuance" in it })
        assertTrue(violations.any { "issueFromTrustedValidationSource" in it })
    }

    @Test
    fun `rogue Kotlin method references cannot mint runtime durability`() {
        val classes = compileKotlin(
            *runtimeDurabilityKotlinStubs(),
            "attack/RogueKotlinRuntimeDurabilityHandle.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore
                import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability
                import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
                class RogueKotlinRuntimeDurabilityHandle {
                    fun forge(
                        medium: DenyOnlyMarkerDurableMedium,
                        store: DestructivePreExecutionDurableStore,
                    ) {
                        val outer = RuntimeDestructiveSafetyDurability::issueFromTrustedAndroidStores
                        outer(medium, store)
                        val companion = RuntimeDestructiveSafetyDurability.Companion::issueFromTrustedAndroidStores
                        companion(medium, store)
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "runtime durability issuance" in it })
        assertTrue(violations.any { "issueFromTrustedAndroidStores" in it })
    }

    @Test
    fun `rogue Kotlin caller cannot invoke dedicated artifact expectation mint`() {
        val classes = compileKotlin(
            *trustedArtifactExpectationKotlinStubs(),
            "attack/RogueKotlinDedicatedArtifactMint.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.DestructiveArtifactBuildPurpose
                import com.example.devicemanagement.destructive.DestructiveArtifactIdentityExpectation
                class RogueKotlinDedicatedArtifactMint {
                    fun forge() {
                        DestructiveArtifactIdentityExpectation
                            .TrustedDestructiveArtifactExpectationMint
                            .issueFromTrustedValidationSource(
                                "aa", "bb", "com.example.app", "com.example.app/Admin",
                                DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
                            )
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "trusted artifact expectation issuance" in it })
        assertTrue(violations.any { "issueFromTrustedValidationSource" in it })
    }

    @Test
    fun `rogue Kotlin caller cannot invoke dedicated runtime durability mint`() {
        val classes = compileKotlin(
            *runtimeDurabilityKotlinStubs(),
            "attack/RogueKotlinDedicatedRuntimeDurabilityMint.kt" to
                """
                package attack
                import com.example.devicemanagement.destructive.DestructivePreExecutionDurableStore
                import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability
                import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
                class RogueKotlinDedicatedRuntimeDurabilityMint {
                    fun forge(
                        medium: DenyOnlyMarkerDurableMedium,
                        store: DestructivePreExecutionDurableStore,
                    ) {
                        RuntimeDestructiveSafetyDurability.RuntimeDestructiveSafetyDurabilityMint
                            .issueFromTrustedAndroidStores(medium, store)
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "runtime durability issuance" in it })
        assertTrue(violations.any { "issueFromTrustedAndroidStores" in it })
    }

    @Test
    fun `authorized Kotlin validation source may mint trusted artifact expectation`() {
        val classes = compileKotlin(
            *trustedArtifactExpectationKotlinStubs(),
            "com/example/devicemanagement/destructive/TrustedDestructiveArtifactValidationSource.kt" to
                """
                package com.example.devicemanagement.destructive
                object TrustedDestructiveArtifactValidationSource {
                    fun trustedExpectation(): DestructiveArtifactIdentityExpectation? {
                        return DestructiveArtifactIdentityExpectation
                            .TrustedDestructiveArtifactExpectationMint
                            .issueFromTrustedValidationSource(
                            "aa", "bb", "com.example.app", "com.example.app/Admin",
                            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
                        )
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.none { "trusted artifact expectation issuance" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `authorized Kotlin Android factory may mint runtime durability`() {
        val classes = compileKotlin(
            *runtimeDurabilityKotlinStubs(),
            "com/example/devicemanagement/logging/StructuredLogger.kt" to
                """
                package com.example.devicemanagement.logging
                interface StructuredLogger
                """.trimIndent(),
            "com/example/devicemanagement/persistence/AndroidDestructiveSafetyPersistence.kt" to
                """
                package com.example.devicemanagement.persistence
                import android.content.Context
                import com.example.devicemanagement.destructive.RuntimeDestructiveSafetyDurability
                import com.example.devicemanagement.logging.StructuredLogger
                object AndroidDestructiveSafetyPersistence {
                    fun issueRuntimeDurability(
                        context: Context,
                        logger: StructuredLogger,
                    ): RuntimeDestructiveSafetyDurability? {
                        return RuntimeDestructiveSafetyDurability.RuntimeDestructiveSafetyDurabilityMint
                            .issueFromTrustedAndroidStores(null, null)
                    }
                }
                """.trimIndent(),
        )
        val violations = verify(":device-management-impl", classes)
        assertTrue(
            violations.none { "runtime durability issuance" in it },
            violations.joinToString("\n"),
        )
    }

    @Test
    fun `recovery class cannot reference human confirmation mint`() {
        val classes = compileJava(
            *humanConfirmationMintStubs(),
            "com/example/devicemanagement/recovery/RogueRecoveryHumanConfirmation.java" to
                """
                package com.example.devicemanagement.recovery;
                import com.example.devicemanagement.destructive.DestructiveHumanConfirmationMint;
                public final class RogueRecoveryHumanConfirmation {
                    DestructiveHumanConfirmationMint mint;
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(violations.any { "recovery code" in it })
        assertTrue(violations.any { "DestructiveHumanConfirmationMint" in it })
    }

    @Test
    fun `authorized TrustedRuntime adapter may persist through the deny-only medium`() {
        val classes = compileJava(
            denyOnlyMarkerMediumStub(),
            markerWriteResultStub(),
            "com/example/devicemanagement/persistence/TrustedRuntimeDenyOnlyCooldownMarkerStore.java" to
                """
                package com.example.devicemanagement.persistence;
                import com.example.devicemanagement.destructive.MarkerWriteResult;
                public final class TrustedRuntimeDenyOnlyCooldownMarkerStore {
                    public MarkerWriteResult writeMarker(byte[] bytes) {
                        DenyOnlyMarkerDurableMedium medium = null;
                        medium.persistEncodedMarker(bytes);
                        return null;
                    }
                }
                """.trimIndent(),
        )

        val violations = verify(":sensitive-actions", classes)
        assertTrue(
            violations.none { "destructive-safety persistence mutation" in it },
            violations.joinToString("\n"),
        )
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

    private fun auditWriterStub(): Pair<String, String> {
        return "com/example/devicemanagement/audit/SensitiveActionAuditWriter.java" to
            """
            package com.example.devicemanagement.audit;
            public interface SensitiveActionAuditWriter {
                AuditAppendResult append(AuditAppendRequest request);
            }
            """.trimIndent()
    }

    private fun durableAuditRepositoryStub(
        implementInterface: Boolean = false,
    ): Pair<String, String> {
        val implementsClause =
            if (implementInterface) " implements SensitiveActionAuditWriter" else ""
        return "com/example/devicemanagement/audit/DurableAuditRepository.java" to
            """
            package com.example.devicemanagement.audit;
            public final class DurableAuditRepository$implementsClause {
                public AuditAppendResult append(AuditAppendRequest request) {
                    return null;
                }
            }
            """.trimIndent()
    }

    private fun auditRecordStoreStub(): Pair<String, String> {
        return "com/example/devicemanagement/audit/AuditRecordStore.java" to
            """
            package com.example.devicemanagement.audit;
            public interface AuditRecordStore {
                long insert(NewAuditRecord record);
                AuditRecordRead latest(int limit);
                int count();
                void deleteOldest(int count);
            }
            final class AuditRecordRead {}
            """.trimIndent()
    }

    private fun sqliteAuditRecordStoreStub(
        implementInterface: Boolean = false,
    ): Pair<String, String> {
        val implementsClause =
            if (implementInterface) " implements AuditRecordStore" else ""
        return "com/example/devicemanagement/audit/SqliteAuditRecordStore.java" to
            """
            package com.example.devicemanagement.audit;
            public final class SqliteAuditRecordStore$implementsClause {
                public long insert(NewAuditRecord record) {
                    return 0L;
                }
                public AuditRecordRead latest(int limit) {
                    return null;
                }
                public int count() {
                    return 0;
                }
                public void deleteOldest(int count) {}
            }
            """.trimIndent()
    }

    private fun denyOnlyMarkerMediumStub(): Pair<String, String> {
        return "com/example/devicemanagement/persistence/DenyOnlyMarkerDurableMedium.java" to
            """
            package com.example.devicemanagement.persistence;
            public interface DenyOnlyMarkerDurableMedium {
                DenyOnlyMarkerPersistResult persistEncodedMarker(byte[] encoded);
            }
            enum DenyOnlyMarkerPersistResult { WRITTEN, FAILED }
            """.trimIndent()
    }

    private fun sqliteDenyOnlyMarkerStoreStub(): Pair<String, String> {
        return "com/example/devicemanagement/persistence/SqliteDenyOnlyMarkerStore.java" to
            """
            package com.example.devicemanagement.persistence;
            public final class SqliteDenyOnlyMarkerStore {
                public DenyOnlyMarkerPersistResult persistEncodedMarker(byte[] encoded) {
                    return DenyOnlyMarkerPersistResult.FAILED;
                }
            }
            """.trimIndent()
    }

    private fun destructivePreExecutionStoreStub(): Pair<String, String> {
        return "com/example/devicemanagement/destructive/DestructivePreExecutionDurableStore.java" to
            """
            package com.example.devicemanagement.destructive;
            public interface DestructivePreExecutionDurableStore {
                long insert(DestructivePreExecutionDurableRecord record);
            }
            """.trimIndent()
    }

    private fun sqliteDestructivePreExecutionStoreStub(): Pair<String, String> {
        return "com/example/devicemanagement/persistence/SqliteDestructivePreExecutionStore.java" to
            """
            package com.example.devicemanagement.persistence;
            import com.example.devicemanagement.destructive.DestructivePreExecutionDurableRecord;
            public final class SqliteDestructivePreExecutionStore {
                public long insert(DestructivePreExecutionDurableRecord record) {
                    return 0L;
                }
            }
            """.trimIndent()
    }

    private fun destructivePreExecutionRecordStub(): Pair<String, String> {
        return "com/example/devicemanagement/destructive/DestructivePreExecutionDurableRecord.java" to
            """
            package com.example.devicemanagement.destructive;
            public final class DestructivePreExecutionDurableRecord {}
            """.trimIndent()
    }

    private fun markerWriteResultStub(): Pair<String, String> {
        return "com/example/devicemanagement/destructive/MarkerWriteResult.java" to
            """
            package com.example.devicemanagement.destructive;
            public interface MarkerWriteResult {}
            """.trimIndent()
    }

    private fun trustedArtifactExpectationStubs(): Array<Pair<String, String>> {
        return arrayOf(
            "com/example/devicemanagement/destructive/DestructiveArtifactBuildPurpose.java" to
                """
                package com.example.devicemanagement.destructive;
                public enum DestructiveArtifactBuildPurpose { DISPOSABLE_DEVICE_VALIDATION }
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveArtifactIdentityExpectation.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveArtifactIdentityExpectation {
                    public static DestructiveArtifactIdentityExpectation issueFromTrustedValidationSource(
                        String certificateSha256,
                        String artifactSha256,
                        String packageName,
                        String adminComponent,
                        DestructiveArtifactBuildPurpose buildPurpose
                    ) {
                        return null;
                    }
                }
                """.trimIndent(),
        )
    }

    private fun humanConfirmationMintStubs(): Array<Pair<String, String>> {
        return arrayOf(
            "com/example/devicemanagement/destructive/DestructiveOperatorChallenge.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveOperatorChallenge {}
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveCorrelationId.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveCorrelationId {}
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveTargetBinding.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveTargetBinding {}
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveScope.java" to
                """
                package com.example.devicemanagement.destructive;
                public enum DestructiveScope { DEVICE_FACTORY_RESET }
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveArtifactIdentity.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveArtifactIdentity {}
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveAttemptLease.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveAttemptLease {}
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveHumanConfirmation.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveHumanConfirmation {}
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveHumanConfirmationResult.java" to
                """
                package com.example.devicemanagement.destructive;
                public abstract class DestructiveHumanConfirmationResult {}
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveHumanConfirmationMint.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class DestructiveHumanConfirmationMint {
                    public static DestructiveHumanConfirmation issueFromTrustedConfirmationSource(
                        DestructiveOperatorChallenge challenge,
                        DestructiveCorrelationId correlationId,
                        DestructiveTargetBinding binding,
                        DestructiveScope scope,
                        DestructiveArtifactIdentity artifactIdentity,
                        DestructiveAttemptLease attemptLease,
                        long issuedAtMonotonicMillis
                    ) {
                        return null;
                    }
                }
                """.trimIndent(),
        )
    }

    private fun runtimeDestructiveSafetyDurabilityStub(): Pair<String, String> {
        return "com/example/devicemanagement/destructive/RuntimeDestructiveSafetyDurability.java" to
            """
            package com.example.devicemanagement.destructive;
            import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium;
            public final class RuntimeDestructiveSafetyDurability {
                public static RuntimeDestructiveSafetyDurability issueFromTrustedAndroidStores(
                    DenyOnlyMarkerDurableMedium cooldownMedium,
                    DestructivePreExecutionDurableStore preExecutionStore
                ) {
                    return null;
                }
            }
            """.trimIndent()
    }

    private fun structuredLoggerStub(): Pair<String, String> {
        return "com/example/devicemanagement/logging/StructuredLogger.java" to
            """
            package com.example.devicemanagement.logging;
            public interface StructuredLogger {}
            """.trimIndent()
    }

    private fun newAuditRecordStub(): Pair<String, String> {
        return "com/example/devicemanagement/audit/NewAuditRecord.java" to
            """
            package com.example.devicemanagement.audit;
            public final class NewAuditRecord {}
            """.trimIndent()
    }

    private fun auditAppendTypes(): Array<Pair<String, String>> {
        return arrayOf(
            "com/example/devicemanagement/audit/AuditAppendRequest.java" to
                """
                package com.example.devicemanagement.audit;
                public final class AuditAppendRequest {}
                """.trimIndent(),
            "com/example/devicemanagement/audit/AuditAppendResult.java" to
                """
                package com.example.devicemanagement.audit;
                public abstract class AuditAppendResult {}
                """.trimIndent(),
        )
    }

    private fun customContextWrapperSubclass(
        className: String,
        methodName: String,
        statement: String,
    ): Pair<String, String> {
        return "attack/$className.java" to
            """
            package attack;
            import android.content.ContextWrapper;
            public final class $className extends ContextWrapper {
                public $className() {
                    super(null);
                }
                void $methodName() {
                    $statement
                }
            }
            """.trimIndent()
    }

    private fun assertInvocationOwner(
        classes: File,
        callerClass: String,
        callerMethod: String,
        invokedName: String,
        expectedOwner: String,
    ) {
        var foundOwner: String? = null
        ClassReader(File(classes, "$callerClass.class").readBytes()).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name != callerMethod) {
                        return null
                    }
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            if (name == invokedName) {
                                foundOwner = owner
                            }
                        }
                    }
                }
            },
            ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        assertEquals(
            expectedOwner,
            foundOwner,
            "Expected bytecode invoke owner $expectedOwner for $invokedName, found $foundOwner",
        )
        check(foundOwner != "android/content/Context") {
            "Fixture compiled to Context owner instead of the custom subclass"
        }
        check(foundOwner != "android/content/ContextWrapper") {
            "Fixture compiled to ContextWrapper owner instead of the custom subclass"
        }
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

    private fun trustedArtifactExpectationKotlinStubs(): Array<Pair<String, String>> {
        return arrayOf(
            "com/example/devicemanagement/destructive/DestructiveArtifactBuildPurpose.kt" to
                """
                package com.example.devicemanagement.destructive
                enum class DestructiveArtifactBuildPurpose { DISPOSABLE_DEVICE_VALIDATION }
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructiveArtifactIdentityExpectation.kt" to
                """
                package com.example.devicemanagement.destructive
                class DestructiveArtifactIdentityExpectation {
                    companion object TrustedDestructiveArtifactExpectationFactory {
                        @JvmStatic
                        fun issueFromTrustedValidationSource(
                            certificateSha256: String,
                            artifactSha256: String,
                            packageName: String,
                            adminComponent: String,
                            buildPurpose: DestructiveArtifactBuildPurpose,
                        ): DestructiveArtifactIdentityExpectation? = null
                    }
                    object TrustedDestructiveArtifactExpectationMint {
                        fun issueFromTrustedValidationSource(
                            certificateSha256: String,
                            artifactSha256: String,
                            packageName: String,
                            adminComponent: String,
                            buildPurpose: DestructiveArtifactBuildPurpose,
                        ): DestructiveArtifactIdentityExpectation? = null
                    }
                }
                """.trimIndent(),
        )
    }

    private fun realChainHandoffStubs(): Array<Pair<String, String>> {
        return arrayOf(
            "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class FutureDestructiveRealChainBoundary {
                    public static class FutureDestructiveExecutionBundle {
                        public static final class Companion {
                            public static FutureDestructiveExecutionBundle create() {
                                return new FutureDestructiveExecutionBundle();
                            }
                        }
                    }
                    public static class RealChainFinalLiveValidationPermit {}
                    public static abstract class FutureDestructiveExecutorContract {
                        public Object execute(FutureDestructiveExecutionBundle bundle) { return null; }
                        public Object onAuthorizedHandoff() { return null; }
                    }
                    public Object mintFinalLiveValidationPermit() { return null; }
                    public Object assembleBundleFromPermit(Object permit) { return null; }
                    public void registerIssuedBundle(FutureDestructiveExecutionBundle bundle) {}
                    public Object assembleAndHandoff() { return null; }
                }
                """.trimIndent(),
            "com/example/devicemanagement/destructive/FutureDestructiveExecutionBundle.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class FutureDestructiveExecutionBundle {}
                """.trimIndent(),
            "com/example/devicemanagement/destructive/RealChainFinalLiveValidationPermit.java" to
                """
                package com.example.devicemanagement.destructive;
                public final class RealChainFinalLiveValidationPermit {}
                """.trimIndent(),
        )
    }

    private fun realChainHandoffKotlinStubs(): Array<Pair<String, String>> {
        return arrayOf(
            "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary.kt" to
                """
                package com.example.devicemanagement.destructive
                class FutureDestructiveRealChainBoundary {
                    class FutureDestructiveExecutionBundle
                    class RealChainFinalLiveValidationPermit {
                        object LiveValidationMint {
                            fun mintFinalLiveValidationPermit(): RealChainFinalLiveValidationPermit =
                                RealChainFinalLiveValidationPermit()
                        }
                    }
                    abstract class FutureDestructiveExecutorContract {
                        fun execute(bundle: FutureDestructiveExecutionBundle): Any? = null
                    }
                    fun mintFinalLiveValidationPermit(): Any? = null
                    fun assembleBundleFromPermit(permit: Any?): FutureDestructiveExecutionBundle? = null
                }
                """.trimIndent(),
        )
    }

    private fun runtimeDurabilityKotlinStubs(): Array<Pair<String, String>> {
        return arrayOf(
            "com/example/devicemanagement/persistence/DenyOnlyMarkerDurableMedium.kt" to
                """
                package com.example.devicemanagement.persistence
                class DenyOnlyMarkerDurableMedium
                """.trimIndent(),
            "com/example/devicemanagement/destructive/DestructivePreExecutionDurableStore.kt" to
                """
                package com.example.devicemanagement.destructive
                class DestructivePreExecutionDurableStore
                """.trimIndent(),
            "com/example/devicemanagement/destructive/RuntimeDestructiveSafetyDurability.kt" to
                """
                package com.example.devicemanagement.destructive
                import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
                class RuntimeDestructiveSafetyDurability {
                    companion object {
                        @JvmStatic
                        fun issueFromTrustedAndroidStores(
                            cooldownMedium: DenyOnlyMarkerDurableMedium?,
                            preExecutionStore: DestructivePreExecutionDurableStore?,
                        ): RuntimeDestructiveSafetyDurability? = null
                    }
                    object RuntimeDestructiveSafetyDurabilityMint {
                        fun issueFromTrustedAndroidStores(
                            cooldownMedium: DenyOnlyMarkerDurableMedium?,
                            preExecutionStore: DestructivePreExecutionDurableStore?,
                        ): RuntimeDestructiveSafetyDurability? = null
                    }
                }
                """.trimIndent(),
        )
    }

    private fun compileKotlin(vararg sources: Pair<String, String>): File {
        val root = Files.createTempDirectory("policy-kotlin-fixture").toFile()
        val sourceRoot = File(root, "src").apply { mkdirs() }
        val classes = File(root, "classes").apply { mkdirs() }
        val sourceFiles = sources.map { (path, source) ->
            val relative = if (path.endsWith(".kt")) path else "$path.kt"
            File(sourceRoot, relative).apply {
                parentFile.mkdirs()
                writeText(source)
            }
        }
        val platform = compileJava()
        val stdlib = jarOnClasspath(KotlinVersion::class.java)
        val classpath = listOf(platform.absolutePath, stdlib.absolutePath)
            .joinToString(File.pathSeparator)
        val compilerClasspath = System.getProperty("java.class.path")
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val command = mutableListOf(
            java,
            "-Djava.awt.headless=true",
            "-cp", compilerClasspath,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-d", classes.absolutePath,
            "-cp", classpath,
            "-jvm-target", "17",
        )
        command.addAll(sourceFiles.map { it.absolutePath })
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "kotlinc failed:\n$output" }
        return classes
    }

    private fun jarOnClasspath(type: Class<*>): File {
        return File(type.protectionDomain.codeSource.location.toURI())
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
                public boolean moveDatabaseFrom(Context sourceContext, String name) {
                    return false;
                }
            }
            """.trimIndent(),
        "android/content/ContextWrapper.java" to
            """
            package android.content;
            public class ContextWrapper extends Context {
                public ContextWrapper(Context base) {}
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
        "android/database/DatabaseUtils.java" to
            """
            package android.database;
            import android.content.Context;
            import android.database.sqlite.SQLiteDatabase;
            public final class DatabaseUtils {
                public static void createDbFromSqlStatements(
                    Context context,
                    String dbName,
                    int dbVersion,
                    String sql
                ) {}
                public static final class InsertHelper {
                    public InsertHelper(SQLiteDatabase db, String tableName) {}
                }
            }
            """.trimIndent(),
        "android/system/Os.java" to
            """
            package android.system;
            import java.io.FileDescriptor;
            public final class Os {
                public static FileDescriptor open(String path, int flags, int mode) {
                    return null;
                }
                public static void unlink(String path) {}
                public static void rename(String oldPath, String newPath) {}
                public static void truncate(String path, long length) {}
                public static void ftruncate(FileDescriptor fd, long length) {}
                public static void chmod(String path, int mode) {}
                public static void chown(String path, int uid, int gid) {}
                public static int getpid() { return 0; }
            }
            """.trimIndent(),
        "android/system/OsConstants.java" to
            """
            package android.system;
            public final class OsConstants {
                public static int O_RDWR = 2;
                public static int O_CREAT = 64;
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
                public void wipeDevice(int flags) {}
            }
            """.trimIndent(),
    )
}
