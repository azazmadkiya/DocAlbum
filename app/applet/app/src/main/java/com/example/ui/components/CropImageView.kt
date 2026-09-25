package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.DocumentEdgeDetector
import kotlin.math.abs
import kotlin.math.hypot

enum class CropHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT,
    BODY
}

data class AspectRatioPreset(
    val label: String,
    val iconDescription: String,
    val ratio: Float? // width / height, null for freeform
)

@Composable
fun CropImageView(
    bitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var normLeft by remember { mutableStateOf(0.05f) }
    var normTop by remember { mutableStateOf(0.05f) }
    var normRight by remember { mutableStateOf(0.95f) }
    var normBottom by remember { mutableStateOf(0.95f) }

    val presets = listOf(
        AspectRatioPreset("Freeform", "Freeform crop", null),
        AspectRatioPreset("ID Card (85:54)", "Standard ID Card ratio", 85.6f / 53.98f),
        AspectRatioPreset("A4 Ratio (1:1.41)", "Standard A4 ratio", 1f / 1.414f),
        AspectRatioPreset("Square (1:1)", "Square ratio", 1f)
    )

    var selectedPresetIndex by remember { mutableStateOf(0) }

    fun applyAspectRatioPreset(preset: AspectRatioPreset) {
        val ratio = preset.ratio ?: return
        val currentW = normRight - normLeft
        val currentH = normBottom - normTop
        val centerX = normLeft + currentW / 2f
        val centerY = normTop + currentH / 2f

        var newW = currentW
        var newH = currentW / ratio
        if (newH > 1f) {
            newH = 1f
            newW = newH * ratio
        }
        if (newW > 1f) {
            newW = 1f
            newH = newW / ratio
        }

        var l = centerX - newW / 2f
        var t = centerY - newH / 2f
        var r = l + newW
        var b = t + newH

        if (l < 0f) {
            r -= l
            l = 0f
        }
        if (r > 1f) {
            l -= (r - 1f)
            r = 1f
        }
        if (t < 0f) {
            b -= t
            t = 0f
        }
        if (b > 1f) {
            t -= (b - 1f)
            b = 1f
        }

        normLeft = l.coerceIn(0f, 1f)
        normTop = t.coerceIn(0f, 1f)
        normRight = r.coerceIn(0f, 1f)
        normBottom = b.coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Title & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Crop, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto-Detect & Crop Document",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Aspect Ratio Presets Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEachIndexed { index, preset ->
                FilterChip(
                    selected = selectedPresetIndex == index,
                    onClick = {
                        selectedPresetIndex = index
                        if (preset.ratio != null) {
                            applyAspectRatioPreset(preset)
                        }
                    },
                    label = { Text(preset.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Crop Canvas Area
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF020617))
                .pointerInput(bitmap) {
                    detectDragGestures(
                        onDragStart = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            if (width <= 0f || height <= 0f) return@detectDragGestures

                            val dx = dragAmount.x / width
                            val dy = dragAmount.y / height

                            val minSize = 0.1f
                            normLeft = (normLeft + dx).coerceIn(0f, normRight - minSize)
                            normTop = (normTop + dy).coerceIn(0f, normBottom - minSize)
                            normRight = (normRight + dx).coerceIn(normLeft + minSize, 1f)
                            normBottom = (normBottom + dy).coerceIn(normTop + minSize, 1f)
                            selectedPresetIndex = 0
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                val bmpW = bitmap.width.toFloat()
                val bmpH = bitmap.height.toFloat()

                val scale = minOf(canvasW / bmpW, canvasH / bmpH)
                val imgW = bmpW * scale
                val imgH = bmpH * scale
                val imgLeft = (canvasW - imgW) / 2f
                val imgTop = (canvasH - imgH) / 2f

                // 1. Draw source bitmap
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                    dstSize = IntSize(imgW.toInt(), imgH.toInt())
                )

                val cropScreenLeft = imgLeft + normLeft * imgW
                val cropScreenTop = imgTop + normTop * imgH
                val cropScreenRight = imgLeft + normRight * imgW
                val cropScreenBottom = imgTop + normBottom * imgH

                // 2. Dimmed surrounding masks
                val dimColor = Color.Black.copy(alpha = 0.65f)
                drawRect(color = dimColor, topLeft = Offset(0f, 0f), size = Size(canvasW, cropScreenTop))
                drawRect(color = dimColor, topLeft = Offset(0f, cropScreenBottom), size = Size(canvasW, canvasH - cropScreenBottom))
                drawRect(color = dimColor, topLeft = Offset(0f, cropScreenTop), size = Size(cropScreenLeft, cropScreenBottom - cropScreenTop))
                drawRect(color = dimColor, topLeft = Offset(cropScreenRight, cropScreenTop), size = Size(canvasW - cropScreenRight, cropScreenBottom - cropScreenTop))

                val cropW = cropScreenRight - cropScreenLeft
                val cropH = cropScreenBottom - cropScreenTop

                // 3. Grid lines
                val gridColor = Color.White.copy(alpha = 0.35f)
                val gridStroke = 1.dp.toPx()
                drawLine(gridColor, Offset(cropScreenLeft + cropW / 3f, cropScreenTop), Offset(cropScreenLeft + cropW / 3f, cropScreenBottom), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(cropScreenLeft + 2 * cropW / 3f, cropScreenTop), Offset(cropScreenLeft + 2 * cropW / 3f, cropScreenBottom), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(cropScreenLeft, cropScreenTop + cropH / 3f), Offset(cropScreenRight, cropScreenTop + cropH / 3f), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(cropScreenLeft, cropScreenTop + 2 * cropH / 3f), Offset(cropScreenRight, cropScreenTop + 2 * cropH / 3f), strokeWidth = gridStroke)

                // 4. Boundary rectangle
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropScreenLeft, cropScreenTop),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 5. High-contrast Corner brackets
                val bracketLen = 22.dp.toPx()
                val bracketThickness = 4.dp.toPx()
                val bracketColor = Color(0xFF38BDF8)
                drawLine(bracketColor, Offset(cropScreenLeft, cropScreenTop), Offset(cropScreenLeft + bracketLen, cropScreenTop), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenLeft, cropScreenTop), Offset(cropScreenLeft, cropScreenTop + bracketLen), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenRight - bracketLen, cropScreenTop), Offset(cropScreenRight, cropScreenTop), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenRight, cropScreenTop), Offset(cropScreenRight, cropScreenTop + bracketLen), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenLeft, cropScreenBottom), Offset(cropScreenLeft + bracketLen, cropScreenBottom), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenLeft, cropScreenBottom - bracketLen), Offset(cropScreenLeft, cropScreenBottom), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenRight - bracketLen, cropScreenBottom), Offset(cropScreenRight, cropScreenBottom), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenRight, cropScreenBottom - bracketLen), Offset(cropScreenRight, cropScreenBottom), bracketThickness)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Action buttons: Auto Detect, Fit Full, Confirm
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val rect = DocumentEdgeDetector.detectDocumentEdges(bitmap)
                    normLeft = (rect.left.toFloat() / bitmap.width).coerceIn(0f, 1f)
                    normTop = (rect.top.toFloat() / bitmap.height).coerceIn(0f, 1f)
                    normRight = (rect.right.toFloat() / bitmap.width).coerceIn(normLeft + 0.1f, 1f)
                    normBottom = (rect.bottom.toFloat() / bitmap.height).coerceIn(normTop + 0.1f, 1f)
                    selectedPresetIndex = 0
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Auto Detect", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    normLeft = 0f
                    normTop = 0f
                    normRight = 1f
                    normBottom = 1f
                    selectedPresetIndex = 0
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Fit Full", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val leftPx = (normLeft * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                    val topPx = (normTop * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                    val widthPx = ((normRight - normLeft) * bitmap.width).toInt().coerceIn(1, bitmap.width - leftPx)
                    val heightPx = ((normBottom - normTop) * bitmap.height).toInt().coerceIn(1, bitmap.height - topPx)
                    val cropped = Bitmap.createBitmap(bitmap, leftPx, topPx, widthPx, heightPx)
                    onCropConfirmed(cropped)
                },
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
