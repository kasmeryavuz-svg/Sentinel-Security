import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.util.jar.JarFile

internal data class PolicyVerificationTarget(
    val artifactPath: String,
    val displayPath: String,
    val bytes: ByteArray,
)

internal object ProductionBytecodePolicyVerifier {
    private const val DPM = "android/app/admin/DevicePolicyManager"

    private val authorizedDpmCallers = setOf(
        "com/example/devicemanagement/management/AndroidDevicePolicyPlatform",
        "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
        "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService",
        "com/example/devicemanagement/management/AndroidDevicePolicyCameraService",
    )

    private val allowedDpmInvocations = setOf(
        "isDeviceOwnerApp(Ljava/lang/String;)Z",
        "isProfileOwnerApp(Ljava/lang/String;)Z",
        "isAdminActive(Landroid/content/ComponentName;)Z",
        "isProvisioningAllowed(Ljava/lang/String;)Z",
        "getActiveAdmins()Ljava/util/List;",
        "getScreenCaptureDisabled(Landroid/content/ComponentName;)Z",
        "getCameraDisabled(Landroid/content/ComponentName;)Z",
        "setScreenCaptureDisabled(Landroid/content/ComponentName;Z)V",
        "setCameraDisabled(Landroid/content/ComponentName;Z)V",
    )

    private val forbiddenLoaderOwners = setOf(
        "java/lang/ClassLoader",
        "java/net/URLClassLoader",
        "dalvik/system/BaseDexClassLoader",
        "dalvik/system/DexClassLoader",
        "dalvik/system/PathClassLoader",
        "dalvik/system/InMemoryDexClassLoader",
    )

    fun verify(targets: Iterable<PolicyVerificationTarget>): List<String> {
        return targets.flatMap(::verifyClass)
    }

    fun classTargets(
        artifactPath: String,
        roots: Iterable<File>,
    ): List<PolicyVerificationTarget> {
        return roots.flatMap { root ->
            when {
                root.isDirectory -> root.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .map {
                        PolicyVerificationTarget(
                            artifactPath = artifactPath,
                            displayPath = it.path,
                            bytes = it.readBytes(),
                        )
                    }
                    .toList()
                root.isFile && root.extension == "jar" -> JarFile(root).use { jar ->
                    jar.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .map {
                            PolicyVerificationTarget(
                                artifactPath = artifactPath,
                                displayPath = "${root.path}!/${it.name}",
                                bytes = jar.getInputStream(it).readBytes(),
                            )
                        }
                        .toList()
                }
                root.isFile && root.extension == "class" -> listOf(
                    PolicyVerificationTarget(
                        artifactPath = artifactPath,
                        displayPath = root.path,
                        bytes = root.readBytes(),
                    ),
                )
                else -> emptyList()
            }
        }
    }

    private fun verifyClass(target: PolicyVerificationTarget): List<String> {
        val violations = mutableListOf<String>()
        ClassReader(target.bytes).accept(
            PolicyClassVisitor(target, violations),
            ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return violations
    }

    private class PolicyClassVisitor(
        private val target: PolicyVerificationTarget,
        private val violations: MutableList<String>,
    ) : ClassVisitor(Opcodes.ASM9) {
        private lateinit var className: String

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            className = name
            checkType(superName, "supertype")
            interfaces.orEmpty().forEach { checkType(it, "interface") }
            signature?.let { checkDescriptor(it, "class signature") }
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            checkDescriptor(descriptor, "annotation")
            return null
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?,
        ): org.objectweb.asm.FieldVisitor? {
            checkDescriptor(descriptor, "field $name")
            signature?.let { checkDescriptor(it, "field $name signature") }
            checkConstant(value, "field $name")
            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            checkDescriptor(descriptor, "method $name")
            signature?.let { checkDescriptor(it, "method $name signature") }
            exceptions.orEmpty().forEach { checkType(it, "method $name exception") }
            if (access and Opcodes.ACC_NATIVE != 0) {
                violation("$className.$name$descriptor declares a native/JNI entry point")
            }
            return PolicyMethodVisitor(name, descriptor)
        }

        private inner class PolicyMethodVisitor(
            private val methodName: String,
            private val methodDescriptor: String,
        ) : MethodVisitor(Opcodes.ASM9) {
            private val location: String
                get() = "$className.$methodName$methodDescriptor"

            override fun visitTypeInsn(opcode: Int, type: String) {
                checkType(type, "$location type instruction")
                checkForbiddenOwner(type, "<type>", location)
            }

            override fun visitFieldInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
            ) {
                checkDpmOwner(owner, "$location field $name")
                checkDescriptor(descriptor, "$location field $name")
            }

            override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
                isInterface: Boolean,
            ) {
                checkDpmInvocation(owner, name, descriptor, location)
                checkForbiddenOwner(owner, name, location)
                checkDescriptor(descriptor, "$location invocation")
            }

