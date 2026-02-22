package com.isotjs.todosian

import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.ui.category.CategoryScreen
import com.isotjs.todosian.ui.home.HomeScreen
import com.isotjs.todosian.ui.onboarding.OnboardingScreen
import com.isotjs.todosian.ui.settings.SettingsScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TodosianApp(
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
) {
    val navController = rememberNavController()
    val startDestination = remember(fileRepository) {
        if (fileRepository.getFolderUri() == null) Routes.Onboarding else Routes.Home
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300),
            ) + fadeIn(animationSpec = tween(150))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 4 },
                animationSpec = tween(300),
            ) + fadeOut(animationSpec = tween(150))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 4 },
                animationSpec = tween(300),
            ) + fadeIn(animationSpec = tween(150))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300),
            ) + fadeOut(animationSpec = tween(150))
        },
    ) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                fileRepository = fileRepository,
                onFinished = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Home) { backStackEntry ->
            val refreshSignal = backStackEntry.savedStateHandle
                .getStateFlow(KEY_REFRESH_HOME, 0L)
                .collectAsStateWithLifecycle()
                .value

            HomeScreen(
                fileRepository = fileRepository,
                appSettingsRepository = appSettingsRepository,
                onOpenCategory = { uri ->
                    navController.navigate(Routes.category(uri))
                },
                onOpenSettings = {
                    navController.navigate(Routes.Settings)
                },
                refreshSignal = refreshSignal,
                onRequireOnboarding = {
                    navController.navigate(Routes.Onboarding) {
                        popUpTo(Routes.Home) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(
                fileRepository = fileRepository,
                appSettingsRepository = appSettingsRepository,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        KEY_REFRESH_HOME,
                        System.currentTimeMillis(),
                    )
                    navController.popBackStack()
                },
                onRequireOnboarding = {
                    navController.navigate(Routes.Onboarding) {
                        popUpTo(Routes.Home) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.Category,
            arguments = listOf(
                navArgument(Routes.ARG_CATEGORY_URI) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString(Routes.ARG_CATEGORY_URI).orEmpty()
            val uri = Uri.parse(encoded)
            CategoryScreen(
                fileRepository = fileRepository,
                appSettingsRepository = appSettingsRepository,
                categoryUri = uri,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        KEY_REFRESH_HOME,
                        System.currentTimeMillis(),
                    )
                    navController.popBackStack()
                },
            )
        }
    }
}

private const val KEY_REFRESH_HOME = "refresh_home"

object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Settings = "settings"

    const val ARG_CATEGORY_URI = "categoryUri"
    const val Category = "category/{$ARG_CATEGORY_URI}"

    fun category(uri: Uri): String = "category/${Uri.encode(uri.toString())}"
}
