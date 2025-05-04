package com.example.autosyncdrive.di

import android.content.Context
import com.example.autosyncdrive.repositories.MainRepository
import com.example.autosyncdrive.viewmodels.MainViewModelFactory

/**
 * Manual dependency injection for smaller projects
 * For larger projects, consider using Hilt or Koin
 */
object DIModule {
    private var mainRepository: MainRepository? = null

    // Get Google Drive Repository
    fun provideGoogleDriveRepository(context: Context): MainRepository {
        return mainRepository ?: MainRepository(context).also {
            mainRepository = it
        }
    }

    // Get Google Drive ViewModel Factory
    fun provideGoogleDriveViewModelFactory(context: Context): MainViewModelFactory {
        return MainViewModelFactory(provideGoogleDriveRepository(context))
    }


}