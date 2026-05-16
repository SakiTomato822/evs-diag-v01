package com.lynk.evsdiag

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private var pngFiles: List<File> = emptyList()
    private var currentPngIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val processInput = findViewById<EditText>(R.id.processNameInput)
        val widthInput = findViewById<EditText>(R.id.widthInput)
        val heightInput = findViewById<EditText>(R.id.heightInput)
        val frameInput = findViewById<EditText>(R.id.frameCountInput)
        val intervalInput = findViewById<EditText>(R.id.intervalInput)
        val runButton = findViewById<Button>(R.id.runButton)
        val prevButton = findViewById<Button>(R.id.prevButton)
        val nextButton = findViewById<Button>(R.id.nextButton)
        val previewImage = findViewById<ImageView>(R.id.previewImage)
        val previewInfoText = findViewById<TextView>(R.id.previewInfoText)
        val logText = findViewById<TextView>(R.id.logText)

        prevButton.isEnabled = false
        nextButton.isEnabled = false

        prevButton.setOnClickListener {
            showPngAt(
                index = currentPngIndex - 1,
                previewImage = previewImage,
                previewInfoText = previewInfoText,
                prevButton = prevButton,
                nextButton = nextButton,
            )
        }
        nextButton.setOnClickListener {
            showPngAt(
                index = currentPngIndex + 1,
                previewImage = previewImage,
                previewInfoText = previewInfoText,
                prevButton = prevButton,
                nextButton = nextButton,
            )
        }

        runButton.setOnClickListener {
            runButton.isEnabled = false
            logText.text = ""
            lifecycleScope.launch {
                try {
                    val config = DiagConfig(
                        processName = processInput.text.toString().trim(),
                        width = widthInput.text.toString().trim().toIntOrNull() ?: 1920,
                        height = heightInput.text.toString().trim().toIntOrNull() ?: 896,
                        frameCount = frameInput.text.toString().trim().toIntOrNull() ?: 10,
                        intervalMs = intervalInput.text.toString().trim().toLongOrNull() ?: 500L,
                    )

                    appendLog(logText, "start: ${config.processName}")
                    val runner = CaptureRunner(this@MainActivity)
                    val result = runner.run(config) { line ->
                        lifecycleScope.launch(Dispatchers.Main) { appendLog(logText, line) }
                    }
                    appendLog(logText, "session: ${result.sessionDir.absolutePath}")
                    appendLog(logText, "raw: ${result.rawDir.absolutePath}")
                    appendLog(logText, "png: ${result.pngDir.absolutePath}")
                    appendLog(logText, "unique hash: ${result.hashes.toSet().size}/${result.hashes.size}")

                    loadSessionPngs(
                        pngDir = result.pngDir,
                        previewImage = previewImage,
                        previewInfoText = previewInfoText,
                        prevButton = prevButton,
                        nextButton = nextButton,
                    )
                } catch (t: Throwable) {
                    appendLog(logText, "error: ${t.message}")
                } finally {
                    withContext(Dispatchers.Main) {
                        runButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun appendLog(view: TextView, line: String) {
        val prev = view.text?.toString().orEmpty()
        view.text = if (prev.isBlank()) line else "$prev\n$line"
    }

    private fun loadSessionPngs(
        pngDir: File,
        previewImage: ImageView,
        previewInfoText: TextView,
        prevButton: Button,
        nextButton: Button,
    ) {
        pngFiles = pngDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (pngFiles.isEmpty()) {
            currentPngIndex = -1
            previewImage.setImageDrawable(null)
            previewInfoText.setText(R.string.preview_empty)
            prevButton.isEnabled = false
            nextButton.isEnabled = false
            return
        }

        showPngAt(
            index = 0,
            previewImage = previewImage,
            previewInfoText = previewInfoText,
            prevButton = prevButton,
            nextButton = nextButton,
        )
    }

    private fun showPngAt(
        index: Int,
        previewImage: ImageView,
        previewInfoText: TextView,
        prevButton: Button,
        nextButton: Button,
    ) {
        if (pngFiles.isEmpty()) {
            currentPngIndex = -1
            previewImage.setImageDrawable(null)
            previewInfoText.setText(R.string.preview_empty)
            prevButton.isEnabled = false
            nextButton.isEnabled = false
            return
        }

        val clampedIndex = index.coerceIn(0, pngFiles.lastIndex)
        currentPngIndex = clampedIndex
        val file = pngFiles[clampedIndex]
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        if (bitmap != null) {
            previewImage.setImageBitmap(bitmap)
            previewInfoText.text = "${clampedIndex + 1}/${pngFiles.size}  ${file.name}  (${file.length()} bytes)"
        } else {
            previewImage.setImageDrawable(null)
            previewInfoText.text = "${clampedIndex + 1}/${pngFiles.size}  decode failed: ${file.name}"
        }

        prevButton.isEnabled = clampedIndex > 0
        nextButton.isEnabled = clampedIndex < pngFiles.lastIndex
    }
}
