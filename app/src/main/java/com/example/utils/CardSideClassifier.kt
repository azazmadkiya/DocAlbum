package com.example.utils

import android.graphics.Bitmap

enum class DocumentSide {
    FRONT,
    BACK,
    UNKNOWN
}

object CardSideClassifier {

    data class ClassificationResult(
        val side: DocumentSide,
        val confidence: Float,
        val reason: String
    )

    /**
     * Lightweight heuristic classifier to determine whether an ID card image
     * is the Front side or Back side based on visual feature distribution
     * (such as QR code block density on right/center vs photo/demographics on left).
     */
    fun classifyCardSide(bitmap: Bitmap): ClassificationResult {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 50 || h < 50) {
            return ClassificationResult(DocumentSide.UNKNOWN, 0.4f, "Image too small")
        }

        val sampleW = 100
        val sampleH = (sampleW * h.toFloat() / w.toFloat()).toInt().coerceAtLeast(30)
        val small = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)

        val pixels = IntArray(sampleW * sampleH)
        small.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        var rightDark = 0
        var leftDark = 0

        val midX = sampleW / 2
        for (y in 0 until sampleH) {
            val rowOff = y * sampleW
            for (x in 0 until sampleW) {
                val p = pixels[rowOff + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val gray = (299 * r + 587 * g + 114 * b) / 1000

                if (gray < 180) {
                    if (x >= midX) rightDark++
                    else leftDark++
                }
            }
        }

        val isBack = rightDark >= leftDark
        return if (isBack) {
            ClassificationResult(DocumentSide.BACK, 0.85f, "Back side layout detected (QR/Address density on right)")
        } else {
            ClassificationResult(DocumentSide.FRONT, 0.85f, "Front side layout detected (Photo/Demographics on left)")
        }
    }
}
