package com.kodastream.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kodastream.app.R
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.NavArgs
import com.kodastream.app.ui.details.DetailsScreen
import com.kodastream.app.ui.details.DetailsViewModel
import com.kodastream.app.ui.extensions.ExtensionsScreen
import com.kodastream.app.ui.home.HomeScreen
import com.kodastream.app.ui.player.PlayerScreen
import com.kodastream.app.ui.player.PlayerViewModel
import com.kodastream.app.ui.search.SearchScreen
import com.kodastream.app.ui.settings.SettingsScreen
import com.kodastream.app.ui.theme.ThemeViewModel
import com.lagradost.cloudstream3.SearchResponse

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val EXTENSIONS = "extensions"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{id}"
    const val PLAYER = "player/{id}"
}

private data class TopLevel(val route: String, val labelRes: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    TopLevel(Routes.SEARCH, R.string.nav_search, Icons.Rounded.Search),
    TopLevel(Routes.EXTENSIONS, R.string.nav_extensions, Icons.Rounded.Extension),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
)

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openDetails(item: SearchResponse) {
    val id = NavArgs.put(
        DetailsArgs(
            apiName = item.apiName,
            url = item.url,
            title = item.name,
            posterUrl = item.posterUrl,
            posterHeaders = item.posterHeaders,
        )
    )
    navigate("details/$id")
}

@Composable
fun AppRoot(themeViewModel: ThemeViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBar = topLevelDestinations.any { it.route == route }

    // A repository link opened from outside the app: go to the Extensions tab, which shows the dialog.
    val pendingRepo by AppEvents.pendingRepoUrl.collectAsState()
    LaunchedEffect(pendingRepo) {
        if (pendingRepo != null) navController.navigateTopLevel(Routes.EXTENSIONS)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    contentPadding = innerPadding,
                    onOpenItem = navController::openDetails,
                    onOpenExtensions = { navController.navigateTopLevel(Routes.EXTENSIONS) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    contentPadding = innerPadding,
                    onOpenItem = navController::openDetails,
                )
            }
            composable(Routes.EXTENSIONS) {
                ExtensionsScreen(contentPadding = innerPadding)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(contentPadding = innerPadding, themeViewModel = themeViewModel)
            }
            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                val args = NavArgs.details(entry.arguments?.getInt("id") ?: -1)
                if (args == null) {
                    // The process was recreated and the in-memory arguments are gone.
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val viewModel: DetailsViewModel = viewModel(
                        factory = viewModelFactory { initializer { DetailsViewModel(args) } }
                    )
                    DetailsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenItem = navController::openDetails,
                        onPlay = { playerArgs ->
                            navController.navigate("player/${NavArgs.put(playerArgs)}")
                        },
                    )
                }
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                val args = NavArgs.player(entry.arguments?.getInt("id") ?: -1)
                if (args == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val viewModel: PlayerViewModel = viewModel(
                        factory = viewModelFactory { initializer { PlayerViewModel(args) } }
                    )
                    PlayerScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
