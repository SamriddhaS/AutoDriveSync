package com.example.autosyncdrive.data.models

enum class SyncStatus {
    PENDING,    // File needs to be synced
    IN_PROGRESS, // Currently being uploaded
    SYNCED,     // Successfully synced
    FAILED      // Failed to sync
}

enum class UploadStatus {
    SUCCESS,
    DUPLICATE_NAME,
    NETWORK_ERROR,
    AUTH_ERROR,
    OTHER_ERROR
}