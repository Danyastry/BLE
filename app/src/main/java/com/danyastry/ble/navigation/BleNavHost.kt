package com.danyastry.ble.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.danyastry.ble.catalog.CatalogScreen
import com.danyastry.ble.catalog.FeatureCatalog
import com.danyastry.ble.catalog.FeatureScreen

private const val CATALOG_ROUTE = "catalog"
private const val FEATURE_ID_ARG = "featureId"
private const val FEATURE_ROUTE = "feature/{$FEATURE_ID_ARG}"

private fun featureRoute(id: String) = "feature/$id"

@Composable
fun BleNavHost(catalog: FeatureCatalog) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = CATALOG_ROUTE) {
        composable(CATALOG_ROUTE) {
            CatalogScreen(
                catalog = catalog,
                onFeatureClick = { id -> navController.navigate(featureRoute(id)) },
            )
        }
        composable(
            route = FEATURE_ROUTE,
            arguments = listOf(navArgument(FEATURE_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val entry = backStackEntry.arguments?.getString(FEATURE_ID_ARG)?.let(catalog::find)
            if (entry == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                FeatureScreen(entry = entry, onBack = { navController.popBackStack() })
            }
        }
    }
}
