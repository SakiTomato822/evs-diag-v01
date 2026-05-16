package com.lynk.evsdiag

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object FrameConverter {
    fun uyvyToPng(raw: ByteArray, width: Int, height: Int, outFile: File): Boolean {
        val expected = width * height * 2
        if (raw.size < expected) return false

        val pixels = IntArray(width * height)
        var src = 0
        var dst = 0
        while (src + 3 < expected && dst + 1 < pixels.size) {
            val u = raw[src].toInt() and 0xff
            val y0 = raw[src + 1].toInt() and 0xff
            val v = raw[src + 2].toInt() and 0xff
            val y1 = raw[src + 3].toInt() and 0xff

            pixels[dst] = yuvToArgb(y0, u, v)
            pixels[dst + 1] = yuvToArgb(y1, u, v)

            src += 4
            dst += 2
        }

        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return true
    }

    private fun yuvToArgb(y: Int, u: Int, v: Int): Int {
        val c = y - 16
        val d = u - 128
        val e = v - 128

        val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
        val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
        val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }
}
