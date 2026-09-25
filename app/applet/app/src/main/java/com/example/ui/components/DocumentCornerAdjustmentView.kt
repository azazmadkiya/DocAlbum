package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.DocumentEdgeDetector
import kotlin.math.hypot

enum class CornerHandleType {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT
}

@Composable
fun DocumentCornerAdjustmentView(
    bitmap: Bitmap,
    onCornersConfirmed: (PointF, PointF, PointF, PointF) -> Unit,
    onCancel: () -> Unit
) {
    // Initial auto-detected or default corners (normalized 0f..1f)
    val initialRect = remember(bitmap) { DocumentEdgeDetector.detectDocumentEdges(bitmap) }
    
    var topLeft by remember { 
        mutableStateOf(Offset(initialRect.left.toFloat() / bitmap.width, initialRect.top.toFloat() / bitmap.height)) 
    }
    var topRight by remember { 
        mutableStateOf(Offset(initialRect.right.toFloat() / bitmap.width, initialRect.top.toFloat() / bitmap.height)) 
    }
    var bottomRight by remember { 
        mutableStateOf(Offset(initialRect.right.toFloat() / bitmap.width, initialRect.bottom.toFloat() / bitmap.height)) 
    }
    var bottomLeft by remember { 
        mutableStateOf(Offset(initialRect.left.toFloat() / bitmap.width, initialRect.bottom.toFloat() / bitmap.height)) 
    }

    var activeHandle by remember { mutableStateOf(CornerHandleType.NONE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Crop, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Manual Corner Adjustment",
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
        Text(
            text = "Drag the 4 corner handles to precisely align with document edges",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF020617))
                .pointerInput(bitmap) {
                    val handleRadiusPx = 48f // touch target radius
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Map canvas tap offset to normalized coordinates relative to rendered image
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val bmpW = bitmap.width.toFloat()
                            val bmpH = bitmap.height.toFloat()
                            val scale = minOf(canvasW / bmpW, canvasH / bmpH)
                            val imgW = bmpW * scale
                            val imgH = bmpH * scale
                            val imgLeft = (canvasW - imgW) / 2f
                            val imgTop = (canvasH - imgH) / 2f

                            fun toScreen(pt: Offset) = Offset(
                                imgLeft + pt.x * imgW,
                                imgTop + pt.y * imgH
                            )

                            val tlScreen = toScreen(topLeft)
                            val trScreen = toScreen(topRight)
                            val brScreen = toScreen(bottomRight)
                            val blScreen = toScreen(bottomLeft)

                            activeHandle = when {
                                hypot(offset.x - tlScreen.x, offset.y - tlScreen.y) <= handleRadiusPx -> CornerHandleType.TOP_LEFT
                                hypot(offset.x - trScreen.x, offset.y - trScreen.y) <= handleRadiusPx -> CornerHandleType.TOP_RIGHT
                                hypot(offset.x - brScreen.x, offset.y - brScreen.y) <= handleRadiusPx -> CornerHandleType.BOTTOM_RIGHT
                                hypot(offset.x - blScreen.x, offset.y - blScreen.y) <= handleRadiusPx -> CornerHandleType.BOTTOM_LEFT
                                else -> CornerHandleType.NONE
                            }
                        },
                        onDragEnd = { activeHandle = CornerHandleType.NONE },
                        onDragCancel = { activeHandle = CornerHandleType.NONE },
                        onDrag = { change, dragAmount ->
                            if (activeHandle == CornerHandleType.NONE) return@detectDragGestures
                            change.consume()

                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val bmpW = bitmap.width.toFloat()
                            val bmpH = bitmap.height.toFloat()
                            val scale = minOf(canvasW / bmpW, canvasH / bmpH)
                            val imgW = bmpW * scale
                            val imgH = bmpH * scale

                            if (imgW <= 0f || imgH <= 0f) return@detectDragGestures

                            val dxNorm = dragAmount.x / imgW
                            val dyNorm = dragAmount.y / imgH

                            when (activeHandle) {
                                CornerHandleType.TOP_LEFT -> {
                                    topLeft = Offset(
                                        (topLeft.x + dxNorm).coerceIn(0f, topRight.x - 0.1f),
                                        (topLeft.y + dyNorm).coerceIn(0f, bottomLeft.y - 0.1f)
                                    )
                                }
                                CornerHandleType.TOP_RIGHT -> {
                                    topRight = Offset(
                                        (topRight.x + dxNorm).coerceIn(topLeft.x + 0.1f, 1f),
                                        (topRight.y + dyNorm).coerceIn(0f, bottomRight.y - 0.1f)
                                    )
                                }
                                CornerHandleType.BOTTOM_RIGHT -> {
                                    bottomRight = Offset(
                                        (bottomRight.x + dxNorm).coerceIn(bottomLeft.x + 0.1f, 1f),
                                        (bottomRight.y + dyNorm).coerceIn(topRight.y + 0.1f, 1f)
                                    )
                                }
                                CornerHandleType.BOTTOM_LEFT -> {
                                    bottomLeft = Offset(
                                        (bottomLeft.x + dxNorm).coerceIn(0f, bottomRight.x - 0.1f),
                                        (bottomLeft.y + dyNorm).coerceIn(topLeft.y + 0.1f, 1f)
                                    )
                                }
                                CornerHandleType.NONE -> {}
                            }
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

                fun toScreen(pt: Offset) = Offset(
                    imgLeft + pt.x * imgW,
                    imgTop + pt.y * imgH
                )

                val tlScreen = toScreen(topLeft)
                val trScreen = toScreen(topRight)
                val brScreen = toScreen(bottomRight)
                val blScreen = toScreen(bottomLeft)

                // 2. Draw connecting polygon outline
                val polygonPath = Path().apply {
                    moveTo(tlScreen.x, tlScreen.y)
                    lineTo(trScreen.x, trScreen.y)
                    lineTo(brScreen.x, brScreen.y)
                    lineTo(blScreen.x, blScreen.y)
                    close()
                }

                drawPath(
                    path = polygonPath,
                    color = Color(0xFF38BDF8),
                    style = Stroke(width = 3.dp.toPx())
                )

                // 3. Draw draggable corner handle circles
                val handleRadius = 16.dp.toPx()
                val handleColor = Color(0xFF38BDF8)
                val handleBorderColor = Color.White

                listOf(tlScreen, trScreen, brScreen, blScreen).forEach { pt ->
                    drawCircle(
                        color = handleBorderColor,
                        radius = handleRadius + 2.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = handleColor,
                        radius = handleRadius,
                        center = pt
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val rect = DocumentEdgeDetector.detectDocumentEdges(bitmap)
                    topLeft = Offset(rect.left.toFloat() / bitmap.width, rect.top.toFloat() / bitmap.height)
                    topRight = Offset(rect.right.toFloat() / bitmap.width, rect.top.toFloat() / bitmap.height)
                    bottomRight = Offset(rect.right.toFloat() / bitmap.width, rect.bottom.toFloat() / bitmap.height)
                    bottomLeft = Offset(rect.left.toFloat() / bitmap.width, rect.bottom.toFloat() / bitmap.height)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset")
            }

            Button(
                onClick = {
                    onCornersConfirmed(
                        PointF(topLeft.x * bitmap.width, topLeft.y * bitmap.height),
                        PointF(topRight.x * bitmap.width, topRight.y * bitmap.height),
                        PointF(bottomRight.x * bitmap.width, bottomRight.y * bitmap.height),
                        PointF(bottomLeft.x * bitmap.width, bottomLeft.y * bitmap.height)
                    )
                },
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Confirm Corners", fontWeight = FontWeight.Bold)
            }
        }
    }
}
