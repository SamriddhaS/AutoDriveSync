package com.example.autosyncdrive.data.localdb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Database(entities = [FileInfo::class], version = 1, exportSchema = false)
@TypeConverters(UriConverter::class) // Add this annotation
abstract class FileStorageDb : RoomDatabase() {

    abstract fun fileInfoDao(): FileStoreDao

    companion object {
        @Volatile
        private var INSTANCE: FileStorageDb? = null

        fun getDatabase(context: Context): FileStorageDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FileStorageDb::class.java,
                    "auto_sync_drive_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}