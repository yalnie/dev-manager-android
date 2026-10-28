package org.webosbrew.devmanager.ui.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.webosbrew.devmanager.data.model.RemoteFile
import org.webosbrew.devmanager.ui.common.ConfirmationDialog
import org.webosbrew.devmanager.ui.common.ErrorCard
import org.webosbrew.devmanager.ui.common.LoadingCard
import org.webosbrew.devmanager.ui.common.ProgressDialog
import org.webosbrew.devmanager.ui.common.StorageBar
import org.webosbrew.devmanager.ui.common.TopDeviceBar

@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onNavigateToDevices: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    var fileToDownload by remember { mutableStateOf<RemoteFile?>(null) }

    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast("/") ?: "uploaded_file"
            viewModel.uploadFile(it, name)
        }
    }

    val downloadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null && fileToDownload != null) {
            viewModel.downloadFile(fileToDownload!!, uri)
            fileToDownload = null
        }
    }

    LaunchedEffect(uiState.messageSnackbar) {
        uiState.messageSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopDeviceBar(
                currentDevice = uiState.currentDevice,
                devices = uiState.devices,
                onSelectDevice = { viewModel.selectDevice(it) },
                onAddDeviceClick = onNavigateToDevices,
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        enabled = uiState.canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { viewModel.navigateForward() },
                        enabled = uiState.canGoForward
                    ) {
                        Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(
                        onClick = { viewModel.loadFiles(uiState.currentPath) },
                        enabled = uiState.currentDevice != null
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        enabled = uiState.currentDevice != null
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                    }
                    IconButton(
                        onClick = { uploadPicker.launch("*/*") },
                        enabled = uiState.currentDevice != null
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Upload")
                    }
                    if (uiState.selectedFiles.isNotEmpty()) {
                        IconButton(onClick = { showConfirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            StorageBar(storageInfo = uiState.storageInfo)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Breadcrumb Bar
            BreadcrumbBar(
                parts = uiState.breadcrumbParts,
                onPartClick = { viewModel.navigateToBreadcrumbIndex(it) }
            )

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.currentDevice == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = onNavigateToDevices) {
                            Text("Select or Add Device")
                        }
                    }
                } else if (uiState.isLoading && uiState.files.isEmpty()) {
                    LoadingCard(text = "Listing directory ${uiState.currentPath}...")
                } else if (uiState.errorMessage != null && uiState.files.isEmpty()) {
                    ErrorCard(
                        title = "Unable to list directory",
                        message = uiState.errorMessage ?: "",
                        onRetry = { viewModel.loadFiles(uiState.currentPath) }
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.files, key = { it.path }) { file ->
                            val isSelected = uiState.selectedFiles.contains(file)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (file.isDirectory) {
                                            viewModel.navigateTo(file.path)
                                        } else {
                                            viewModel.toggleFileSelection(file)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleFileSelection(file) }
                                )

                                Icon(
                                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row {
                                        Text(
                                            text = file.permissions,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = file.formattedSize,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!file.isDirectory) {
                                    IconButton(
                                        onClick = {
                                            fileToDownload = file
                                            downloadPicker.launch(file.name)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Download",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Directory") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Directory Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.createDirectory(folderName.trim())
                            showNewFolderDialog = false
                        }
                    },
                    enabled = folderName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Delete Dialog
    if (showConfirmDelete) {
        val count = uiState.selectedFiles.size
        ConfirmationDialog(
            title = "Delete Files",
            message = "Are you sure you want to delete $count selected item(s)?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                showConfirmDelete = false
                viewModel.deleteSelectedFiles()
            },
            onDismiss = { showConfirmDelete = false }
        )
    }

    // Progress Dialog
    uiState.transferProgress?.let { (progress, status) ->
        ProgressDialog(
            title = "File Transfer",
            statusText = status,
            progress = progress
        )
    }
}

@Composable
private fun BreadcrumbBar(
    parts: List<String>,
    onPartClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        parts.forEachIndexed { index, part ->
            Text(
                text = part,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == parts.size - 1) FontWeight.Bold else FontWeight.Normal,
                color = if (index == parts.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPartClick(index) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (index < parts.size - 1) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
