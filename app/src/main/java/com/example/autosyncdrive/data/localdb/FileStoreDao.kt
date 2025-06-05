package com.example.autosyncdrive.data.localdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileStoreDao {
    @Query("SELECT * FROM file_cache")
    fun getAllFiles(): Flow<List<FileInfo>>

    @Query("SELECT * FROM file_cache")
    suspend fun getAllFilesList(): List<FileInfo>

    @Query("SELECT * FROM file_cache WHERE syncStatus = :status")
    suspend fun getFilesByStatus(status: SyncStatus): List<FileInfo>

    @Query("SELECT * FROM file_cache WHERE syncStatus = :status")
    fun observeFilesByStatus(status: SyncStatus): Flow<List<FileInfo>>

    // Get sync queue (pending files)
    @Query("SELECT * FROM file_cache WHERE syncStatus = 'PENDING' ORDER BY lastModified ASC")
    suspend fun getSyncQueue(): List<FileInfo>

    @Query("SELECT * FROM file_cache WHERE syncStatus = 'PENDING' ORDER BY lastModified ASC")
    fun observeSyncQueue(): Flow<List<FileInfo>>

    @Query("SELECT * FROM file_cache WHERE syncStatus = 'SYNCED'")
    fun observeSyncedFiles(): Flow<List<FileInfo>>

    @Query("SELECT * FROM file_cache WHERE syncStatus = 'FAILED'")
    fun observeFailedFiles(): Flow<List<FileInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileInfo>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileInfo)

    @Update
    suspend fun updateFile(file: FileInfo)

    @Query("UPDATE file_cache SET syncStatus = :status WHERE documentId = :documentId")
    suspend fun updateSyncStatus(documentId: String, status: SyncStatus)

    @Query("UPDATE file_cache SET syncStatus = :status, googleDriveFileId = :driveFileId, lastSyncedAt = :syncTime WHERE documentId = :documentId")
    suspend fun updateSyncSuccess(documentId: String, status: SyncStatus, driveFileId: String, syncTime: Long)

    @Query("UPDATE file_cache SET syncStatus = :status, syncRetryCount = syncRetryCount + 1 WHERE documentId = :documentId")
    suspend fun updateSyncFailure(documentId: String, status: SyncStatus)

    @Query("DELETE FROM file_cache WHERE documentId NOT IN (:existingFilePaths)")
    suspend fun deleteNonExistentFiles(existingFilePaths: List<String>)

    @Query("DELETE FROM file_cache")
    suspend fun deleteAllFiles()

//    @Transaction
//    suspend fun refreshFiles(files: List<FileInfo>) {
//        deleteAllFiles()
//        insertFiles(files)
//    }

    @Query("SELECT COUNT(*) FROM file_cache")
    suspend fun getFileCount(): Int

    @Query("SELECT COUNT(*) FROM file_cache WHERE syncStatus = 'PENDING'")
    suspend fun getPendingFileCount(): Int

    @Query("SELECT COUNT(*) FROM file_cache WHERE syncStatus = 'SYNCED'")
    suspend fun getSyncedFileCount(): Int

    @Query("SELECT COUNT(*) FROM file_cache WHERE syncStatus = 'FAILED'")
    suspend fun getFailedFileCount(): Int

    @Transaction
    suspend fun refreshFiles(newFiles: List<FileInfo>) {
        val existingFiles = getAllFilesList()
        val existingFilesMap = existingFiles.associateBy { it.documentId }

        val filesToInsertOrUpdate = mutableListOf<FileInfo>()

        for (newFile in newFiles) {
            val existingFile = existingFilesMap[newFile.documentId]

            if (existingFile == null) {
                // New file - add with PENDING status
                filesToInsertOrUpdate.add(newFile.copy(syncStatus = SyncStatus.PENDING))
            } else {
                // Existing file - check if modified
                if (hasFileChanged(existingFile, newFile)) {
                    // File modified - reset to PENDING status
                    filesToInsertOrUpdate.add(
                        newFile.copy(
                            syncStatus = SyncStatus.PENDING,
                            googleDriveFileId = null,
                            lastSyncedAt = null,
                            syncRetryCount = 0
                        )
                    )
                } else {
                    // File unchanged - keep existing sync status
                    filesToInsertOrUpdate.add(
                        newFile.copy(
                            syncStatus = existingFile.syncStatus,
                            googleDriveFileId = existingFile.googleDriveFileId,
                            lastSyncedAt = existingFile.lastSyncedAt,
                            syncRetryCount = existingFile.syncRetryCount,
                            fileHash = existingFile.fileHash
                        )
                    )
                }
            }
        }

        // Delete files that no longer exist
        val newFileIds = newFiles.map { it.documentId }
        deleteNonExistentFiles(newFileIds)

        // Insert/Update all files
        insertFiles(filesToInsertOrUpdate)
    }

    // Helper function to detect file changes
    private fun hasFileChanged(existingFile: FileInfo, newFile: FileInfo): Boolean {
        return existingFile.lastModified != newFile.lastModified ||
                existingFile.size != newFile.size ||
                existingFile.fileHash != newFile.fileHash
    }
}