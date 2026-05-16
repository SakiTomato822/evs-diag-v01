package com.lynk.evsdiag

import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

object RootShell {
    fun run(command: String, timeoutMs: Long = 10_000): ShellResult {
        val process = ProcessBuilder("su", "-c", command)
            .directory(File("/"))
            .start()

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ShellResult(-1, "", "timeout")
        }

        val stdout = process.inputStream.bufferedReader().use { it.readText().trim() }
        val stderr = process.errorStream.bufferedReader().use { it.readText().trim() }
        return ShellResult(process.exitValue(), stdout, stderr)
    }
}
