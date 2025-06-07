package com.example.autosyncdrive.data.models

data class SyncStats(
    val totalFiles: Int,
    val pendingFiles: Int,
    val syncedFiles: Int,
    val failedFiles: Int
)
