package com.nomadclub.cashchat.feature.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nomadclub.cashchat.feature.chat.ChatScreen
import com.nomadclub.cashchat.feature.mypage.MyPageScreen
import com.nomadclub.cashchat.feature.rewards.RewardsScreen
import com.nomadclub.cashchat.feature.settings.SettingsScreen
import com.nomadclub.cashchat.feature.shop.ShopScreen

private const val ROUTE_SETTINGS = "settings"

@Composable
fun MainScreen(
    points: Int,
    messageCount: Int,
    addPoints: (Int) -> Unit,
    spendPoints: (Int) -> Boolean,
    incrementMessageCount: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isChatScreen = currentDestination?.route == MainTab.CHAT.route
    val isSettingsScreen = currentDestination?.route == ROUTE_SETTINGS
    val showBottomBar = !isChatScreen && !isSettingsScreen

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    MainTab.values().forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.CHAT.route,
            modifier = Modifier.padding(
                top = innerPadding.calculateTopPadding(),
                bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp
            )
        ) {
            composable(MainTab.CHAT.route) {
                ChatScreen(
                    points = points,
                    messageCount = messageCount,
                    addPoints = addPoints,
                    onNavigateTab = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    incrementMessageCount = incrementMessageCount
                )
            }
            composable(MainTab.REWARDS.route) {
                RewardsScreen(points = points, messageCount = messageCount, addPoints = addPoints)
            }
            composable(MainTab.SHOP.route) {
                ShopScreen(points = points, spendPoints = spendPoints)
            }
            composable(MainTab.MY_PAGE.route) {
                MyPageScreen(
                    points = points,
                    onLogout = onLogout,
                    onNavigateToSettings = {
                        navController.navigate(ROUTE_SETTINGS)
                    }
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onBack = { navController.navigateUp() }
                )
            }
        }
    }
}
