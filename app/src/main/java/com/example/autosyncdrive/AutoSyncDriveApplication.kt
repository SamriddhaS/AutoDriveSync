package com.example.autosyncdrive

import android.app.Application
import com.example.autosyncdrive.di.DIModule

class AutoSyncDriveApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize database once at app startup
        DIModule.provideAppDatabase(this)
    }
}