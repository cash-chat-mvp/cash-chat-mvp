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
import com.nomadclub.cashchat.feature.chat.ChatViewModel
import com.nomadclub.cashchat.feature.chat.ConversationListScreen
import com.nomadclub.cashchat.feature.chat.evolution.EvolutionScreen
import com.nomadclub.cashchat.feature.mypage.MyPageScreen
import org.koin.androidx.compose.koinViewModel
import com.nomadclub.cashchat.feature.rewards.BenefitZoneScreen
import com.nomadclub.cashchat.feature.settings.SettingsScreen
import com.nomadclub.cashchat.feature.shop.ShopScreen

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CONVERSATIONS = "chat/conversations"
private const val ROUTE_EVOLUTION = "evolution"

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

    // CHAT 라우트와 대화 목록 라우트가 같은 인스턴스를 공유하도록 MainScreen 레벨에서 획득
    val chatViewModel: ChatViewModel = koinViewModel()

    // 채팅은 메인 탭이므로 하단 탭바를 노출해 리워드/상점/MY 접근을 유지한다.
    // 설정·대화목록·진화 화면만 풀스크린(탭바 숨김)으로 처리한다.
    val fullScreenRoutes = setOf(ROUTE_SETTINGS, ROUTE_CONVERSATIONS, ROUTE_EVOLUTION)
    val showBottomBar = currentDestination?.route !in fullScreenRoutes

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
                    onOpenConversations = { navController.navigate(ROUTE_CONVERSATIONS) },
                    onOpenEvolution = { navController.navigate(ROUTE_EVOLUTION) },
                    viewModel = chatViewModel,
                )
            }
            composable(ROUTE_CONVERSATIONS) {
                ConversationListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { id ->
                        chatViewModel.openConversation(id)
                        navController.popBackStack()
                    },
                    onNewConversation = {
                        chatViewModel.chatStore.startNewConversation()
                        navController.popBackStack()
                    },
                )
            }
            composable(ROUTE_EVOLUTION) {
                EvolutionScreen(onClose = { navController.popBackStack() })
            }
            composable(MainTab.REWARDS.route) {
                BenefitZoneScreen()
            }
            composable(MainTab.SHOP.route) {
                ShopScreen()
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
