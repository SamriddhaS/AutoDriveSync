package com.example.autosyncdrive.data.models

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data class to hold file information
 */
@Entity(tableName = "file_cache")
data class FileInfo(
    @PrimaryKey
    val documentId:String,
    val name: String,
    val uri: Uri,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val isBackedUp:Boolean,
    // Sync-related fields
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val googleDriveFileId: String? = null,
    val lastSyncedAt: Long? = null,
    val syncRetryCount: Int = 0,
    val fileHash: String? = null
)


