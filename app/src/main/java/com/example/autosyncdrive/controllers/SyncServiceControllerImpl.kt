package com.example.autosyncdrive.controllers

import android.content.Context
import com.example.autosyncdrive.data.models.SyncResult
import com.example.autosyncdrive.utils.SyncForegroundService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

class SyncServiceControllerImpl (
    private val context: Context
) : SyncServiceController {

    override fun startSync(syncType: String) {
        SyncForegroundService.startSync(context, syncType)
    }

    override fun startSyncForSingleFile(documentId: String, syncType: String) {
        SyncForegroundService.startSyncForSingleFile(context, syncType, documentId)
    }

    override fun retryFailedSync() {
        SyncForegroundService.retryFailedSync(context)
    }

    override fun stopSync() {
        SyncForegroundService.stopSync(context)
    }

    override fun observeSyncResult(): Flow<SyncResult> {
        return SyncForegroundService.syncResult.filterNotNull()
    }
}