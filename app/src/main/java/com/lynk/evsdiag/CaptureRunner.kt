package com.lynk.evsdiag

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagConfig(
    val processName: String,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val intervalMs: Long,
)

data class DiagResult(
    val sessionDir: File,
    val rawDir: File,
    val pngDir: File,
    val hashes: List<String>,
)

class CaptureRunner(private val context: Context) {
    suspend fun run(config: DiagConfig, onLog: (String) -> Unit): DiagResult = withContext(Dispatchers.IO) {
        val sessionStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val sessionDir = File(baseDir, "evs_diag_v01/$sessionStamp")
        val rawDir = File(sessionDir, "raw")
        val pngDir = File(sessionDir, "png")
        val tmpDir = File(context.cacheDir, "evs_diag_v01_tmp")
        rawDir.mkdirs()
        pngDir.mkdirs()
        tmpDir.mkdirs()

        log(onLog, "session: ${sessionDir.absolutePath}")
        log(onLog, "step1: find EVS pid")
        val pid = findPid(config.processName)
        if (pid <= 0) {
            throw IllegalStateException("cannot find pid for ${config.processName}")
        }
        log(onLog, "pid: $pid")

        val videoCheck = RootShell.run("ls -l /proc/$pid/fd/* 2>/dev/null | grep -E 'video[0-9]+cif'")
        if (videoCheck.stdout.isBlank()) {
            log(onLog, "warning: no video*cif fd found yet. open AVM first.")
        } else {
            log(onLog, "video*cif: ${videoCheck.stdout}")
        }

        log(onLog, "step2: collect dmabuf fds")
        val dmabufFds = listDmabufFds(pid)
        if (dmabufFds.isEmpty()) {
            throw IllegalStateException("no dmabuf fd found in /proc/$pid/fd")
        }
        log(onLog, "dmabuf fds: ${dmabufFds.joinToString(",")}")

        val frameBytes = config.width * config.height * 2
        val hashes = mutableListOf<String>()
        val reportLines = mutableListOf<String>()
        reportLines += "process=${config.processName}"
        reportLines += "pid=$pid"
        reportLines += "width=${config.width} height=${config.height} frameBytes=$frameBytes"
        reportLines += "dmabufFds=${dmabufFds.joinToString(",")}"
        reportLines += "frameCount=${config.frameCount} intervalMs=${config.intervalMs}"

        var logicalSeq = 0
        while (logicalSeq < config.frameCount) {
            val dmabufFd = dmabufFds[logicalSeq % dmabufFds.size]
            val ts = System.currentTimeMillis()
            val raw = captureFrameByDd(
                pid = pid,
                dmabufFd = dmabufFd,
                frameBytes = frameBytes,
                tmpDir = tmpDir,
            )
            if (raw == null || raw.size < frameBytes) {
                log(onLog, "frame[$logicalSeq] fd=$dmabufFd capture failed")
                reportLines += "frame=$logicalSeq ts=$ts fd=$dmabufFd capture=failed"
                logicalSeq += 1
                delay(config.intervalMs)
                continue
            }

            val sha = sha256(raw)
            hashes += sha
            val stem = "frame_${logicalSeq}_ts${ts}_fd${dmabufFd}_sha${sha.take(16)}"
            val rawFile = File(rawDir, "$stem.raw")
            rawFile.writeBytes(raw)

            val pngFile = File(pngDir, "$stem.png")
            val pngOk = FrameConverter.uyvyToPng(raw, config.width, config.height, pngFile)
            val nonZeroRatio = nonZeroRatio(raw)
            reportLines += "frame=$logicalSeq ts=$ts fd=$dmabufFd sha256=$sha nonZeroRatio=$nonZeroRatio raw=${rawFile.name} png=${pngFile.name} pngOk=$pngOk"
            log(onLog, "frame[$logicalSeq] fd=$dmabufFd sha=${sha.take(16)} nz=$nonZeroRatio pngOk=$pngOk")

            logicalSeq += 1
            delay(config.intervalMs)
        }

        val uniqueHashes = hashes.toSet().size
        reportLines += "uniqueHashes=$uniqueHashes total=${hashes.size}"
        File(sessionDir, "summary.txt").writeText(reportLines.joinToString(separator = "\n"))
        log(onLog, "done: uniqueHashes=$uniqueHashes total=${hashes.size}")

        DiagResult(
            sessionDir = sessionDir,
            rawDir = rawDir,
            pngDir = pngDir,
            hashes = hashes,
        )
    }

    private fun findPid(processName: String): Int {
        val escaped = processName.replace("'", "'\\''")
        val result = RootShell.run("pidof '$escaped'")
        if (result.exitCode == 0 && result.stdout.isNotBlank()) {
            return result.stdout.split(Regex("\\s+"))
                .mapNotNull { it.toIntOrNull() }
                .firstOrNull()
                ?: -1
        }

        val fallback = RootShell.run("ps -A | grep '$escaped' | head -n 1")
        if (fallback.exitCode != 0 || fallback.stdout.isBlank()) return -1
        return fallback.stdout.split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull() }
            .firstOrNull()
            ?: -1
    }

    private fun listDmabufFds(pid: Int): List<Int> {
        val cmd = """
            for f in /proc/$pid/fd/*; do
              t=$(readlink "$f" 2>/dev/null)
              case "$t" in
                /dmabuf:*) basename "$f" ;;
              esac
            done | sort -n
        """.trimIndent()
        val result = RootShell.run(cmd)
        if (result.exitCode != 0 || result.stdout.isBlank()) return emptyList()
        return result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }
            .toList()
    }

    private fun captureFrameByDd(
        pid: Int,
        dmabufFd: Int,
        frameBytes: Int,
        tmpDir: File,
    ): ByteArray? {
        val tmpFile = File(tmpDir, "cap_${pid}_${dmabufFd}_${System.nanoTime()}.bin")
        val tmpPath = tmpFile.absolutePath.replace("'", "'\\''")
        val cmd = "dd if=/proc/$pid/fd/$dmabufFd of='$tmpPath' bs=1 count=$frameBytes 2>/dev/null; chmod 0644 '$tmpPath' 2>/dev/null"
        RootShell.run(cmd, timeoutMs = 30_000)

        if (!tmpFile.exists()) return null
        val bytes = try {
            tmpFile.readBytes()
        } catch (_: Throwable) {
            null
        } finally {
            tmpFile.delete()
        } ?: return null

        if (bytes.size < frameBytes) return null
        return if (bytes.size == frameBytes) bytes else bytes.copyOf(frameBytes)
    }

    private fun nonZeroRatio(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "0.0000"
        val nonZero = bytes.count { it.toInt() != 0 }
        val ratio = nonZero.toDouble() / bytes.size.toDouble()
        return String.format(Locale.US, "%.4f", ratio)
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun log(onLog: (String) -> Unit, message: String) {
        onLog("[${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}] $message")
    }
}
