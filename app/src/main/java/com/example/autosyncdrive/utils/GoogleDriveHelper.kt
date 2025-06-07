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
import com.google.api.client.http.FileContent
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.util.*

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

    // Create Drive service
    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val transport = AndroidHttp.newCompatibleTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName("Your App Name")
            .build()
    }

    // Create a sample file
    suspend fun createSampleFile(): File = withContext(Dispatchers.IO) {
        val file = File(context.getExternalFilesDir(null), "MyFile.txt")
        FileWriter(file).use { writer ->
            writer.write("Hello, this is a test file from Jetpack Compose!")
        }
        file
    }

    // Upload file to Google Drive
    suspend fun uploadFileToDrive(account: GoogleSignInAccount, file: File): String? = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(account)

            // File metadata
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = file.name

            // File content
            val mediaContent = com.google.api.client.http.FileContent("text/plain", file)

            // Upload file
            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            uploadedFile.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file", e)
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
        fileName: String
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

            // Check if file with same name already exists
            val existingFiles = googleDriveService.files().list()
                .setQ("name='$fileName' and trashed=false")
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

            // Create file content
            //val mediaContent = FileContent(null, file)

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