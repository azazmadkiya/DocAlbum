package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.utils.CardEdgeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class CardEdgeDetectorTest {

    @Test
    fun testDetectCard_onContrastedBackground() {
        // Create an image with a dark background and a centered white card (Aadhaar aspect ratio)
        val imageW = 800
        val imageH = 600
        val bitmap = Bitmap.createBitmap(imageW, imageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background: dark wood/slate
        canvas.drawColor(Color.rgb(40, 45, 55))

        // Draw a simulated Aadhaar card: 85.6mm x 53.98mm ratio ~ 1.586
        // Size: 500 x 315 px placed at (150, 142)
        val cardLeft = 150f
        val cardTop = 142f
        val cardRight = 650f
        val cardBottom = 457f
        
        val cardPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(cardLeft, cardTop, cardRight, cardBottom, cardPaint)

        // Run Computer Vision Edge Detector
        val result = CardEdgeDetector.detectCard(bitmap)

        assertNotNull(result)
        assertTrue("Card detection confidence should be high", result.confidence >= 0.5f)

        // Verify detected normalized boundaries correspond approximately to the card
        val norm = result.normalizedRect
        val detectedLeftPx = norm.left * imageW
        val detectedRightPx = norm.right * imageW
        val detectedTopPx = norm.top * imageH
        val detectedBottomPx = norm.bottom * imageH

        // Allow tolerance for downscaled edge gradient localization
        val tolerance = 80f
        assertTrue("Detected left edge is close to cardLeft", abs(detectedLeftPx - cardLeft) < tolerance)
        assertTrue("Detected right edge is close to cardRight", abs(detectedRightPx - cardRight) < tolerance)
        assertTrue("Detected top edge is close to cardTop", abs(detectedTopPx - cardTop) < tolerance)
        assertTrue("Detected bottom edge is close to cardBottom", abs(detectedBottomPx - cardBottom) < tolerance)
    }

    @Test
    fun testCropAndWarpCard_aspectRatio() {
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val result = CardEdgeDetector.detectCard(bitmap)

        val cropped = CardEdgeDetector.cropAndWarpCard(bitmap, result.quad, targetWidth = 1000)
        assertNotNull(cropped)
        assertEquals(1000, cropped.width)
        
        val expectedHeight = kotlin.math.round(1000f / CardEdgeDetector.AADHAAR_ASPECT_RATIO).toInt()
        assertEquals(expectedHeight, cropped.height)
    }

    @Test
    fun testCropRect_boundaries() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val rect = RectF(0.1f, 0.2f, 0.8f, 0.7f)
        val cropped = CardEdgeDetector.cropRect(bitmap, rect)

        assertNotNull(cropped)
        assertTrue(cropped.width > 0)
        assertTrue(cropped.height > 0)
        assertEquals(280, cropped.width) // (0.8 - 0.1) * 400
        assertEquals(150, cropped.height) // (0.7 - 0.2) * 300
    }
}
