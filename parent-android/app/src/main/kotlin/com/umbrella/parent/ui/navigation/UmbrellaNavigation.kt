package com.umbrella.parent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.umbrella.parent.ui.components.Screen
import com.umbrella.parent.ui.screens.chat.ChatRuleScreen
import com.umbrella.parent.ui.screens.dashboard.DashboardScreen
import com.umbrella.parent.ui.screens.rules.ActiveRulesScreen

@Composable
fun UmbrellaNavigation(
    navController: NavHostController = rememberNavController()
) {
    val items = listOf(
        Screen.Dashboard,
        Screen.ChatRule,
        Screen.ActiveRules
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToChat = {
                        navController.navigate(Screen.ChatRule.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.ChatRule.route) {
                ChatRuleScreen()
            }
            composable(Screen.ActiveRules.route) {
                ActiveRulesScreen(
                    onNavigateToChat = {
                        navController.navigate(Screen.ChatRule.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
