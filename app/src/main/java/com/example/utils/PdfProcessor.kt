package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfProcessor {
    data class ExtractedDoc(
        val frontUri: Uri?,
        val backUri: Uri?,
        val title: String
    )

    fun processPdf(context: Context, pdfUri: Uri): ExtractedDoc? {
        try {
            val parcelFileDescriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return null
            val renderer = PdfRenderer(parcelFileDescriptor)
            val pageCount = renderer.pageCount
            if (pageCount <= 0) {
                renderer.close()
                parcelFileDescriptor.close()
                return null
            }

            // Render Page 1 at high resolution (300 DPI print quality scale ~ 3x)
            val page1 = renderer.openPage(0)
            val width = page1.width * 3
            val height = page1.height * 3
            val bitmap1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas1 = Canvas(bitmap1)
            canvas1.drawColor(Color.WHITE)
            page1.render(bitmap1, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page1.close()

            var frontBitmap: Bitmap
            var backBitmap: Bitmap? = null

            if (pageCount >= 2) {
                // Page 1 is front, Page 2 is back
                frontBitmap = bitmap1
                val page2 = renderer.openPage(1)
                val bitmap2 = Bitmap.createBitmap(page2.width * 3, page2.height * 3, Bitmap.Config.ARGB_8888)
                val canvas2 = Canvas(bitmap2)
                canvas2.drawColor(Color.WHITE)
                page2.render(bitmap2, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page2.close()
                backBitmap = bitmap2
            } else {
                // Single page containing both front and back (e.g. Aadhaar Card PDF)
                // Automatically split vertically: top half = front, bottom half = back
                val halfH = height / 2
                frontBitmap = Bitmap.createBitmap(bitmap1, 0, 0, width, halfH)
                backBitmap = Bitmap.createBitmap(bitmap1, 0, halfH, width, height - halfH)
            }

            renderer.close()
            parcelFileDescriptor.close()

            // Save extracted bitmaps to cache files
            val frontFile = File(context.cacheDir, "pdf_front_${System.currentTimeMillis()}.png")
            FileOutputStream(frontFile).use { out ->
                frontBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val frontUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", frontFile)

            var backUri: Uri? = null
            if (backBitmap != null) {
                val backFile = File(context.cacheDir, "pdf_back_${System.currentTimeMillis()}.png")
                FileOutputStream(backFile).use { out ->
                    backBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                backUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backFile)
            }

            return ExtractedDoc(frontUri, backUri, "Official ID Card (PDF)")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
