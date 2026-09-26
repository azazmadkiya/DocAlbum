package com.example.utils

import android.graphics.Bitmap
import kotlin.math.abs

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

        // Downscale for fast pixel sampling (e.g. 150x100)
        val sampleW = 150
        val sampleH = (sampleW * h.toFloat() / w.toFloat()).toInt().coerceAtLeast(50)
        val small = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)

        val pixels = IntArray(sampleW * sampleH)
        small.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        var rightHalfEdges = 0f
        var leftHalfEdges = 0f
        var qrCandidateScore = 0f

        val midX = sampleW / 2
        for (y in 1 until sampleH - 1) {
            val rowOff = y * sampleW
            for (x in 1 until sampleW - 1) {
                val p = pixels[rowOff + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val gray = (299 * r + 587 * g + 114 * b) / 1000

                val rightP = pixels[rowOff + x + 1]
                val rR = (rightP shr 16) and 0xFF
                val rG = (rightP shr 8) and 0xFF
                val rB = rightP and 0xFF
                val rightGray = (299 * rR + 587 * rG + 114 * rB) / 1000

                val diff = abs(gray - rightGray)
                if (diff > 50) {
                    if (x >= midX) {
                        rightHalfEdges += 1f
                        if (x > sampleW * 0.55f && y > sampleH * 0.1f && y < sampleH * 0.85f) {
                            qrCandidateScore += 1f
                        }
                    } else {
                        leftHalfEdges += 1f
                    }
                }
            }
        }

        val totalPixels = sampleW.toFloat() * sampleH.toFloat()
        val qrDensity = qrCandidateScore / (totalPixels * 0.2f)

        return if (qrDensity > 0.12f || rightHalfEdges > leftHalfEdges * 1.35f) {
            ClassificationResult(
                DocumentSide.BACK,
                (0.65f + (qrDensity * 0.3f)).coerceAtMost(0.95f),
                "Detected high-density QR code / address block typical of Back Side"
            )
        } else {
            ClassificationResult(
                DocumentSide.FRONT,
                0.75f,
                "Detected photo / demographic layout typical of Front Side"
            )
        }
    }
}
