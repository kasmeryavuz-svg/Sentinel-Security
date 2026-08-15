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
            dex.forEachCodeItem { classDescriptor, methodName, proto, code ->
                val location = "$classDescriptor->$methodName$proto"
                val scanned = scanCodeUnits(
                    units = code.units,
                    wipeDeviceMethodIndexes = wipeDeviceIndexes,
                    wipeDataMethodIndexes = wipeDataIndexes,
                    handlerEntries = code.handlerEntries,
                )
                val methodHasDestructiveInvoke =
                    scanned.wipeDeviceExactZero.isNotEmpty() || scanned.wipeDataCount > 0
                if (scanned.decodeIncomplete) {
                    violations +=
                        "$sourceName has unparseable DEX instructions at $location " +
                            "pc=${scanned.unparseablePc}; exact integer constant 0 cannot be proved"
                } else if (methodHasDestructiveInvoke && (scanned.unparseablePc != null || code.handlersUnparseable)) {
                    violations +=
                        "$sourceName has unparseable DEX control-flow at $location " +
                            "pc=${scanned.unparseablePc}; exact integer constant 0 cannot be proved"
                }
                repeat(scanned.wipeDataCount) {
                    violations +=
                        "$sourceName contains DevicePolicyManager.wipeData at $location"
                }
                scanned.wipeDeviceExactZero.forEach { exactConstantZero ->
                    val site = "$sourceName $location wipeDevice(I)V"
                    wipeDeviceCalls += site
                    if (!exactConstantZero || code.handlersUnparseable || scanned.unparseablePc != null) {
                        violations +=
                            "$sourceName invokes DevicePolicyManager.wipeDevice without " +
                                "control-flow proof of exact integer constant 0 at $location"
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
        handlerEntries: Set<Int> = emptySet(),
    ): DexWipeStreamResult {
        val decoded = decodeMethod(units) ?: return DexWipeStreamResult(
            wipeDeviceExactZero = emptyList(),
            wipeDataCount = 0,
            unparseablePc = 0,
            decodeIncomplete = true,
        )
        if (decoded.unparseablePc != null) {
            return collectInvokes(
                decoded = decoded,
                wipeDeviceMethodIndexes = wipeDeviceMethodIndexes,
                wipeDataMethodIndexes = wipeDataMethodIndexes,
                predecessors = emptyMap(),
                handlerEntries = handlerEntries,
                unparseablePc = decoded.unparseablePc,
                decodeIncomplete = true,
            )
        }
        val predecessors = buildPredecessors(decoded, units)
            ?: return collectInvokes(
                decoded = decoded,
                wipeDeviceMethodIndexes = wipeDeviceMethodIndexes,
                wipeDataMethodIndexes = wipeDataMethodIndexes,
                predecessors = emptyMap(),
                handlerEntries = handlerEntries,
                unparseablePc = decoded.instructions.keys.minOrNull() ?: 0,
                decodeIncomplete = false,
            )
        for (handler in handlerEntries) {
            if (handler !in decoded.instructions) {
                return collectInvokes(
                    decoded = decoded,
                    wipeDeviceMethodIndexes = wipeDeviceMethodIndexes,
                    wipeDataMethodIndexes = wipeDataMethodIndexes,
                    predecessors = predecessors,
                    handlerEntries = handlerEntries,
                    unparseablePc = handler,
                    decodeIncomplete = false,
                )
            }
        }
        return collectInvokes(
            decoded = decoded,
            wipeDeviceMethodIndexes = wipeDeviceMethodIndexes,
            wipeDataMethodIndexes = wipeDataMethodIndexes,
            predecessors = predecessors,
            handlerEntries = handlerEntries,
            unparseablePc = null,
            decodeIncomplete = false,
        )
    }

    private fun collectInvokes(
        decoded: DecodedMethod,
        wipeDeviceMethodIndexes: Set<Int>,
        wipeDataMethodIndexes: Set<Int>,
        predecessors: Map<Int, Set<Int>>,
        handlerEntries: Set<Int>,
        unparseablePc: Int?,
        decodeIncomplete: Boolean,
    ): DexWipeStreamResult {
        val wipeDeviceExactZero = mutableListOf<Boolean>()
        var wipeDataCount = 0
        decoded.instructions.values.forEach { insn ->
            if (insn.opcode !in INVOKE_OPCODES) {
                return@forEach
            }
            if (insn.methodIndex in wipeDataMethodIndexes) {
                wipeDataCount++
            }
            if (insn.methodIndex in wipeDeviceMethodIndexes) {
                val proved = unparseablePc == null &&
                    hasControlFlowConstZeroProof(
                        invoke = insn,
                        decoded = decoded,
                        predecessors = predecessors,
                        handlerEntries = handlerEntries,
                    )
                wipeDeviceExactZero += proved
            }
        }
        return DexWipeStreamResult(
            wipeDeviceExactZero = wipeDeviceExactZero,
            wipeDataCount = wipeDataCount,
            unparseablePc = unparseablePc,
            decodeIncomplete = decodeIncomplete,
        )
    }

    private fun hasControlFlowConstZeroProof(
        invoke: DecodedInsn,
        decoded: DecodedMethod,
        predecessors: Map<Int, Set<Int>>,
        handlerEntries: Set<Int>,
    ): Boolean {
        if (invoke.pc in handlerEntries) {
            return false
        }
        val previousPc = decoded.instructions.keys.filter { it < invoke.pc }.maxOrNull() ?: return false
        val previous = decoded.instructions.getValue(previousPc)
        if (previous.pc + previous.size != invoke.pc) {
            return false
        }
        if (!previous.fallThrough || !previous.isExactConstantZeroTo(invoke.flagsRegister)) {
            return false
        }
        return predecessors[invoke.pc] == setOf(previous.pc)
    }

    private fun decodeMethod(units: IntArray): DecodedMethod? {
        if (units.isEmpty()) {
            return DecodedMethod(instructions = emptyMap(), payloadStarts = emptySet(), unparseablePc = null)
        }
        val payloadStarts = mutableSetOf<Int>()
        val instructions = linkedMapOf<Int, DecodedInsn>()
        var pc = 0
        while (pc < units.size) {
            if (pc in payloadStarts) {
                val skipped = payloadSize(units, pc) ?: return DecodedMethod(
                    instructions = instructions,
                    payloadStarts = payloadStarts,
                    unparseablePc = pc,
                )
                if (skipped <= 0 || pc + skipped > units.size) {
                    return DecodedMethod(
                        instructions = instructions,
                        payloadStarts = payloadStarts,
                        unparseablePc = pc,
                    )
                }
                pc += skipped
                continue
            }
            val decoded = decodeInsn(units, pc) ?: return DecodedMethod(
                instructions = instructions,
                payloadStarts = payloadStarts,
                unparseablePc = pc,
            )
            if (decoded.payloadOffset != 0) {
                val start = pc + decoded.payloadOffset
                if (start <= pc || start >= units.size || payloadSize(units, start) == null) {
                    return DecodedMethod(
                        instructions = instructions,
                        payloadStarts = payloadStarts,
                        unparseablePc = pc,
                    )
                }
                payloadStarts += start
            }
            instructions[pc] = decoded
            pc += decoded.size
        }
        return DecodedMethod(
            instructions = instructions,
            payloadStarts = payloadStarts,
            unparseablePc = null,
        )
    }

    private fun buildPredecessors(decoded: DecodedMethod, units: IntArray): Map<Int, Set<Int>>? {
        val predecessors = mutableMapOf<Int, MutableSet<Int>>()
        decoded.instructions.values.forEach { insn ->
            val successors = successors(insn, decoded, units) ?: return null
            successors.forEach { target ->
                predecessors.getOrPut(target) { mutableSetOf() }.add(insn.pc)
            }
        }
        return predecessors
    }

    private fun successors(
        insn: DecodedInsn,
        decoded: DecodedMethod,
        units: IntArray,
    ): Set<Int>? {
        val targets = mutableSetOf<Int>()
        if (insn.fallThrough) {
            val next = insn.pc + insn.size
            when {
                next == units.size -> Unit
                next in decoded.payloadStarts -> Unit
                next in decoded.instructions -> targets += next
                else -> return null
            }
        }
        if (insn.branchOffset != null) {
            val target = insn.pc + insn.branchOffset
            if (target !in decoded.instructions) {
                return null
            }
            targets += target
        }
        if (insn.payloadOffset != 0 && insn.opcode in SWITCH_OPCODES) {
            val payloadPc = insn.pc + insn.payloadOffset
            val switchTargets = switchTargets(units, insn.pc, payloadPc, insn.opcode) ?: return null
            switchTargets.forEach { target ->
                if (target !in decoded.instructions) {
                    return null
                }
                targets += target
            }
        }
        return targets
    }

    private fun switchTargets(
        units: IntArray,
        switchPc: Int,
        payloadPc: Int,
        opcode: Int,
    ): List<Int>? {
        if (payloadPc + 1 >= units.size) {
            return null
        }
        val ident = units[payloadPc]
        val size = units[payloadPc + 1]
        if (size < 0) {
            return null
        }
        val targetBase = when {
            opcode == 0x2b && ident == 0x0100 -> payloadPc + 4
            opcode == 0x2c && ident == 0x0200 -> payloadPc + 2 + size * 2
            else -> return null
        }
        if (targetBase + size * 2 > units.size) {
            return null
        }
        return (0 until size).map { index ->
            val off = targetBase + index * 2
            val rel = units[off] or (units[off + 1] shl 16)
            switchPc + rel
        }
    }

    private fun DecodedInsn.isExactConstantZeroTo(register: Int): Boolean {
        if (register < 0) {
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
            0x0e, 0x0f, 0x10, 0x11, 0x27 -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                fallThrough = false,
            )
            0x12 -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                destRegister = (first shr 8) and 0x0f,
                literal = signExtend((first shr 12) and 0x0f, 4),
            )
            0x13 -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                destRegister = (first shr 8) and 0xff,
                literal = signExtend(second, 16),
            )
            0x14 -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                destRegister = (first shr 8) and 0xff,
                literal = second or (third shl 16),
            )
            0x15 -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                destRegister = (first shr 8) and 0xff,
                literal = second shl 16,
            )
            0x28 -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                fallThrough = false,
                branchOffset = signExtend((first shr 8) and 0xff, 8),
            )
            0x29 -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                fallThrough = false,
                branchOffset = signExtend(second, 16),
            )
            0x2a -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                fallThrough = false,
                branchOffset = signExtend(second or (third shl 16), 32),
            )
            in 0x32..0x3d -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                branchOffset = signExtend(second, 16),
            )
            in 0x6e..0x72 -> {
                val argCount = (first shr 12) and 0x0f
                val registerC = third and 0x0f
                val registerD = (third shr 4) and 0x0f
                DecodedInsn(
                    pc = pc,
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
                    pc = pc,
                    opcode = opcode,
                    size = size,
                    methodIndex = second,
                    flagsRegister = if (argCount >= 2) start + 1 else -1,
                )
            }
            0x26, 0x2b, 0x2c -> DecodedInsn(
                pc = pc,
                opcode = opcode,
                size = size,
                payloadOffset = signExtend(second or (third shl 16), 32),
            )
            else -> DecodedInsn(pc = pc, opcode = opcode, size = size)
        }
    }

    private fun signExtend(value: Int, bits: Int): Int {
        val shift = 32 - bits
        return (value shl shift) shr shift
    }

    private data class DecodedInsn(
        val pc: Int,
        val opcode: Int,
        val size: Int,
        val destRegister: Int = -1,
        val literal: Int = 0,
        val methodIndex: Int = -1,
        val flagsRegister: Int = -1,
        val payloadOffset: Int = 0,
        val fallThrough: Boolean = true,
        val branchOffset: Int? = null,
    )

    private data class DecodedMethod(
        val instructions: Map<Int, DecodedInsn>,
        val payloadStarts: Set<Int>,
        val unparseablePc: Int?,
    )

    internal data class DexWipeStreamResult(
        val wipeDeviceExactZero: List<Boolean>,
        val wipeDataCount: Int,
        val unparseablePc: Int?,
        val decodeIncomplete: Boolean = false,
    )

    private val INVOKE_OPCODES = (0x6e..0x72).toSet() + (0x74..0x78).toSet()
    private val SWITCH_OPCODES = setOf(0x2b, 0x2c)
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
            visit: (
                classDescriptor: String,
                methodName: String,
                proto: String,
                code: DexMethodCode,
            ) -> Unit,
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
                            readCode(codeOff),
                        )
                    }
                }
            }
        }

        private fun readCode(codeOff: Int): DexMethodCode {
            val triesSize = bytes.readUShort(codeOff + 6)
            val insnsSize = bytes.readInt(codeOff + 12)
            val insnsOff = codeOff + 16
            val units = IntArray(insnsSize) { index ->
                bytes.readUShort(insnsOff + index * 2)
            }
            if (triesSize == 0) {
                return DexMethodCode(
                    units = units,
                    handlerEntries = emptySet(),
                    handlersUnparseable = false,
                )
            }
            var triesOff = insnsOff + insnsSize * 2
            if (insnsSize % 2 == 1) {
                triesOff += 2
            }
            val handlerListOff = triesOff + triesSize * 8
            val handlers = mutableSetOf<Int>()
            repeat(triesSize) { index ->
                val tryOff = triesOff + index * 8
                if (tryOff + 8 > bytes.size) {
                    return DexMethodCode(units, emptySet(), handlersUnparseable = true)
                }
                val handlerOff = bytes.readUShort(tryOff + 6)
                if (!readCatchHandler(handlerListOff + handlerOff, handlers)) {
                    return DexMethodCode(units, emptySet(), handlersUnparseable = true)
                }
            }
            return DexMethodCode(
                units = units,
                handlerEntries = handlers,
                handlersUnparseable = false,
            )
        }

        private fun readCatchHandler(offset: Int, handlers: MutableSet<Int>): Boolean {
            val cursor = Cursor(offset)
            val size = try {
                cursor.sleb()
            } catch (_: Exception) {
                return false
            }
            val typedCount = kotlin.math.abs(size)
            repeat(typedCount) {
                try {
                    cursor.uleb()
                    handlers += cursor.uleb()
                } catch (_: Exception) {
                    return false
                }
            }
            if (size <= 0) {
                try {
                    handlers += cursor.uleb()
                } catch (_: Exception) {
                    return false
                }
            }
            return true
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
                    check(offset < bytes.size)
                    val value = bytes[offset].toInt() and 0xff
                    offset++
                    result = result or ((value and 0x7f) shl shift)
                    if (value and 0x80 == 0) {
                        return result
                    }
                    shift += 7
                    check(shift < 32)
                }
            }

            fun sleb(): Int {
                var result = 0
                var shift = 0
                var value: Int
                do {
                    check(offset < bytes.size)
                    value = bytes[offset].toInt() and 0xff
                    offset++
                    result = result or ((value and 0x7f) shl shift)
                    shift += 7
                    check(shift <= 32)
                } while (value and 0x80 != 0)
                if (shift < 32 && value and 0x40 != 0) {
                    result = result or (-1 shl shift)
                }
                return result
            }
        }
    }

    private data class DexMethodCode(
        val units: IntArray,
        val handlerEntries: Set<Int>,
        val handlersUnparseable: Boolean,
    )

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
