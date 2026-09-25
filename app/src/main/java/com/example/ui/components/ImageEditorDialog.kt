package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

@Composable
fun ImageEditorDialog(
    imageUri: Uri,
    initialCropMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Uri) -> Unit
) {
    val context = LocalContext.current

    val originalBitmap = remember(imageUri) {
        try {
            val stream = context.contentResolver.openInputStream(imageUri)
            BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            null
        }
    }

    var currentBitmap by remember(originalBitmap) { mutableStateOf(originalBitmap) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var isGrayscale by remember { mutableStateOf(false) }
    var isCroppingMode by remember { mutableStateOf(initialCropMode) }

    fun processBitmap(): Bitmap? {
        val bmp = currentBitmap ?: return null
        val matrix = Matrix().apply { postRotate(rotationAngle) }
        var rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

        if (isGrayscale) {
            val width = rotated.width
            val height = rotated.height
            val grayBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(grayBmp)
            val paint = android.graphics.Paint()
            val colorMatrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(rotated, 0f, 0f, paint)
            rotated = grayBmp
        }
        return rotated
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (isCroppingMode && currentBitmap != null) {
                // If there was an unbaked rotation, bake it before cropping
                val workingBitmap = if (rotationAngle != 0f) {
                    val matrix = Matrix().apply { postRotate(rotationAngle) }
                    Bitmap.createBitmap(currentBitmap!!, 0, 0, currentBitmap!!.width, currentBitmap!!.height, matrix, true)
                } else {
                    currentBitmap!!
                }

                CropImageView(
                    bitmap = workingBitmap,
                    onCropConfirmed = { cropped ->
                        currentBitmap = cropped
                        rotationAngle = 0f // baked
                        isCroppingMode = false
                    },
                    onCancel = {
                        isCroppingMode = false
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Edit Document Photo",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Crop card boundaries & adjust photo quality",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Image Preview Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        val processed = processBitmap()
                        if (processed != null) {
                            Image(
                                bitmap = processed.asImageBitmap(),
                                contentDescription = "Edited Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )

                            // Overlay chip if photo is cropped
                            if (currentBitmap != originalBitmap) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Crop,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Cropped",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Editing Tools Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Crop Tool Button
                        FilledTonalButton(
                            onClick = { isCroppingMode = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crop Photo", fontWeight = FontWeight.Bold)
                        }

                        // Rotate Button
                        OutlinedButton(
                            onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotate 90°")
                        }

                        // B&W Contrast Filter Button
                        OutlinedButton(
                            onClick = { isGrayscale = !isGrayscale },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Contrast, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isGrayscale) "Color" else "B&W Scan")
                        }

                        // Reset Button
                        OutlinedButton(
                            onClick = {
                                currentBitmap = originalBitmap
                                rotationAngle = 0f
                                isGrayscale = false
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Apply and save
                    Button(
                        onClick = {
                            val finalBmp = processBitmap()
                            if (finalBmp != null) {
                                try {
                                    val file = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
                                    val out = FileOutputStream(file)
                                    finalBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                    out.flush()
                                    out.close()
                                    val savedUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    onConfirm(savedUri)
                                } catch (e: Exception) {
                                    onConfirm(imageUri)
                                }
                            } else {
                                onConfirm(imageUri)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply & Use Photo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
