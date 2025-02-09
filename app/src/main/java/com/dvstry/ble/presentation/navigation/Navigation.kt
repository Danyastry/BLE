package com.dvstry.ble.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dvstry.ble.presentation.MainScreen
import com.dvstry.ble.presentation.SecondScreen

@Composable
fun Navigation(
    onStateChange:()->Unit
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.MAIN_SCREEN){
        composable(Screen.MAIN_SCREEN){
            MainScreen(navController = navController)
        }

        composable(Screen.ANOTHER_SCREEN){
            SecondScreen(
                onStateChange
            )
        }
    }
}

