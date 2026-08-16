import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs an external build tool while draining its merged output concurrently.
 *
 * Waiting for a child process before reading its output can deadlock when the
 * platform pipe buffer fills. Windows pipe buffers are especially small, so a
 * full `aapt2 dump xmltree` can otherwise time out even though aapt2 is healthy.
 * Output is capped and still fully drained; timeout, overflow, or reader failure
 * returns null so callers remain fail closed.
 */
object BoundedProcessRunner {
    const val DEFAULT_TIMEOUT_SECONDS = 60L
    const val DEFAULT_MAX_OUTPUT_BYTES = 8 * 1024 * 1024

    data class Result(
        val exitCode: Int,
        val output: String,
    )

    fun run(
        command: List<String>,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    ): Result? {
        if (command.isEmpty() || timeoutSeconds <= 0 || maxOutputBytes <= 0) {
            return null
        }
        val process = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return null
        }
        val output = ByteArrayOutputStream()
        val overflow = AtomicBoolean(false)
        val readFailure = AtomicReference<Throwable?>(null)
        val drainer = Thread(
            {
                try {
                    process.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) {
                                break
                            }
                            val remaining = maxOutputBytes - output.size()
                            if (remaining > 0) {
                                output.write(buffer, 0, minOf(count, remaining))
                            }
                            if (count > remaining) {
                                overflow.set(true)
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    readFailure.set(failure)
                }
            },
            "bounded-build-tool-output",
        ).apply {
            isDaemon = true
            start()
        }
        val finished = try {
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished) {
            process.destroyForcibly()
            try {
                process.waitFor(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            joinQuietly(drainer)
            return null
        }
        joinQuietly(drainer)
        if (drainer.isAlive || readFailure.get() != null || overflow.get()) {
            return null
        }
        return Result(
            exitCode = process.exitValue(),
            output = output.toString(StandardCharsets.UTF_8.name()),
        )
    }

    private fun joinQuietly(thread: Thread) {
        try {
            thread.join(5_000L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
