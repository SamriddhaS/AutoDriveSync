package com.example.autosyncdrive.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.autosyncdrive.utils.GoogleDriveHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(modifier: Modifier = Modifier, navigateTo:(route:String)->Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {

        GoogleDriveScreen()

    }
}

@Composable
fun GoogleDriveScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State
    var isSignedIn by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }

    // Check if already signed in
    LaunchedEffect(Unit) {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastSignedInAccount != null) {
            isSignedIn = true
            account = lastSignedInAccount
        }
    }

    val googleDriveHelper = remember { GoogleDriveHelper(context) }

    // Activity result launcher for Google Sign-In
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val signedInAccount = googleDriveHelper.handleSignInResult(task)

            if (signedInAccount != null) {
                isSignedIn = true
                account = signedInAccount
                Toast.makeText(context, "Signed in as ${signedInAccount.email}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isSignedIn) {
            Button(
                onClick = {
                    signInLauncher.launch(googleDriveHelper.getSignInIntent())
                }
            ) {
                Text("Connect to Google Drive")
            }
        } else {
            Text("Connected to Google Drive")
            Text("Account: ${account?.email ?: "Unknown"}")

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        uploadStatus = "Uploading..."

                        account?.let { acc ->
                            val file = googleDriveHelper.createSampleFile()
                            val fileId = googleDriveHelper.uploadFileToDrive(acc, file)

                            if (fileId != null) {
                                uploadStatus = "File uploaded successfully! ID: $fileId"
                                Toast.makeText(context, "File uploaded successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                uploadStatus = "Upload failed"
                                Toast.makeText(context, "Failed to upload file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            ) {
                Text("Upload Test file : MyFile.txt")
            }

            Spacer(modifier = Modifier.height(8.dp))

            uploadStatus?.let {
                Text(it)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    googleDriveHelper.googleSignInClient.signOut()
                    isSignedIn = false
                    account = null
                    uploadStatus = null
                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Sign Out")
            }
        }
    }
}