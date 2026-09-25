package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.DocViewModel
import com.example.utils.A4DocumentGenerator
import com.example.utils.CardPrintSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreviewScreen(
    viewModel: DocViewModel,
    onBack: () -> Unit,
    onPrintConfirmed: () -> Unit = {}
) {
    val context = LocalContext.current

    val title by viewModel.docTitle.collectAsState()
    val frontUri by viewModel.frontUri.collectAsState()
    val backUri by viewModel.backUri.collectAsState()
    val layoutStyle by viewModel.layoutStyle.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val cardPrintSize by viewModel.cardPrintSize.collectAsState()
    val showCutGuides by viewModel.showCutGuides.collectAsState()
    val showLabels by viewModel.showLabels.collectAsState()

    // High resolution processed A4 bitmap generated off the main thread
    val a4BitmapState = produceState<Bitmap?>(
        initialValue = null,
        frontUri,
        backUri,
        layoutStyle,
        filterType,
        cardPrintSize,
        showCutGuides,
        showLabels,
        title
    ) {
        value = withContext(Dispatchers.Default) {
            A4DocumentGenerator.generateA4Bitmap(
                context = context,
                title = title,
                frontUri = frontUri,
                backUri = backUri,
                layoutStyle = layoutStyle,
                cardPrintSize = cardPrintSize,
                filterType = filterType,
                showCutGuides = showCutGuides,
                showLabels = showLabels
            )
        }
    }

    val a4Bitmap = a4BitmapState.value

    // Zoom & Pan inspection state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun resetZoom() {
        scale = 1f
        offset = Offset.Zero
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "A4 Print Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (title.isNotBlank()) title else "Official Document (300 DPI)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Editor"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { resetZoom() },
                        enabled = scale != 1f || offset != Offset.Zero
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Reset Zoom to Fit"
                        )
                    }
                    IconButton(
                        onClick = {
                            if (a4Bitmap != null) {
                                val uri = A4DocumentGenerator.saveA4BitmapToStorage(context, a4Bitmap, title)
                                if (uri != null) {
                                    A4DocumentGenerator.shareA4Image(context, uri, title)
                                }
                            }
                        },
                        enabled = a4Bitmap != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Document"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Quick adjustments strip in preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Layout Mode
                        FilterChip(
                            selected = layoutStyle == "STACKED",
                            onClick = {
                                viewModel.setLayoutStyle(if (layoutStyle == "STACKED") "SIDE_BY_SIDE" else "STACKED")
                            },
                            label = {
                                Text(if (layoutStyle == "STACKED") "Top & Bottom" else "Side by Side", fontSize = 12.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (layoutStyle == "STACKED") Icons.Default.VerticalSplit else Icons.Default.HorizontalSplit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )

                        // Print Filter
                        FilterChip(
                            selected = filterType == "COLOR",
                            onClick = {
                                viewModel.setFilterType(if (filterType == "COLOR") "BW" else "COLOR")
                            },
                            label = {
                                Text(if (filterType == "COLOR") "Full Color" else "B&W Laser", fontSize = 12.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (filterType == "COLOR") Icons.Default.Palette else Icons.Default.FilterBAndW,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )

                        // Cut Guides
                        FilterChip(
                            selected = showCutGuides,
                            onClick = { viewModel.setShowCutGuides(!showCutGuides) },
                            label = { Text("Cut Marks", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showCutGuides) Icons.Default.Check else Icons.Default.CropFree,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Primary Print Confirmation and Secondary Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Secondary Save Button
                        OutlinedButton(
                            onClick = {
                                if (a4Bitmap != null) {
                                    A4DocumentGenerator.saveA4BitmapToStorage(context, a4Bitmap, title)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("save_image_button"),
                            shape = RoundedCornerShape(14.dp),
                            enabled = a4Bitmap != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Image", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        // Primary Confirm & Print Button
                        Button(
                            onClick = {
                                if (a4Bitmap != null) {
                                    viewModel.saveDocument {
                                        A4DocumentGenerator.printA4Bitmap(context, a4Bitmap, title)
                                        Toast.makeText(context, "Document saved to history & sent to printer!", Toast.LENGTH_SHORT).show()
                                        onPrintConfirmed()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp)
                                .testTag("confirm_print_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            enabled = a4Bitmap != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Confirm & Print",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A)), // Dark drafting canvas for high-contrast paper inspection
            contentAlignment = Alignment.Center
        ) {
            // Document Specifications Banner floating at top
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ISO 216 A4 (210 × 297 mm) • 300 DPI Ultra HD",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            if (a4Bitmap == null) {
                // Loading state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF38BDF8),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Rendering High-Definition A4 Layout…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Optimizing 300 DPI card alignment & cut borders",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            } else {
                // Interactive A4 Paper Viewport with Pan and Zoom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 48.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(0.9f, 4.0f)
                                scale = newScale
                                if (newScale > 1f) {
                                    val maxOffsetX = 400f * (newScale - 1f)
                                    val maxOffsetY = 700f * (newScale - 1f)
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.4f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Realistic Physical A4 Paper Sheet (aspect ratio 210 / 297 = 0.707)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.92f)
                            .aspectRatio(210f / 297f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(3.dp),
                                spotColor = Color.Black
                            )
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                            .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = a4Bitmap.asImageBitmap(),
                            contentDescription = "Processed A4 Document Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Floating Zoom Level and Quick Zoom Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    scale = (scale - 0.4f).coerceAtLeast(1f)
                                    if (scale == 1f) offset = Offset.Zero
                                },
                                modifier = Modifier.size(28.dp),
                                enabled = scale > 1f
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Zoom Out",
                                    tint = if (scale > 1f) Color.White else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = "${(scale * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.widthIn(min = 40.dp),
                                textAlign = TextAlign.Center
                            )

                            IconButton(
                                onClick = {
                                    scale = (scale + 0.4f).coerceAtMost(4f)
                                },
                                modifier = Modifier.size(28.dp),
                                enabled = scale < 4f
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In",
                                    tint = if (scale < 4f) Color.White else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (scale != 1f || offset != Offset.Zero) {
                                VerticalDivider(
                                    modifier = Modifier.height(14.dp),
                                    color = Color(0xFF475569)
                                )
                                TextButton(
                                    onClick = { resetZoom() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        text = "Reset",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
