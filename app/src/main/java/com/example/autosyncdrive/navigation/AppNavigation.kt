package com.example.autosyncdrive.navigation

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.autosyncdrive.ui.screens.HomeScreen
import com.example.autosyncdrive.ui.screens.MainViewModel

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
                navigateTo = { route -> navController.navigate(route) },
                modifier = modifier
            )
        }

    }
}