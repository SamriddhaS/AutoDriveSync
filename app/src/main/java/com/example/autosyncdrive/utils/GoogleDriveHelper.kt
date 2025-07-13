package com.example.autosyncdrive.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.autosyncdrive.data.models.UploadResult
import com.example.autosyncdrive.data.models.UploadStatus
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.extensions.android.http.AndroidHttp

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class GoogleDriveHelperFactory {
    fun create(applicationContext: Context): GoogleDriveHelper {
        return GoogleDriveHelper(applicationContext)
    }
}

class GoogleDriveHelper(private val context: Context) {
    private val TAG = "GoogleDriveHelper"

    // Create a Google Sign-In client
    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    fun getGoogleDriveAccount() = GoogleSignIn.getLastSignedInAccount(context)

    // Get sign-in intent
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    // Handle sign-in result
    fun handleSignInResult(completedTask: Task<GoogleSignInAccount>): GoogleSignInAccount? {
        return try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d(TAG, "Sign-in successful, account: ${account.email}")
            account
        } catch (e: ApiException) {
            Log.w(TAG, "Sign-in failed with code: ${e.statusCode}")
            null
        }
    }

    /**
     * Upload file to Google Drive with custom filename and detailed status
     * This method should be added to your existing GoogleDriveHelper class
     */
    suspend fun uploadFileToDriveWithName(
        account: GoogleSignInAccount,
        uri: Uri,
        fileName: String,
        //folderId: String? = null // Add folder ID parameter
        folderName: String
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val googleAccountCredential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE)
            )
            googleAccountCredential.selectedAccount = account.account

            val googleDriveService = Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory(),
                googleAccountCredential
            )
                .setApplicationName("AutoSyncDrive")
                .build()

            val folderId = findOrCreateFolder(googleDriveService, folderName = folderName)

            // Build query to check for existing files
            val query = if (folderId != null) {
                "name='$fileName' and '$folderId' in parents and trashed=false"
            } else {
                "name='$fileName' and trashed=false"
            }

            // Check if file with same name already exists in the specified folder
            val existingFiles = googleDriveService.files().list()
                .setQ(query)
                .execute()

            if (existingFiles.files.isNotEmpty()) {
                // File with same name exists
                Log.d("GoogleDriveHelper", "File with name '$fileName' already exists")
                return@withContext UploadResult(
                    status = UploadStatus.DUPLICATE_NAME,
                    errorMessage = "File with name '$fileName' already exists"
                )
            }

            // Create file metadata
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = fileName

            // Set parent folder if provided
            if (folderId != null) {
                fileMetadata.parents = listOf(folderId)
            }

            // Get MIME type from the URI
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            // Create InputStreamContent from URI instead of FileContent
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")

            val fileSize = getFileSizeFromUri(uri)

            val mediaContent = InputStreamContent(mimeType, inputStream).apply {
                if (fileSize > 0) {
                    length = fileSize
                }
            }

            // Upload file
            val uploadedFile = googleDriveService.files()
                .create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            // Close the input stream
            inputStream.close()

            Log.d("GoogleDriveHelper", "File uploaded successfully with ID: ${uploadedFile.id}")
            return@withContext UploadResult(
                status = UploadStatus.SUCCESS,
                fileId = uploadedFile.id
            )

        } catch (e: UserRecoverableAuthIOException) {
            Log.e("GoogleDriveHelper", "Authentication error uploading file: $fileName", e)
            return@withContext UploadResult(
                status = UploadStatus.AUTH_ERROR,
                errorMessage = "Authentication required: ${e.message}"
            )
        } catch (e: GoogleJsonResponseException) {
            Log.e("GoogleDriveHelper", "Google API error uploading file: $fileName", e)
            return@withContext UploadResult(
                status = UploadStatus.OTHER_ERROR,
                errorMessage = "Google API error: ${e.message}"
            )
        } catch (e: java.net.UnknownHostException) {
            Log.e("GoogleDriveHelper", "Network error uploading file: $fileName", e)
            return@withContext UploadResult(
                status = UploadStatus.NETWORK_ERROR,
                errorMessage = "Network error: ${e.message}"
            )
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("GoogleDriveHelper", "Network timeout uploading file: $fileName", e)
            return@withContext UploadResult(
                status = UploadStatus.NETWORK_ERROR,
                errorMessage = "Network timeout: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Unknown error uploading file: $fileName", e)
            return@withContext UploadResult(
                status = UploadStatus.OTHER_ERROR,
                errorMessage = "Unknown error: ${e.message}"
            )
        }
    }

    // Helper function to find or create a folder
    suspend fun findOrCreateFolder(
        googleDriveService: Drive,
        folderName: String
    ): String = withContext(Dispatchers.IO) {
        // Build query to find existing folder
        val query = "name='$folderName' and mimeType='application/vnd.google-apps.folder' and trashed=false"


        // Search for existing folder
        val existingFolders = googleDriveService.files().list()
            .setQ(query)
            .execute()

        if (existingFolders.files.isNotEmpty()) {
            // Folder exists, return its ID
            return@withContext existingFolders.files[0].id
        } else {
            // Create new folder
            val folderMetadata = com.google.api.services.drive.model.File()
            folderMetadata.name = folderName
            folderMetadata.mimeType = "application/vnd.google-apps.folder"

            val createdFolder = googleDriveService.files()
                .create(folderMetadata)
                .setFields("id")
                .execute()

            return@withContext createdFolder.id
        }
    }

    /**
     * Helper function to get file size from URI
     */
    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else {
                    -1L
                }
            } ?: -1L
        } catch (e: Exception) {
            Log.w("GoogleDriveHelper", "Could not determine file size for URI: $uri", e)
            -1L
        }
    }
}