internal object DexWipeDeviceVerifier {
    private const val DPM = "Landroid/app/admin/DevicePolicyManager;"
    private const val WIPE_DEVICE = "wipeDevice"
    private const val WIPE_DATA = "wipeData"
    private const val INT_DESCRIPTOR = "I"
    private const val VOID_DESCRIPTOR = "V"

    fun verify(dexFiles: List<ByteArray>, sourceName: String): List<String> {
        val wipeDeviceCalls = mutableListOf<String>()
        val violations = mutableListOf<String>()
        dexFiles.forEach { bytes ->
            val dex = DexFile(bytes)
            val wipeDeviceIndexes = dex.methodIndexes(DPM, WIPE_DEVICE, INT_DESCRIPTOR, VOID_DESCRIPTOR)
            val wipeDataIndexes = dex.methodIndexes(DPM, WIPE_DATA, INT_DESCRIPTOR, VOID_DESCRIPTOR)
            dex.forEachCodeItem { classDescriptor, methodName, proto, units ->
                val location = "$classDescriptor->$methodName$proto"
                val scanned = scanCodeUnits(units, wipeDeviceIndexes, wipeDataIndexes)
                if (scanned.unparseablePc != null) {
                    violations +=
                        "$sourceName has unparseable DEX instructions at $location " +
                            "pc=${scanned.unparseablePc}; exact integer constant 0 cannot be proved"
                    return@forEachCodeItem
                }
                repeat(scanned.wipeDataCount) {
                    violations +=
                        "$sourceName contains DevicePolicyManager.wipeData at $location"
                }
                scanned.wipeDeviceExactZero.forEach { exactConstantZero ->
                    val site = "$sourceName $location wipeDevice(I)V"
                    wipeDeviceCalls += site
                    if (!exactConstantZero) {
                        violations +=
                            "$sourceName invokes DevicePolicyManager.wipeDevice without " +
                                "exact integer constant 0 at $location"
                    }
                }
            }
        }
        if (wipeDeviceCalls.size != 1) {
            violations +=
                "$sourceName must contain exactly one DevicePolicyManager.wipeDevice(I)V " +
                    "invoke; found ${wipeDeviceCalls.size}" +
                    if (wipeDeviceCalls.isEmpty()) {
                        ""
                    } else {
                        ": ${wipeDeviceCalls.joinToString()}"
                    }
        }
        return violations
    }

    internal fun scanCodeUnits(
        units: IntArray,
        wipeDeviceMethodIndexes: Set<Int>,
        wipeDataMethodIndexes: Set<Int> = emptySet(),
    ): DexWipeStreamResult {
        val wipeDeviceExactZero = mutableListOf<Boolean>()
        var wipeDataCount = 0
        val payloadStarts = mutableSetOf<Int>()
        var pc = 0
        var previous: DecodedInsn? = null
        while (pc < units.size) {
            if (pc in payloadStarts) {
                val skipped = payloadSize(units, pc) ?: return DexWipeStreamResult(
                    wipeDeviceExactZero = wipeDeviceExactZero,
                    wipeDataCount = wipeDataCount,
                    unparseablePc = pc,
                )
                if (skipped <= 0 || pc + skipped > units.size) {
                    return DexWipeStreamResult(
                        wipeDeviceExactZero = wipeDeviceExactZero,
                        wipeDataCount = wipeDataCount,
                        unparseablePc = pc,
                    )
                }
                pc += skipped
                previous = null
                continue
            }
            val decoded = decodeInsn(units, pc)
            if (decoded == null) {
                return DexWipeStreamResult(
                    wipeDeviceExactZero = wipeDeviceExactZero,
                    wipeDataCount = wipeDataCount,
                    unparseablePc = pc,
                )
            }
            if (decoded.payloadOffset != 0) {
                payloadStarts += pc + decoded.payloadOffset
            }
            if (decoded.opcode in INVOKE_OPCODES) {
                if (decoded.methodIndex in wipeDataMethodIndexes) {
                    wipeDataCount++
                }
                if (decoded.methodIndex in wipeDeviceMethodIndexes) {
                    wipeDeviceExactZero += previous.isExactConstantZeroTo(decoded.flagsRegister)
                }
            }
            previous = decoded
            pc += decoded.size
        }
        return DexWipeStreamResult(
            wipeDeviceExactZero = wipeDeviceExactZero,
            wipeDataCount = wipeDataCount,
            unparseablePc = null,
        )
    }

    private fun DecodedInsn?.isExactConstantZeroTo(register: Int): Boolean {
        if (this == null || register < 0) {
            return false
        }
        return opcode in CONSTANT_OPCODES && destRegister == register && literal == 0
    }

