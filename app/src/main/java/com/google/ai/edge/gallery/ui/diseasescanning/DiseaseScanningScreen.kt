/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.diseasescanning

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.common.chat.ModelSelector
import com.google.ai.edge.gallery.ui.common.ConfigDialog
import com.google.ai.edge.gallery.common.getBitmapFromUri
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseScanningScreen(
    viewModel: DiseaseScanningViewModel,
    modelManagerViewModel: ModelManagerViewModel,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scanResults = viewModel.scanResults
    val isScanning = viewModel.isScanning
    val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
    val currentModel = modelManagerUiState.selectedModel
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // State for configuration dialog
    var showConfigDialog by remember { mutableStateOf(false) }
    
    // State for image source selection dialog
    var showImageSourceDialog by remember { mutableStateOf(false) }
    
    // Create a file URI for camera capture
    val createImageUri = {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "DISEASE_SCAN_$timeStamp.jpg"
            val storageDir = File(context.cacheDir, "images")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val imageFile = File(storageDir, imageFileName)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                imageFile
            )
        } catch (e: Exception) {
            Log.e("DiseaseScanningScreen", "Error creating image URI: ${e.message}", e)
            null
        }
    }
    
    // Remember the camera URI
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    
    // Auto-scroll to bottom when new results are added
    LaunchedEffect(scanResults.size) {
        if (scanResults.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(scanResults.size - 1)
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            val bitmap = getBitmapFromUri(context, cameraUri!!)
            bitmap?.let { bitmapResult ->
                currentModel?.let { model ->
                    viewModel.scanImage(context, modelManagerViewModel, model, bitmapResult)
                }
            }
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, launch camera
            createImageUri()?.let { uri ->
                cameraUri = uri
                cameraLauncher.launch(uri)
            } ?: run {
                Toast.makeText(context, "Error creating image file", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Permission denied
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            val bitmap = getBitmapFromUri(context, imageUri)
            bitmap?.let { bitmapResult ->
                currentModel?.let { model ->
                    viewModel.scanImage(context, modelManagerViewModel, model, bitmapResult)
                }
            }
        }
    }
    
    // Function to show image source selection
    val showImagePicker = {
        showImageSourceDialog = true
    }
    
    // Function to launch camera with permission check
    val launchCamera = {
        when (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, launch camera
                createImageUri()?.let { uri ->
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                } ?: run {
                    Toast.makeText(context, "Error creating image file", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // Request permission
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }
    
    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = "Disease Scanning",
                leftAction = AppBarAction(
                    actionType = AppBarActionType.NAVIGATE_UP,
                    actionFn = navigateUp
                )
            )
        },
        floatingActionButton = {
            // Settings FAB when model is available
            currentModel?.let {
                FloatingActionButton(
                    onClick = { showConfigDialog = true },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Model Settings"
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (scanResults.isEmpty()) {
                // Initial state - show scan button
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { showImagePicker() },
                        modifier = Modifier
                            .size(200.dp, 80.dp),
                        enabled = !isScanning && currentModel != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Scan for diseases",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan for diseases",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Show results and scan again option
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(scanResults) { result ->
                        ScanResultItem(result = result)
                    }
                    
                    // Add new scan button at the bottom
                    item {
                        Button(
                            onClick = { showImagePicker() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = !isScanning && currentModel != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New scan",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isScanning) "Scanning..." else "New Scan",
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
            
            // Show model selection info
            currentModel?.let { model ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Using model: ${model.name}",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            } ?: run {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "No model selected. Please select a model first.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    // Configuration dialog
    if (showConfigDialog && currentModel != null) {
        ConfigDialog(
            title = "Model Configuration",
            configs = currentModel.configs,
            initialValues = currentModel.configValues,
            onDismissed = { showConfigDialog = false },
            onOk = { newConfigValues ->
                showConfigDialog = false
                // Update model configuration
                currentModel.configValues = newConfigValues
                Log.d("DiseaseScanningScreen", "Updated model configuration: $newConfigValues")
            }
        )
    }
    
    // Image source selection dialog
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Select Image Source") },
            text = { Text("Choose how you want to add an image for disease scanning") },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            // Launch camera with permission check
                            launchCamera()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera")
                    }
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            // Launch gallery
                            galleryLauncher.launch("image/*")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImageSourceDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScanResultItem(
    result: ScanResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Display the image
            Image(
                bitmap = result.image.asImageBitmap(),
                contentDescription = "Scanned image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Display the result
            if (result.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Analyzing image...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    text = "Analysis Result:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = result.result,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
