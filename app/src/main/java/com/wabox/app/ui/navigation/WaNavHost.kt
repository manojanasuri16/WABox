package com.wabox.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wabox.app.ui.LocalAppContainer
import com.wabox.app.ui.LocalBottomBarHeight
import com.wabox.app.ui.designsystem.BarItem
import com.wabox.app.ui.designsystem.ToastHost
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaBottomBar
import com.wabox.app.ui.designsystem.WaMotion
import com.wabox.app.ui.home.HomeScreen
import com.wabox.app.ui.navBarBottom
import com.wabox.app.ui.onboarding.OnboardingScreen
import com.wabox.app.ui.saved.SavedScreen
import com.wabox.app.ui.settings.AboutScreen
import com.wabox.app.ui.settings.SettingsScreen
import com.wabox.app.ui.settings.SourcesScreen
import com.wabox.app.ui.viewer.ViewerScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object MainRoute
@Serializable object SourcesRoute
@Serializable object AboutRoute
@Serializable data class ViewerRoute(val mode: String, val startId: String, val folderId: Long = 0, val filter: String = "ALL")

@Composable
fun WaNavHost(startWithOnboarding: Boolean, pendingStatusId: StateFlow<String?>, onConsumedStatus: () -> Unit) {
    val nav = rememberNavController()
    val container = LocalAppContainer.current
    val reduced = Wa.reducedMotion

    LaunchedEffect(Unit) {
        pendingStatusId.collect { id ->
            if (id != null) {
                nav.navigate(ViewerRoute("STATUS", id)) { launchSingleTop = true }
                onConsumedStatus()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Wa.colors.background)) {
        NavHost(
            navController = nav,
            startDestination = if (startWithOnboarding) OnboardingRoute else MainRoute,
            // Push/pop along the same horizontal path (spatial consistency).
            enterTransition = { if (reduced) fadeIn() else slideInHorizontally(WaMotion.standard()) { it } + fadeIn(WaMotion.standard()) },
            exitTransition = { if (reduced) fadeOut() else slideOutHorizontally(WaMotion.standard()) { -it / 4 } + fadeOut(WaMotion.standard()) },
            popEnterTransition = { if (reduced) fadeIn() else slideInHorizontally(WaMotion.standard()) { -it / 4 } + fadeIn(WaMotion.standard()) },
            popExitTransition = { if (reduced) fadeOut() else slideOutHorizontally(WaMotion.standard()) { it } + fadeOut(WaMotion.standard()) },
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(onDone = {
                    nav.navigate(MainRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                })
            }
            composable<MainRoute>(
                exitTransition = { if (targetState.destination.route?.contains("ViewerRoute") == true) fadeOut(WaMotion.standard()) else slideOutHorizontally(WaMotion.standard()) { -it / 4 } + fadeOut(WaMotion.standard()) },
                popEnterTransition = { if (initialState.destination.route?.contains("ViewerRoute") == true) fadeIn(WaMotion.standard()) else slideInHorizontally(WaMotion.standard()) { -it / 4 } + fadeIn(WaMotion.standard()) },
            ) { MainTabs(nav) }
            composable<ViewerRoute>(
                // The viewer grows out of the grid and returns the same way.
                enterTransition = { if (reduced) fadeIn() else scaleIn(WaMotion.standard(), initialScale = 0.92f) + fadeIn(WaMotion.standard()) },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { if (reduced) fadeOut() else scaleOut(WaMotion.standard(), targetScale = 0.92f) + fadeOut(WaMotion.standard()) },
            ) { ViewerScreen(onClose = { nav.popBackStack() }) }
            composable<SourcesRoute> { SourcesScreen(onBack = { nav.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
        }
        ToastHost(
            messages = container.messages.messages,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBarBottom() + 64.dp),
        )
    }
}

@Composable
private fun MainTabs(nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    val barHeight = 58.dp + navBarBottom()
    val items = listOf(
        BarItem("Home", Icons.Outlined.Home, Icons.Rounded.Home),
        BarItem("Saved", Icons.Outlined.PhotoLibrary, Icons.Rounded.PhotoLibrary),
        BarItem("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
    )
    CompositionLocalProvider(LocalBottomBarHeight provides barHeight) {
        Box(Modifier.fillMaxSize()) {
            holder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> HomeScreen(
                        onOpenStatus = { item, folderId, filter -> nav.navigate(ViewerRoute("STATUS", item.id, folderId, filter.name)) },
                        onManageSources = { nav.navigate(SourcesRoute) },
                    )
                    1 -> SavedScreen(onOpen = { item, filter -> nav.navigate(ViewerRoute("SAVED", item.id.toString(), 0, filter.name)) })
                    else -> SettingsScreen(onSources = { nav.navigate(SourcesRoute) }, onAbout = { nav.navigate(AboutRoute) })
                }
            }
            WaBottomBar(items, tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
        }
    }
}
