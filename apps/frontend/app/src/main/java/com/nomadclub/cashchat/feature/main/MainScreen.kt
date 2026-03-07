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
import com.nomadclub.cashchat.feature.shop.ShopScreen

/**
 * 메인 화면: 하단 탭(채팅/리워드/상점/MY) + 탭별 화면을 담는 컨테이너.
 * - points, messageCount: CashChatApp에서 내려주는 공유 상태
 * - addPoints, spendPoints, incrementMessageCount: 포인트/대화 수 변경 콜백
 */
@Composable
fun MainScreen(
    points: Int,
    messageCount: Int,
    addPoints: (Int) -> Unit,
    spendPoints: (Int) -> Boolean,
    incrementMessageCount: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 채팅 화면(route 가 "chat")일 때는 하단 네비게이션 바를 숨김
    val isChatScreen = currentDestination?.route == MainTab.CHAT.route

    // Scaffold: 상단바/하단바 + 본문 영역을 한 번에 배치해 주는 레이아웃
    Scaffold(
        bottomBar = {
            if (!isChatScreen) {
                // 하단 네비게이션 바 (채팅이 아닐 때만 표시)
                NavigationBar {
                    MainTab.values().forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                // 탭 클릭 시 해당 route로 이동
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
        // NavHost: 탭별 화면 전환 컨테이너
        NavHost(
            navController = navController,
            startDestination = MainTab.CHAT.route,
            // 채팅 화면일 때는 하단 패딩(NavigationBar 영역)을 0으로 만들어 전체 화면 사용
            modifier = Modifier.padding(
                top = innerPadding.calculateTopPadding(),
                bottom = if (isChatScreen) 0.dp else innerPadding.calculateBottomPadding()
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
                MyPageScreen(points = points) 
            }
        }
    }
}
