package com.example.autosyncdrive.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autosyncdrive.MainActivity
import com.example.autosyncdrive.R
import com.example.autosyncdrive.data.localdb.FileStoreDao
import com.example.autosyncdrive.data.models.SyncResult
import com.example.autosyncdrive.data.models.SyncStats
import com.example.autosyncdrive.data.models.SyncStatus
import com.example.autosyncdrive.di.DIModule
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SyncForegroundService:Service() {
    companion object {
        private const val TAG = "SyncForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sync_channel"
        private const val CHANNEL_NAME = "File Sync"

        // Actions
        const val ACTION_START_SYNC = "com.example.autosyncdrive.START_SYNC"
        const val ACTION_START_SINGLE_SYNC = "com.example.autosyncdrive.START_SINGLE_SYNC"
        const val ACTION_RETRY_FAILED = "com.example.autosyncdrive.RETRY_FAILED"
        const val ACTION_STOP_SYNC = "com.example.autosyncdrive.STOP_SYNC"

        // Extras
        const val EXTRA_SYNC_TYPE = "sync_type"
        const val EXTRA_DOCUMENT_ID = "document_id"
        const val SYNC_TYPE_MANUAL = "manual"
        const val SYNC_TYPE_AUTO = "auto"

        fun startSync(context: Context, syncType: String = SYNC_TYPE_MANUAL) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_START_SYNC
                putExtra(EXTRA_SYNC_TYPE, syncType)
            }
            context.startForegroundService(intent)
        }

        fun startSyncForSingleFile(context: Context, syncType: String = SYNC_TYPE_MANUAL,documentId:String) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_START_SINGLE_SYNC
                putExtra(EXTRA_SYNC_TYPE, syncType)
                putExtra(EXTRA_DOCUMENT_ID, documentId)
            }
            context.startForegroundService(intent)
        }

        fun retryFailedSync(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_RETRY_FAILED
                putExtra(EXTRA_SYNC_TYPE, SYNC_TYPE_MANUAL)
            }
            context.startForegroundService(intent)
        }

        fun stopSync(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_STOP_SYNC
            }
            context.startService(intent)
        }

        private val _syncResult = MutableStateFlow<SyncResult?>(null) // Or MutableSharedFlow
        val syncResult: StateFlow<SyncResult?> = _syncResult.asStateFlow() // Or .asSharedFlow()
    }

    private lateinit var notificationManager: NotificationManager
    private var serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var currentSyncJob: Job? = null
    private var googleSignInAccount: GoogleSignInAccount? = null
    private var fileStoreDao: FileStoreDao? = null
    private var googleDriveHelper:GoogleDriveHelper? = null
    private var storageHelper : StorageHelper? = null
    private var syncManager : SyncManager? = null

    private var totalFiles = 0
    private var syncedFiles = 0
    private var failedFiles = 0
    private var currentFileName = ""
    private var isSyncing = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        fileStoreDao = DIModule.provideFileInfoDao(this)
        googleDriveHelper = DIModule.provideGoogleDriveHelper(this)
        storageHelper = DIModule.provideStorageHelper(this)
        syncManager = DIModule.provideSyncManager(this)
        googleSignInAccount = googleDriveHelper?.getGoogleDriveAccount()

        if (googleSignInAccount == null) {
            Log.e(TAG, "No signed-in account found")
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_SYNC -> {
                val syncType = intent.getStringExtra(EXTRA_SYNC_TYPE) ?: SYNC_TYPE_MANUAL
                startSyncOperation(syncType)
            }
            ACTION_START_SINGLE_SYNC -> {
                val syncType = intent.getStringExtra(EXTRA_SYNC_TYPE) ?: SYNC_TYPE_MANUAL
                val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID) ?: ""
                startSingleSyncOperation(syncType,documentId)
            }
            ACTION_RETRY_FAILED -> {
                retryFailedOperation()
            }
            ACTION_STOP_SYNC -> {
                stopSyncOperation()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows file sync progress"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startSyncOperation(syncType: String) {
        if (isSyncing){
            Log.d(TAG,"Sync Already In Progress")
            return
        }

        googleSignInAccount?.let { googleSignInAccount ->
            isSyncing = true

            startForeground(NOTIFICATION_ID, createInitialNotification(syncType))
            currentSyncJob = serviceScope.launch {
                try {
                    val stats = SyncStats(
                        totalFiles = fileStoreDao?.getFileCount()?:0,
                        pendingFiles = fileStoreDao?.getPendingFileCount()?:0,
                        syncedFiles = fileStoreDao?.getSyncedFileCount()?:0,
                        failedFiles = fileStoreDao?.getFailedFileCount()?:0
                    )
                    totalFiles = stats.pendingFiles
                    syncedFiles = 0
                    failedFiles = 0

                    Log.d(TAG, "Starting sync for $totalFiles files")

                    if (totalFiles == 0) {
                        showCompletionNotification("No files to sync", syncType)
                        stopSelf()
                        return@launch
                    }

                    // Start observing sync progress
                    observeSyncProgress()

                    // Start the actual sync
                    syncManager?.syncPendingFiles(googleSignInAccount)?.let {
                        handleSyncResult(it, syncType)
                        _syncResult.value = it
                    }

                }catch (e: Exception) {
                    Log.e(TAG, "Error during sync", e)
                    showErrorNotification("Sync failed: ${e.message}")
                    stopSelf()
                } finally {
                    isSyncing = false
                }
            }
        } ?: run {
            Log.e(TAG, "No Google account available")
            stopSelf()
        }
    }

    private fun startSingleSyncOperation(syncType: String,documentId: String) {
        if (isSyncing){
            Log.d(TAG,"Sync Already In Progress")
            return
        }

        googleSignInAccount?.let { googleSignInAccount ->
            isSyncing = true

            startForeground(NOTIFICATION_ID, createInitialNotification(syncType))
            currentSyncJob = serviceScope.launch {
                try {
                    val stats = SyncStats(
                        totalFiles = fileStoreDao?.getFileCount()?:0,
                        pendingFiles = fileStoreDao?.getPendingFileCount()?:0,
                        syncedFiles = fileStoreDao?.getSyncedFileCount()?:0,
                        failedFiles = fileStoreDao?.getFailedFileCount()?:0
                    )
                    totalFiles = stats.pendingFiles
                    syncedFiles = 0
                    failedFiles = 0

                    Log.d(TAG, "Starting sync for $totalFiles files")

                    if (totalFiles == 0) {
                        showCompletionNotification("No files to sync", syncType)
                        stopSelf()
                        return@launch
                    }

                    val file = fileStoreDao?.getSingleSyncFile(documentId = documentId)
                    showSingleNotification(file?.name?:"Name not found")

                    // Start the actual sync
                    syncManager?.syncSingleFileUsingDocumentId(documentId,googleSignInAccount)?.let {
                        handleSyncResult(it, syncType)
                        _syncResult.value = it
                    }

                }catch (e: Exception) {
                    Log.e(TAG, "Error during sync", e)
                    showErrorNotification("Sync failed: ${e.message}")
                    stopSelf()
                } finally {
                    isSyncing = false
                }
            }
        } ?: run {
            Log.e(TAG, "No Google account available")
            stopSelf()
        }
    }

    private fun handleSyncResult(result: SyncResult, syncType: String) {
        syncedFiles = result.syncedCount
        failedFiles = result.failedCount

        if (result.success) {
            showCompletionNotification("Sync completed: ${result.syncedCount} files uploaded", syncType)
        } else {
            showCompletionNotification(
                "Sync finished: ${result.syncedCount} uploaded, ${result.failedCount} failed",
                syncType
            )
        }

        // Stop the service after a delay to let user see the completion
        serviceScope.launch {
            kotlinx.coroutines.delay(5000)
            stopSelf()
        }
    }

    private suspend fun observeSyncProgress() {
        serviceScope.launch {
            fileStoreDao?.observeSyncQueue()?.collect { pendingFiles ->
                val currentPending = pendingFiles.size
                val currentSynced = totalFiles - currentPending

                if (currentSynced != syncedFiles) {
                    syncedFiles = currentSynced
                    updateProgressNotification()
                }
            }
        }
    }

    private fun updateProgressNotification() {
        val progress = if (totalFiles > 0) {
            ((syncedFiles.toFloat() / totalFiles) * 100).toInt()
        } else 0

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Syncing Files")
            .setContentText("$syncedFiles of $totalFiles files synced")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(totalFiles, syncedFiles, false)
            .addAction(createStopAction())
            .setContentIntent(createContentIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showSingleNotification(fileName:String) {

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Syncing In Progress")
            .setContentText("File Name : $fileName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(createStopAction())
            .setContentIntent(createContentIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(message: String, syncType: String) {
        val title = if (syncType == SYNC_TYPE_AUTO) "Auto Sync Complete" else "Manual Sync Complete"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Different icon for completion
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())
            .apply {
                if (failedFiles > 0) {
                    addAction(createRetryAction())
                }
            }
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createInitialNotification(syncType: String): Notification? {
        val title = if (syncType == SYNC_TYPE_AUTO) "Auto Sync" else "Manual Sync"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Preparing to sync files...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure you have this icon
            .setOngoing(true)
            .setProgress(0, 0, true) // Indeterminate progress
            .addAction(createStopAction())
            .setContentIntent(createContentIntent())
            .build()
    }

    private fun createStopAction(): NotificationCompat.Action {
        val stopIntent = Intent(this, SyncForegroundService::class.java).apply {
            action = ACTION_STOP_SYNC
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Stop",
            stopPendingIntent
        ).build()
    }

    private fun showErrorNotification(s: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sync Error")
            .setContentText(s)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createRetryAction(): NotificationCompat.Action {
        val retryIntent = Intent(this, SyncForegroundService::class.java).apply {
            action = ACTION_RETRY_FAILED
        }
        val retryPendingIntent = PendingIntent.getService(
            this, 0, retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Retry",
            retryPendingIntent
        ).build()
    }

    private fun retryFailedOperation() {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress")
            return
        }

        googleSignInAccount?.let { account ->
            isSyncing = true
            startForeground(NOTIFICATION_ID, createInitialNotification(SYNC_TYPE_MANUAL))

            currentSyncJob = serviceScope.launch {
                try {
                    val result = syncManager?.retryFailedFiles(account)?.let {
                        handleSyncResult(it, SYNC_TYPE_MANUAL)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during retry", e)
                    showErrorNotification("Retry failed: ${e.message}")
                    stopSelf()
                } finally {
                    isSyncing = false
                }
            }
        }
    }

    private fun stopSyncOperation() {
        Log.d(TAG, "Stopping sync operation")
        currentSyncJob?.cancel()
        isSyncing = false

        runBlocking(serviceScope.coroutineContext) {
            val failedFiles = fileStoreDao?.getFilesByStatus(SyncStatus.IN_PROGRESS)
            Log.d(TAG, "Reset in progress file : ${failedFiles?.size}")

            // Reset failed files to pending
            if (failedFiles != null) {
                for (file in failedFiles) {
                    fileStoreDao?.updateSyncStatus(file.documentId, SyncStatus.PENDING)
                }
            }
        }

        stopSelf()
    }

}