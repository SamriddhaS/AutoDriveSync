package com.example.autosyncdrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autosyncdrive.di.DIModule
import com.example.autosyncdrive.navigation.AppNavHost
import com.example.autosyncdrive.viewmodels.MainViewModel
import com.example.autosyncdrive.ui.theme.AutoSyncDriveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoSyncDriveTheme {

                val viewModel: MainViewModel = viewModel(
                    factory = DIModule.provideGoogleDriveViewModelFactory(this@MainActivity)
                )

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        mainViewModel = viewModel,
                        context = this@MainActivity,
                        modifier=Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AutoSyncDriveTheme {

    }
}