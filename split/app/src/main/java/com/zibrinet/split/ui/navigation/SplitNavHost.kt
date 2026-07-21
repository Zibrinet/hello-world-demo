package com.zibrinet.split.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zibrinet.split.ui.RootGate
import com.zibrinet.split.ui.RootViewModel
import com.zibrinet.split.ui.expense.ExpenseDetailScreen
import com.zibrinet.split.ui.expense.ExpenseEditorScreen
import com.zibrinet.split.ui.home.HomeScreen
import com.zibrinet.split.ui.onboarding.OnboardingScreen
import com.zibrinet.split.ui.settings.SettingsScreen
import com.zibrinet.split.ui.settle.SettleUpScreen

object Routes {
    const val HOME = "home"
    const val ADD_EXPENSE = "expense/new"
    const val EDIT_EXPENSE = "expense/{expenseId}/edit"
    const val EXPENSE_DETAIL = "expense/{expenseId}"
    const val SETTLE_UP = "settle"
    const val SETTINGS = "settings"

    fun expenseDetail(id: String) = "expense/$id"
    fun editExpense(id: String) = "expense/$id/edit"
}

/** Root gate: first launch shows onboarding until the two participants exist. */
@Composable
fun SplitNavHost(rootViewModel: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val gate by rootViewModel.gate.collectAsStateWithLifecycle()

    Crossfade(targetState = gate, label = "rootGate") { current ->
        when (current) {
            RootGate.LOADING -> Box(Modifier.fillMaxSize())
            RootGate.ONBOARDING -> OnboardingScreen()
            RootGate.READY -> MainNavHost()
        }
    }
}

@Composable
private fun MainNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(180)) },
    ) {
        composable(Routes.HOME) { HomeScreen(navController) }
        // The editor rises like a sheet: spring slide-up over a fade.
        composable(
            Routes.ADD_EXPENSE,
            enterTransition = {
                slideInVertically(spring(stiffness = 400f)) { it / 3 } + fadeIn(tween(220))
            },
            popExitTransition = {
                slideOutVertically(tween(180)) { it / 3 } + fadeOut(tween(180))
            },
        ) { ExpenseEditorScreen(navController) }
        composable(
            Routes.EDIT_EXPENSE,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
            enterTransition = {
                slideInVertically(spring(stiffness = 400f)) { it / 3 } + fadeIn(tween(220))
            },
            popExitTransition = {
                slideOutVertically(tween(180)) { it / 3 } + fadeOut(tween(180))
            },
        ) { ExpenseEditorScreen(navController) }
        composable(
            Routes.EXPENSE_DETAIL,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
        ) { ExpenseDetailScreen(navController) }
        composable(Routes.SETTLE_UP) { SettleUpScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
    }
}
