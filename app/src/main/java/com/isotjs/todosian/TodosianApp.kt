package com.isotjs.todosian

import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.ui.addtask.AddTaskScreen
import com.isotjs.todosian.ui.category.CategoryScreen
import com.isotjs.todosian.ui.dailyfocus.DailyFocusScreen
import com.isotjs.todosian.ui.home.HomeScreen
import com.isotjs.todosian.ui.onboarding.OnboardingScreen
import com.isotjs.todosian.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TodosianApp(
    fileRepository: FileRepository,
    appSettingsRepository: AppSettingsRepository,
    preferencesManager: PreferencesManager,
    addTaskSignal: StateFlow<Long>,
    initialAddTaskTick: Long,
) {
    val navController = rememberNavController()
    val addTaskTick by addTaskSignal.collectAsStateWithLifecycle(initialValue = initialAddTaskTick)
    val startDestination = remember(fileRepository, addTaskTick) {
        when {
            fileRepository.getFolderUri() == null -> Routes.Onboarding
            addTaskTick > 0L -> Routes.AddTask
            else -> Routes.Home
        }
    }

    LaunchedEffect(addTaskTick) {
        if (addTaskTick > 0L &&
            fileRepository.getFolderUri() != null &&
            navController.currentDestination?.route != Routes.AddTask
        ) {
            navController.navigate(Routes.AddTask) {
                launchSingleTop = true
            }
        }
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
                preferencesManager = preferencesManager,
                onOpenCategory = { uri ->
                    navController.navigate(Routes.category(uri))
                },
                onOpenSettings = {
                    navController.navigate(Routes.Settings)
                },
                onOpenDailyFocus = {
                    navController.navigate(Routes.DailyFocus)
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
                preferencesManager = preferencesManager,
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
                navArgument(Routes.ARG_CATEGORY_ADD) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString(Routes.ARG_CATEGORY_URI).orEmpty()
            val uri = encoded.toUri()
            val autoOpenAddTodo = backStackEntry.arguments?.getBoolean(Routes.ARG_CATEGORY_ADD) ?: false
            CategoryScreen(
                fileRepository = fileRepository,
                appSettingsRepository = appSettingsRepository,
                categoryUri = uri,
                autoOpenAddTodo = autoOpenAddTodo,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        KEY_REFRESH_HOME,
                        System.currentTimeMillis(),
                    )
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.AddTask) {
            AddTaskScreen(
                fileRepository = fileRepository,
                onSelectCategory = { uri ->
                    navController.navigate(Routes.category(uri, addTodo = true))
                },
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        KEY_REFRESH_HOME,
                        System.currentTimeMillis(),
                    )
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.DailyFocus) {
            DailyFocusScreen(
                fileRepository = fileRepository,
                appSettingsRepository = appSettingsRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private const val KEY_REFRESH_HOME = "refresh_home"

object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Settings = "settings"
    const val DailyFocus = "daily_focus"
    const val AddTask = "add_task"

    const val ARG_CATEGORY_URI = "categoryUri"
    const val ARG_CATEGORY_ADD = "add"
    const val Category = "category/{$ARG_CATEGORY_URI}?$ARG_CATEGORY_ADD={$ARG_CATEGORY_ADD}"

    fun category(uri: Uri, addTodo: Boolean = false): String =
        "category/${Uri.encode(uri.toString())}?$ARG_CATEGORY_ADD=$addTodo"
}
