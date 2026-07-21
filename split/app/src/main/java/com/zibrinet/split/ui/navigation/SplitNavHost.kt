package com.zibrinet.split.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ADD_EXPENSE = "expense/new"
    const val EDIT_EXPENSE = "expense/{expenseId}/edit"
    const val EXPENSE_DETAIL = "expense/{expenseId}"
    const val SETTLE_UP = "settle"
    const val SETTINGS = "settings"

    fun expenseDetail(id: String) = "expense/$id"
    fun editExpense(id: String) = "expense/$id/edit"
}

@Composable
fun SplitNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(180)) },
    ) {
        composable(Routes.ONBOARDING) { Placeholder("Onboarding") }
        composable(Routes.HOME) { Placeholder("Home") }
        composable(Routes.ADD_EXPENSE) { Placeholder("Add expense") }
        composable(
            Routes.EDIT_EXPENSE,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
        ) { Placeholder("Edit expense") }
        composable(
            Routes.EXPENSE_DETAIL,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
        ) { Placeholder("Expense detail") }
        composable(Routes.SETTLE_UP) { Placeholder("Settle up") }
        composable(Routes.SETTINGS) { Placeholder("Settings") }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name)
    }
}
