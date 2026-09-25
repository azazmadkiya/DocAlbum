package com.example.utils

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs

class AutoCaptureManager(
    private val stabilityThresholdFrames: Int = 3,
    private val maxAllowedDriftPixels: Int = 15,
    private val onCaptureTriggered: () -> Unit
) {
    private var lastRect: Rect? = null
    private var consecutiveStableCount = 0
    private var isTriggered = false

    /**
     * Evaluates a frame's detected document rect. If the document is stable and aligned
     * across [stabilityThresholdFrames] consecutive analyses, triggers auto-capture.
     */
    fun evaluateFrame(bitmap: Bitmap): Boolean {
        if (isTriggered) return false

        val rect = DocumentEdgeDetector.detectDocumentEdges(bitmap)
        val area = rect.width() * rect.height().toFloat()
        val totalArea = bitmap.width * bitmap.height.toFloat()
        val coverageRatio = area / totalArea

        // Check alignment: document should occupy between 15% and 92% of the frame
        val isAligned = coverageRatio in 0.15f..0.92f &&
                rect.left >= 0 && rect.top >= 0 &&
                rect.right <= bitmap.width && rect.bottom <= bitmap.height

        if (!isAligned) {
            reset()
            return false
        }

        val previous = lastRect
        if (previous != null) {
            val drift = abs(rect.left - previous.left) +
                    abs(rect.top - previous.top) +
                    abs(rect.right - previous.right) +
                    abs(rect.bottom - previous.bottom)

            if (drift <= maxAllowedDriftPixels) {
                consecutiveStableCount++
            } else {
                consecutiveStableCount = 1
            }
        } else {
            consecutiveStableCount = 1
        }

        lastRect = rect

        if (consecutiveStableCount >= stabilityThresholdFrames && !isTriggered) {
            isTriggered = true
            onCaptureTriggered()
            return true
        }

        return false
    }

    fun reset() {
        lastRect = null
        consecutiveStableCount = 0
        isTriggered = false
    }

    fun setTriggered(triggered: Boolean) {
        isTriggered = triggered
    }
}
