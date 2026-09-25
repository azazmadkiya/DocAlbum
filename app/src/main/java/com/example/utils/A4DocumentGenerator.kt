package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class CardPrintSize(
    val title: String,
    val description: String,
    val widthMm: Float
) {
    AUTO_BEST("Auto Best (Recommended)", "Clear, balanced & sharp (~130 mm)", 130f),
    REAL_CARD("Real Card (1:1)", "Exact physical wallet size (85.6 mm)", 85.6f),
    COMPACT("Compact", "Slim fit for notes/signature (~105 mm)", 105f),
    LARGE("Large Doc", "Full-width certificate style (~160 mm)", 160f)
}

object A4DocumentGenerator {

    // Standard A4 at 300 DPI (High-definition print standard)
    const val A4_WIDTH_PX = 2480
    const val A4_HEIGHT_PX = 3508
    private const val MM_TO_PX = A4_WIDTH_PX.toFloat() / 210f // ~11.8095 px per mm

    fun generateA4Bitmap(
        context: Context,
        title: String,
        frontUri: Uri?,
        backUri: Uri?,
        layoutStyle: String = "STACKED", // "STACKED" or "SIDE_BY_SIDE"
        cardPrintSize: CardPrintSize = CardPrintSize.AUTO_BEST,
        filterType: String = "COLOR", // "COLOR" or "BW"
        showCutGuides: Boolean = true,
        showLabels: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(A4_WIDTH_PX, A4_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean white background
        canvas.drawColor(Color.WHITE)

        // Load images
        var frontBmp = loadBitmap(context, frontUri)
        var backBmp = loadBitmap(context, backUri)

        // Apply black & white filter if selected
        if (filterType == "BW") {
            frontBmp = frontBmp?.let { applyBwFilter(it) }
            backBmp = backBmp?.let { applyBwFilter(it) }
        }

        // Target card width in pixels derived from physical mm standard
        val baseTargetW = (cardPrintSize.widthMm * MM_TO_PX).toInt()

        // Content Area boundaries - full clean A4 sheet without artificial text
        val contentTop = 160f
        val contentBottom = A4_HEIGHT_PX - 160f
        val availableH = contentBottom - contentTop

        if (layoutStyle == "STACKED") {
            drawStackedCards(
                canvas = canvas,
                frontBmp = frontBmp,
                backBmp = backBmp,
                targetW = baseTargetW,
                contentTop = contentTop,
                availableH = availableH,
                showCutGuides = showCutGuides,
                showLabels = showLabels
            )
        } else {
            drawSideBySideCards(
                canvas = canvas,
                frontBmp = frontBmp,
                backBmp = backBmp,
                targetW = baseTargetW,
                contentTop = contentTop,
                availableH = availableH,
                showCutGuides = showCutGuides,
                showLabels = showLabels
            )
        }

        return bitmap
    }

    private fun drawStackedCards(
        canvas: Canvas,
        frontBmp: Bitmap?,
        backBmp: Bitmap?,
        targetW: Int,
        contentTop: Float,
        availableH: Float,
        showCutGuides: Boolean,
        showLabels: Boolean
    ) {
        val labelHeight = if (showLabels) 60f else 0f
        val gapBetween = 140f // Comfortable vertical gap between cards

        // Calculate natural heights preserving aspect ratios
        val defaultAspect = 85.6f / 53.98f // Standard ID-1
        val frontAspect = frontBmp?.let { it.width.toFloat() / it.height.toFloat() } ?: defaultAspect
        val backAspect = backBmp?.let { it.width.toFloat() / it.height.toFloat() } ?: defaultAspect

        var frontW = targetW.toFloat().coerceAtMost(A4_WIDTH_PX - 320f)
        var frontH = frontW / frontAspect

        var backW = targetW.toFloat().coerceAtMost(A4_WIDTH_PX - 320f)
        var backH = backW / backAspect

        // Ensure both fit comfortably within available height
        val totalNeededH = frontH + backH + (labelHeight * 2) + gapBetween
        if (totalNeededH > availableH) {
            val scale = (availableH - (labelHeight * 2) - gapBetween) / (frontH + backH)
            frontW *= scale
            frontH *= scale
            backW *= scale
            backH *= scale
        }

        // Center vertically on the sheet
        val actualTotalH = frontH + backH + (labelHeight * 2) + gapBetween
        val startY = contentTop + max(0f, (availableH - actualTotalH) / 2f)

        // 1. Draw Front Card
        val frontLeft = (A4_WIDTH_PX - frontW) / 2f
        val frontCardTop = startY + labelHeight
        if (showLabels) {
            drawLabelBadge(canvas, "FRONT SIDE", frontLeft, startY + 28f)
        }
        drawCardItem(
            canvas = canvas,
            bmp = frontBmp,
            left = frontLeft,
            top = frontCardTop,
            width = frontW,
            height = frontH,
            showCutGuides = showCutGuides
        )

        // 2. Draw Back Card
        val backStartY = frontCardTop + frontH + gapBetween
        val backLeft = (A4_WIDTH_PX - backW) / 2f
        val backCardTop = backStartY + labelHeight
        if (showLabels) {
            drawLabelBadge(canvas, "BACK SIDE", backLeft, backStartY + 28f)
        }
        drawCardItem(
            canvas = canvas,
            bmp = backBmp,
            left = backLeft,
            top = backCardTop,
            width = backW,
            height = backH,
            showCutGuides = showCutGuides
        )
    }

    private fun drawSideBySideCards(
        canvas: Canvas,
        frontBmp: Bitmap?,
        backBmp: Bitmap?,
        targetW: Int,
        contentTop: Float,
        availableH: Float,
        showCutGuides: Boolean,
        showLabels: Boolean
    ) {
        val labelHeight = if (showLabels) 60f else 0f
        val gap = 100f

        val defaultAspect = 85.6f / 53.98f
        val frontAspect = frontBmp?.let { it.width.toFloat() / it.height.toFloat() } ?: defaultAspect
        val backAspect = backBmp?.let { it.width.toFloat() / it.height.toFloat() } ?: defaultAspect

        val maxAllowedW = (A4_WIDTH_PX - 320f - gap) / 2f
        var cardW = min(targetW.toFloat(), maxAllowedW)

        var frontH = cardW / frontAspect
        var backH = cardW / backAspect

        val maxCardH = availableH - labelHeight - 80f
        if (max(frontH, backH) > maxCardH) {
            val scale = maxCardH / max(frontH, backH)
            cardW *= scale
            frontH *= scale
            backH *= scale
        }

        val totalRowW = (cardW * 2) + gap
        val startX = (A4_WIDTH_PX - totalRowW) / 2f
        val startY = contentTop + (availableH - max(frontH, backH) - labelHeight) / 2f

        // Front Card
        if (showLabels) {
            drawLabelBadge(canvas, "FRONT SIDE", startX, startY + 28f)
        }
        drawCardItem(
            canvas = canvas,
            bmp = frontBmp,
            left = startX,
            top = startY + labelHeight,
            width = cardW,
            height = frontH,
            showCutGuides = showCutGuides
        )

        // Back Card
        val backX = startX + cardW + gap
        if (showLabels) {
            drawLabelBadge(canvas, "BACK SIDE", backX, startY + 28f)
        }
        drawCardItem(
            canvas = canvas,
            bmp = backBmp,
            left = backX,
            top = startY + labelHeight,
            width = cardW,
            height = backH,
            showCutGuides = showCutGuides
        )
    }

    private fun drawLabelBadge(canvas: Canvas, label: String, x: Float, y: Float) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 26f
            isFakeBoldText = true
            letterSpacing = 0.08f
        }
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        val textW = textPaint.measureText(label)
        val padX = 20f
        val padY = 12f
        val rect = RectF(x, y - 26f - padY, x + textW + (padX * 2), y + padY)
        canvas.drawRoundRect(rect, 8f, 8f, tagBgPaint)
        canvas.drawText(label, x + padX, y, textPaint)
    }

    private fun drawCardItem(
        canvas: Canvas,
        bmp: Bitmap?,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        showCutGuides: Boolean
    ) {
        val destRect = RectF(left, top, left + width, top + height)

        if (bmp != null) {
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            // Clean subtle empty placeholder frame with NO text watermark
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(destRect, 14f, 14f, bgPaint)
        }

        // Draw crisp border and cut guides if enabled
        if (showCutGuides) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(destRect, 12f, 12f, borderPaint)

            // Corner cut marks (subtle L-markers 20px off corners)
            val cutMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            val markLen = 28f
            val offset = 14f

            // Top-Left corner
            canvas.drawLine(left - offset - markLen, top - offset, left - offset, top - offset, cutMarkPaint)
            canvas.drawLine(left - offset, top - offset - markLen, left - offset, top - offset, cutMarkPaint)

            // Top-Right corner
            canvas.drawLine(left + width + offset, top - offset, left + width + offset + markLen, top - offset, cutMarkPaint)
            canvas.drawLine(left + width + offset, top - offset - markLen, left + width + offset, top - offset, cutMarkPaint)

            // Bottom-Left corner
            canvas.drawLine(left - offset - markLen, top + height + offset, left - offset, top + height + offset, cutMarkPaint)
            canvas.drawLine(left - offset, top + height + offset, left - offset, top + height + offset + markLen, cutMarkPaint)

            // Bottom-Right corner
            canvas.drawLine(left + width + offset, top + height + offset, left + width + offset + markLen, top + height + offset, cutMarkPaint)
            canvas.drawLine(left + width + offset, top + height + offset, left + width + offset, top + height + offset + markLen, cutMarkPaint)
        }
    }

    private fun loadBitmap(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        return try {
            val stream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            null
        }
    }

    private fun applyBwFilter(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val matrix = ColorMatrix()
        matrix.setSaturation(0f)

        // Subtle contrast enhancement for crisp document scanning
        val contrastMatrix = ColorMatrix(floatArrayOf(
            1.25f, 0f, 0f, 0f, -25f,
            0f, 1.25f, 0f, 0f, -25f,
            0f, 0f, 1.25f, 0f, -25f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    // Save generated A4 sheet into Pictures/Doc A4 Print/
    fun saveA4BitmapToStorage(
        context: Context,
        bitmap: Bitmap,
        title: String
    ): Uri? {
        val sanitizedTitle = title.ifBlank { "Document" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val filename = "DocA4_${sanitizedTitle}_${System.currentTimeMillis()}.jpg"

        return try {
            val resolver = context.contentResolver
            val uri: Uri?
            val fos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "Doc A4 Print")
                }
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { resolver.openOutputStream(it) }
            } else {
                val imagesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Doc A4 Print")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val imageFile = File(imagesDir, filename)
                uri = Uri.fromFile(imageFile)
                FileOutputStream(imageFile)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                Toast.makeText(context, "Saved to Pictures/Doc A4 Print!", Toast.LENGTH_LONG).show()
            }
            uri
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // Native Android Printing via PrintManager
    fun printA4Bitmap(
        context: Context,
        bitmap: Bitmap,
        docTitle: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service unavailable on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "DocA4_${docTitle.ifBlank { "Document" }}"

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Missing destination file")
                    return
                }

                val pdfDocument = PrintedPdfDocument(
                    context,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                )

                try {
                    val page = pdfDocument.startPage(1)
                    val canvas = page.canvas
                    val pageW = page.info.pageWidth
                    val pageH = page.info.pageHeight

                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    val destRect = Rect(0, 0, pageW, pageH)
                    canvas.drawBitmap(bitmap, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

                    pdfDocument.finishPage(page)

                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDocument.writeTo(out)
                    }

                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                } finally {
                    pdfDocument.close()
                }
            }
        }

        printManager.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .build()
        )
    }

    // Share A4 image via Android Intent
    fun shareA4Image(context: Context, imageUri: Uri, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "A4 Document: $title (Ready for Print)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share A4 Document"))
    }
}