    private fun payloadSize(units: IntArray, pc: Int): Int? {
        if (pc + 1 >= units.size) {
            return null
        }
        val ident = units[pc]
        val size = units[pc + 1]
        return when (ident) {
            0x0100 -> 4 + size * 2
            0x0200 -> 2 + size * 4
            0x0300 -> {
                if (pc + 3 >= units.size) {
                    return null
                }
                val elementWidth = size
                val arraySize = units[pc + 2] or (units[pc + 3] shl 16)
                val dataUnits = (elementWidth.toLong() * arraySize.toLong() + 1L) / 2L
                (4L + dataUnits).toInt()
            }
            else -> null
        }
    }

    private fun decodeInsn(units: IntArray, pc: Int): DecodedInsn? {
        val first = units[pc]
        val opcode = first and 0xff
        val size = INSTRUCTION_UNITS.getOrNull(opcode) ?: return null
        if (size <= 0 || pc + size > units.size) {
            return null
        }
        val second = if (size > 1) units[pc + 1] else 0
        val third = if (size > 2) units[pc + 2] else 0
        return when (opcode) {
            0x12 -> DecodedInsn(
                opcode = opcode,
                size = size,
                destRegister = (first shr 8) and 0x0f,
                literal = signExtend((first shr 12) and 0x0f, 4),
            )
            0x13 -> DecodedInsn(
                opcode = opcode,
                size = size,
                destRegister = (first shr 8) and 0xff,
                literal = signExtend(second, 16),
            )
            0x14 -> DecodedInsn(
                opcode = opcode,
                size = size,
                destRegister = (first shr 8) and 0xff,
                literal = second or (third shl 16),
            )
            0x15 -> DecodedInsn(
                opcode = opcode,
                size = size,
                destRegister = (first shr 8) and 0xff,
                literal = second shl 16,
            )
            in 0x6e..0x72 -> {
                val argCount = (first shr 12) and 0x0f
                val registerC = third and 0x0f
                val registerD = (third shr 4) and 0x0f
                DecodedInsn(
                    opcode = opcode,
                    size = size,
                    methodIndex = second,
                    flagsRegister = if (argCount >= 2) registerD else if (argCount == 1) registerC else -1,
                )
            }
            in 0x74..0x78 -> {
                val argCount = (first shr 8) and 0xff
                val start = third
                DecodedInsn(
                    opcode = opcode,
                    size = size,
                    methodIndex = second,
                    flagsRegister = if (argCount >= 2) start + 1 else -1,
                )
            }
            0x26, 0x2b, 0x2c -> DecodedInsn(
                opcode = opcode,
                size = size,
                payloadOffset = signExtend(second or (third shl 16), 32),
            )
            else -> DecodedInsn(opcode = opcode, size = size)
        }
    }

    private fun signExtend(value: Int, bits: Int): Int {
        val shift = 32 - bits
        return (value shl shift) shr shift
    }

    private data class DecodedInsn(
        val opcode: Int,
        val size: Int,
        val destRegister: Int = -1,
        val literal: Int = 0,
        val methodIndex: Int = -1,
        val flagsRegister: Int = -1,
        val payloadOffset: Int = 0,
    )

    internal data class DexWipeStreamResult(
        val wipeDeviceExactZero: List<Boolean>,
        val wipeDataCount: Int,
        val unparseablePc: Int?,
    )

    private val INVOKE_OPCODES = (0x6e..0x72).toSet() + (0x74..0x78).toSet()
    private val CONSTANT_OPCODES = setOf(0x12, 0x13, 0x14, 0x15)

    private val INSTRUCTION_UNITS = intArrayOf(
        1, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 2, 3, 2, 2, 3, 5, 2, 2, 3, 2, 1, 1, 2,
        2, 1, 2, 2, 3, 3, 3, 1, 1, 2, 3, 3, 3, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0,
        0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3,
        3, 3, 3, 0, 3, 3, 3, 3, 3, 0, 0, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 3, 3, 2, 2,
    )

    private class DexFile(private val bytes: ByteArray) {
        init {
            require(bytes.size >= 0x70) { "DEX too small" }
            require(bytes.decodeToString(0, 4) == "dex\n") { "Not a DEX file" }
        }

        private val stringIdsOff = bytes.readInt(0x3C)
        private val typeIdsOff = bytes.readInt(0x44)
        private val protoIdsOff = bytes.readInt(0x4C)
        private val methodIdsSize = bytes.readInt(0x58)
        private val methodIdsOff = bytes.readInt(0x5C)
        private val classDefsSize = bytes.readInt(0x60)
        private val classDefsOff = bytes.readInt(0x64)

