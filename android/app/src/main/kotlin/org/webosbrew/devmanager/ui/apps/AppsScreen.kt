package org.webosbrew.devmanager.ui.apps

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.webosbrew.devmanager.data.model.PackageInfo
import org.webosbrew.devmanager.data.model.RepoPackage
import org.webosbrew.devmanager.ui.common.ConfirmationDialog
import org.webosbrew.devmanager.ui.common.ErrorCard
import org.webosbrew.devmanager.ui.common.LoadingCard
import org.webosbrew.devmanager.ui.common.ProgressDialog
import org.webosbrew.devmanager.ui.common.StorageBar
import org.webosbrew.devmanager.ui.common.TopDeviceBar

@Composable
fun AppsScreen(
    viewModel: AppsViewModel,
    onNavigateToDevices: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedRepoAppForDetails by remember { mutableStateOf<RepoPackage?>(null) }
    var appToUninstall by remember { mutableStateOf<PackageInfo?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.installLocalIpk(it) }
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
                    Button(
                        onClick = { filePicker.launch("*/*") },
                        enabled = uiState.currentDevice != null,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install IPK")
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
            TabRow(selectedTabIndex = uiState.currentTab.ordinal) {
                Tab(
                    selected = uiState.currentTab == AppsTab.INSTALLED,
                    onClick = { viewModel.selectTab(AppsTab.INSTALLED) },
                    text = { Text("Installed (${uiState.installedApps.size})") }
                )
                Tab(
                    selected = uiState.currentTab == AppsTab.AVAILABLE,
                    onClick = { viewModel.selectTab(AppsTab.AVAILABLE) },
                    text = { Text("Available (${uiState.repoApps.size})") }
                )
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.currentDevice == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No device selected", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onNavigateToDevices) {
                                Text("Add or Select Device")
                            }
                        }
                    }
                } else if (uiState.isLoading && uiState.installedApps.isEmpty()) {
                    LoadingCard(text = "Loading apps from TV...")
                } else if (uiState.errorMessage != null && uiState.installedApps.isEmpty()) {
                    ErrorCard(
                        title = "Unable to load apps",
                        message = uiState.errorMessage ?: "",
                        onRetry = { viewModel.refresh() }
                    )
                } else {
                    when (uiState.currentTab) {
                        AppsTab.INSTALLED -> {
                            val filtered = uiState.installedApps.filter {
                                it.title.contains(uiState.searchQuery, ignoreCase = true) ||
                                        it.id.contains(uiState.searchQuery, ignoreCase = true)
                            }
                            InstalledAppsList(
                                apps = filtered,
                                repoApps = uiState.repoApps,
                                onLaunch = { viewModel.launchApp(it) },
                                onClose = { viewModel.closeApp(it) },
                                onUninstall = { appToUninstall = it },
                                onUpdate = { repoApp -> viewModel.installRepoApp(repoApp) }
                            )
                        }
                        AppsTab.AVAILABLE -> {
                            val installedMap = uiState.installedApps.associateBy { it.id }
                            val filtered = uiState.repoApps.filter {
                                it.title.contains(uiState.searchQuery, ignoreCase = true) ||
                                        (it.summary?.contains(uiState.searchQuery, ignoreCase = true) == true) ||
                                        it.id.contains(uiState.searchQuery, ignoreCase = true)
                            }
                            AvailableAppsList(
                                apps = filtered,
                                installedApps = installedMap,
                                onAppClick = { selectedRepoAppForDetails = it },
                                onInstall = { viewModel.installRepoApp(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // App Details Dialog
    selectedRepoAppForDetails?.let { repoApp ->
        val installed = uiState.installedApps.firstOrNull { it.id == repoApp.id }
        AppDetailsDialog(
            app = repoApp,
            isInstalled = installed != null,
            installedVersion = installed?.version,
            onInstall = { viewModel.installRepoApp(repoApp) },
            onDismiss = { selectedRepoAppForDetails = null }
        )
    }

    // Confirm Uninstall Dialog
    appToUninstall?.let { pkg ->
        ConfirmationDialog(
            title = "Uninstall App",
            message = "Are you sure you want to uninstall \"${pkg.title}\"?",
            confirmText = "Uninstall",
            isDestructive = true,
            onConfirm = {
                val id = pkg.id
                appToUninstall = null
                viewModel.removeApp(id)
            },
            onDismiss = { appToUninstall = null }
        )
    }

    // Install Progress Dialog
    uiState.installProgress?.let { (progress, status) ->
        ProgressDialog(
            title = "Installing Package",
            statusText = status,
            progress = progress
        )
    }
}

@Composable
private fun InstalledAppsList(
    apps: List<PackageInfo>,
    repoApps: List<RepoPackage>,
    onLaunch: (String) -> Unit,
    onClose: (String) -> Unit,
    onUninstall: (PackageInfo) -> Unit,
    onUpdate: (RepoPackage) -> Unit
) {
    val repoMap = remember(repoApps) { repoApps.associateBy { it.id } }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps, key = { it.id }) { app ->
            val matchingRepo = repoMap[app.id]
            val hasUpdate = matchingRepo != null && matchingRepo.version != app.version

            var menuExpanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunch(app.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                if (!app.iconUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = app.iconUri,
                        contentDescription = app.title,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "v${app.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasUpdate && matchingRepo != null) {
                            Text(
                                text = " › v${matchingRepo.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (hasUpdate && matchingRepo != null) {
                    Button(
                        onClick = { onUpdate(matchingRepo) },
                        modifier = Modifier.padding(end = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Update", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Launch") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onLaunch(app.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Close") },
                            leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onClose(app.id)
                            }
                        )
                        if (app.removable) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Uninstall", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onUninstall(app)
                                }
                            )
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun AvailableAppsList(
    apps: List<RepoPackage>,
    installedApps: Map<String, PackageInfo>,
    onAppClick: (RepoPackage) -> Unit,
    onInstall: (RepoPackage) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps, key = { it.id }) { app ->
            val installed = installedApps[app.id]
            val isInstalled = installed != null
            val hasUpdate = isInstalled && installed?.version != app.version

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAppClick(app) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!app.iconUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = app.iconUri,
                        contentDescription = app.title,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.summary ?: "v${app.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { onInstall(app) },
                    colors = if (hasUpdate) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    } else if (isInstalled) {
                        ButtonDefaults.filledTonalButtonColors()
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            hasUpdate -> "Update"
                            isInstalled -> "Reinstall"
                            else -> "Install"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
