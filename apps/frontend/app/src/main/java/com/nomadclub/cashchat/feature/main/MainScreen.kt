package com.nomadclub.cashchat.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val currentRoute = currentDestination?.route
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val floatingBarHeight = 62.dp
    val floatingBarBottomMargin = 12.dp
    val bottomNavInset = floatingBarHeight + floatingBarBottomMargin + navBarBottom + 12.dp
    val showBottomNav = !isKeyboardVisible
    val contentBottomPadding = when {
        currentRoute == MainTab.CHAT.route -> 0.dp
        showBottomNav -> bottomNavInset
        else -> navBarBottom + 12.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = MainTab.CHAT.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarTop)
                .padding(bottom = contentBottomPadding)
        ) {
            composable(MainTab.CHAT.route) {
                ChatScreen(
                    points = points,
                    messageCount = messageCount,
                    addPoints = addPoints,
                    bottomNavInset = if (showBottomNav) bottomNavInset else 0.dp,
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

        AnimatedVisibility(
            visible = showBottomNav,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Floating nav 뒤쪽 받침 레이어: 콘텐츠가 그대로 비쳐 어색해 보이는 현상을 완화
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp + navBarBottom)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFF4F6F8).copy(alpha = 0.70f),
                                Color(0xFFF4F6F8)
                            )
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = showBottomNav,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = floatingBarBottomMargin + navBarBottom)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.96f),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MainTab.values().forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            FloatingNavItem(
                                icon = tab.icon,
                                label = tab.label,
                                active = selected,
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
        }
    }
}

@Composable
private fun FloatingNavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    if (active) Color(0xFF5C6BFA) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.White else Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            color = if (active) Color(0xFF5C6BFA) else Color(0xFF9CA3AF),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp
        )
    }
}
