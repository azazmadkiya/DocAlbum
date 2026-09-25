package com.example.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs

object DocumentEdgeDetector {

    /**
     * Auto-detects document edges (for front or back side captures) using standard Kotlin and Android Bitmap libraries.
     * Downscales the image, computes grayscale luminance values, and evaluates variance/gradient
     * to find the bounding box of the document card against its background.
     */
    fun detectDocumentEdges(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height

        // Downsample for fast, efficient analysis (max 300px dimension)
        val scale = 300f / maxOf(width, height)
        val sampleW = maxOf(50, (width * scale).toInt())
        val sampleH = maxOf(50, (height * scale).toInt())

        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)

        // Compute grayscale luminance matrix
        val lum = Array(sampleH) { FloatArray(sampleW) }
        var totalLum = 0f
        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Standard Rec. 601 luma formula
                val l = 0.299f * r + 0.587f * g + 0.114f * b
                lum[y][x] = l
                totalLum += l
            }
        }
        val avgLum = totalLum / (sampleW * sampleH)

        var minX = sampleW - 1
        var maxX = 0
        var minY = sampleH - 1
        var maxY = 0

        val threshold = 32f // Lum variance threshold to detect document borders

        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val diff = abs(lum[y][x] - avgLum)
                if (diff > threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // Fallback bounds if detection is ambiguous
        if (minX >= maxX || minY >= maxY || (maxX - minX) < (sampleW / 6) || (maxY - minY) < (sampleH / 6)) {
            minX = (sampleW * 0.04f).toInt()
            maxX = (sampleW * 0.96f).toInt()
            minY = (sampleH * 0.04f).toInt()
            maxY = (sampleH * 0.96f).toInt()
        } else {
            // Add slight safe border padding (2%)
            val padX = ((maxX - minX) * 0.02f).toInt()
            val padY = ((maxY - minY) * 0.02f).toInt()
            minX = (minX - padX).coerceIn(0, sampleW - 1)
            maxX = (maxX + padX).coerceIn(0, sampleW - 1)
            minY = (minY - padY).coerceIn(0, sampleH - 1)
            maxY = (maxY + padY).coerceIn(0, sampleH - 1)
        }

        // Map back to original image dimensions
        val origLeft = (minX / scale).toInt().coerceIn(0, width - 2)
        val origTop = (minY / scale).toInt().coerceIn(0, height - 2)
        val origRight = (maxX / scale).toInt().coerceIn(origLeft + 15, width)
        val origBottom = (maxY / scale).toInt().coerceIn(origTop + 15, height)

        if (scaled != bitmap) {
            scaled.recycle()
        }

        return Rect(origLeft, origTop, origRight, origBottom)
    }

    /**
     * Automatically crops the provided bitmap to the auto-detected document edges.
     */
    fun autoCrop(bitmap: Bitmap): Bitmap {
        return try {
            val rect = detectDocumentEdges(bitmap)
            val w = rect.width().coerceAtMost(bitmap.width - rect.left)
            val h = rect.height().coerceAtMost(bitmap.height - rect.top)
            Bitmap.createBitmap(bitmap, rect.left, rect.top, w, h)
        } catch (e: Exception) {
            bitmap
        }
    }
}
