import java.io.File
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertTrue

class ProductionBytecodePolicyVerifierTest {
    @Test
    fun `authorized infrastructure can invoke only approved DPM setter`() {
        val classes = compileJava(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService.java" to
                """
                package com.example.devicemanagement.management;
                import android.app.admin.DevicePolicyManager;
                import android.content.ComponentName;
                public final class AndroidDevicePolicyCameraService {
                    private final DevicePolicyManager manager = new DevicePolicyManager();
                    private final ComponentName adminComponent = new ComponentName();
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
        "android/app/admin/DevicePolicyManager.java" to
            """
            package android.app.admin;
            import android.content.ComponentName;
            public class DevicePolicyManager {
                public void setCameraDisabled(ComponentName admin, boolean disabled) {}
                public void setScreenCaptureDisabled(ComponentName admin, boolean disabled) {}
                public boolean getCameraDisabled(ComponentName admin) { return false; }
                public boolean getScreenCaptureDisabled(ComponentName admin) { return false; }
                public void wipeData(int flags) {}
            }
            """.trimIndent(),
        "dalvik/system/DexFile.java" to
            """
            package dalvik.system;
            public final class DexFile {
                public DexFile(String path) {}
                public Class<?> loadClass(String name, ClassLoader loader) { return null; }
            }
            """.trimIndent(),
    )
}
