package com.example.autosyncdrive.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.autosyncdrive.data.localdb.FileStoreDao
import com.example.autosyncdrive.data.models.FileInfo
import com.example.autosyncdrive.data.models.SyncResult
import com.example.autosyncdrive.data.models.SyncStatus
import com.example.autosyncdrive.data.models.UploadStatus
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class SyncManager(
    private val context: Context,
    private val fileStoreDao: FileStoreDao,
    private val googleDriveHelper: GoogleDriveHelper
) {
    private val TAG = "SyncManager"

    /**
     * Check network availability
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Calculate file hash for change detection
     */
    private fun calculateFileHashFromUri(uri: Uri): String {
        val digest = MessageDigest.getInstance("MD5")
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun syncPendingFiles(account:GoogleSignInAccount): SyncResult = withContext(Dispatchers.IO){
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network available, skipping sync")
            return@withContext SyncResult(
                success = false,
                message = "No network connection available",
                syncedCount = 0,
                failedCount = 0
            )
        }

        val pendingFiles = fileStoreDao.getSyncQueue()
        Log.d(TAG, "Starting sync for ${pendingFiles.size} pending files")

        var syncedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()

        for (file in pendingFiles) {
            try {
                val result = syncSingleFile(account, file,"TestFolder")
                if (result) {
                    syncedCount++
                    Log.d(TAG, "Successfully synced: ${file.name}")
                } else {
                    failedCount++
                    errors.add("Failed to sync: ${file.name}")
                    Log.e(TAG, "Failed to sync: ${file.name}")
                }
            } catch (e: Exception) {
                failedCount++
                val errorMsg = "Error syncing ${file.name}: ${e.message}"
                errors.add(errorMsg)
                Log.e(TAG, errorMsg, e)
            }
        }

        val success = failedCount == 0
        val message = if (success) {
            "Successfully synced $syncedCount files"
        } else {
            "Synced $syncedCount files, $failedCount failed"
        }

        SyncResult(
            success = success,
            message = message,
            syncedCount = syncedCount,
            failedCount = failedCount,
            errors = errors
        )

    }

    private suspend fun syncSingleFile(account: GoogleSignInAccount, fileInfo: FileInfo,folderName:String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Update status to IN_PROGRESS
            fileStoreDao.updateSyncStatus(fileInfo.documentId, SyncStatus.IN_PROGRESS)

            // Handle content URI properly instead of creating File object
            val contentResolver = context.contentResolver

            // Check if the document still exists using DocumentFile or ContentResolver
            try {
                contentResolver.openInputStream(fileInfo.uri)?.use { inputStream ->
                    Log.d(TAG, "File accessible via content URI: ${fileInfo.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "File not accessible: ${fileInfo.name}, URI: ${fileInfo.uri}")
                fileStoreDao.updateSyncFailure(fileInfo.documentId, SyncStatus.FAILED)
                return@withContext false
            }

            // Calculate file hash for future change detection
            val fileHash = calculateFileHashFromUri(uri = fileInfo.uri)

            // Upload to Google Drive with duplicate handling
            val driveFileId = uploadWithDuplicateHandling(account, fileInfo.uri, fileInfo.name,folderName)

            if (driveFileId != null) {

                // Update as successfully synced
                fileStoreDao.updateSyncSuccess(
                    documentId = fileInfo.documentId,
                    status = SyncStatus.SYNCED,
                    driveFileId = driveFileId,
                    syncTime = System.currentTimeMillis(),
                    fileHash = fileHash
                )

//                // Update file hash
//                val updatedFile = fileInfo.copy(fileHash = fileHash)
//                fileStoreDao.updateFile(updatedFile)

                return@withContext true
            } else {
                // Mark as failed
                fileStoreDao.updateSyncFailure(fileInfo.documentId, SyncStatus.FAILED)
                return@withContext false
            }

        }
        catch (e: Exception) {
            Log.e(TAG, "Error syncing file: ${fileInfo.name}", e)
            fileStoreDao.updateSyncFailure(fileInfo.documentId, SyncStatus.FAILED)
            return@withContext false
        }
    }

    /**
     * Upload file with duplicate name handling
     */
    private suspend fun uploadWithDuplicateHandling(
        account: GoogleSignInAccount,
        uri: Uri,
        originalName: String,
        folderName:String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // First, try to upload with original name
            val result = googleDriveHelper.uploadFileToDriveWithName(account, uri, originalName,folderName)

            when (result.status) {
                UploadStatus.SUCCESS -> {
                    Log.d(TAG, "Successfully uploaded file: $originalName")
                    return@withContext result.fileId
                }

                UploadStatus.DUPLICATE_NAME -> {
                    Log.d(TAG, "File name '$originalName' already exists, trying with unique name")
                    return@withContext uploadWithUniqueName(account, uri = uri, originalName,folderName)
                }

                UploadStatus.NETWORK_ERROR -> {
                    Log.e(TAG, "Network error while uploading file: $originalName")
                    return@withContext null
                }

                UploadStatus.AUTH_ERROR -> {
                    Log.e(TAG, "Authentication error while uploading file: $originalName")
                    return@withContext null
                }

                UploadStatus.OTHER_ERROR -> {
                    Log.e(TAG, "Unknown error while uploading file: $originalName")
                    return@withContext null
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception while uploading file: $originalName", e)
            return@withContext null
        }
    }

    /**
     * Upload file with unique name (only called when we know there's a duplicate)
     */
    private suspend fun uploadWithUniqueName(
        account: GoogleSignInAccount,
        uri: Uri,
        originalName: String,
        folderName:String
    ): String? = withContext(Dispatchers.IO) {
        val nameWithoutExtension = originalName.substringBeforeLast(".")
        val extension = if (originalName.contains(".")) {
            ".${originalName.substringAfterLast(".")}"
        } else {
            ""
        }

        // Try up to 10 variations only (much safer limit)
        for (copyNumber in 1..10) {
            try {
                val fileName = "${nameWithoutExtension}_copy${copyNumber}${extension}"
                val result = googleDriveHelper.uploadFileToDriveWithName(account, uri, fileName,folderName)

                when (result.status) {
                    UploadStatus.SUCCESS -> {
                        Log.d(TAG, "Successfully uploaded file with unique name: $fileName")
                        return@withContext result.fileId
                    }

                    UploadStatus.DUPLICATE_NAME -> {
                        Log.d(TAG, "Name '$fileName' also exists, trying next variation")
                        continue // Try next variation
                    }

                    else -> {
                        // For any other error (network, auth, etc.), don't continue trying
                        Log.e(TAG, "Upload failed with error: ${result.status} for file: $fileName")
                        return@withContext null
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception while uploading with unique name", e)
                return@withContext null
            }
        }

        Log.e(TAG, "Could not find unique name after 10 attempts for file: $originalName")
        return@withContext null
    }

    /**
     * Retry failed files
     */
    suspend fun retryFailedFiles(account: GoogleSignInAccount): SyncResult = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext SyncResult(
                success = false,
                message = "No network connection available",
                syncedCount = 0,
                failedCount = 0
            )
        }

        val failedFiles = fileStoreDao.getFilesByStatus(SyncStatus.FAILED)
        Log.d(TAG, "Retrying ${failedFiles.size} failed files")

        // Reset failed files to pending
        for (file in failedFiles) {
            fileStoreDao.updateSyncStatus(file.documentId, SyncStatus.PENDING)
        }

        // Sync them again
        return@withContext syncPendingFiles(account)
    }

}