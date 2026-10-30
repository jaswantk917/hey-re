package org.stypox.dicio.ui.nav

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.stypox.dicio.R
import org.stypox.dicio.io.input.stt_popup.SttPopupActivity
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

    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        enterTransition = { settingsSlideIn(SlideDirection.Start) },
        exitTransition = { settingsSlideOut(SlideDirection.Start) },
        popEnterTransition = { settingsSlideIn(SlideDirection.End) },
        popExitTransition = { settingsSlideOut(SlideDirection.End) },
    ) {
        composable<Home> {
            val context = LocalContext.current
            HomeScreen(
                onSettingsClick = { navController.navigate(MainSettings) },
                onAboutClick = { navController.navigate(About) },
                onSttPopupClick = {
                    val intent = Intent(context, SttPopupActivity::class.java)
                    context.startActivity(intent)
                },
            )
        }

        composable<MainSettings> {
            MainSettingsScreen(
                navigationIcon = backIcon,
                navigateToSkillSettings = { navController.navigate(SkillSettings) },
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

// the shared-axis transition of Android's own Settings app (SettingsLib Spa AnimatedNavGraphBuilder):
// pages move a fifth of the width, the old one fades out quickly, then the new one fades in;
// navigation-compose also drives it with the predictive back gesture
private fun AnimatedContentTransitionScope<NavBackStackEntry>.settingsSlideIn(towards: SlideDirection) =
    slideIntoContainer(towards, slideInSpec, initialOffset = { it / 5 }) + fadeIn(fadeInSpec)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.settingsSlideOut(towards: SlideDirection) =
    slideOutOfContainer(towards, slideOutSpec, targetOffset = { it / 5 }) + fadeOut(fadeOutSpec)

private const val FADE_OUT_MILLIS = 75
private const val FADE_IN_MILLIS = 300
private val slideInSpec = tween<IntOffset>(FADE_IN_MILLIS, FADE_OUT_MILLIS, LinearOutSlowInEasing)
private val slideOutSpec = tween<IntOffset>(FADE_IN_MILLIS)
private val fadeOutSpec = tween<Float>(FADE_OUT_MILLIS, easing = FastOutLinearInEasing)
private val fadeInSpec = tween<Float>(FADE_IN_MILLIS, FADE_OUT_MILLIS, LinearOutSlowInEasing)

