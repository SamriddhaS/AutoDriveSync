package com.example.autosyncdrive.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.autosyncdrive.data.models.FileInfo
import com.example.autosyncdrive.utils.PermissionHandler
import com.example.autosyncdrive.utils.RequestStoragePermission
import com.example.autosyncdrive.viewmodels.MainViewModel
import com.example.autosyncdrive.viewmodels.SyncUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
    ) {

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val permissionHandler = remember { PermissionHandler(context) }

    // Permission request handler
    RequestStoragePermission(
        permissionHandler = permissionHandler,
        onPermissionResult = { granted ->
            if (granted && !uiState.storageState.hasSelectedDirectory) {
                // Permissions granted but no directory selected yet
                // Could auto-launch directory picker here if needed
            }
        }
    )

    // Directory picker launcher
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleDirectoryPickerResult(result.resultCode, result.data)
    }

    // Google Sign-in launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.resultCode, result.data)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        GoogleDriveTab(
            uiState = uiState.googleDriveState,
            onSignInClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
            onSignOutClick = { viewModel.signOut() },
            onUploadClick = { viewModel.startSync() },
        )

        LocalStorageTab(
            uiState = uiState.storageState,
            syncUiState = uiState.syncState,
            onSelectDirectoryClick = {
                if (permissionHandler.hasStoragePermissions()) {
                    directoryPickerLauncher.launch(viewModel.getDirectoryPickerIntent())
                } else {
                    // Launch app settings if permissions were denied
                    context.startActivity(permissionHandler.getAppSettingsIntent())
                }
            },
            onScanClick = { viewModel.scanSelectedDirectory() },
            onRetryFailedSync = { viewModel.retryFailedSync() }
        )
    }
}

@Composable
fun GoogleDriveTab(
    uiState: com.example.autosyncdrive.viewmodels.GoogleDriveUiState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onUploadClick: () -> Unit,
) {
    Column {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (uiState.isSignedIn) {
                Text(
                    text = "Drive Connected : ${uiState.account?.email}",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = onUploadClick) {
                        Text("Start Sync")
                    }

                    OutlinedButton(onClick = onSignOutClick) {
                        Text("Sign Out")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.uploadStatus != null) {
                    Text(
                        text = "Status: ${uiState.uploadStatus}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Not signed in to Google Drive",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = onSignInClick) {
                        Text("Sign In with Google")
                    }
                }
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Error: ${uiState.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun LocalStorageTab(
    uiState: com.example.autosyncdrive.viewmodels.StorageUiState,
    syncUiState: SyncUiState,
    onSelectDirectoryClick: () -> Unit,
    onScanClick: () -> Unit,
    onRetryFailedSync: () -> Unit
) {
    Column {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Directory selection status
            if (uiState.hasSelectedDirectory && uiState.directoryUri != null) {
                Text(
                    text = "Selected Directory:",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = uiState.directoryUri.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Button(
                        onClick = onScanClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan Directory"
                        )
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("Scan Directory")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onSelectDirectoryClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Change Directory"
                        )
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("Change Directory")
                    }
                }

                // Last scan time
                uiState.lastScanTime?.let { lastScanTime ->
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    val formattedDate = dateFormat.format(Date(lastScanTime))

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Last Scan: $formattedDate",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                syncUiState.let { syncState ->
                    syncState.isSyncing?.let {
                        Text(
                            text = "Is Syncing : $it",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    syncState.lastSyncTime?.let {
                        Text(
                            text = "Last Sync Time : $it",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    syncState.pendingFiles?.let {
                        Text(
                            text = "Pending File Count : ${it.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    syncState.syncedFiles?.let {
                        Text(
                            text = "Synced File Count : ${it.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    syncState.failedFiles?.let {
                        Text(
                            text = "Failed File Count : ${it.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    syncState.error?.let {
                        Text(
                            text = "Error : $it",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File list
                if (uiState.files.isNotEmpty()) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "Files (${uiState.files.size}):",
                            style = MaterialTheme.typography.titleMedium
                        )

                        if(syncUiState.failedFiles.isNotEmpty()){
                            Button(
                                onClick = onRetryFailedSync,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry Failed Sync")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn {
                        items(uiState.files) { fileInfo ->
                            FileItem(fileInfo)
                            Divider()
                        }
                    }
                } else {
                    Text(
                        text = "No files found in the directory",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // No directory selected yet
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No directory selected",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onSelectDirectoryClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Select Directory"
                        )
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text("Select Directory")
                    }
                }
            }

            // Error message
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Error: ${uiState.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun FileItem(fileInfo: FileInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = fileInfo.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Type: ${fileInfo.mimeType ?: "Unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatFileSize(fileInfo.size),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Last modified date if available
            fileInfo.lastModified?.let { lastModified ->
                val dateFormat = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(lastModified))

                Text(
                    text = "Modified: $formattedDate",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "Backup State: ${fileInfo.syncStatus}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Format file size in human-readable format
 */
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()

    return String.format(
        "%.1f %s",
        size / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}