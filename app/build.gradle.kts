import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.tasks.testing.Test
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.util.jar.JarFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

fun String.isVerificationClasspath(): Boolean {
    return contains("UnitTest") ||
        contains("AndroidTest") ||
        contains("TestFixtures")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun sentinelSecret(name: String): String? {
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val local = rootProject.file("local.properties")
    if (!local.isFile) {
        return null
    }
    val properties = Properties()
    local.reader(Charsets.UTF_8).use { properties.load(it) }
    return properties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
}

class ProductionSigningSecrets(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun readProductionSigningSecrets(): ProductionSigningSecrets? {
    val names = listOf(
        "SENTINEL_RELEASE_STORE_FILE",
        "SENTINEL_RELEASE_STORE_PASSWORD",
        "SENTINEL_RELEASE_KEY_ALIAS",
        "SENTINEL_RELEASE_KEY_PASSWORD",
    )
    val values = names.map { sentinelSecret(it) }
    val present = values.count { !it.isNullOrBlank() }
    if (present == 0) {
        return null
    }
    check(present == names.size) {
        "Incomplete production signing secrets. Set all of ${names.joinToString()} " +
            "via environment variables or gitignored local.properties. " +
            "Refusing to fall back to the Android debug keystore."
    }
    val storeFile = file(requireNotNull(values[0]))
    check(storeFile.isFile) {
        "Production keystore file is missing. A production distribution cannot be signed."
    }
    check(!storeFile.name.contains("debug", ignoreCase = true)) {
        "Production signing must not use the Android debug keystore"
    }
    return ProductionSigningSecrets(
        storeFile = storeFile,
        storePassword = requireNotNull(values[1]),
        keyAlias = requireNotNull(values[2]),
        keyPassword = requireNotNull(values[3]),
    )
}

fun readExpectedProductionCertSha256(): String? {
    return sentinelSecret("SENTINEL_RELEASE_CERT_SHA256")
}

fun productionDistributionTasksRequested(): Boolean {
    return gradle.startParameter.taskNames.any { requested ->
        val leaf = requested.substringAfterLast(':')
        leaf.equals("assembleProductionRelease", ignoreCase = true) ||
            leaf.equals("bundleProductionRelease", ignoreCase = true)
    }
}

val productionDistributionFlag =
    providers.gradleProperty("sentinel.productionDistribution")
        .map { it.equals("true", ignoreCase = true) }
        .orElse(false)
val requestProductionDistribution =
    productionDistributionFlag.get() || productionDistributionTasksRequested()

val productionSigningSecrets = if (requestProductionDistribution) {
    val secrets = readProductionSigningSecrets()
    val fingerprint = readExpectedProductionCertSha256()
    val decision = ProductionDistributionSigningGate.decide(
        distributionRequested = true,
        inputs = ProductionDistributionSigningGate.ObservedSigningInputs(
            storeFilePresent = secrets != null,
            storePasswordPresent = secrets != null,
            keyAliasPresent = secrets != null,
            keyPasswordPresent = secrets != null,
            certificateFingerprintPresent = !fingerprint.isNullOrBlank(),
            storeFileExists = secrets?.storeFile?.isFile == true,
            storeFileLooksLikeDebugOrTest =
                secrets?.storeFile?.name?.contains("debug", ignoreCase = true) == true,
            certificateFingerprintValid = fingerprint != null &&
                ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(fingerprint) != null,
        ),
    )
    check(ProductionDistributionSigningGate.mustAttach(decision)) {
        "Production distribution signing refused: $decision"
    }
    secrets
} else {
    check(
        ProductionDistributionSigningGate.decide(
            distributionRequested = false,
            inputs = null,
        ) == ProductionDistributionSigningGate.Decision.DO_NOT_ATTACH,
    ) {
        "ordinary release must not attach production signing"
    }
    null
}
val configuredProductionCertSha256 = if (requestProductionDistribution) {
    readExpectedProductionCertSha256()
} else {
    null
}

fun validationOnlySigningTaskRequested(): Boolean {
    return gradle.startParameter.taskNames.any { requested ->
        val leaf = requested.substringAfterLast(':')
        leaf.equals("assembleSignedDisposableValidation", ignoreCase = true)
    }
}

val requestValidationOnlySigning = validationOnlySigningTaskRequested()
check(!requestProductionDistribution || !requestValidationOnlySigning) {
    "validation-only signing and production distribution cannot be requested together"
}

class ValidationOnlySigningSecrets(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun readValidationOnlySigningSecrets(): ValidationOnlySigningSecrets? {
    val names = listOf(
        "SENTINEL_VALIDATION_STORE_FILE",
        "SENTINEL_VALIDATION_STORE_PASSWORD",
        "SENTINEL_VALIDATION_KEY_ALIAS",
        "SENTINEL_VALIDATION_KEY_PASSWORD",
    )
    val values = names.map { sentinelSecret(it) }
    val present = values.count { !it.isNullOrBlank() }
    if (present == 0) {
        return null
    }
    check(present == names.size) {
        "Incomplete validation-only signing secrets. Set all of ${names.joinToString()} " +
            "via environment variables or gitignored local.properties. " +
            "Refusing to fall back to the Android debug keystore or SENTINEL_RELEASE_*."
    }
    val storeFile = file(requireNotNull(values[0]))
    val keyAlias = requireNotNull(values[2])
    check(storeFile.isFile) {
        "Validation-only keystore file is missing. A disposableValidation APK cannot be signed."
    }
    check(
        !ValidationOnlySigningGate.looksLikeDebugOrTestMaterial(storeFile.name, keyAlias),
    ) {
        "Validation-only signing must not use Android debug or test key material"
    }
    return ValidationOnlySigningSecrets(
        storeFile = storeFile,
        storePassword = requireNotNull(values[1]),
        keyAlias = keyAlias,
        keyPassword = requireNotNull(values[3]),
    )
}

fun readExpectedValidationCertSha256(): String? {
    return sentinelSecret("SENTINEL_VALIDATION_CERT_SHA256")
}

val validationOnlySigningSecrets = if (requestValidationOnlySigning) {
    val secrets = readValidationOnlySigningSecrets()
    val fingerprint = readExpectedValidationCertSha256()
    val decision = ValidationOnlySigningGate.decide(
        validationSigningRequested = true,
        productionReleaseTarget = false,
        inputs = ValidationOnlySigningGate.ObservedSigningInputs(
            storeFilePresent = secrets != null,
            storePasswordPresent = secrets != null,
            keyAliasPresent = secrets != null,
            keyPasswordPresent = secrets != null,
            certificateFingerprintPresent = !fingerprint.isNullOrBlank(),
            storeFileExists = secrets?.storeFile?.isFile == true,
            storeFileLooksLikeDebugOrTest = secrets != null &&
                ValidationOnlySigningGate.looksLikeDebugOrTestMaterial(
                    secrets.storeFile.name,
                    secrets.keyAlias,
                ),
            certificateFingerprintValid = fingerprint != null &&
                ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(fingerprint) != null,
        ),
    )
    check(ValidationOnlySigningGate.mustAttach(decision)) {
        "Validation-only signing refused: $decision"
    }
    check(ValidationOnlySigningGate.validationInputNamespaceSeparateFromProduction()) {
        "validation-only inputs must stay separate from SENTINEL_RELEASE_*"
    }
    check(!ValidationOnlySigningGate.validationKeySeparationVerified()) {
        "cryptographic validation-key separation is not verified; no real key exists"
    }
    check(!ValidationOnlySigningGate.mayAttachToProductionRelease()) {
        "production release must never use the validation-only key"
    }
    secrets
} else {
    check(
        ValidationOnlySigningGate.decide(
            validationSigningRequested = false,
            productionReleaseTarget = false,
            inputs = null,
        ) == ValidationOnlySigningGate.Decision.DO_NOT_ATTACH,
    ) {
        "ordinary disposableValidation must not attach validation-only signing"
    }
    null
}
val configuredValidationCertSha256 = if (requestValidationOnlySigning) {
    readExpectedValidationCertSha256()
} else {
    null
}

android {
    namespace = "com.example.devicemanagement"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.devicemanagement"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val secrets = productionSigningSecrets
        if (requestProductionDistribution && secrets != null) {
            create("production") {
                storeFile = secrets.storeFile
                storePassword = secrets.storePassword
                keyAlias = secrets.keyAlias
                keyPassword = secrets.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        val validationSecrets = validationOnlySigningSecrets
        if (requestValidationOnlySigning && validationSecrets != null) {
            create("validationOnly") {
                storeFile = validationSecrets.storeFile
                storePassword = validationSecrets.storePassword
                keyAlias = validationSecrets.keyAlias
                keyPassword = validationSecrets.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isProfileable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (requestProductionDistribution) {
                signingConfig = signingConfigs.getByName("production")
            }
        }
        create(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE) {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = null
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isProfileable = false
        }
    }

    if (requestValidationOnlySigning) {
        buildTypes
            .getByName(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE)
            .signingConfig = signingConfigs.getByName("validationOnly")
    }

    val disposableValidationSigning = buildTypes
        .getByName(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE)
        .signingConfig
    if (!requestValidationOnlySigning) {
        check(disposableValidationSigning == null) {
            "disposableValidation must remain unsigned even if production-signing " +
                "environment variables are present"
        }
        check(signingConfigs.findByName("validationOnly") == null) {
            "ordinary assembleDisposableValidation must remain unsigned unless " +
                "validation-only signing is explicitly requested"
        }
    } else {
        check(disposableValidationSigning === signingConfigs.getByName("validationOnly")) {
            "explicit validation-only signing must attach only the validationOnly configuration"
        }
        check(disposableValidationSigning !== signingConfigs.findByName("production")) {
            "disposableValidation must never use production signing inputs"
        }
    }
    check(signingConfigs.findByName("debug") !== disposableValidationSigning) {
        "disposableValidation must not use the Android debug signing key"
    }
    if (!requestProductionDistribution) {
        check(signingConfigs.findByName("production") == null) {
            "ordinary release must not create a production signing configuration"
        }
        check(buildTypes.getByName("release").signingConfig == null) {
            "ordinary assembleRelease/bundleRelease must remain unsigned unless " +
                "production distribution is explicitly requested"
        }
    }
    check(buildTypes.getByName("release").signingConfig !== signingConfigs.findByName("debug")) {
        "release must never use the Android debug signing key"
    }
    val validationOnlySigning = signingConfigs.findByName("validationOnly")
    if (validationOnlySigning != null) {
        check(buildTypes.getByName("release").signingConfig !== validationOnlySigning) {
            "release must never use the validation-only signing key"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
            it.systemProperty(
                "appMainSourceDir",
                layout.projectDirectory.dir("src/main").asFile.absolutePath,
            )
            it.systemProperty(
                "repoRoot",
                rootProject.layout.projectDirectory.asFile.absolutePath,
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
    }
}

dependencies {
    implementation(project(":device-management"))
    testImplementation("junit:junit:4.13.2")
}

val testKotlinCompileNegativeCompiler by configurations.creating

dependencies {
    testKotlinCompileNegativeCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
}

val checkAppDependencyIsolation by tasks.registering {
    group = "verification"
    description =
        "Proves mutation implementations are absent from app compile classpaths and packaged at runtime."

    doLast {
        val forbiddenCompileProjects = setOf(
            ":device-management-impl",
            ":sensitive-actions",
        )
        val productionCompileConfigurations = configurations
            .filter {
                it.isCanBeResolved &&
                    it.name.endsWith("CompileClasspath", ignoreCase = true) &&
                    !it.name.isVerificationClasspath()
            }
        val implementationLeaks = productionCompileConfigurations
            .flatMap { configuration ->
                configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
                    val projectPath =
                        (component.id as? ProjectComponentIdentifier)?.projectPath
                    if (projectPath in forbiddenCompileProjects) {
                        "${configuration.name}: $projectPath"
                    } else {
                        null
                    }
                }
            }
        check(implementationLeaks.isEmpty()) {
            "Mutation implementation leaked onto app compile classpath:\n" +
                implementationLeaks.joinToString("\n")
        }
        productionCompileConfigurations.forEach { configuration ->
                val visibleProjects = configuration.incoming.resolutionResult.allComponents
                    .mapNotNull {
                        (it.id as? ProjectComponentIdentifier)?.projectPath
                    }
                    .filterNot { it == project.path }
                    .toSet()
                val expectedProjects = setOf(
                    ":device-management",
                    ":device-management-api",
                    ":sensitive-actions-api",
                )
                check(visibleProjects == expectedProjects) {
                    "${configuration.name} has unexpected project API surface; " +
                        "expected $expectedProjects, found $visibleProjects"
                }
            }

        val expectedRuntimeProjects = setOf(
            ":device-management",
            ":device-management-api",
            ":device-management-impl",
            ":sensitive-actions-api",
            ":sensitive-actions",
        )
        val productionRuntimeConfigurations = configurations.filter {
            it.isCanBeResolved &&
                it.name.endsWith("RuntimeClasspath", ignoreCase = true) &&
                !it.name.isVerificationClasspath()
        }
        productionRuntimeConfigurations.forEach { configuration ->
            val runtimeProjects = configuration.incoming.resolutionResult.allComponents
                .mapNotNull {
                    (it.id as? ProjectComponentIdentifier)?.projectPath
                }
                .filterNot { it == project.path }
                .toSet()
            check(runtimeProjects == expectedRuntimeProjects) {
                "${configuration.name} runtime project surface changed; " +
                    "expected $expectedRuntimeProjects, found $runtimeProjects"
            }
        }

        val allowedExternalModules = setOf(
            "org.jetbrains.kotlin:kotlin-stdlib",
            "org.jetbrains:annotations",
        )
        (productionCompileConfigurations + productionRuntimeConfigurations)
            .forEach { configuration ->
                val externalModules = configuration.incoming.resolutionResult.allComponents
                    .mapNotNull {
                        val id = it.id as? ModuleComponentIdentifier
                        id?.let { module -> "${module.group}:${module.module}" }
                    }
                    .toSet()
                check(externalModules == allowedExternalModules) {
                    "${configuration.name} has unapproved external production artifacts; " +
                        "expected $allowedExternalModules, found $externalModules"
                }
                check("com.android.tools.build:apksig" !in externalModules) {
                    "apksig leaked onto the Android app classpath via ${configuration.name}"
                }
                check(":provisioning-qr" !in configuration.incoming.resolutionResult.allComponents
                    .mapNotNull { (it.id as? ProjectComponentIdentifier)?.projectPath }
                    .toSet()) {
                    "workstation QR tooling leaked onto the Android app classpath"
                }
            }
    }
}

val checkAppApiCompileNegative by tasks.registering {
    group = "verification"
    description = "Compiles adversarial Java and Kotlin app snippets against the app compile classpath."
    val javaNegative = fileTree("src/compileNegative/java") { include("**/*.java") }
    val kotlinNegative = fileTree("src/compileNegative/kotlin") { include("**/*.kt") }
    val javaPositive = fileTree("src/compilePositive/java") { include("**/*.java") }
    val debugJavaCompile = tasks.named<JavaCompile>("compileDebugJavaWithJavac")
    inputs.files(
        javaNegative,
        kotlinNegative,
        javaPositive,
        debugJavaCompile.map { it.classpath },
    )

    doLast {
        val compileFiles = debugJavaCompile.get().classpath.files
        val compileClasspath = (compileFiles + android.bootClasspath)
            .joinToString(File.pathSeparator) { it.absolutePath }
        val outputRoot = layout.buildDirectory.dir("compile-negative").get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()
        val javac = File(System.getProperty("java.home"), "bin/javac")

        val approvedTopLevelClasses = setOf(
            "com/example/devicemanagement/logging/StructuredLogger",
            "com/example/devicemanagement/action/ActionResult",
            "com/example/devicemanagement/action/SensitiveActionOperation",
            "com/example/devicemanagement/action/SensitiveActionController",
            "com/example/devicemanagement/audit/AuditActionNames",
            "com/example/devicemanagement/audit/AuditEvent",
            "com/example/devicemanagement/audit/AuditEventPhase",
            "com/example/devicemanagement/audit/AuditHistory",
            "com/example/devicemanagement/audit/AuditHistoryProvider",
            "com/example/devicemanagement/audit/AuditReasonCode",
            "com/example/devicemanagement/audit/AuditSchema",
            "com/example/devicemanagement/audit/AuditStorageHealth",
            "com/example/devicemanagement/audit/AuditStorageStatus",
            "com/example/devicemanagement/audit/AuditStorageStatusProvider",
            "com/example/devicemanagement/recovery/InterruptedRequest",
            "com/example/devicemanagement/recovery/RecoveryInspection",
            "com/example/devicemanagement/recovery/RecoveryInspectionHealth",
            "com/example/devicemanagement/recovery/RecoveryInspectionProvider",
            "com/example/devicemanagement/trigger/SensitiveActionCommands",
            "com/example/devicemanagement/trigger/Trigger",
            "com/example/devicemanagement/destructive/DestructiveSimulationRequest",
            "com/example/devicemanagement/destructive/DestructiveScope",
            "com/example/devicemanagement/destructive/DestructiveSimulationOutcome",
            "com/example/devicemanagement/destructive/DestructiveSimulationStatus",
            "com/example/devicemanagement/destructive/DestructiveEvidencePhase",
            "com/example/devicemanagement/destructive/DestructiveSimulationEvidence",
            "com/example/devicemanagement/management/DeviceManagement",
            "com/example/devicemanagement/management/DeviceManagementServices",
            "com/example/devicemanagement/management/ManagementMode",
            "com/example/devicemanagement/management/ManagementCapability",
            "com/example/devicemanagement/management/DeviceManagementStatus",
            "com/example/devicemanagement/management/DeviceManagementStatusProvider",
            "com/example/devicemanagement/management/ProvisioningAvailability",
            "com/example/devicemanagement/management/ProvisioningOption",
            "com/example/devicemanagement/management/ProvisioningReadiness",
            "com/example/devicemanagement/management/ProvisioningReadinessProvider",
            "com/example/devicemanagement/management/DeviceOwnerValidationResult",
            "com/example/devicemanagement/management/DeviceOwnerValidation",
            "com/example/devicemanagement/management/DeviceOwnerValidationProvider",
            "com/example/devicemanagement/management/ScreenCapturePolicyState",
            "com/example/devicemanagement/management/ScreenCapturePolicyStatus",
            "com/example/devicemanagement/management/ScreenCapturePolicyStatusProvider",
            "com/example/devicemanagement/management/CameraPolicyState",
            "com/example/devicemanagement/management/CameraPolicyStatus",
            "com/example/devicemanagement/management/CameraPolicyStatusProvider",
            "com/example/devicemanagement/management/StatusBarPolicyState",
            "com/example/devicemanagement/management/StatusBarPolicyStatus",
            "com/example/devicemanagement/management/StatusBarPolicyStatusProvider",
            "com/example/devicemanagement/facade/R",
        )
        val appBuildDirectory = layout.buildDirectory.get().asFile.canonicalFile
        val appVisibleProjectClasses = compileFiles
            .filterNot { root ->
                root.canonicalFile.toPath().startsWith(appBuildDirectory.toPath())
            }
            .flatMap { root ->
                when {
                    root.isDirectory -> root.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .map {
                            it.relativeTo(root).invariantSeparatorsPath
                                .removeSuffix(".class")
                        }
                        .toList()
                    root.isFile && root.extension == "jar" -> JarFile(root).use { jar ->
                        jar.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".class") }
                            .map { it.name.removeSuffix(".class") }
                            .toList()
                    }
                    else -> emptyList()
                }
            }
            .filter { it.startsWith("com/example/devicemanagement/") }
        val unapprovedClasses = appVisibleProjectClasses.filter { className ->
            className.substringBefore('$') !in approvedTopLevelClasses
        }
        check(unapprovedClasses.isEmpty()) {
            "App compile classpath exposes non-allowlisted JVM classes:\n" +
                unapprovedClasses.sorted().joinToString("\n")
        }

        fun compileJava(source: File): Pair<Int, String> {
            val output = ByteArrayOutputStream()
            val result = project.exec {
                commandLine(
                    javac.absolutePath,
                    "-proc:none",
                    "-source",
                    "17",
                    "-target",
                    "17",
                    "-classpath",
                    compileClasspath,
                    "-d",
                    File(outputRoot, source.nameWithoutExtension).absolutePath,
                    source.absolutePath,
                )
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            return result.exitValue to output.toString(Charsets.UTF_8)
        }

        javaPositive.files.forEach { source ->
            val (exitCode, output) = compileJava(source)
            check(exitCode == 0) {
                "Public facade control failed to compile: ${source.path}\n$output"
            }
        }
        javaNegative.files.forEach { source ->
            val (exitCode, output) = compileJava(source)
            check(exitCode != 0) {
                "Forbidden Java app composition unexpectedly compiled: ${source.path}\n$output"
            }
        }
        kotlinNegative.files.forEach { source ->
            val output = ByteArrayOutputStream()
            val result = project.javaexec {
                classpath = testKotlinCompileNegativeCompiler
                mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                args(
                    "-classpath",
                    compileClasspath,
                    "-d",
                    File(outputRoot, source.nameWithoutExtension).absolutePath,
                    source.absolutePath,
                )
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            check(result.exitValue != 0) {
                "Forbidden Kotlin app composition unexpectedly compiled: ${source.path}\n" +
                    output.toString(Charsets.UTF_8)
            }
        }
    }
}

fun secureDocument(file: File) = DocumentBuilderFactory.newInstance().apply {
    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    isNamespaceAware = true
    isExpandEntityReferences = false
}.newDocumentBuilder().parse(file)

fun org.w3c.dom.NodeList.elements(): List<Element> {
    return (0 until length).map(::item).filterIsInstance<Element>()
}

fun Element.androidName(androidNamespace: String): String {
    return getAttributeNS(androidNamespace, "name")
}

fun Element.intentActions(androidNamespace: String): List<String> {
    return getElementsByTagName("action").elements().map {
        it.getAttributeNS(androidNamespace, "name")
    }
}

fun Element.intentCategories(androidNamespace: String): List<String> {
    return getElementsByTagName("category").elements().map {
        it.getAttributeNS(androidNamespace, "name")
    }
}

fun verifyProvisioningManifest(
    manifest: org.w3c.dom.Document,
    androidNamespace: String,
    variantName: String,
) {
    val forbiddenActions = setOf(
        "android.app.action.PROVISION_MANAGED_DEVICE",
        "android.app.action.PROVISION_MANAGED_PROFILE",
        "android.app.action.PROVISION_MANAGED_USER",
        "android.app.action.PROVISION_MANAGED_SHARE_DEVICE",
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.LOCKED_BOOT_COMPLETED",
        "android.intent.action.QUICKBOOT_POWERON",
    )
    val allActions = manifest.getElementsByTagName("action").elements()
        .map { it.getAttributeNS(androidNamespace, "name") }
        .toSet()
    check(allActions.intersect(forbiddenActions).isEmpty()) {
        "Effective $variantName manifest declares forbidden provisioning launcher " +
            "actions: ${allActions.intersect(forbiddenActions)}"
    }

    val activities = manifest.getElementsByTagName("activity").elements()
    val exportedActivities = activities.filter { activity ->
        activity.getAttributeNS(androidNamespace, "exported") == "true"
    }
    val expectedExported = setOf(
        "com.example.devicemanagement.ui.MainActivity",
        "com.example.devicemanagement.provisioning.GetProvisioningModeActivity",
        "com.example.devicemanagement.provisioning.AdminPolicyComplianceActivity",
    )
    val exportedNames = exportedActivities.map { it.androidName(androidNamespace) }.toSet()
    check(exportedNames == expectedExported) {
        "Effective $variantName exported activities changed; expected " +
            "$expectedExported, found $exportedNames"
    }

    fun requireProvisioningActivity(
        className: String,
        action: String,
    ) {
        val activity = activities.single { it.androidName(androidNamespace) == className }
        check(activity.getAttributeNS(androidNamespace, "exported") == "true") {
            "Effective $variantName $className must be exported for the platform " +
                "provisioning contract"
        }
        check(
            activity.getAttributeNS(androidNamespace, "permission") ==
                "android.permission.BIND_DEVICE_ADMIN",
        ) {
            "Effective $variantName $className must be protected with " +
                "BIND_DEVICE_ADMIN"
        }
        val actions = activity.intentActions(androidNamespace)
        val categories = activity.intentCategories(androidNamespace)
        check(actions == listOf(action)) {
            "Effective $variantName $className intent actions changed: $actions"
        }
        check(categories == listOf("android.intent.category.DEFAULT")) {
            "Effective $variantName $className intent categories changed: $categories"
        }
        check("android.intent.category.LAUNCHER" !in categories) {
            "Effective $variantName $className must not have a launcher category"
        }
        check(activity.intentActions(androidNamespace).intersect(forbiddenActions).isEmpty()) {
            "Effective $variantName $className must not declare a managed-device " +
                "provisioning launcher action"
        }
    }

    requireProvisioningActivity(
        "com.example.devicemanagement.provisioning.GetProvisioningModeActivity",
        "android.app.action.GET_PROVISIONING_MODE",
    )
    requireProvisioningActivity(
        "com.example.devicemanagement.provisioning.AdminPolicyComplianceActivity",
        "android.app.action.ADMIN_POLICY_COMPLIANCE",
    )

    val launcherActivity = activities.single {
        it.androidName(androidNamespace) ==
            "com.example.devicemanagement.ui.MainActivity"
    }
    check(launcherActivity.getAttributeNS(androidNamespace, "permission").isEmpty()) {
        "Effective $variantName MainActivity must not require BIND_DEVICE_ADMIN"
    }
    check(
        launcherActivity.intentActions(androidNamespace) ==
            listOf("android.intent.action.MAIN"),
    )
    check(
        launcherActivity.intentCategories(androidNamespace) ==
            listOf("android.intent.category.LAUNCHER"),
    )

    val exportedServices = manifest.getElementsByTagName("service").elements()
        .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
    check(exportedServices.isEmpty()) {
        "Effective $variantName manifest must not export services"
    }
    val exportedProviders = manifest.getElementsByTagName("provider").elements()
        .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
    check(exportedProviders.isEmpty()) {
        "Effective $variantName manifest must not export providers"
    }
    val exportedReceivers = manifest.getElementsByTagName("receiver").elements()
        .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
        .map { it.androidName(androidNamespace) }
    check(
        exportedReceivers == listOf(
            "com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
        ),
    ) {
        "Effective $variantName exported receivers changed: $exportedReceivers"
    }
}

DestructiveValidationCandidateEvidence.assertCandidateEvidenceTasksIsolated()

tasks.register<GenerateDestructiveValidationCandidateEvidenceTask>(
    "generateDestructiveValidationCandidateEvidence",
) {
    group = "verification"
    description =
        "Inspect one explicitly supplied APK as an untrusted destructive-validation " +
            "candidate. Never auto-selects a build output and never mints a trusted expectation."
    candidateApkPath.set(
        providers.gradleProperty("sentinel.destructiveValidationCandidateApk").orElse(""),
    )
    androidSdkDirectory.set(android.sdkDirectory.absolutePath)
    projectRootPath.set(rootProject.layout.projectDirectory.asFile.absolutePath)
    reportFile.set(
        layout.buildDirectory.file("reports/destructive-validation-explicit-candidate.txt"),
    )
    snapshotDirectory.set(
        layout.buildDirectory.dir("tmp/destructive-validation-explicit-candidate-snapshot"),
    )
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name
        val capitalized = variantName.replaceFirstChar { it.uppercaseChar() }
        val policyGuard = tasks.register<ProductionBytecodePolicyTask>(
            "check${capitalized}ProductionBytecodePolicy",
        ) {
            group = "verification"
            description =
                "Verifies compiled $variantName app classes against production policy boundaries."
            artifactPath.set(project.path)
            mergedNativeLibraries.from(
                variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS),
            )
            productionFiles.from(fileTree("src") {
                exclude("test/**", "androidTest/**", "testFixtures/**")
            })
        }
        variant.artifacts
            .forScope(ScopedArtifacts.Scope.PROJECT)
            .use(policyGuard)
            .toGet(
                ScopedArtifact.CLASSES,
                ProductionBytecodePolicyTask::classJars,
                ProductionBytecodePolicyTask::classDirectories,
            )
        rootProject.tasks.named("checkProductionBytecodePolicy").configure {
            dependsOn(policyGuard)
        }
        val mergedManifestArtifact =
            variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val guardTask = tasks.register(
            "check${capitalized}EffectiveDeviceAdminMetadata",
        ) {
            group = "verification"
            description =
                "Validates effective DeviceAdmin metadata for $variantName."
            dependsOn("process${capitalized}Resources")
            inputs.file(mergedManifestArtifact)

            doLast {
                val androidNamespace =
                    "http://schemas.android.com/apk/res/android"
                val approvedPolicies = setOf("disable-camera", "wipe-data")
                val checkpoint17BForbiddenPolicies = setOf(
                    "reset-password",
                    "force-lock",
                    "limit-password",
                    "watch-login",
                    "expire-password",
                    "encrypted-storage",
                    "disable-keyguard-features",
                )
                val expectedReceiver =
                    "com.example.devicemanagement.management." +
                        "SentinelDeviceAdminReceiver"
                val aapt2 = file(
                    "${android.sdkDirectory}/build-tools/" +
                        "${android.buildToolsVersion}/aapt2",
                )
                check(aapt2.isFile) { "aapt2 is unavailable at $aapt2" }

                val mergedManifest = mergedManifestArtifact.get().asFile
                check(mergedManifest.isFile) {
                    "Merged $variantName manifest is unavailable at $mergedManifest"
                }
                val manifest = secureDocument(mergedManifest)
                val deviceAdminReceivers = manifest.getElementsByTagName("receiver")
                    .elements()
                    .filter { candidate ->
                        candidate.getAttributeNS(androidNamespace, "permission") ==
                            "android.permission.BIND_DEVICE_ADMIN" ||
                            candidate.getElementsByTagName("meta-data")
                                .elements()
                                .any {
                                    it.getAttributeNS(androidNamespace, "name") ==
                                        "android.app.device_admin"
                                } ||
                            candidate.getElementsByTagName("action")
                                .elements()
                                .any {
                                    it.getAttributeNS(androidNamespace, "name") ==
                                        "android.app.action.DEVICE_ADMIN_ENABLED"
                                }
                    }
                check(deviceAdminReceivers.size == 1) {
                    "Effective $variantName manifest must contain exactly one " +
                        "DeviceAdmin receiver; found ${deviceAdminReceivers.size}"
                }
                val metadataElements = manifest.getElementsByTagName("meta-data")
                    .elements()
                    .filter {
                        it.getAttributeNS(androidNamespace, "name") ==
                            "android.app.device_admin"
                    }
                check(metadataElements.size == 1) {
                    "Effective $variantName manifest must contain exactly one " +
                        "android.app.device_admin metadata declaration"
                }
                val metadata = metadataElements.single()
                val receiver = checkNotNull(metadata.parentNode as? Element) {
                    "Effective $variantName DeviceAdmin metadata must belong " +
                        "to a receiver"
                }
                check(receiver.tagName == "receiver")
                check(
                    receiver.getAttributeNS(androidNamespace, "name") ==
                        expectedReceiver,
                ) {
                    "Effective $variantName DeviceAdmin metadata resolved to " +
                        "an unexpected receiver"
                }
                check(receiver == deviceAdminReceivers.single()) {
                    "Effective $variantName DeviceAdmin metadata belongs to an " +
                        "unapproved receiver"
                }
                check(
                    receiver.getAttributeNS(androidNamespace, "permission") ==
                        "android.permission.BIND_DEVICE_ADMIN",
                ) {
                    "Effective $variantName DeviceAdmin receiver permission changed"
                }
                check(
                    receiver.getAttributeNS(androidNamespace, "exported") == "true" &&
                        receiver.getAttributeNS(androidNamespace, "enabled") != "false",
                ) {
                    "Effective $variantName DeviceAdmin receiver is disabled or not exported"
                }
                val enableActions = receiver.getElementsByTagName("action")
                    .elements()
                    .map { it.getAttributeNS(androidNamespace, "name") }
                check(
                    enableActions.toSet() == setOf(
                        "android.app.action.DEVICE_ADMIN_ENABLED",
                        "android.app.action.PROFILE_PROVISIONING_COMPLETE",
                    ) && enableActions.size == 2,
                ) {
                    "Effective $variantName DeviceAdmin receiver actions changed: " +
                        enableActions
                }
                val purposeMetadata = manifest.getElementsByTagName("meta-data")
                    .elements()
                    .filter {
                        it.getAttributeNS(androidNamespace, "name") ==
                            DestructiveValidationExpectedIdentity.BUILD_PURPOSE_METADATA_NAME
                    }
                if (
                    variantName ==
                    DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE
                ) {
                    check(purposeMetadata.size == 1) {
                        "Effective $variantName manifest must contain exactly one " +
                            "disposable-validation build-purpose metadata entry; " +
                            "found ${purposeMetadata.size}"
                    }
                    check(
                        purposeMetadata.single().getAttributeNS(androidNamespace, "value") ==
                            DestructiveValidationExpectedIdentity
                                .BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION,
                    ) {
                        "Effective $variantName build-purpose metadata value changed"
                    }
                } else {
                    check(purposeMetadata.isEmpty()) {
                        "Effective $variantName manifest must not claim the " +
                            "disposable-validation build purpose"
                    }
                }
                verifyProvisioningManifest(manifest, androidNamespace, variantName)
                val hardeningViolations = EffectiveManifestSecurityVerifier.verify(
                    manifest = manifest,
                    androidNamespace = androidNamespace,
                    variantName = variantName,
                    requireNonDebuggable = variantName == "release" ||
                        variantName ==
                        DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE,
                )
                check(hardeningViolations.isEmpty()) {
                    hardeningViolations.joinToString("\n")
                }
                check(
                    metadata.getAttributeNS(androidNamespace, "resource") ==
                        "@xml/device_admin_receiver",
                ) {
                    "Effective $variantName DeviceAdminReceiver must resolve " +
                        "@xml/device_admin_receiver"
                }

                val binaryLinked = layout.buildDirectory.file(
                    "intermediates/linked_resources_binary_format/$variantName/" +
                        "process${capitalized}Resources/" +
                        "linked-resources-binary-format-$variantName.ap_",
                ).get().asFile
                val protoLinked = layout.buildDirectory.file(
                    "intermediates/linked_resources_proto_format/$variantName/" +
                        "process${capitalized}Resources/" +
                        "linked-resources-proto-format-$variantName.ap_",
                ).get().asFile
                val linkedResources = when {
                    binaryLinked.isFile -> binaryLinked
                    protoLinked.isFile -> protoLinked
                    else -> error(
                        "Linked $variantName resources are unavailable at " +
                            "$binaryLinked or $protoLinked",
                    )
                }
                val output = ByteArrayOutputStream()
                project.exec {
                    commandLine(
                        aapt2.absolutePath,
                        "dump",
                        "xmltree",
                        linkedResources.absolutePath,
                        "--file",
                        "res/xml/device_admin_receiver.xml",
                    )
                    standardOutput = output
                }
                val elementLines = output.toString(Charsets.UTF_8)
                    .lineSequence()
                    .mapNotNull { line ->
                        val match = Regex(
                            """^(\s*)E:\s+([A-Za-z0-9_-]+)\b""",
                        ).find(line) ?: return@mapNotNull null
                        match.groupValues[1].length to match.groupValues[2]
                    }
                    .toList()
                check(elementLines.firstOrNull()?.second == "device-admin") {
                    "Effective $variantName metadata root must be device-admin"
                }
                val usesPoliciesIndex = elementLines.indexOfFirst {
                    it.second == "uses-policies"
                }
                check(usesPoliciesIndex >= 0)
                check(elementLines.count { it.second == "uses-policies" } == 1)
                val usesPoliciesIndent = elementLines[usesPoliciesIndex].first
                val policyElements = elementLines
                    .drop(usesPoliciesIndex + 1)
                    .takeWhile { it.first > usesPoliciesIndent }
                    .map { it.second }
                check(policyElements.intersect(checkpoint17BForbiddenPolicies).isEmpty()) {
                    "Checkpoint 19B DeviceAdmin still forbids unreviewed policies; " +
                        "found $policyElements"
                }
                check(
                    policyElements.toSet() == approvedPolicies &&
                        policyElements.size == approvedPolicies.size,
                ) {
                    "Effective $variantName DeviceAdmin policies must be " +
                        "exactly $approvedPolicies; found $policyElements"
                }
                val approvedElements = setOf(
                    "device-admin",
                    "uses-policies",
                    "disable-camera",
                    "wipe-data",
                )
                val unapprovedElements =
                    elementLines.map { it.second }.toSet() - approvedElements
                check(unapprovedElements.isEmpty()) {
                    "Effective $variantName metadata contains unapproved " +
                        "elements: $unapprovedElements"
                }
            }
        }

        tasks.matching {
            it.name == "assemble$capitalized" ||
                it.name == "bundle$capitalized" ||
                it.name == "package$capitalized" ||
                it.name == "test${capitalized}UnitTest" ||
                it.name == "check$capitalized" ||
                it.name == "check"
        }.configureEach {
            dependsOn(
                guardTask,
                policyGuard,
                rootProject.tasks.named("checkProductionBytecodePolicy"),
            )
        }

        if (variantName == "release") {
            val apkSecurity = tasks.register<ReleaseArtifactSecurityTask>(
                "checkReleaseProductionSecurity",
            ) {
                group = "verification"
                description =
                    "Inspects the release APK, merged manifest, R8 mapping, and signing boundary."
                this.variantName.set("release")
                requireNonDebuggable.set(true)
                productionDistributionRequested.set(requestProductionDistribution)
                expectedProductionCertSha256.set(configuredProductionCertSha256.orEmpty())
                mergedManifest.set(mergedManifestArtifact)
                backupRules.set(file("src/main/res/xml/backup_rules.xml"))
                dataExtractionRules.set(file("src/main/res/xml/data_extraction_rules.xml"))
                networkSecurityConfig.set(file("src/main/res/xml/network_security_config.xml"))
                mappingFile.set(
                    variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE),
                )
                apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
                val buildToolsDir = file(
                    "${android.sdkDirectory}/build-tools/" +
                        "${android.buildToolsVersion}",
                )
                buildToolsDirectory.set(buildToolsDir.absolutePath)
                apksignerPath.set(
                    ApksignerLocator.resolveFromBuildTools(buildToolsDir)
                        ?.absolutePath
                        .orEmpty(),
                )
                signingReport.set(
                    layout.buildDirectory.file(
                        "reports/release-signing-boundary.txt",
                    ),
                )
            }
            val bundleSecurity = tasks.register<ReleaseArtifactSecurityTask>(
                "checkReleaseBundleProductionSecurity",
            ) {
                group = "verification"
                description =
                    "Inspects the release AAB DEX, signing identity, and merged release security policy."
                this.variantName.set("release")
                requireNonDebuggable.set(true)
                productionDistributionRequested.set(requestProductionDistribution)
                expectedProductionCertSha256.set(configuredProductionCertSha256.orEmpty())
                mergedManifest.set(mergedManifestArtifact)
                backupRules.set(file("src/main/res/xml/backup_rules.xml"))
                dataExtractionRules.set(file("src/main/res/xml/data_extraction_rules.xml"))
                networkSecurityConfig.set(file("src/main/res/xml/network_security_config.xml"))
                mappingFile.set(
                    variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE),
                )
                bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
                val buildToolsDir = file(
                    "${android.sdkDirectory}/build-tools/" +
                        "${android.buildToolsVersion}",
                )
                buildToolsDirectory.set(buildToolsDir.absolutePath)
                apksignerPath.set(
                    ApksignerLocator.resolveFromBuildTools(buildToolsDir)
                        ?.absolutePath
                        .orEmpty(),
                )
                signingReport.set(
                    layout.buildDirectory.file(
                        "reports/release-bundle-security.txt",
                    ),
                )
            }
            tasks.register<CheckUnsignedDestructiveValidationCandidateEvidenceTask>(
                "checkUnsignedDestructiveValidationCandidateEvidence",
            ) {
                group = "verification"
                description =
                    "Prove the temporary unsigned release APK is an ineligible untrusted " +
                        "candidate. Never mints a trusted expectation or enables signing."
                apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
                androidSdkDirectory.set(android.sdkDirectory.absolutePath)
                projectRootPath.set(rootProject.layout.projectDirectory.asFile.absolutePath)
                reportFile.set(
                    layout.buildDirectory.file(
                        "reports/destructive-validation-candidate.txt",
                    ),
                )
                snapshotDirectory.set(
                    layout.buildDirectory.dir(
                        "tmp/destructive-validation-unsigned-release-snapshot",
                    ),
                )
            }
            tasks.matching {
                it.name == "assembleRelease" || it.name == "checkRelease"
            }.configureEach {
                dependsOn(apkSecurity)
            }
            tasks.matching { it.name == "bundleRelease" }.configureEach {
                dependsOn(bundleSecurity)
            }
        }

        if (
            variantName ==
            DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE
        ) {
            tasks.register<CheckUnsignedDisposableValidationBuildPurposeEvidenceTask>(
                "checkUnsignedDisposableValidationBuildPurposeEvidence",
            ) {
                group = "verification"
                description =
                    "Prove the dedicated unsigned disposable-validation APK exposes an " +
                        "observed build purpose and remains an ineligible untrusted candidate."
                apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
                androidSdkDirectory.set(android.sdkDirectory.absolutePath)
                projectRootPath.set(rootProject.layout.projectDirectory.asFile.absolutePath)
                reportFile.set(
                    layout.buildDirectory.file(
                        "reports/destructive-validation-disposable-purpose.txt",
                    ),
                )
                snapshotDirectory.set(
                    layout.buildDirectory.dir(
                        "tmp/destructive-validation-disposable-purpose-snapshot",
                    ),
                )
            }
            tasks.register<CheckSignedDisposableValidationTask>(
                "checkSignedDisposableValidation",
            ) {
                group = "verification"
                description =
                    "Fail-closed inspection of an explicitly requested signed " +
                        "disposableValidation APK. Never mints trust and is not a witness."
                validationSigningRequested.set(requestValidationOnlySigning)
                apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
                androidSdkDirectory.set(android.sdkDirectory.absolutePath)
                expectedCertificateSha256.set(configuredValidationCertSha256.orEmpty())
                reportFile.set(
                    layout.buildDirectory.file(
                        "reports/signed-disposable-validation.txt",
                    ),
                )
                snapshotDirectory.set(
                    layout.buildDirectory.dir(
                        "tmp/signed-disposable-validation-snapshot",
                    ),
                )
            }
        }
    }
}

tasks.register<CheckDestructiveSigningCeremonyPreparationTask>(
    "checkDestructiveSigningCeremonyPreparation",
) {
    group = "verification"
    description =
        "Prove the real signing-ceremony preparation state remains NOT_READY. " +
            "Never signs, never reads production secrets, and never mints trust."
    disposableValidationRemainsUnsigned.set(
        android.buildTypes
            .getByName(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE)
            .signingConfig == null,
    )
    productionSigningConfigurationActive.set(
        android.signingConfigs.findByName("production") != null,
    )
    productionDistributionRequested.set(requestProductionDistribution)
    filledCeremonyRecordPath.set(
        rootProject.layout.projectDirectory
            .file(DestructiveSigningCeremonyPreparation.FILLED_RECORD_RELATIVE_PATH)
            .asFile
            .absolutePath,
    )
    filledCeremonyRecord.from(
        rootProject.layout.projectDirectory.dir("local").asFileTree.matching {
            include("destructive-signing-ceremony-record.txt")
        },
    )
    reportFile.set(
        layout.buildDirectory.file("reports/destructive-signing-ceremony-preparation.txt"),
    )
    temporaryDirectory.set(
        layout.buildDirectory.dir("tmp/destructive-signing-ceremony-preparation"),
    )
}

val checkBackupDataExtractionPolicy by tasks.registering {
    group = "verification"
    description =
        "Verifies backup and data-extraction XML excludes all Sentinel private data."
    val backup = file("src/main/res/xml/backup_rules.xml")
    val extraction = file("src/main/res/xml/data_extraction_rules.xml")
    val network = file("src/main/res/xml/network_security_config.xml")
    inputs.files(backup, extraction, network)
    doLast {
        val violations = BackupPolicyVerifier.verify(backup, extraction).toMutableList()
        violations += NetworkSecurityConfigVerifier.verify(network)
        check(violations.isEmpty()) {
            violations.joinToString("\n")
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(
        checkAppDependencyIsolation,
        checkAppApiCompileNegative,
        checkBackupDataExtractionPolicy,
    )
    systemProperty(
        "appSourceDir",
        layout.projectDirectory.dir("src").asFile.absolutePath,
    )
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(
        checkAppDependencyIsolation,
        checkAppApiCompileNegative,
        checkBackupDataExtractionPolicy,
    )
}

val checkProductionDistributionSigning by tasks.registering {
    group = "verification"
    description =
        "Fails closed when a production distribution is requested without signing secrets."
    doLast {
        check(readProductionSigningSecrets() != null) {
            "Production distribution signing requires SENTINEL_RELEASE_STORE_FILE, " +
                "SENTINEL_RELEASE_STORE_PASSWORD, SENTINEL_RELEASE_KEY_ALIAS, and " +
                "SENTINEL_RELEASE_KEY_PASSWORD from the environment or gitignored " +
                "local.properties. Refusing to fall back to the Android debug key."
        }
        val rawFingerprint = readExpectedProductionCertSha256()
        check(!rawFingerprint.isNullOrBlank()) {
            "Production distribution requires SENTINEL_RELEASE_CERT_SHA256 from the " +
                "environment or gitignored local.properties. A random developer " +
                "certificate is never treated as production."
        }
        check(
            ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(rawFingerprint) != null,
        ) {
            "SENTINEL_RELEASE_CERT_SHA256 is not a valid SHA-256 fingerprint"
        }
        val releaseSigning = android.signingConfigs.findByName("production")
        check(releaseSigning != null) {
            "Production signing configuration was not applied"
        }
        check(android.signingConfigs.findByName("debug") !== releaseSigning) {
            "Production signing must not reuse the Android debug signing configuration"
        }
    }
}

tasks.register("assembleProductionRelease") {
    group = "build"
    description =
        "Release assemble that inspects the produced APK and fails unless it is " +
            "exactly PRODUCTION_SIGNED."
    dependsOn(
        checkProductionDistributionSigning,
        "assembleRelease",
        "checkReleaseProductionSecurity",
    )
}

tasks.register("bundleProductionRelease") {
    group = "build"
    description =
        "Release bundle that inspects the produced AAB and fails unless it is " +
            "exactly PRODUCTION_SIGNED."
    dependsOn(
        checkProductionDistributionSigning,
        "bundleRelease",
        "checkReleaseBundleProductionSecurity",
    )
}

tasks.register("assembleSignedDisposableValidation") {
    group = "build"
    description =
        "Disposable-validation assemble that attaches a separate validation-only " +
            "key after an explicit request and fail-closed inspection. Not " +
            "production, not trusted, and not an independent witness."
    dependsOn(
        "assembleDisposableValidation",
        "checkSignedDisposableValidation",
    )
    doFirst {
        check(requestValidationOnlySigning) {
            "validation-only signing attaches only when " +
                "assembleSignedDisposableValidation is requested"
        }
        check(android.signingConfigs.findByName("validationOnly") != null) {
            "validation-only signing configuration was not applied"
        }
        check(
            android.buildTypes
                .getByName(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE)
                .signingConfig === android.signingConfigs.findByName("validationOnly"),
        ) {
            "disposableValidation must use only the validation-only signing configuration"
        }
        check(
            android.buildTypes.getByName("release").signingConfig !==
                android.signingConfigs.findByName("validationOnly"),
        ) {
            "release must never use the validation-only signing key"
        }
        check(android.signingConfigs.findByName("production") == null) {
            "validation-only signing must not create a production signing configuration"
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(checkAppDependencyIsolation)
}
