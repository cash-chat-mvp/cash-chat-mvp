package com.nomadclub.cashchat.feature.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 하단 탭 바에 표시되는 탭 종류.
 * route: 네비게이션 경로 문자열, icon: 탭 아이콘, label: 탭 이름(한글)
 */
enum class MainTab(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    CHAT(
        route = "chat",
        icon = Icons.AutoMirrored.Filled.Chat,
        label = "채팅"
    ),
    REWARDS(
        route = "rewards",
        icon = Icons.Default.Redeem,
        label = "리워드"
    ),
    SHOP(
        route = "shop",
        icon = Icons.Default.Store,
        label = "상점"
    ),
    MY_PAGE(
        route = "mypage",
        icon = Icons.Default.Person,
        label = "MY"
    )
}