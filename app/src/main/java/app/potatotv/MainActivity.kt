package app.potatotv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.potatotv.ui.browser.BrowserScreen
import app.potatotv.ui.components.PotatoBottomBar
import app.potatotv.ui.components.PotatoTopBar
import app.potatotv.ui.home.HomeScreen
import app.potatotv.ui.iptv.IptvScreen
import app.potatotv.ui.mylist.MyListScreen
import app.potatotv.ui.search.SearchScreen
import app.potatotv.ui.settings.SettingsScreen
import app.potatotv.ui.sites.SitesScreen
import app.potatotv.ui.theme.PotatoBg
import app.potatotv.ui.theme.PotatoTvTheme
import app.potatotv.ui.theme.ResponsiveTvUi
import app.potatotv.ui.theme.isCompactScreen
import app.potatotv.ui.title.TitleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            ResponsiveTvUi {
                PotatoTvTheme {
                    PotatoApp()
                }
            }
        }
    }
}

@Composable
fun PotatoApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val hideChrome = route?.startsWith("browser") == true
    val compact = isCompactScreen()

    // Lock ordinary screens to the flavour's orientation, but let the browser
    // rotate freely so a site's fullscreen video player can go landscape.
    val activity = androidx.compose.ui.platform.LocalContext.current as? Activity
    DisposableEffect(route, BuildConfig.FLAVOR) {
        activity?.requestedOrientation = when {
            route?.startsWith("browser") == true -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            BuildConfig.FLAVOR == "mobile" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose { }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PotatoBg)
    ) {
        Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.weight(1f)
        ) {
            composable("home") {
                HomeScreen(nav)
            }
            composable("search") {
                SearchScreen(nav)
            }
            composable("sites") {
                SitesScreen(nav)
            }
            composable("mylist") {
                MyListScreen(nav)
            }
            composable("iptv") {
                IptvScreen(nav)
            }
            composable("settings") {
                SettingsScreen(nav)
            }
            composable(
                "title/{type}/{id}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                TitleScreen(
                    nav = nav,
                    type = backStackEntry.arguments?.getString("type") ?: "movie",
                    id = backStackEntry.arguments?.getString("id") ?: ""
                )
            }
            composable(
                "browser?url={url}&name={name}&siteId={siteId}&imdb={imdb}&type={type}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("siteId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("imdb") { type = NavType.StringType; defaultValue = "" },
                    navArgument("type") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                BrowserScreen(
                    nav = nav,
                    url = backStackEntry.arguments?.getString("url").orEmpty(),
                    name = backStackEntry.arguments?.getString("name").orEmpty(),
                    siteId = backStackEntry.arguments?.getString("siteId").orEmpty(),
                    imdb = backStackEntry.arguments?.getString("imdb").orEmpty(),
                    type = backStackEntry.arguments?.getString("type").orEmpty()
                )
            }
        }
        if (!hideChrome && compact) {
            PotatoBottomBar(currentRoute = route) { destination ->
                nav.navigate(destination) {
                    launchSingleTop = true
                    popUpTo("home") { inclusive = destination == "home" }
                }
            }
        }
        }

        if (!hideChrome && !compact) {
            Box(Modifier.align(Alignment.TopCenter)) {
                PotatoTopBar(currentRoute = route) { destination ->
                    nav.navigate(destination) {
                        launchSingleTop = true
                        popUpTo("home") { inclusive = destination == "home" }
                    }
                }
            }
        }
    }
}
