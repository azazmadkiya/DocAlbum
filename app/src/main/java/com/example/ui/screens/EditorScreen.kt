package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.components.ImageEditorDialog
import com.example.ui.components.PasswordInputDialog
import com.example.ui.viewmodel.DocViewModel
import com.example.utils.A4DocumentGenerator
import com.example.utils.PDFEncryptionManager
import com.example.utils.PdfProcessor
import com.example.utils.CardPrintSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: DocViewModel,
    onBack: () -> Unit,
    onOpenPreview: (() -> Unit)? = null
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
    val encryptionManager = remember { PDFEncryptionManager(context) }
    var isPasswordProtected by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showPdfPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val handleExportWithCheck: (() -> Unit) -> Unit = { exportAction ->
        if (isPasswordProtected && encryptionManager.currentConfig == null) {
            pendingExportAction = exportAction
            showPasswordDialog = true
        } else {
            exportAction()
        }
    }

    var showInternalPreview by remember { mutableStateOf(false) }

    if (showInternalPreview) {
        PrintPreviewScreen(
            viewModel = viewModel,
            onBack = { showInternalPreview = false }
        )
        return
    }

    val handleOpenPreview: () -> Unit = {
        if (onOpenPreview != null) {
            onOpenPreview()
        } else {
            showInternalPreview = true
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Front, 1: Back, 2: A4 Preview

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var editingSide by remember { mutableStateOf<String?>(null) } // "FRONT" or "BACK"
    var startInCropMode by remember { mutableStateOf(false) }

    fun createTempUri(): Uri {
        val file = File(context.cacheDir, "doc_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val frontGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            editingSide = "FRONT"
            startInCropMode = true
        }
    }

    val frontCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            pendingImageUri = tempCameraUri
            editingSide = "FRONT"
            startInCropMode = true
        }
    }

    val frontCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempUri()
            tempCameraUri = uri
            frontCameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val backGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            editingSide = "BACK"
            startInCropMode = true
        }
    }

    val backCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            pendingImageUri = tempCameraUri
            editingSide = "BACK"
            startInCropMode = true
        }
    }

    val backCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempUri()
            tempCameraUri = uri
            backCameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val extracted = com.example.utils.PdfProcessor.processPdf(context, uri)
                if (extracted != null) {
                    if (extracted.frontUri != null) viewModel.setFrontUri(extracted.frontUri)
                    if (extracted.backUri != null) viewModel.setBackUri(extracted.backUri)
                    if (extracted.title.isNotBlank()) viewModel.setDocTitle(extracted.title)
                    Toast.makeText(context, "Official PDF loaded & auto-cropped successfully!", Toast.LENGTH_LONG).show()
                    selectedTab = 2 // jump to A4 preview studio
                } else {
                    Toast.makeText(context, "Failed to parse PDF document", Toast.LENGTH_SHORT).show()
                }
            } catch (e: com.example.utils.PdfProcessor.PasswordRequiredException) {
                pendingPdfUri = uri
                showPdfPasswordDialog = true
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

        if (showPdfPasswordDialog) {
        PasswordInputDialog(
            title = "Protected PDF Password",
            description = "This PDF (e.g., Aadhaar Card) is password protected. Enter the document password (e.g., first 4 letters of name in caps + birth year) to decrypt and import.",
            confirmButtonText = "Decrypt & Import",
            requireConfirmation = false,
            onDismiss = {
                showPdfPasswordDialog = false
                pendingPdfUri = null
            },
            onSubmit = { password ->
                try {
                    val uri = pendingPdfUri
                    if (uri != null) {
                        val extracted = com.example.utils.PdfProcessor.processPdf(context, uri, password)
                        if (extracted != null) {
                            if (extracted.frontUri != null) viewModel.setFrontUri(extracted.frontUri)
                            if (extracted.backUri != null) viewModel.setBackUri(extracted.backUri)
                            if (extracted.title.isNotBlank()) viewModel.setDocTitle(extracted.title)
                            Toast.makeText(context, "Encrypted PDF decrypted & loaded successfully!", Toast.LENGTH_LONG).show()
                            selectedTab = 2
                            showPdfPasswordDialog = false
                            pendingPdfUri = null
                        } else {
                            Toast.makeText(context, "Incorrect password or invalid PDF structure", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: com.example.utils.PdfProcessor.PasswordRequiredException) {
                    Toast.makeText(context, "Incorrect password. Please try again.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Decryption error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showPasswordDialog) {
        PasswordInputDialog(
            title = "Protect Exported PDF",
            description = "Set owner and user passwords to secure your exported A4 document.",
            confirmButtonText = "Set Password & Export",
            requireConfirmation = true,
            onDismiss = {
                showPasswordDialog = false
                pendingExportAction = null
            },
            onSubmit = { password ->
                val success = encryptionManager.setPasswords(password)
                if (success) {
                    showPasswordDialog = false
                    pendingExportAction?.invoke()
                    pendingExportAction = null
                }
            }
        )
    }

    if (pendingImageUri != null && editingSide != null) {
        ImageEditorDialog(
            imageUri = pendingImageUri!!,
            initialCropMode = startInCropMode,
            onDismiss = {
                pendingImageUri = null
                editingSide = null
                startInCropMode = false
            },
            onConfirm = { editedUri ->
                if (editingSide == "FRONT") {
                    viewModel.setFrontUri(editedUri)
                    Toast.makeText(context, "Front side photo cropped & saved!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.setBackUri(editedUri)
                    Toast.makeText(context, "Back side photo cropped & saved!", Toast.LENGTH_SHORT).show()
                }
                pendingImageUri = null
                editingSide = null
                startInCropMode = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("A4 Document Studio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveDocument {
                            Toast.makeText(context, "Saved to history successfully!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    label = { Text("Front Side") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    label = { Text("Back Side") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Print, contentDescription = null) },
                    label = { Text("A4 Studio") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.setDocTitle(it) },
                label = { Text("Document Name (optional for history)") },
                placeholder = { Text("e.g. Aadhaar Card, Driving License, PAN Card") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedButton(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Official PDF (Aadhaar / ID Card)", fontWeight = FontWeight.Bold)
            }

            when (selectedTab) {
                0 -> {
                    SideCaptureAndCropSection(
                        sideTitle = "Front Side of Document",
                        imageUri = frontUri,
                        onPickGallery = {
                            frontGalleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onCaptureCamera = {
                            frontCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        onEditCrop = {
                            if (frontUri != null) {
                                pendingImageUri = frontUri
                                editingSide = "FRONT"
                                startInCropMode = true
                            }
                        },
                        onClear = { viewModel.setFrontUri(null) },
                        onNext = { selectedTab = 1 }
                    )
                }
                1 -> {
                    SideCaptureAndCropSection(
                        sideTitle = "Back Side of Document",
                        imageUri = backUri,
                        onPickGallery = {
                            backGalleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onCaptureCamera = {
                            backCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        onEditCrop = {
                            if (backUri != null) {
                                pendingImageUri = backUri
                                editingSide = "BACK"
                                startInCropMode = true
                            }
                        },
                        onClear = { viewModel.setBackUri(null) },
                        onNext = { selectedTab = 2 }
                    )
                }
                2 -> {
                    A4StudioSection(
                        title = title,
                        frontUri = frontUri,
                        backUri = backUri,
                        layoutStyle = layoutStyle,
                        filterType = filterType,
                        cardPrintSize = cardPrintSize,
                        showCutGuides = showCutGuides,
                        showLabels = showLabels,
                        onLayoutStyleChange = { viewModel.setLayoutStyle(it) },
                        onFilterTypeChange = { viewModel.setFilterType(it) },
                        onCardPrintSizeChange = { viewModel.setCardPrintSize(it) },
                        onShowCutGuidesChange = { viewModel.setShowCutGuides(it) },
                        onShowLabelsChange = { viewModel.setShowLabels(it) },
                        onPrint = handleOpenPreview,
                        isPasswordProtected = isPasswordProtected,
                        onTogglePasswordProtection = { enabled ->
                            isPasswordProtected = enabled
                            if (enabled && encryptionManager.currentConfig == null) {
                                pendingExportAction = {}
                                showPasswordDialog = true
                            } else if (!enabled) {
                                encryptionManager.clearProtection()
                            }
                        },
                        onSaveToStorage = {
                            handleExportWithCheck {
                                val a4Bmp = A4DocumentGenerator.generateA4Bitmap(
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
                                viewModel.saveDocument {
                                    A4DocumentGenerator.saveA4BitmapToStorage(context, a4Bmp, title)
                                }
                            }
                        },
                        onShare = {
                            handleExportWithCheck {
                                val a4Bmp = A4DocumentGenerator.generateA4Bitmap(
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
                                val uri = A4DocumentGenerator.saveA4BitmapToStorage(context, a4Bmp, title)
                                if (uri != null) {
                                    A4DocumentGenerator.shareA4Image(context, uri, title)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SideCaptureAndCropSection(
    sideTitle: String,
    imageUri: Uri?,
    onPickGallery: () -> Unit,
    onCaptureCamera: () -> Unit,
    onEditCrop: () -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sideTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scan document page using device camera with auto-crop & 300 DPI cleanup",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Scanner Assistant Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Camera Document Scanner",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Place document on flat surface • Auto-crop & perspective helper",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable(enabled = imageUri != null) { onEditCrop() },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = sideTitle,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Tap to Crop guide overlay
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Crop,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tap to Crop",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No page scanned yet", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                        Text("Tap Scan Document below", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCaptureCamera,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (imageUri != null) "Rescan Page" else "Scan Document", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPickGallery,
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                }
            }

            if (imageUri != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onEditCrop,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crop & Edit Image", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Proceed to Next Step")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
fun A4StudioSection(
    title: String,
    frontUri: Uri?,
    backUri: Uri?,
    layoutStyle: String,
    filterType: String,
    cardPrintSize: CardPrintSize,
    showCutGuides: Boolean,
    showLabels: Boolean,
    isPasswordProtected: Boolean,
    onTogglePasswordProtection: (Boolean) -> Unit,
    onLayoutStyleChange: (String) -> Unit,
    onFilterTypeChange: (String) -> Unit,
    onCardPrintSizeChange: (CardPrintSize) -> Unit,
    onShowCutGuidesChange: (Boolean) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onPrint: () -> Unit,
    onSaveToStorage: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Studio Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "A4 Professional Print Studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Auto-adjusted for best printable size • No bulky distortion • Sharp 300 DPI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Current Size Highlight Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Print Size: ${cardPrintSize.title}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = cardPrintSize.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // --- A4 WYSIWYG SHEET PREVIEW ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clickable { onPrint() },
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Full Preview",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    // Proportional card width fraction based on standard 210mm A4 width
                    val widthFraction = when (cardPrintSize) {
                        CardPrintSize.AUTO_BEST -> 0.62f
                        CardPrintSize.REAL_CARD -> 0.42f
                        CardPrintSize.COMPACT -> 0.50f
                        CardPrintSize.LARGE -> 0.78f
                    }

                    // Sheet Content (Cards)
                    if (layoutStyle == "STACKED") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Front Card
                            A4CardPreviewItem(
                                label = if (showLabels) "FRONT SIDE" else null,
                                uri = frontUri,
                                widthFraction = widthFraction,
                                showCutGuides = showCutGuides,
                                isBw = filterType == "BW",
                                placeholderText = "Front Card"
                            )

                            // Back Card
                            A4CardPreviewItem(
                                label = if (showLabels) "BACK SIDE" else null,
                                uri = backUri,
                                widthFraction = widthFraction,
                                showCutGuides = showCutGuides,
                                isBw = filterType == "BW",
                                placeholderText = "Back Card"
                            )
                        }
                    } else {
                        // SIDE_BY_SIDE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                A4CardPreviewItem(
                                    label = if (showLabels) "FRONT" else null,
                                    uri = frontUri,
                                    widthFraction = 0.95f,
                                    showCutGuides = showCutGuides,
                                    isBw = filterType == "BW",
                                    placeholderText = "Front"
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                A4CardPreviewItem(
                                    label = if (showLabels) "BACK" else null,
                                    uri = backUri,
                                    widthFraction = 0.95f,
                                    showCutGuides = showCutGuides,
                                    isBw = filterType == "BW",
                                    placeholderText = "Back"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- CONTROLS: 1. Auto-Adjust Printable Size ---
            Text(
                text = "Auto-Adjust Printable Size (No Bulky)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardPrintSize.entries.forEach { size ->
                    FilterChip(
                        selected = cardPrintSize == size,
                        onClick = { onCardPrintSizeChange(size) },
                        label = {
                            Text(
                                text = size.title,
                                fontWeight = if (cardPrintSize == size) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (cardPrintSize == size) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- CONTROLS: 2. Layout & Print Color ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = layoutStyle == "STACKED",
                    onClick = { onLayoutStyleChange("STACKED") },
                    label = { Text("Top & Bottom") },
                    leadingIcon = { Icon(Icons.Default.VerticalSplit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = layoutStyle == "SIDE_BY_SIDE",
                    onClick = { onLayoutStyleChange("SIDE_BY_SIDE") },
                    label = { Text("Side by Side") },
                    leadingIcon = { Icon(Icons.Default.HorizontalSplit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == "COLOR",
                    onClick = { onFilterTypeChange("COLOR") },
                    label = { Text("Color Print") },
                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = filterType == "BW",
                    onClick = { onFilterTypeChange("BW") },
                    label = { Text("B&W Scan / Laser") },
                    leadingIcon = { Icon(Icons.Default.FilterBAndW, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- CONTROLS: 3. Cut Marks & Labels ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showCutGuides,
                    onClick = { onShowCutGuidesChange(!showCutGuides) },
                    label = { Text("Cut Marks & Border") },
                    leadingIcon = {
                        Icon(
                            if (showCutGuides) Icons.Default.Check else Icons.Default.CropFree,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = showLabels,
                    onClick = { onShowLabelsChange(!showLabels) },
                    label = { Text("Front/Back Labels") },
                    leadingIcon = {
                        Icon(
                            if (showLabels) Icons.Default.Check else Icons.Default.Label,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // --- PASSWORD PROTECTION CARD ---
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isPasswordProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Password Protection",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPasswordProtected) "Secured with password encryption" else "Optional export encryption",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isPasswordProtected,
                        onCheckedChange = { onTogglePasswordProtection(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // --- ACTION BUTTONS: Print, Save, Share ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPrint,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview & Print", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onSaveToStorage,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save A4", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val uriHandler = LocalUriHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            uriHandler.openUri("https://azazmadkiya.morbi.store")
                        } catch (e: Exception) {
                            try {
                                uriHandler.openUri("http://azazmadkiya.morbi.store")
                            } catch (e2: Exception) {}
                        }
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Developed By ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Azazmadkiya",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun A4CardPreviewItem(
    label: String?,
    uri: Uri?,
    widthFraction: Float,
    showCutGuides: Boolean,
    isBw: Boolean,
    placeholderText: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(widthFraction),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label != null) {
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(85.6f / 53.98f) // Exact standard card ratio
                .clip(RoundedCornerShape(4.dp))
                .background(if (uri != null) Color.White else Color(0xFFF8FAFC))
                .then(
                    if (showCutGuides) {
                        Modifier.border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = label ?: "Document Card",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
