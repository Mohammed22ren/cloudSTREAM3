package com.kodastream.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.NavArgs
import com.kodastream.app.ui.components.softSpring
import com.kodastream.app.ui.details.DetailsScreen
import com.kodastream.app.ui.details.DetailsViewModel
import com.kodastream.app.ui.extensions.ExtensionsScreen
import com.kodastream.app.ui.home.HomeScreen
import com.kodastream.app.ui.library.LibraryScreen
import com.kodastream.app.ui.player.PlayerScreen
import com.kodastream.app.ui.player.PlayerViewModel
import com.kodastream.app.ui.search.SearchScreen
import com.kodastream.app.ui.settings.SettingsScreen
import com.kodastream.app.ui.theme.ThemeViewModel
import com.lagradost.cloudstream3.SearchResponse

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val EXTENSIONS = "extensions"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{id}"
    const val PLAYER = "player/{id}"
}

private data class TopLevel(
    val route: String,
    val labelRes: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    TopLevel(Routes.SEARCH, R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search),
    TopLevel(Routes.LIBRARY, R.string.nav_library, Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    TopLevel(Routes.EXTENSIONS, R.string.nav_extensions, Icons.Rounded.Extension, Icons.Outlined.Extension),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
)

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openDetails(args: DetailsArgs) {
    navigate("details/${NavArgs.put(args)}")
}

private fun NavHostController.openDetails(item: SearchResponse) {
    openDetails(
        DetailsArgs(
            apiName = item.apiName,
            url = item.url,
            title = item.name,
            posterUrl = item.posterUrl,
            posterHeaders = item.posterHeaders,
        )
    )
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

    val context = LocalContext.current
    val navPosition by AppPrefs.navPosition.collectAsState(initial = AppPrefs.navPosition(context))
    val sideMode = showBar && navPosition != AppPrefs.NAV_BOTTOM

    // Either shell hands the screens the same contract: system-bar insets,
    // plus the pill's strip only when the floating bar is actually there.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = when {
        !showBar -> PaddingValues(0.dp)
        sideMode -> PaddingValues(top = topInset, bottom = navInset)
        else -> PaddingValues(top = topInset, bottom = 84.dp + navInset)
    }

    // The app keeps its locale direction everywhere. The one exception is the
    // shell row that pins the rail: in Arabic it must still put the rail on
    // the left, so that row is LTR and the content slot gets the original
    // direction re-provided inside it.
    val contentDirection = LocalLayoutDirection.current

    if (sideMode) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxSize()) {
                SideNavRail(selectedRoute = route, onSelect = { navController.navigateTopLevel(it) })
                CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
                    AppNavGraph(navController, themeViewModel, contentPadding)
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            AppNavGraph(navController, themeViewModel, contentPadding)
            AnimatedVisibility(
                visible = showBar,
                enter = fadeIn(tween(220)) +
                    slideInVertically(animationSpec = softSpring()) { it * 2 },
                exit = fadeOut(tween(180)) +
                    slideOutVertically(animationSpec = softSpring()) { it * 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FloatingNavPill(selectedRoute = route, onSelect = { navController.navigateTopLevel(it) })
            }
        }
    }
}

/** The routes themselves, shared verbatim by both navigation shells. */
@Composable
private fun AppNavGraph(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    contentPadding: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                contentPadding = contentPadding,
                onOpenItem = navController::openDetails,
                onOpenExtensions = { navController.navigateTopLevel(Routes.EXTENSIONS) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                contentPadding = contentPadding,
                onOpenItem = navController::openDetails,
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                contentPadding = contentPadding,
                onOpenItem = navController::openDetails,
            )
        }
        composable(Routes.EXTENSIONS) {
            ExtensionsScreen(contentPadding = contentPadding)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(contentPadding = contentPadding, themeViewModel = themeViewModel)
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

/**
 * The side rail: same five destinations, same icons, same order as the pill,
 * drawn vertically with the label under each icon. Scrollable so short
 * screens in landscape do not clip the last item.
 */
@Composable
private fun SideNavRail(selectedRoute: String?, onSelect: (String) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding(),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            topLevelDestinations.forEach { destination ->
                val selected = selectedRoute == destination.route
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelect(destination.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(destination.labelRes),
                            maxLines = 1,
                        )
                    },
                    alwaysShowLabel = true,
                )
            }
        }
    }
}

/**
 * The floating navigation pill: one tonal capsule, the open tab expanding to
 * say its name, the rest as icons. Ported from Koda's home pill, with the
 * tab set replaced by KodaStream's routes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNavPill(selectedRoute: String?, onSelect: (String) -> Unit) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
        content = {
            topLevelDestinations.forEach { destination ->
                val selected = selectedRoute == destination.route

                val animatedPadding by animateDpAsState(
                    targetValue = if (selected) 20.dp else 12.dp,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "pillPadding"
                )
                val animatedContainerColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "pillContainer"
                )
                val animatedContentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "pillContent"
                )

                Surface(
                    onClick = { onSelect(destination.route) },
                    shape = CircleShape,
                    color = animatedContainerColor,
                    contentColor = animatedContentColor,
                    modifier = Modifier.height(48.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = animatedPadding)
                            .animateContentSize(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
                            contentDescription = stringResource(destination.labelRes),
                            modifier = Modifier.size(24.dp)
                        )
                        // The pill names only the open tab.
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn() + expandHorizontally(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                            ),
                            exit = fadeOut() + shrinkHorizontally(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(destination.labelRes),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
