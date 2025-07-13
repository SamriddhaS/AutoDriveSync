package com.example.autosyncdrive.data.repositories

import android.content.Intent
import android.net.Uri
import com.example.autosyncdrive.data.models.FileInfo
import com.example.autosyncdrive.data.models.SyncStats
import com.example.autosyncdrive.data.models.SyncStatus
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    // Authentication
    fun getSignInIntent(): Intent
    fun getLastSignedInAccount(): GoogleSignInAccount?
    fun handleSignInResult(task: Task<GoogleSignInAccount>): GoogleSignInAccount?
    fun signOut()

    // Directory Operations
    fun getDirectoryPickerIntent(): Intent
    fun processDirectorySelection(uri: Uri?): Boolean
    fun hasSelectedDirectory(): Boolean
    fun getSelectedDirectoryUri(): Uri?

    // File Operations
    fun observeFiles(): Flow<List<FileInfo>>
    suspend fun scanDirectory()
    fun clearFileCache()

    // Sync Operations
    suspend fun getSyncStats(): SyncStats
    fun observeSyncQueue(): Flow<List<FileInfo>>
    fun observeSyncedFiles(): Flow<List<FileInfo>>
    fun observeFailedFiles(): Flow<List<FileInfo>>
    suspend fun getFilesByStatus(status: SyncStatus): List<FileInfo>
    suspend fun resetFileToSync(fileInfo: FileInfo)
}