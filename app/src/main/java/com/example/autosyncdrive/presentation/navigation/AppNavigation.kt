package com.example.autosyncdrive.presentation.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.autosyncdrive.presentation.screens.HomeScreen
import com.example.autosyncdrive.presentation.viewmodels.MainViewModel

object Routes{
    const val HOME_SCREEN = "home_screen"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel,
    context:Context?,
    modifier: Modifier
    ) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME_SCREEN
    ){

        composable(route = Routes.HOME_SCREEN){
            HomeScreen(
                viewModel = mainViewModel,
                modifier = modifier
            )
        }

    }
}