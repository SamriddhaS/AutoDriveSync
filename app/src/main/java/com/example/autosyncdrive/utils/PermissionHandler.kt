package com.example.autosyncdrive.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Class to handle storage permissions
 */
class PermissionHandler(private val context: Context) {

    /**
     * Check if the app has storage permissions
     * @return true if all required permissions are granted, false otherwise
     */
    fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            hasPermissions(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            // Android 12 and below
            hasPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Check if all given permissions are granted
     */
    private fun hasPermissions(vararg permissions: String): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get the permissions needed for storage access based on API level
     */
    fun getRequiredStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            // Android 12 and below
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Create intent to app settings
     */
    fun getAppSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        return intent
    }
}

/**
 * Composable to handle storage permission request
 */
@Composable
fun RequestStoragePermission(
    permissionHandler: PermissionHandler,
    onPermissionResult: (Boolean) -> Unit
): Boolean {
    var permissionsGranted by remember { mutableStateOf(permissionHandler.hasStoragePermissions()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        // Consider permissions granted only if all requested permissions are granted
        val allGranted = permissionsMap.values.all { it }
        permissionsGranted = allGranted
        onPermissionResult(allGranted)
    }

    DisposableEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(permissionHandler.getRequiredStoragePermissions())
        } else {
            onPermissionResult(true)
        }

        onDispose { }
    }

    return permissionsGranted
}