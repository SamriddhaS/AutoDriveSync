package com.example.autosyncdrive.di

import android.content.Context
import com.example.autosyncdrive.data.MainRepository
import com.example.autosyncdrive.data.localdb.FileStorageDb
import com.example.autosyncdrive.viewmodels.MainViewModelFactory

/**
 * Manual dependency injection for smaller projects
 * For larger projects, consider using Hilt or Koin
 */
object DIModule {
    private var mainRepository: MainRepository? = null
    private var appDatabase: FileStorageDb? = null

    // Provide AppDatabase instance
    fun provideAppDatabase(context: Context): FileStorageDb {
        return appDatabase ?: FileStorageDb.getDatabase(context).also {
            appDatabase = it
        }
    }

    // Get File Info DAO
    fun provideFileInfoDao(context: Context) = provideAppDatabase(context).fileInfoDao()

    // Get MainRepository with database injection
    fun provideMainRepository(context: Context): MainRepository {
        return mainRepository ?: MainRepository(
            context = context,
            fileStoreDao = provideFileInfoDao(context)
        ).also {
            mainRepository = it
        }
    }

    // Get Google Drive ViewModel Factory
    fun provideGoogleDriveViewModelFactory(context: Context): MainViewModelFactory {
        return MainViewModelFactory(provideMainRepository(context))
    }


}