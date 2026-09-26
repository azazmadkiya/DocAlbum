package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PdfProcessor {

    class PasswordRequiredException : Exception("Password required to decrypt PDF")

    data class ExtractedDoc(
        val frontUri: Uri?,
        val backUri: Uri?,
        val title: String
    )

    fun processPdf(context: Context, pdfUri: Uri, password: String? = null): ExtractedDoc? {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(pdfUri) ?: return null
            val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.pdf")
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()

            val document: PDDocument = try {
                if (!password.isNullOrBlank()) {
                    PDDocument.load(tempFile, password)
                } else {
                    PDDocument.load(tempFile)
                }
            } catch (e: Exception) {
                tempFile.delete()
                val msg = e.message?.lowercase() ?: ""
                val cls = e.javaClass.simpleName.lowercase()
                if (msg.contains("password") || msg.contains("encrypt") || cls.contains("password") || cls.contains("crypt")) {
                    throw PasswordRequiredException()
                }
                throw e
            }

            val renderer = PDFRenderer(document)
            val pageCount = document.numberOfPages
            if (pageCount <= 0) {
                document.close()
                tempFile.delete()
                return null
            }

            val page1Image = renderer.renderImageWithDPI(0, 300f)
            var frontBitmap: Bitmap
            var backBitmap: Bitmap? = null

            if (pageCount >= 2) {
                frontBitmap = page1Image
                backBitmap = renderer.renderImageWithDPI(1, 300f)
            } else {
                // Single page e-Aadhaar PDF:
                // Top ~40% is instruction text. Bottom ~55% is the actual Aadhaar card.
                val width = page1Image.width
                val height = page1Image.height
                val cardTop = (height * 0.40f).toInt().coerceIn(0, height - 100)
                val cardBottom = (height * 0.98f).toInt().coerceIn(cardTop + 100, height)
                val cardHeight = cardBottom - cardTop
                val halfCardH = cardHeight / 2

                // Front Side = Top half of the Aadhaar card (Photo, Name, DOB, Aadhaar Number)
                frontBitmap = Bitmap.createBitmap(page1Image, 0, cardTop, width, halfCardH)
                // Back Side = Bottom half of the Aadhaar card (Address, QR Code)
                backBitmap = Bitmap.createBitmap(page1Image, 0, cardTop + halfCardH, width, cardBottom - (cardTop + halfCardH))
            }

            document.close()
            tempFile.delete()

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

            return ExtractedDoc(frontUri, backUri, "Aadhaar / Official ID Card (PDF)")
        } catch (e: PasswordRequiredException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
