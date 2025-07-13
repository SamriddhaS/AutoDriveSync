package com.example.autosyncdrive.data.repositories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.autosyncdrive.data.localdb.FileStoreDao
import com.example.autosyncdrive.data.models.FileInfo
import com.example.autosyncdrive.data.models.SyncStats
import com.example.autosyncdrive.data.models.SyncStatus
import com.example.autosyncdrive.utils.GoogleDriveHelper
import com.example.autosyncdrive.utils.StorageHelper
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository to handle Google Drive operations
 */
class SyncRepositoryImpl(
    private val context: Context,
    private val fileStoreDao: FileStoreDao,
    private val googleDriveHelper: GoogleDriveHelper,
    private val storageHelper: StorageHelper
):SyncRepository {

    //private val syncManager = SyncManager(context, fileStoreDao, googleDriveHelper)
    private val TAG = "MainRepository"

    // Get sign-in intent
    override fun getSignInIntent() = googleDriveHelper.getSignInIntent()

    // Check if user is already signed in
    override fun getLastSignedInAccount(): GoogleSignInAccount? {
        return googleDriveHelper.getGoogleDriveAccount()
    }

    // Handle sign-in result
    override fun handleSignInResult(task: Task<GoogleSignInAccount>): GoogleSignInAccount? {
        return googleDriveHelper.handleSignInResult(task)
    }

    // Sign out
    override fun signOut() {
        googleDriveHelper.googleSignInClient.signOut()
    }


    // NEW METHODS FOR STORAGE FUNCTIONALITY

    /**
     * Create directory picker intent
     */
    override fun getDirectoryPickerIntent(): Intent {
        return storageHelper.createDirectoryPickerIntent()
    }

    /**
     * Process the result from directory picker
     */
    override fun processDirectorySelection(uri: Uri?): Boolean {
        return storageHelper.processDirectorySelection(uri)
    }

    /**
     * Check if a directory has been selected
     */
    override fun hasSelectedDirectory(): Boolean {
        return storageHelper.getSelectedDirectoryUri() != null
    }

    /**
     * Get the URI of the selected directory
     */
    override fun getSelectedDirectoryUri(): Uri? {
        return storageHelper.getSelectedDirectoryUri()
    }

    /**
     * Scan the selected directory and emit files as a Flow
     */
//    fun scanDirectory(): Flow<List<FileInfo>> = flow {
//        val files = storageHelper.scanDirectory()
//        emit(files)
//    }.flowOn(Dispatchers.IO)

    /**
     * Alternative implementation using Room's Flow support
     * This will automatically update the UI whenever the database changes
     */
    override fun observeFiles(): Flow<List<FileInfo>> {
        return fileStoreDao.getAllFiles()
            .flowOn(Dispatchers.IO)
    }

    /**
     * Scan/Refresh files from storage and update cache
     */
    override suspend fun scanDirectory():Unit = withContext(Dispatchers.IO) {
        Log.d(TAG, "scanDirectory")
        try {
            val freshFiles = storageHelper.scanDirectory()
            updateFileCache(freshFiles)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing files", e)
        }
    }

    /**
     * Update the file cache with fresh data
     */
    private suspend fun updateFileCache(files: List<FileInfo>) = withContext(Dispatchers.IO) {
        try {
            fileStoreDao.refreshFiles(files)
            Log.d(TAG, "Updated cache with ${files.size} files")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating file cache", e)
        }
    }

    /**
     * Clear the file cache
     */
    override fun clearFileCache() {
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                fileStoreDao.deleteAllFiles()
                Log.d(TAG, "File cache cleared")
            }
        }
    }


    /**
     * Get sync statistics
     */
    override suspend fun getSyncStats(): SyncStats = withContext(Dispatchers.IO) {
        SyncStats(
            totalFiles = fileStoreDao.getFileCount(),
            pendingFiles = fileStoreDao.getPendingFileCount(),
            syncedFiles = fileStoreDao.getSyncedFileCount(),
            failedFiles = fileStoreDao.getFailedFileCount()
        )
    }

    /**
     * Observe sync queue
     */
    override fun observeSyncQueue(): Flow<List<FileInfo>> {
        return fileStoreDao.observeSyncQueue()
    }

    /**
     * Observe synced files
     */
    override fun observeSyncedFiles(): Flow<List<FileInfo>> {
        return fileStoreDao.observeSyncedFiles()
    }

    /**
     * Observe failed files
     */
    override fun observeFailedFiles(): Flow<List<FileInfo>> {
        return fileStoreDao.observeFailedFiles()
    }

    /**
     * Get files by specific sync status
     */
    override suspend fun getFilesByStatus(status: SyncStatus): List<FileInfo> = withContext(Dispatchers.IO) {
        return@withContext fileStoreDao.getFilesByStatus(status)
    }

    /**
     * Manually reset a file's sync status to pending
     * Useful for re-syncing specific files
     */
    override suspend fun resetFileToSync(fileInfo: FileInfo): Unit = withContext(Dispatchers.IO) {
        fileStoreDao.updateSyncStatus(fileInfo.documentId, SyncStatus.PENDING)
        Log.d(TAG, "Reset file to sync: ${fileInfo.name}")
    }

}