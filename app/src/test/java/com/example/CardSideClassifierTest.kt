package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.utils.CardSideClassifier
import com.example.utils.DocumentSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardSideClassifierTest {

    @Test
    fun testClassifyCardSide_backWithQRBlock() {
        val bitmap = Bitmap.createBitmap(400, 250, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val qrPaint = Paint().apply { color = Color.BLACK }
        canvas.drawRect(210f, 10f, 390f, 240f, qrPaint)

        val result = CardSideClassifier.classifyCardSide(bitmap)
        assertNotNull(result)
        assertEquals(DocumentSide.BACK, result.side)
    }

    @Test
    fun testClassifyCardSide_frontLayout() {
        val bitmap = Bitmap.createBitmap(400, 250, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val whitePaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(210f, 10f, 390f, 240f, whitePaint)

        val result = CardSideClassifier.classifyCardSide(bitmap)
        assertNotNull(result)
        assertEquals(DocumentSide.FRONT, result.side)
    }
}