            override fun visitInvokeDynamicInsn(
                name: String,
                descriptor: String,
                bootstrapMethodHandle: Handle,
                vararg bootstrapMethodArguments: Any,
            ) {
                violation("$location uses invokedynamic ($name$descriptor)")
                checkHandle(bootstrapMethodHandle, location)
                bootstrapMethodArguments.forEach {
                    checkConstant(it, "$location invokedynamic argument")
                }
            }

            override fun visitLdcInsn(value: Any?) {
                checkConstant(value, "$location constant")
            }

            override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
                checkDescriptor(descriptor, "$location array")
            }
        }

        private fun checkConstant(value: Any?, location: String) {
            when (value) {
                is Type -> checkDescriptor(value.descriptor, location)
                is Handle -> checkHandle(value, location)
            }
        }

        private fun checkHandle(handle: Handle, location: String) {
            checkDpmInvocation(handle.owner, handle.name, handle.desc, "$location method handle")
            checkForbiddenOwner(handle.owner, handle.name, "$location method handle")
            checkDescriptor(handle.desc, "$location method handle")
        }

        private fun checkDpmInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            if (owner != DPM) return
            val invocation = "$name$descriptor"
            if (
                target.artifactPath != ":device-management-impl" ||
                className !in authorizedDpmCallers
            ) {
                violation(
                    "$location invokes $DPM.$invocation outside the explicitly " +
                        "authorized implementation classes",
                )
            }
            if (invocation !in allowedDpmInvocations) {
                violation("$location invokes non-allowlisted $DPM.$invocation")
            }
        }

        private fun checkDpmOwner(owner: String, location: String) {
            if (
                owner == DPM &&
                (
                    target.artifactPath != ":device-management-impl" ||
                        className !in authorizedDpmCallers
                    )
            ) {
                violation("$location references $DPM outside the authorized implementation")
            }
        }

        private fun checkType(type: String?, location: String) {
            if (type == null) return
            checkDpmOwner(type, location)
            checkForbiddenOwner(type, "<type>", location)
        }

        private fun checkDescriptor(descriptor: String, location: String) {
            if (descriptor.contains("L$DPM;")) {
                checkDpmOwner(DPM, location)
            }
            forbiddenLoaderOwners.forEach { owner ->
                if (descriptor.contains("L$owner;")) {
                    violation("$location references forbidden dynamic loader $owner")
                }
            }
            if (
                descriptor.contains("Ljava/lang/reflect/") ||
                descriptor.contains("Lkotlin/reflect/") ||
                descriptor.contains("Ljava/lang/invoke/")
            ) {
                violation("$location references a forbidden reflection or method-handle type")
            }
        }

        private fun checkForbiddenOwner(owner: String, name: String, location: String) {
            val reason = when {
                owner.startsWith("java/lang/reflect/") -> "Java reflection"
                owner.startsWith("kotlin/reflect/") -> "Kotlin reflection"
                owner.startsWith("java/lang/invoke/") -> "method handles"
                owner in forbiddenLoaderOwners -> "dynamic class loading"
                owner == "java/lang/Class" &&
                    name in setOf(
                        "forName",
                        "getMethod",
                        "getMethods",
                        "getDeclaredMethod",
                        "getDeclaredMethods",
                        "getConstructor",
                        "getConstructors",
                        "getDeclaredConstructor",
                        "getDeclaredConstructors",
                        "getField",
                        "getFields",
                        "getDeclaredField",
                        "getDeclaredFields",
                    ) -> "reflective lookup"
                owner == "java/lang/System" && name in setOf("load", "loadLibrary") ->
                    "native library loading"
                owner == "java/lang/Runtime" &&
                    name in setOf("load", "loadLibrary", "exec") ->
                    "native library or process loading"
                owner == "java/lang/ProcessBuilder" && name == "start" ->
                    "process execution"
                owner.startsWith("javax/tools/") -> "runtime code compilation"
                else -> null
            }
            if (reason != null) {
                violation("$location uses forbidden $reason via $owner.$name")
            }
        }

        private fun violation(message: String) {
            violations += "${target.artifactPath}:${target.displayPath}: $message"
        }
    }
}
