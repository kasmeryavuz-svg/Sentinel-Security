import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DexWipeDeviceVerifierTest {
    @Test
    fun `immediately previous const 4 zero is accepted`() {
        val units = const4(dest = 0, literal = 0) +
            invokeVirtual(methodIndex = 0, thisRegister = 1, flagsRegister = 0)
        val result = DexWipeDeviceVerifier.scanCodeUnits(units, wipeDeviceMethodIndexes = setOf(0))
        assertNull(result.unparseablePc)
        assertEquals(listOf(true), result.wipeDeviceExactZero)
        assertEquals(0, result.wipeDataCount)
    }

    @Test
    fun `non-zero const 4 is rejected`() {
        val units = const4(dest = 0, literal = 1) +
            invokeVirtual(methodIndex = 0, thisRegister = 1, flagsRegister = 0)
        val result = DexWipeDeviceVerifier.scanCodeUnits(units, wipeDeviceMethodIndexes = setOf(0))
        assertEquals(listOf(false), result.wipeDeviceExactZero)
    }

    @Test
    fun `local move between const and invoke cannot prove constant zero`() {
        val units = const4(dest = 0, literal = 0) +
            intArrayOf(move(dest = 2, source = 0)) +
            invokeVirtual(methodIndex = 0, thisRegister = 1, flagsRegister = 2)
        val result = DexWipeDeviceVerifier.scanCodeUnits(units, wipeDeviceMethodIndexes = setOf(0))
        assertEquals(listOf(false), result.wipeDeviceExactZero)
    }

    @Test
    fun `second wipeDevice invoke is recorded even when the first is constant zero`() {
        val units = const4(dest = 0, literal = 0) +
            invokeVirtual(methodIndex = 0, thisRegister = 1, flagsRegister = 0) +
            const4(dest = 0, literal = 0) +
            invokeVirtual(methodIndex = 0, thisRegister = 1, flagsRegister = 0)
        val result = DexWipeDeviceVerifier.scanCodeUnits(units, wipeDeviceMethodIndexes = setOf(0))
        assertEquals(listOf(true, true), result.wipeDeviceExactZero)
    }

    @Test
    fun `wipeData invoke is counted independently of wipeDevice`() {
        val units = const4(dest = 0, literal = 0) +
            invokeVirtual(methodIndex = 7, thisRegister = 1, flagsRegister = 0)
        val result = DexWipeDeviceVerifier.scanCodeUnits(
            units,
            wipeDeviceMethodIndexes = setOf(0),
            wipeDataMethodIndexes = setOf(7),
        )
        assertEquals(emptyList(), result.wipeDeviceExactZero)
        assertEquals(1, result.wipeDataCount)
    }

    @Test
    fun `invoke range still requires the flags register to be previous constant zero`() {
        val units = const4(dest = 1, literal = 0) +
            invokeVirtualRange(methodIndex = 0, startRegister = 0, argCount = 2)
        val accepted = DexWipeDeviceVerifier.scanCodeUnits(units, wipeDeviceMethodIndexes = setOf(0))
        assertEquals(listOf(true), accepted.wipeDeviceExactZero)

        val rejected = DexWipeDeviceVerifier.scanCodeUnits(
            const4(dest = 0, literal = 0) +
                invokeVirtualRange(methodIndex = 0, startRegister = 0, argCount = 2),
            wipeDeviceMethodIndexes = setOf(0),
        )
        assertEquals(listOf(false), rejected.wipeDeviceExactZero)
    }

    @Test
    fun `unknown opcode fails closed`() {
        val units = intArrayOf(0x003e)
        val result = DexWipeDeviceVerifier.scanCodeUnits(units, wipeDeviceMethodIndexes = setOf(0))
        assertEquals(0, result.unparseablePc)
        assertEquals(emptyList(), result.wipeDeviceExactZero)
    }

    @Test
    fun `packed-switch payload is skipped so a later const-zero wipe remains visible`() {
        val payload = intArrayOf(
            0x0100,
            1,
            0,
            0,
            0,
            0,
        )
        val units = intArrayOf(
            0x002b,
            3,
            0,
        ) + payload + const4(dest = 0, literal = 0) +
            invokeVirtual(methodIndex = 0, thisRegister = 1, flagsRegister = 0)
        val result = DexWipeDeviceVerifier.scanCodeUnits(units, wipeDeviceMethodIndexes = setOf(0))
        assertNull(result.unparseablePc, "unparseable at ${result.unparseablePc}")
        assertEquals(listOf(true), result.wipeDeviceExactZero)
    }

    @Test
    fun `instruction table width for invoke-polymorphic is four units`() {
        assertTrue(DexWipeDeviceVerifier.scanCodeUnits(
            intArrayOf(0x00fa, 0, 0, 0),
            wipeDeviceMethodIndexes = emptySet(),
        ).unparseablePc == null)
    }

    private fun const4(dest: Int, literal: Int): IntArray {
        return intArrayOf(0x12 or ((dest and 0x0f) shl 8) or ((literal and 0x0f) shl 12))
    }

    private fun move(dest: Int, source: Int): Int {
        return 0x01 or ((dest and 0x0f) shl 8) or ((source and 0x0f) shl 12)
    }

    private fun invokeVirtual(methodIndex: Int, thisRegister: Int, flagsRegister: Int): IntArray {
        return intArrayOf(
            0x6e or (2 shl 12),
            methodIndex,
            (thisRegister and 0x0f) or ((flagsRegister and 0x0f) shl 4),
        )
    }

    private fun invokeVirtualRange(methodIndex: Int, startRegister: Int, argCount: Int): IntArray {
        return intArrayOf(
            0x74 or ((argCount and 0xff) shl 8),
            methodIndex,
            startRegister,
        )
    }
}