        fun methodIndexes(
            classDescriptor: String,
            name: String,
            parameter: String,
            returnType: String,
        ): Set<Int> {
            return (0 until methodIdsSize).filter { index ->
                val off = methodIdsOff + index * 8
                val classIdx = bytes.readUShort(off)
                val protoIdx = bytes.readUShort(off + 2)
                val nameIdx = bytes.readInt(off + 4)
                string(nameIdx) == name &&
                    type(classIdx) == classDescriptor &&
                    protoMatches(protoIdx, parameter, returnType)
            }.toSet()
        }

        fun forEachCodeItem(
            visit: (classDescriptor: String, methodName: String, proto: String, units: IntArray) -> Unit,
        ) {
            repeat(classDefsSize) { classIndex ->
                val defOff = classDefsOff + classIndex * 32
                val classDescriptor = type(bytes.readInt(defOff))
                val classDataOff = bytes.readInt(defOff + 24)
                if (classDataOff == 0) {
                    return@repeat
                }
                val cursor = Cursor(classDataOff)
                val staticFieldsSize = cursor.uleb()
                val instanceFieldsSize = cursor.uleb()
                val directMethodsSize = cursor.uleb()
                val virtualMethodsSize = cursor.uleb()
                repeat(staticFieldsSize + instanceFieldsSize) {
                    cursor.uleb()
                    cursor.uleb()
                }
                var methodIndex = 0
                repeat(directMethodsSize + virtualMethodsSize) { index ->
                    if (index == directMethodsSize) {
                        methodIndex = 0
                    }
                    methodIndex += cursor.uleb()
                    cursor.uleb()
                    val codeOff = cursor.uleb()
                    if (codeOff != 0) {
                        val nameIdx = bytes.readInt(methodIdsOff + methodIndex * 8 + 4)
                        val protoIdx = bytes.readUShort(methodIdsOff + methodIndex * 8 + 2)
                        visit(
                            classDescriptor,
                            string(nameIdx),
                            proto(protoIdx),
                            codeUnits(codeOff),
                        )
                    }
                }
            }
        }

        private fun protoMatches(protoIdx: Int, parameter: String, returnType: String): Boolean {
            val off = protoIdsOff + protoIdx * 12
            if (type(bytes.readInt(off + 4)) != returnType) {
                return false
            }
            val parametersOff = bytes.readInt(off + 8)
            if (parametersOff == 0) {
                return false
            }
            val size = bytes.readInt(parametersOff)
            if (size != 1) {
                return false
            }
            return type(bytes.readUShort(parametersOff + 4)) == parameter
        }

        private fun proto(protoIdx: Int): String {
            val off = protoIdsOff + protoIdx * 12
            val returnType = type(bytes.readInt(off + 4))
            val parametersOff = bytes.readInt(off + 8)
            if (parametersOff == 0) {
                return "()$returnType"
            }
            val size = bytes.readInt(parametersOff)
            val params = (0 until size).joinToString("") { index ->
                type(bytes.readUShort(parametersOff + 4 + index * 2))
            }
            return "($params)$returnType"
        }

        private fun codeUnits(codeOff: Int): IntArray {
            val insnsSize = bytes.readInt(codeOff + 12)
            val insnsOff = codeOff + 16
            return IntArray(insnsSize) { index ->
                bytes.readUShort(insnsOff + index * 2)
            }
        }

        private fun type(index: Int): String {
            val descriptorIdx = bytes.readInt(typeIdsOff + index * 4)
            return string(descriptorIdx)
        }

        private fun string(index: Int): String {
            val dataOff = bytes.readInt(stringIdsOff + index * 4)
            return bytes.readMutf8(dataOff)
        }

        private inner class Cursor(start: Int) {
            private var offset = start

            fun uleb(): Int {
                var result = 0
                var shift = 0
                while (true) {
                    val value = bytes[offset].toInt() and 0xff
                    offset++
                    result = result or ((value and 0x7f) shl shift)
                    if (value and 0x80 == 0) {
                        return result
                    }
                    shift += 7
                }
            }
        }
    }

    private fun ByteArray.readInt(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun ByteArray.readUShort(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArray.readMutf8(offset: Int): String {
        var cursor = offset
        while (cursor < size && (this[cursor].toInt() and 0x80) != 0) {
            cursor++
        }
        cursor++
        val buffer = java.io.ByteArrayOutputStream()
        while (cursor < size && this[cursor] != 0.toByte()) {
            buffer.write(this[cursor].toInt() and 0xff)
            cursor++
        }
        return buffer.toString("UTF-8")
    }
}
