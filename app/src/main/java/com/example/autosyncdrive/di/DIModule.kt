package com.example.autosyncdrive.di

import android.content.Context
import com.example.autosyncdrive.controllers.SyncServiceController
import com.example.autosyncdrive.controllers.SyncServiceControllerImpl
import com.example.autosyncdrive.data.repositories.SyncRepositoryImpl
import com.example.autosyncdrive.data.localdb.FileStorageDb
import com.example.autosyncdrive.data.repositories.SyncRepository
import com.example.autosyncdrive.utils.GoogleDriveHelper
import com.example.autosyncdrive.utils.GoogleDriveHelperFactory
import com.example.autosyncdrive.utils.StorageHelper
import com.example.autosyncdrive.utils.StorageHelperFactory
import com.example.autosyncdrive.utils.SyncHelperFactory
import com.example.autosyncdrive.utils.SyncManager
import com.example.autosyncdrive.presentation.viewmodels.MainViewModelFactory

/**
 * Manual dependency injection for smaller projects
 * For larger projects, consider using Hilt or Koin
 */
object DIModule {
    private var syncRepositoryImpl: SyncRepositoryImpl? = null
    private var appDatabase: FileStorageDb? = null
    private var googleDriveHelper: GoogleDriveHelper? = null
    private var storageHelper: StorageHelper? = null
    private var syncManager:SyncManager?=null

    private val googleDriveHelperFactory = GoogleDriveHelperFactory()
    private val storageHelperFactory = StorageHelperFactory()
    private val syncManagerFactory = SyncHelperFactory()

    // Provide AppDatabase instance
    fun provideAppDatabase(context: Context): FileStorageDb {
        return appDatabase ?: FileStorageDb.getDatabase(context.applicationContext).also {
            appDatabase = it
        }
    }

    // Get File Info DAO
    fun provideFileInfoDao(context: Context) = provideAppDatabase(context.applicationContext).fileInfoDao()

    // Create GoogleDriveHelper using factory
    fun provideGoogleDriveHelper(context: Context): GoogleDriveHelper {
        return googleDriveHelper ?: googleDriveHelperFactory
            .create(context.applicationContext)  // Use Application Context!
            .also { googleDriveHelper = it }
    }

    // Create StorageHelper using factory
    fun provideStorageHelper(context: Context): StorageHelper {
        return storageHelper ?: storageHelperFactory
            .create(context.applicationContext)  // Use Application Context!
            .also { storageHelper = it }
    }

    fun provideSyncManager(context: Context): SyncManager {
        return syncManager ?: syncManagerFactory
            .create(
                applicationContext = context.applicationContext,
                fileStoreDao = provideFileInfoDao(context),
                googleDriveHelper = provideGoogleDriveHelper(context)
            )
            .also { syncManager = it }
    }

    // Get MainRepository with database injection
    fun provideMainRepository(context: Context): SyncRepository {
        return syncRepositoryImpl ?: SyncRepositoryImpl(
            context = context.applicationContext,
            fileStoreDao = provideFileInfoDao(context.applicationContext),
            googleDriveHelper = provideGoogleDriveHelper(context = context.applicationContext),
            storageHelper = provideStorageHelper(context = context.applicationContext)
        ).also {
            syncRepositoryImpl = it
        }
    }

    fun provideSyncServiceController(context: Context): SyncServiceController {
        return SyncServiceControllerImpl(context.applicationContext)
    }

    // Get Google Drive ViewModel Factory
    fun provideGoogleDriveViewModelFactory(context: Context): MainViewModelFactory {
        return MainViewModelFactory(provideMainRepository(context.applicationContext), provideSyncServiceController(context.applicationContext))
    }


}