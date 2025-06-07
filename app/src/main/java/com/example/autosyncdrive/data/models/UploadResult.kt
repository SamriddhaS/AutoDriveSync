package com.example.autosyncdrive.data.models

data class UploadResult(
    val status: UploadStatus,
    val fileId: String? = null,
    val errorMessage: String? = null
)
