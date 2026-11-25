package com.cinedrive.app.ui

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cinedrive.app.providers.MediaType
import com.cinedrive.app.providers.Title

class MainActivity : ComponentActivity() {

    /** True while a video is open; leaving the app then shows it picture-in-picture. */
    var pipAllowed = false
    val inPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CineDriveTheme { AppNav(this) } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipAllowed) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("search", "Search", Icons.Filled.Search),
    Tab("mylist", "My List", Icons.AutoMirrored.Filled.List),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

private fun NavHostController.openDetails(titleKey: String) = navigate("details/${Uri.encode(titleKey)}")

private fun NavHostController.openPlayer(playKey: String, titleKey: String) =
    navigate("player/${Uri.encode(playKey)}?title=${Uri.encode(titleKey)}")

@Composable
private fun AppNav(activity: MainActivity) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    val openTitle: (Title) -> Unit = { nav.openDetails(it.key) }
    val playTitle: (Title) -> Unit = { t ->
        if (t.type == MediaType.SERIES) nav.openDetails(t.key) else nav.openPlayer(t.key, t.key)
    }

    Scaffold(
        containerColor = Palette.Background,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = Color(0xF20B0B0C)) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White, selectedTextColor = Color.White,
                            indicatorColor = Palette.SurfaceHigh,
                            unselectedIconColor = Palette.TextSecondary, unselectedTextColor = Palette.TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        // Screens draw behind the status bar themselves; only keep clear of the bottom bar.
        NavHost(nav, startDestination = "home",
            modifier = Modifier.padding(if (showBar) PaddingValues(bottom = pad.calculateBottomPadding()) else PaddingValues())) {
            composable("home") {
                HomeScreen(onOpen = openTitle, onPlay = playTitle,
                    onResume = { p -> nav.openPlayer(p.key, p.titleKey) })
            }
            composable("search") { SearchScreen(onOpen = openTitle) }
            composable("mylist") { MyListScreen(onOpen = { nav.openDetails(it) }) }
            composable("settings") { SettingsScreen() }
            composable("details/{key}") { e ->
                val key = e.arguments?.getString("key").orEmpty()
                DetailsScreen(key, onPlay = { playKey -> nav.openPlayer(playKey, key) }, onBack = { nav.popBackStack() })
            }
            composable(
                "player/{key}?title={title}",
                arguments = listOf(navArgument("title") { type = NavType.StringType; defaultValue = "" }),
            ) { e ->
                val key = e.arguments?.getString("key").orEmpty()
                val title = e.arguments?.getString("title").orEmpty()
                PlayerScreen(
                    playKey = key, titleKey = title, activity = activity,
                    onPlayNext = { next ->
                        nav.popBackStack()
                        nav.openPlayer(next, title)
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
