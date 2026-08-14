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

    private data class InvocationOrigin(
        val className: String,
        val methodName: String,
        val methodDescriptor: String,
    )

    private fun origins(vararg origins: InvocationOrigin): Set<InvocationOrigin> =
        origins.toSet()

    private val authorizedDpmCallers = setOf(
        "com/example/devicemanagement/management/AndroidDevicePolicyPlatform",
        "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
        "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService",
        "com/example/devicemanagement/management/AndroidDevicePolicyCameraService",
        "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService",
    )

    private val allowedDpmInvocations = mapOf(
        "isDeviceOwnerApp(Ljava/lang/String;)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
            "isDeviceOwnerApp",
            "()Z",
        )),
        "isProfileOwnerApp(Ljava/lang/String;)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
            "isProfileOwnerApp",
            "()Z",
        )),
        "isAdminActive(Landroid/content/ComponentName;)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
            "isExpectedAdminActive",
            "()Z",
        )),
        "isProvisioningAllowed(Ljava/lang/String;)Z" to origins(
            InvocationOrigin(
                "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
                "isDeviceOwnerProvisioningAllowed",
                "()Z",
            ),
            InvocationOrigin(
                "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
                "isProfileOwnerProvisioningAllowed",
                "()Z",
            ),
        ),
        "getActiveAdmins()Ljava/util/List;" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
            "activeAdminComponentNames",
            "()Ljava/util/Set;",
        )),
        "getScreenCaptureDisabled(Landroid/content/ComponentName;)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService",
            "isScreenCaptureDisabled",
            "()Z",
        )),
        "getCameraDisabled(Landroid/content/ComponentName;)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService",
            "isCameraDisabled",
            "()Z",
        )),
        "setScreenCaptureDisabled(Landroid/content/ComponentName;Z)V" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService",
            "setScreenCaptureDisabled",
            "(Z)V",
        )),
        "setCameraDisabled(Landroid/content/ComponentName;Z)V" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService",
            "setCameraDisabled",
            "(Z)V",
        )),
        "isStatusBarDisabled()Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService",
            "isStatusBarDisabled",
            "()Z",
        )),
        "setStatusBarDisabled(Landroid/content/ComponentName;Z)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService",
            "setStatusBarDisabled",
            "(Z)Z",
        )),
    )

    private val forbiddenLoaderOwners = setOf(
        "java/lang/ClassLoader",
        "java/net/URLClassLoader",
        "dalvik/system/BaseDexClassLoader",
        "dalvik/system/DexClassLoader",
        "dalvik/system/PathClassLoader",
        "dalvik/system/InMemoryDexClassLoader",
        "dalvik/system/DexFile",
    )

    private val verifiedMutationExecutorScreenCapture = InvocationOrigin(
        "com/example/devicemanagement/management/VerifiedPolicyMutationExecutor",
        "executeScreenCapture",
        "(Lcom/example/devicemanagement/management/VerifiedPolicyMutation" +
            "\$ScreenCapture;Ljava/lang/String;)" +
            "Lcom/example/devicemanagement/management/PolicyMutation;",
    )

    private val verifiedMutationExecutorCamera = InvocationOrigin(
        "com/example/devicemanagement/management/VerifiedPolicyMutationExecutor",
        "executeCamera",
        "(Lcom/example/devicemanagement/management/VerifiedPolicyMutation" +
            "\$Camera;Ljava/lang/String;)" +
            "Lcom/example/devicemanagement/management/PolicyMutation;",
    )

    private val verifiedMutationExecutorStatusBar = InvocationOrigin(
        "com/example/devicemanagement/management/VerifiedPolicyMutationExecutor",
        "executeStatusBar",
        "(Lcom/example/devicemanagement/management/VerifiedPolicyMutation" +
            "\$StatusBar;Ljava/lang/String;)" +
            "Lcom/example/devicemanagement/management/PolicyMutation;",
    )

    /**
     * Narrow policy setters are bound to VerifiedPolicyMutationExecutor whether the
     * bytecode call owner is the interface or the concrete Android implementation.
     * Restricting only the interface leaves a concrete-type / cast bypass that still
     * reaches the allowlisted DPM mutators inside those implementations.
     */
    private val verifiedMutationOrigins = mapOf(
        "com/example/devicemanagement/management/DevicePolicyScreenCaptureService." +
            "setScreenCaptureDisabled(Z)V" to verifiedMutationExecutorScreenCapture,
        "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService." +
            "setScreenCaptureDisabled(Z)V" to verifiedMutationExecutorScreenCapture,
        "com/example/devicemanagement/management/DevicePolicyCameraService." +
            "setCameraDisabled(Z)V" to verifiedMutationExecutorCamera,
        "com/example/devicemanagement/management/AndroidDevicePolicyCameraService." +
            "setCameraDisabled(Z)V" to verifiedMutationExecutorCamera,
        "com/example/devicemanagement/management/DevicePolicyStatusBarService." +
            "setStatusBarDisabled(Z)Z" to verifiedMutationExecutorStatusBar,
        "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService." +
            "setStatusBarDisabled(Z)Z" to verifiedMutationExecutorStatusBar,
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
                checkVerifiedMutationInvocation(owner, name, descriptor, location)
                checkForbiddenOwner(owner, name, location)
                checkDescriptor(descriptor, "$location invocation")
            }

            override fun visitInvokeDynamicInsn(
                name: String,
                descriptor: String,
                bootstrapMethodHandle: Handle,
                vararg bootstrapMethodArguments: Any,
            ) {
                val isCompilerStringConcatenation =
                    bootstrapMethodHandle.owner == "java/lang/invoke/StringConcatFactory" &&
                        bootstrapMethodHandle.name in
                        setOf("makeConcat", "makeConcatWithConstants") &&
                        bootstrapMethodArguments.none { it is Handle }
                if (isCompilerStringConcatenation) {
                    checkDescriptor(descriptor, "$location string concatenation")
                    bootstrapMethodArguments.forEach {
                        checkConstant(it, "$location string concatenation argument")
                    }
                    return
                }
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
            checkVerifiedMutationInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
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
            val actualOrigin = InvocationOrigin(className, methodName(location), methodDescriptor(location))
            val approvedOrigins = allowedDpmInvocations[invocation].orEmpty()
            if (target.artifactPath != ":device-management-impl" || actualOrigin !in approvedOrigins) {
                violation(
                    "$location invokes $DPM.$invocation outside the explicitly " +
                        "authorized implementation method",
                )
            }
            if (invocation !in allowedDpmInvocations.keys) {
                violation("$location invokes non-allowlisted $DPM.$invocation")
            }
        }

        private fun checkVerifiedMutationInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            val invocation = "$owner.$name$descriptor"
            val approvedOrigin = verifiedMutationOrigins[invocation] ?: return
            val actualOrigin = InvocationOrigin(
                className,
                methodName(location),
                methodDescriptor(location),
            )
            if (
                target.artifactPath != ":device-management-impl" ||
                actualOrigin != approvedOrigin
            ) {
                violation(
                    "$location invokes narrow policy mutation $invocation outside " +
                        "VerifiedPolicyMutationExecutor",
                )
            }
        }

        private fun methodName(location: String): String {
            return location.removePrefix("$className.").substringBefore('(')
        }

        private fun methodDescriptor(location: String): String {
            return location.substringAfter("$className.${methodName(location)}")
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
                        "newInstance",
                        "getClassLoader",
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
