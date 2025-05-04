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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
fun HomeScreen(
    modifier: Modifier = Modifier,
    navigateTo:(route:String)->Unit,
    mainViewModel: MainViewModel,
    ) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        // Collect UI state
        val uiState by mainViewModel.uiState.collectAsState()
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // Activity result launcher for Google Sign-In
        val signInLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            mainViewModel.handleSignInResult(result.resultCode, result.data)
        }

        LaunchedEffect(uiState.error) {
            uiState.error?.let { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        }

        // Show toast on successful sign-in
        LaunchedEffect(uiState.isSignedIn) {
            if (uiState.isSignedIn && uiState.account != null) {
                Toast.makeText(context, "Signed in as ${uiState.account?.email}", Toast.LENGTH_SHORT).show()
            }
        }

        // Show toast when file is uploaded
        LaunchedEffect(uiState.uploadStatus) {
            if (uiState.uploadStatus?.contains("successfully") == true) {
                Toast.makeText(context, "File uploaded successfully!", Toast.LENGTH_SHORT).show()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!uiState.isSignedIn) {
                Button(
                    onClick = {
                        signInLauncher.launch(mainViewModel.getSignInIntent())
                    }
                ) {
                    Text("Connect to Google Drive")
                }
            } else {
                Text("Connected to Google Drive")
                Text("Account: ${uiState.account?.email ?: "Unknown"}")

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { mainViewModel.uploadTestFile() },
                    enabled = !uiState.isLoading
                ) {
                    Text("Upload Test file : MyFile.txt")
                }

                Spacer(modifier = Modifier.height(8.dp))

                uiState.uploadStatus?.let {
                    Text(it)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        mainViewModel.signOut()
                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Sign Out")
                }
            }
        }

    }
}