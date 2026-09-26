package com.example.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Computer vision algorithm using standard image processing techniques
 * (Gaussian smoothing, Sobel edge gradients, projection profile analysis,
 * contour bounds scoring, and projective perspective transformation)
 * to automatically detect the edges of an Aadhaar card (or any standard ID card)
 * and crop/warp it to the document boundaries.
 */
data class CardQuad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    /**
     * Converts quad to normalized [0f..1f] bounding box for interactive crop handles.
     */
    fun toNormalizedRect(imageWidth: Float, imageHeight: Float): RectF {
        val minX = minOf(topLeft.x, bottomLeft.x) / imageWidth
        val maxX = maxOf(topRight.x, bottomRight.x) / imageWidth
        val minY = minOf(topLeft.y, topRight.y) / imageHeight
        val maxY = maxOf(bottomLeft.y, bottomRight.y) / imageHeight
        val safeLeft = minX.coerceIn(0f, 0.90f)
        val safeTop = minY.coerceIn(0f, 0.90f)
        val safeRight = maxX.coerceIn(safeLeft + 0.08f, 1f)
        val safeBottom = maxY.coerceIn(safeTop + 0.08f, 1f)
        return RectF(safeLeft, safeTop, safeRight, safeBottom)
    }
}

object CardEdgeDetector {
    // ISO/IEC 7810 ID-1 standard (CR80) dimensions: 85.60 mm × 53.98 mm
    const val AADHAAR_ASPECT_RATIO = 85.6f / 53.98f // ~1.5858

    data class DetectionResult(
        val quad: CardQuad,
        val normalizedRect: RectF,
        val confidence: Float,
        val isCardDetected: Boolean
    )

    /**
     * Analyzes image using computer vision pipeline:
     * 1. Downscales for fast real-time processing and noise elimination.
     * 2. Luminance conversion and 3x3 Gaussian smoothing.
     * 3. Sobel spatial gradient computation (Gx, Gy and magnitude).
     * 4. Gradient projection profiles and edge transition detection.
     * 5. Aspect-ratio and area validation tailored for Aadhaar cards.
     */
    fun detectCard(bitmap: Bitmap): DetectionResult {
        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()

        if (origW < 20 || origH < 20) {
            val defaultRect = RectF(0.05f, 0.05f, 0.95f, 0.95f)
            val defaultQuad = CardQuad(
                PointF(origW * 0.05f, origH * 0.05f),
                PointF(origW * 0.95f, origH * 0.05f),
                PointF(origW * 0.95f, origH * 0.95f),
                PointF(origW * 0.05f, origH * 0.95f)
            )
            return DetectionResult(defaultQuad, defaultRect, 0.5f, false)
        }

        // 1. Downscale to working size (max dimension 640px)
        val maxDim = 640
        val scale = min(1f, maxDim.toFloat() / max(origW, origH))
        val w = (origW * scale).toInt().coerceAtLeast(80)
        val h = (origH * scale).toInt().coerceAtLeast(80)
        val smallBmp = Bitmap.createScaledBitmap(bitmap, w, h, true)

        // 2. Extract Grayscale Luminance
        val pixels = IntArray(w * h)
        smallBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (299 * r + 587 * g + 114 * b) / 1000
        }

