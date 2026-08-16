import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoundedProcessRunnerTest {
    @Test
    fun `large child output is drained without a pipe deadlock`() {
        val root = Files.createTempDirectory("bounded-process-large-output").toFile()
        try {
            val source = writeLargeOutputFixture(root, chunks = 256)
            val result = BoundedProcessRunner.run(
                command = listOf(javaExecutable().absolutePath, source.absolutePath),
                timeoutSeconds = 30,
                maxOutputBytes = 3 * 1024 * 1024,
            )
            assertEquals(0, result?.exitCode)
            assertEquals(2 * 1024 * 1024, result?.output?.length)
            assertTrue(result!!.output.all { it == 'x' })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `output larger than the cap is fully drained then refused`() {
        val root = Files.createTempDirectory("bounded-process-overflow").toFile()
        try {
            val source = writeLargeOutputFixture(root, chunks = 32)
            val result = BoundedProcessRunner.run(
                command = listOf(javaExecutable().absolutePath, source.absolutePath),
                timeoutSeconds = 30,
                maxOutputBytes = 32 * 1024,
            )
            assertNull(result)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `invalid runner bounds fail closed without starting a process`() {
        assertNull(BoundedProcessRunner.run(emptyList()))
        assertNull(BoundedProcessRunner.run(listOf("unused"), timeoutSeconds = 0))
        assertNull(BoundedProcessRunner.run(listOf("unused"), maxOutputBytes = 0))
    }

    private fun writeLargeOutputFixture(root: File, chunks: Int): File {
        return File(root, "LargeOutput.java").apply {
            writeText(
                """
                public class LargeOutput {
                    public static void main(String[] args) throws Exception {
                        byte[] chunk = new byte[8192];
                        java.util.Arrays.fill(chunk, (byte) 'x');
                        for (int i = 0; i < $chunks; i++) {
                            System.out.write(chunk);
                        }
                    }
                }
                """.trimIndent(),
            )
        }
    }

    private fun javaExecutable(): File {
        val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        return File(System.getProperty("java.home"), "bin/java${if (windows) ".exe" else ""}")
    }
}
