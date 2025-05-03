package com.example.autosyncdrive.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.extensions.android.http.AndroidHttp

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
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
}