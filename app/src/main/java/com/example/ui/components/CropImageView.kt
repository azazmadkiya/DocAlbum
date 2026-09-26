package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.CardEdgeDetector
import com.example.utils.CardQuad
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
    val context = LocalContext.current
    val presets = remember {
        listOf(
            AspectRatioPreset("Freeform", "Free", null),
            AspectRatioPreset("ID Card (CR80)", "85:54", 85.6f / 53.98f), // 1.586
            AspectRatioPreset("3:2 Card", "3:2", 1.5f),
            AspectRatioPreset("4:3 Standard", "4:3", 4f / 3f),
            AspectRatioPreset("1:1 Square", "1:1", 1f),
            AspectRatioPreset("A4 Page", "1:1.41", 1f / 1.4142f)
        )
    }
    var selectedPresetIndex by remember { mutableIntStateOf(1) } // Default to ID Card preset

    // Normalized coordinates [0f..1f] relative to displayed image
    var normLeft by remember { mutableFloatStateOf(0.08f) }
    var normTop by remember { mutableFloatStateOf(0.12f) }
    var normRight by remember { mutableFloatStateOf(0.92f) }
    var normBottom by remember { mutableFloatStateOf(0.88f) }

    var detectedResult by remember { mutableStateOf<CardEdgeDetector.DetectionResult?>(null) }
    var isAutoDetectedActive by remember { mutableStateOf(false) }

    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
    val density = LocalDensity.current
    val cornerThresholdPx = with(density) { 44.dp.toPx() }
    val edgeThresholdPx = with(density) { 24.dp.toPx() }

    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()
    val bmpAspect = bmpW / bmpH

    fun applyPresetRatio(ratio: Float?) {
        if (ratio == null) return
        val normTargetAspect = ratio / bmpAspect
        var w = 0.86f
        var h = w / normTargetAspect
        if (h > 0.86f) {
            h = 0.86f
            w = h * normTargetAspect
        }
        val safeW = w.coerceIn(0.1f, 0.98f)
        val safeH = h.coerceIn(0.1f, 0.98f)
        normLeft = ((1f - safeW) / 2f).coerceIn(0f, 1f - safeW)
        normTop = ((1f - safeH) / 2f).coerceIn(0f, 1f - safeH)
        normRight = normLeft + safeW
        normBottom = normTop + safeH
        isAutoDetectedActive = false
    }

    fun triggerAutoDetect(showToast: Boolean = true) {
        val result = CardEdgeDetector.detectCard(bitmap)
        detectedResult = result
        normLeft = result.normalizedRect.left
        normTop = result.normalizedRect.top
        normRight = result.normalizedRect.right
        normBottom = result.normalizedRect.bottom
        selectedPresetIndex = 0 // Custom / freeform to respect the exact detected bounds
        isAutoDetectedActive = true
        if (showToast) {
            Toast.makeText(context, "Card edges auto-detected successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Automatically detect document edges on first launch
    LaunchedEffect(bitmap) {
        val result = CardEdgeDetector.detectCard(bitmap)
        detectedResult = result
        if (result.isCardDetected) {
            normLeft = result.normalizedRect.left
            normTop = result.normalizedRect.top
            normRight = result.normalizedRect.right
            normBottom = result.normalizedRect.bottom
            selectedPresetIndex = 0
            isAutoDetectedActive = true
        } else {
            applyPresetRatio(presets[selectedPresetIndex].ratio)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Smart Card Crop",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Auto-detects edges or adjust handles manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Crop")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Ratio Presets & Auto-Detect Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prominent Auto-Detect Button
            FilledTonalButton(
                onClick = { triggerAutoDetect(showToast = true) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isAutoDetectedActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isAutoDetectedActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Auto-Detect Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            presets.forEachIndexed { index, preset ->
                FilterChip(
                    selected = selectedPresetIndex == index && !isAutoDetectedActive,
                    onClick = {
                        selectedPresetIndex = index
                        applyPresetRatio(preset.ratio)
                    },
                    label = { Text(preset.label, fontSize = 12.sp) },
                    leadingIcon = if (selectedPresetIndex == index && !isAutoDetectedActive) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null
                )
            }
        }

        if (isAutoDetectedActive) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Aadhaar Card Boundaries Auto-Detected (${((detectedResult?.confidence ?: 0.9f) * 100).toInt()}% confidence)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Interactive Cropping Canvas
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            val containerW = maxWidth.value * density.density
            val containerH = maxHeight.value * density.density
            val containerAspect = containerW / containerH

            val imgW: Float
            val imgH: Float
            val imgLeft: Float
            val imgTop: Float

            if (bmpAspect > containerAspect) {
                imgW = containerW
                imgH = containerW / bmpAspect
                imgLeft = 0f
                imgTop = (containerH - imgH) / 2f
            } else {
                imgH = containerH
                imgW = containerH * bmpAspect
                imgLeft = (containerW - imgW) / 2f
                imgTop = 0f
            }

            val cropScreenLeft = imgLeft + normLeft * imgW
            val cropScreenTop = imgTop + normTop * imgH
            val cropScreenRight = imgLeft + normRight * imgW
            val cropScreenBottom = imgTop + normBottom * imgH

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imgW, imgH, imgLeft, imgTop) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val x = offset.x
                                val y = offset.y
                                activeHandle = when {
                                    hypot(x - cropScreenLeft, y - cropScreenTop) <= cornerThresholdPx -> CropHandle.TOP_LEFT
                                    hypot(x - cropScreenRight, y - cropScreenTop) <= cornerThresholdPx -> CropHandle.TOP_RIGHT
                                    hypot(x - cropScreenLeft, y - cropScreenBottom) <= cornerThresholdPx -> CropHandle.BOTTOM_LEFT
                                    hypot(x - cropScreenRight, y - cropScreenBottom) <= cornerThresholdPx -> CropHandle.BOTTOM_RIGHT
                                    x in cropScreenLeft..cropScreenRight && abs(y - cropScreenTop) <= edgeThresholdPx -> CropHandle.EDGE_TOP
                                    x in cropScreenLeft..cropScreenRight && abs(y - cropScreenBottom) <= edgeThresholdPx -> CropHandle.EDGE_BOTTOM
                                    y in cropScreenTop..cropScreenBottom && abs(x - cropScreenLeft) <= edgeThresholdPx -> CropHandle.EDGE_LEFT
                                    y in cropScreenTop..cropScreenBottom && abs(x - cropScreenRight) <= edgeThresholdPx -> CropHandle.EDGE_RIGHT
                                    x in cropScreenLeft..cropScreenRight && y in cropScreenTop..cropScreenBottom -> CropHandle.BODY
                                    else -> CropHandle.NONE
                                }
                            },
                            onDragEnd = { activeHandle = CropHandle.NONE },
                            onDragCancel = { activeHandle = CropHandle.NONE },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x / imgW
                                val dy = dragAmount.y / imgH
                                val minSize = 0.08f
                                isAutoDetectedActive = false

                                when (activeHandle) {
                                    CropHandle.TOP_LEFT -> {
                                        normLeft = (normLeft + dx).coerceIn(0f, normRight - minSize)
                                        normTop = (normTop + dy).coerceIn(0f, normBottom - minSize)
                                        selectedPresetIndex = 0
                                    }
                                    CropHandle.TOP_RIGHT -> {
                                        normRight = (normRight + dx).coerceIn(normLeft + minSize, 1f)
                                        normTop = (normTop + dy).coerceIn(0f, normBottom - minSize)
                                        selectedPresetIndex = 0
                                    }
                                    CropHandle.BOTTOM_LEFT -> {
                                        normLeft = (normLeft + dx).coerceIn(0f, normRight - minSize)
                                        normBottom = (normBottom + dy).coerceIn(normTop + minSize, 1f)
                                        selectedPresetIndex = 0
                                    }
                                    CropHandle.BOTTOM_RIGHT -> {
                                        normRight = (normRight + dx).coerceIn(normLeft + minSize, 1f)
                                        normBottom = (normBottom + dy).coerceIn(normTop + minSize, 1f)
                                        selectedPresetIndex = 0
                                    }
                                    CropHandle.EDGE_TOP -> {
                                        normTop = (normTop + dy).coerceIn(0f, normBottom - minSize)
                                        selectedPresetIndex = 0
                                    }
                                    CropHandle.EDGE_BOTTOM -> {
                                        normBottom = (normBottom + dy).coerceIn(normTop + minSize, 1f)
                                        selectedPresetIndex = 0
                                    }
                                    CropHandle.EDGE_LEFT -> {
                                        normLeft = (normLeft + dx).coerceIn(0f, normRight - minSize)
                                        selectedPresetIndex = 0
                                    }
                                    CropHandle.EDGE_RIGHT -> {
                                        normRight = (normRight + dx).coerceIn(normLeft + minSize, 1f)
                                        selectedPresetIndex = 0
                                    }
                                    CropHandle.BODY -> {
                                        val width = normRight - normLeft
                                        val height = normBottom - normTop
                                        val newLeft = (normLeft + dx).coerceIn(0f, 1f - width)
                                        val newTop = (normTop + dy).coerceIn(0f, 1f - height)
                                        normLeft = newLeft
                                        normTop = newTop
                                        normRight = newLeft + width
                                        normBottom = newTop + height
                                    }
                                    CropHandle.NONE -> {}
                                }
                            }
                        )
                    }
            ) {
                // 1. Draw source bitmap
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                    dstSize = IntSize(imgW.toInt(), imgH.toInt())
                )

                // 2. Dimmed surrounding masks
                val dimColor = Color.Black.copy(alpha = 0.65f)

                // Top
                drawRect(
                    color = dimColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, cropScreenTop)
                )
                // Bottom
                drawRect(
                    color = dimColor,
                    topLeft = Offset(0f, cropScreenBottom),
                    size = Size(size.width, size.height - cropScreenBottom)
                )
                // Left
                drawRect(
                    color = dimColor,
                    topLeft = Offset(0f, cropScreenTop),
                    size = Size(cropScreenLeft, cropScreenBottom - cropScreenTop)
                )
                // Right
                drawRect(
                    color = dimColor,
                    topLeft = Offset(cropScreenRight, cropScreenTop),
                    size = Size(size.width - cropScreenRight, cropScreenBottom - cropScreenTop)
                )

                val cropW = cropScreenRight - cropScreenLeft
                val cropH = cropScreenBottom - cropScreenTop

                // 3. Grid lines (rule of thirds)
                val gridColor = Color.White.copy(alpha = 0.35f)
                val gridStroke = 1.dp.toPx()

                drawLine(
                    gridColor,
                    Offset(cropScreenLeft + cropW / 3f, cropScreenTop),
                    Offset(cropScreenLeft + cropW / 3f, cropScreenBottom),
                    strokeWidth = gridStroke
                )
                drawLine(
                    gridColor,
                    Offset(cropScreenLeft + 2 * cropW / 3f, cropScreenTop),
                    Offset(cropScreenLeft + 2 * cropW / 3f, cropScreenBottom),
                    strokeWidth = gridStroke
                )
                drawLine(
                    gridColor,
                    Offset(cropScreenLeft, cropScreenTop + cropH / 3f),
                    Offset(cropScreenRight, cropScreenTop + cropH / 3f),
                    strokeWidth = gridStroke
                )
                drawLine(
                    gridColor,
                    Offset(cropScreenLeft, cropScreenTop + 2 * cropH / 3f),
                    Offset(cropScreenRight, cropScreenTop + 2 * cropH / 3f),
                    strokeWidth = gridStroke
                )

                // 4. Boundary rectangle
                val boundaryColor = if (isAutoDetectedActive) Color(0xFF38BDF8) else Color.White
                drawRect(
                    color = boundaryColor,
                    topLeft = Offset(cropScreenLeft, cropScreenTop),
                    size = Size(cropW, cropH),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 5. High-contrast Corner brackets
                val bracketLen = 22.dp.toPx()
                val bracketThickness = 4.dp.toPx()
                val bracketColor = Color(0xFF38BDF8) // Light blue accent

                // Top-Left
                drawLine(bracketColor, Offset(cropScreenLeft, cropScreenTop), Offset(cropScreenLeft + bracketLen, cropScreenTop), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenLeft, cropScreenTop), Offset(cropScreenLeft, cropScreenTop + bracketLen), bracketThickness)

                // Top-Right
                drawLine(bracketColor, Offset(cropScreenRight - bracketLen, cropScreenTop), Offset(cropScreenRight, cropScreenTop), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenRight, cropScreenTop), Offset(cropScreenRight, cropScreenTop + bracketLen), bracketThickness)

                // Bottom-Left
                drawLine(bracketColor, Offset(cropScreenLeft, cropScreenBottom), Offset(cropScreenLeft + bracketLen, cropScreenBottom), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenLeft, cropScreenBottom), Offset(cropScreenLeft, cropScreenBottom - bracketLen), bracketThickness)

                // Bottom-Right
                drawLine(bracketColor, Offset(cropScreenRight - bracketLen, cropScreenBottom), Offset(cropScreenRight, cropScreenBottom), bracketThickness)
                drawLine(bracketColor, Offset(cropScreenRight, cropScreenBottom - bracketLen), Offset(cropScreenRight, cropScreenBottom), bracketThickness)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    normLeft = 0f
                    normTop = 0f
                    normRight = 1f
                    normBottom = 1f
                    selectedPresetIndex = 0
                    isAutoDetectedActive = false
                },
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Fit Full", fontSize = 12.sp)
            }

            // Perspective Homography Flatten & Warp Button
            FilledTonalButton(
                onClick = {
                    val quad = detectedResult?.quad ?: CardQuad(
                        android.graphics.PointF(normLeft * bitmap.width, normTop * bitmap.height),
                        android.graphics.PointF(normRight * bitmap.width, normTop * bitmap.height),
                        android.graphics.PointF(normRight * bitmap.width, normBottom * bitmap.height),
                        android.graphics.PointF(normLeft * bitmap.width, normBottom * bitmap.height)
                    )
                    val warped = CardEdgeDetector.cropAndWarpCard(bitmap, quad)
                    Toast.makeText(context, "Perspective unskewed & cropped!", Toast.LENGTH_SHORT).show()
                    onCropConfirmed(warped)
                },
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Straighten", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                modifier = Modifier.weight(1.3f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
