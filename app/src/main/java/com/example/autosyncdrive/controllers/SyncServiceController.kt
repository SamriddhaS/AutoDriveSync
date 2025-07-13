package com.example.autosyncdrive.controllers

import com.example.autosyncdrive.data.models.SyncResult
import com.example.autosyncdrive.utils.SyncForegroundService.Companion.SYNC_TYPE_MANUAL
import kotlinx.coroutines.flow.Flow

interface SyncServiceController {
    fun startSync(syncType: String = SYNC_TYPE_MANUAL)
    fun startSyncForSingleFile(documentId: String, syncType: String = SYNC_TYPE_MANUAL)
    fun retryFailedSync()
    fun stopSync()
    fun observeSyncResult(): Flow<SyncResult>
}