        // 3. 3x3 Gaussian Blur (noise suppression)
        val blurred = IntArray(w * h)
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val sum = (
                    gray[(y - 1) * w + (x - 1)] + 2 * gray[(y - 1) * w + x] + gray[(y - 1) * w + (x + 1)] +
                    2 * gray[yOffset + (x - 1)] + 4 * gray[yOffset + x] + 2 * gray[yOffset + (x + 1)] +
                    gray[(y + 1) * w + (x - 1)] + 2 * gray[(y + 1) * w + x] + gray[(y + 1) * w + (x + 1)]
                ) shr 4
                blurred[yOffset + x] = sum
            }
        }

        // 4. Sobel Gradients
        val gradMag = FloatArray(w * h)
        val gradX = FloatArray(w * h)
        val gradY = FloatArray(w * h)
        var sumMag = 0f
        var countMag = 0

        for (y in 1 until h - 1) {
            val yOffset = y * w
            val yPrev = (y - 1) * w
            val yNext = (y + 1) * w
            for (x in 1 until w - 1) {
                val gx = -blurred[yPrev + (x - 1)] + blurred[yPrev + (x + 1)] -
                         2 * blurred[yOffset + (x - 1)] + 2 * blurred[yOffset + (x + 1)] -
                         blurred[yNext + (x - 1)] + blurred[yNext + (x + 1)]

                val gy = -blurred[yPrev + (x - 1)] - 2 * blurred[yPrev + x] - blurred[yPrev + (x + 1)] +
                         blurred[yNext + (x - 1)] + 2 * blurred[yNext + x] + blurred[yNext + (x + 1)]

                val m = hypot(gx.toFloat(), gy.toFloat())
                gradMag[yOffset + x] = m
                gradX[yOffset + x] = gx.toFloat()
                gradY[yOffset + x] = gy.toFloat()
                sumMag += m
                countMag++
            }
        }

        val avgMag = if (countMag > 0) sumMag / countMag else 20f
        val edgeThreshold = max(28f, avgMag * 1.35f)

        // 5. Projection Profile Analysis along X and Y axes
        val colGradSum = FloatArray(w)
        val rowGradSum = FloatArray(h)

        val centralYStart = (h * 0.12f).toInt()
        val centralYEnd = (h * 0.88f).toInt()
        val centralXStart = (w * 0.12f).toInt()
        val centralXEnd = (w * 0.88f).toInt()

        for (x in 1 until w - 1) {
            var sum = 0f
            for (y in centralYStart until centralYEnd) {
                val m = gradMag[y * w + x]
                if (m > edgeThreshold) {
                    sum += abs(gradX[y * w + x])
                }
            }
            colGradSum[x] = sum
        }

        for (y in 1 until h - 1) {
            var sum = 0f
            val yOffset = y * w
            for (x in centralXStart until centralXEnd) {
                val m = gradMag[yOffset + x]
                if (m > edgeThreshold) {
                    sum += abs(gradY[yOffset + x])
                }
            }
            rowGradSum[y] = sum
        }

        // 6. Find dominant edge peaks from margins towards center
        val maxColVal = colGradSum.maxOrNull() ?: 1f
        val maxRowVal = rowGradSum.maxOrNull() ?: 1f
        val colPeakThreshold = maxColVal * 0.25f
        val rowPeakThreshold = maxRowVal * 0.25f

        // Search Left edge: [w * 0.02 .. w * 0.40]
        var bestLeft = (w * 0.06f).toInt()
        var maxLeftPeak = 0f
        val leftSearchEnd = (w * 0.40f).toInt()
        for (x in (w * 0.03f).toInt() until leftSearchEnd) {
            if (colGradSum[x] > maxLeftPeak && colGradSum[x] > colPeakThreshold) {
                maxLeftPeak = colGradSum[x]
                bestLeft = x
            }
        }

        // Search Right edge: [w * 0.60 .. w * 0.98]
        var bestRight = (w * 0.94f).toInt()
        var maxRightPeak = 0f
        val rightSearchStart = (w * 0.60f).toInt()
        for (x in (w * 0.97f).toInt() downTo rightSearchStart) {
            if (colGradSum[x] > maxRightPeak && colGradSum[x] > colPeakThreshold) {
                maxRightPeak = colGradSum[x]
                bestRight = x
            }
        }

        // Search Top edge: [h * 0.02 .. h * 0.40]
        var bestTop = (h * 0.06f).toInt()
        var maxTopPeak = 0f
        val topSearchEnd = (h * 0.40f).toInt()
        for (y in (h * 0.03f).toInt() until topSearchEnd) {
            if (rowGradSum[y] > maxTopPeak && rowGradSum[y] > rowPeakThreshold) {
                maxTopPeak = rowGradSum[y]
                bestTop = y
            }
        }

        // Search Bottom edge: [h * 0.60 .. h * 0.98]
        var bestBottom = (h * 0.94f).toInt()
        var maxBottomPeak = 0f
        val bottomSearchStart = (h * 0.60f).toInt()
        for (y in (h * 0.97f).toInt() downTo bottomSearchStart) {
            if (rowGradSum[y] > maxBottomPeak && rowGradSum[y] > rowPeakThreshold) {
                maxBottomPeak = rowGradSum[y]
                bestBottom = y
            }
        }

        // 7. Refine bounds with aspect ratio awareness (Aadhaar ~ 1.586)
        var detectedW = (bestRight - bestLeft).toFloat()
        var detectedH = (bestBottom - bestTop).toFloat()

        if (detectedW < w * 0.3f || detectedH < h * 0.25f) {
            // Fallback to centered default proportional box
            val fallbackW = w * 0.86f
            val fallbackH = (fallbackW / AADHAAR_ASPECT_RATIO).coerceAtMost(h * 0.86f)
            bestLeft = ((w - fallbackW) / 2f).toInt()
            bestRight = (bestLeft + fallbackW).toInt()
            bestTop = ((h - fallbackH) / 2f).toInt()
            bestBottom = (bestTop + fallbackH).toInt()
        }

        // Normalized result [0f..1f]
        val normLeft = (bestLeft.toFloat() / w).coerceIn(0f, 0.90f)
        val normTop = (bestTop.toFloat() / h).coerceIn(0f, 0.90f)
        val normRight = (bestRight.toFloat() / w).coerceIn(normLeft + 0.10f, 1f)
        val normBottom = (bestBottom.toFloat() / h).coerceIn(normTop + 0.10f, 1f)
        val normalizedRect = RectF(normLeft, normTop, normRight, normBottom)

        // 8. Corner Points in Full Image Resolution
        val origLeft = normLeft * origW
        val origTop = normTop * origH
        val origRight = normRight * origW
        val origBottom = normBottom * origH

        val quad = CardQuad(
            topLeft = PointF(origLeft, origTop),
            topRight = PointF(origRight, origTop),
            bottomRight = PointF(origRight, origBottom),
            bottomLeft = PointF(origLeft, origBottom)
        )

        val aspect = (normRight - normLeft) * origW / max(1f, (normBottom - normTop) * origH)
        val aspectScore = 1f - (abs(aspect - AADHAAR_ASPECT_RATIO) / AADHAAR_ASPECT_RATIO).coerceIn(0f, 1f)
        val isDetected = (maxLeftPeak > 0 || maxRightPeak > 0 || maxTopPeak > 0 || maxBottomPeak > 0)

        return DetectionResult(
            quad = quad,
            normalizedRect = normalizedRect,
            confidence = (0.5f + 0.5f * aspectScore).coerceIn(0.5f, 0.98f),
            isCardDetected = isDetected
        )
    }

    /**
     * Crops image using an axis-aligned normalized rectangle.
     */
    fun cropRect(bitmap: Bitmap, rect: RectF): Bitmap {
        val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val width = ((rect.right - rect.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - left)
        val height = ((rect.bottom - rect.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * Performs a 4-point projective homography perspective warp.
     * Takes any quadrilateral (even tilted or photographed at an angle)
     * and maps it to a perfectly flat, rectified rectangle of standard ID card proportions!
     */
    fun cropAndWarpCard(
        bitmap: Bitmap,
        quad: CardQuad,
        targetWidth: Int = 1000
    ): Bitmap {
        val targetHeight = (targetWidth / AADHAAR_ASPECT_RATIO).roundToInt().coerceAtLeast(100)

        val srcPoints = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return outputBitmap
    }

    /**
     * Full one-step computer vision auto-detection and crop.
     */
    fun autoCropCard(bitmap: Bitmap): Bitmap {
        val result = detectCard(bitmap)
        return cropAndWarpCard(bitmap, result.quad)
    }
}
