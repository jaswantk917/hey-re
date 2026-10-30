package org.stypox.dicio.ui.nav

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.stypox.dicio.R
import org.stypox.dicio.settings.MainSettingsScreen
import org.stypox.dicio.settings.SkillSettingsScreen
import org.stypox.dicio.ui.about.AboutScreen
import org.stypox.dicio.ui.home.HomeScreen

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val backIcon = @Composable {
        IconButton(
            onClick = { navController.navigateUp() }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
    }

    val shift = with(LocalDensity.current) { PAGE_SHIFT.roundToPx() }
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        enterTransition = { pageOpenEnter(shift) },
        exitTransition = { pageOpenExit(shift) },
        popEnterTransition = { pageCloseEnter(shift) },
        popExitTransition = { pageCloseExit(shift) },
    ) {
        composable<Home> {
            HomeScreen(
                onSettingsClick = { navController.navigate(MainSettings) },
            )
        }

        composable<MainSettings> {
            MainSettingsScreen(
                navigationIcon = backIcon,
                navigateToSkillSettings = { navController.navigate(SkillSettings) },
                navigateToAbout = { navController.navigate(About) },
            )
        }

        composable<SkillSettings> {
            SkillSettingsScreen(navigationIcon = backIcon)
        }

        composable<About> {
            AboutScreen(navigationIcon = backIcon)
        }
    }
}

// Android's own page transition (core/res/res/anim/activity_{open,close}_{enter,exit}.xml), which
// Settings uses: pages shift 96dp; the page on top fades in or out in 83ms while the one below
// stays opaque, so there is never a blank frame. navigation-compose also drives it from the
// predictive back gesture
private fun pageOpenEnter(shift: Int) =
    slideInHorizontally(tween(PAGE_MILLIS, easing = EmphasizedEasing)) { shift } +
        fadeIn(tween(FADE_MILLIS, delayMillis = 50, easing = LinearEasing))

private fun pageOpenExit(shift: Int) =
    slideOutHorizontally(tween(PAGE_MILLIS, easing = EmphasizedEasing)) { -shift }

private fun pageCloseEnter(shift: Int) =
    slideInHorizontally(tween(PAGE_MILLIS, easing = EmphasizedEasing)) { -shift }

private fun pageCloseExit(shift: Int) =
    slideOutHorizontally(tween(PAGE_MILLIS, easing = EmphasizedEasing)) { shift } +
        fadeOut(tween(FADE_MILLIS, delayMillis = 35, easing = LinearEasing))

private val PAGE_SHIFT = 96.dp
private const val PAGE_MILLIS = 450
private const val FADE_MILLIS = 83
// the closest cubic to the platform's fast_out_extra_slow_in path
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
