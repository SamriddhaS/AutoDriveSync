package com.example.autosyncdrive.data
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.autosyncdrive.data.localdb.FileInfo
import com.example.autosyncdrive.data.localdb.FileStoreDao
import com.example.autosyncdrive.utils.GoogleDriveHelper
import com.example.autosyncdrive.utils.StorageHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository to handle Google Drive operations
 */
class MainRepository(
    private val context: Context,
    private val fileStoreDao: FileStoreDao
) {
    private val googleDriveHelper = GoogleDriveHelper(context)
    private val storageHelper = StorageHelper(context)
    private val TAG = "MainRepository"

    // Get sign-in intent
    fun getSignInIntent() = googleDriveHelper.getSignInIntent()

    // Check if user is already signed in
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    // Handle sign-in result
    fun handleSignInResult(task: Task<GoogleSignInAccount>): GoogleSignInAccount? {
        return googleDriveHelper.handleSignInResult(task)
    }

    // Sign out
    fun signOut() {
        googleDriveHelper.googleSignInClient.signOut()
    }

    // Create a sample file
    suspend fun createSampleFile(): File {
        return googleDriveHelper.createSampleFile()
    }

    // Upload file to Google Drive
    suspend fun uploadFileToDrive(account: GoogleSignInAccount): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = createSampleFile()
            val fileId = googleDriveHelper.uploadFileToDrive(account, file)

            if (fileId != null) {
                Result.success(fileId)
            } else {
                Result.failure(Exception("Failed to upload file"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file", e)
            Result.failure(e)
        }
    }

    // NEW METHODS FOR STORAGE FUNCTIONALITY

    /**
     * Create directory picker intent
     */
    fun getDirectoryPickerIntent(): Intent {
        return storageHelper.createDirectoryPickerIntent()
    }

    /**
     * Process the result from directory picker
     */
    fun processDirectorySelection(uri: Uri?): Boolean {
        return storageHelper.processDirectorySelection(uri)
    }

    /**
     * Check if a directory has been selected
     */
    fun hasSelectedDirectory(): Boolean {
        return storageHelper.getSelectedDirectoryUri() != null
    }

    /**
     * Get the URI of the selected directory
     */
    fun getSelectedDirectoryUri(): Uri? {
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
    fun observeFiles(): Flow<List<FileInfo>> {
        return fileStoreDao.getAllFiles()
            .flowOn(Dispatchers.IO)
    }

    /**
     * Scan/Refresh files from storage and update cache
     */
    suspend fun scanDirectory() = withContext(Dispatchers.IO) {
        Log.d(TAG,"scanDirectory")
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
    fun clearFileCache() {
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                fileStoreDao.deleteAllFiles()
                Log.d(TAG, "File cache cleared")
            }
        }
    }

}