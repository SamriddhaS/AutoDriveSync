package com.example.autosyncdrive.data.models

data class SyncResult(
    val success: Boolean,
    val message: String,
    val syncedCount: Int,
    val failedCount: Int,
    val errors: List<String> = emptyList()
)
