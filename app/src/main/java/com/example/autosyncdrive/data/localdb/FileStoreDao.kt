package com.example.autosyncdrive.data.localdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FileStoreDao {
    @Query("SELECT * FROM file_cache")
    fun getAllFiles(): Flow<List<FileInfo>>

    @Query("SELECT * FROM file_cache")
    suspend fun getAllFilesList(): List<FileInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileInfo>)

    @Query("DELETE FROM file_cache WHERE documentId NOT IN (:existingFilePaths)")
    suspend fun deleteNonExistentFiles(existingFilePaths: List<String>)

    @Query("DELETE FROM file_cache")
    suspend fun deleteAllFiles()

    @Transaction
    suspend fun refreshFiles(files: List<FileInfo>) {
        deleteAllFiles()
        insertFiles(files)
    }

    @Query("SELECT COUNT(*) FROM file_cache")
    suspend fun getFileCount(): Int
}