package com.example.autosyncdrive.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.autosyncdrive.data.models.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



// Factory for StorageHelper
class StorageHelperFactory {
    fun create(applicationContext: Context): StorageHelper {
        return StorageHelper(applicationContext)
    }
}

/**
 * Helper class to manage storage operations
 */
class StorageHelper(private val context: Context) {

    private val TAG = "StorageHelper"

    /**
     * Creates an intent to select a directory
     */
    fun createDirectoryPickerIntent(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        return intent
    }

    /**
     * Processes the result from directory picker
     * @param uri The URI of the selected directory
     * @return True if the directory was successfully processed, false otherwise
     */
    fun processDirectorySelection(uri: Uri?): Boolean {
        if (uri == null) {
            Log.e(TAG, "Failed to get directory URI")
            return false
        }

        try {
            // Take persistent permission
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Store the URI for future use
            val sharedPrefs = context.getSharedPreferences("storage_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("selected_directory_uri", uri.toString()).apply()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error processing directory selection", e)
            return false
        }
    }

    /**
     * Checks if a directory has been selected previously
     * @return The URI of the selected directory, or null if none
     */
    fun getSelectedDirectoryUri(): Uri? {
        val sharedPrefs = context.getSharedPreferences("storage_prefs", Context.MODE_PRIVATE)
        val uriString = sharedPrefs.getString("selected_directory_uri", null)
        return if (uriString != null) Uri.parse(uriString) else null
    }

    /**
     * Scans the selected directory and returns a list of all files
     * @return List of file information or empty list if no directory is selected or error occurs.
     */
    suspend fun scanDirectory(includeHidden:Boolean=false): List<FileInfo> = withContext(Dispatchers.IO) {
        val fileList = mutableListOf<FileInfo>()
        val directoryUri = getSelectedDirectoryUri() ?: return@withContext fileList

        try {
            // Use contentResolver to query the directory
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryUri,
                DocumentsContract.getTreeDocumentId(directoryUri)
            )

            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val documentId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val name = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    val mimeType = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    val size = it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                    val lastModified = it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)

                    if (!includeHidden && name.startsWith(".")) {
                        continue
                    }

                    val fileInfo = FileInfo(
                        name = name,
                        uri = fileUri,
                        mimeType = mimeType,
                        size = size,
                        lastModified = lastModified,
                        isBackedUp = false,
                        documentId = documentId
                    )

                    fileList.add(fileInfo)
                    Log.d(TAG, "Found file: ${fileInfo.name}, type: ${fileInfo.mimeType}, uri: $fileUri")
                }
            } ?: run {
                Log.e(TAG, "Failed to get cursor for directory: $directoryUri")
                return@withContext emptyList() // Explicitly return empty list on error
            }

            fileList
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory", e)
            return@withContext emptyList() // Return empty list on exception
        }
    }

    /**
     * Helper function to get file size and handle errors
     */
    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        val contentResolver = context.contentResolver
        try {
            val cursor = contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    size = it.getLong(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size for URI: $uri", e)
            // Return 0 as default size on error.  Consider different error handling.
        }
        return size
    }
}

