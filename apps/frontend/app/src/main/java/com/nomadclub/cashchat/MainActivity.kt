package com.nomadclub.cashchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.nomadclub.cashchat.feature.main.MainScreen
import com.nomadclub.cashchat.feature.onboarding.OnboardingScreen
import com.nomadclub.cashchat.ui.theme.CashChatTheme

/**
 * 앱 내 화면 경로(route)를 정의하는 객체.
 * Jetpack Compose Navigation에서 "onboarding", "main" 같은 문자열로 화면을 구분합니다.
 */
private object AppRoute {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main?firstEntry={firstEntry}"

    /** 메인 화면으로 이동할 때 쿼리 파라미터 firstEntry를 붙인 경로 문자열을 반환 */
    fun main(firstEntry: Boolean = false): String = "main?firstEntry=$firstEntry"
}

/**
 * 앱의 진입점(Activity).
 * onCreate: 앱이 처음 실행될 때 한 번 호출됩니다.
 * setContent: Compose로 UI를 그리기 시작하는 곳입니다.
 * enableEdgeToEdge: 상태바/네비게이션바까지 화면을 쓰도록 설정합니다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CashChatTheme {
                CashChatApp()
            }
        }
    }
}

/**
 * 앱 전체 네비게이션과 공유 상태를 담당하는 Composable.
 * rememberNavController(): 화면 전환을 관리하는 컨트롤러 (한 번 생성 후 유지)
 * rememberSaveable: 화면 회전 등으로 재생성돼도 값이 유지됩니다 (포인트, 메시지 수 등)
 */
@Composable
private fun CashChatApp() {
    val navController = rememberNavController()

    // 앱 전역에서 쓰는 포인트와 대화 횟수 (다른 화면에서도 동일한 값을 참조)
    var points by rememberSaveable { mutableIntStateOf(0) }
    var messageCount by rememberSaveable { mutableIntStateOf(0) }

    /** 포인트 증가 (리워드/미션 등에서 호출) */
    fun addPoints(value: Int) {
        if (value <= 0) return
        points += value
    }

    /** 포인트 사용 (상점에서 구매 시). 잔액 부족이면 false 반환 */
    fun spendPoints(value: Int): Boolean {
        if (value <= 0) return false
        return if (points >= value) {
            points -= value
            true
        } else {
            false
        }
    }

    /** 대화 1회당 호출. 메시지 수 +1, 포인트 +10 */
    fun incrementMessageCount() {
        messageCount += 1
        addPoints(10)
    }

    // NavHost = "여기서 보여줄 화면은 route에 따라 결정된다"는 컨테이너
    NavHost(
        navController = navController,
        startDestination = AppRoute.ONBOARDING  // 처음에는 온보딩 화면부터
    ) {
        // "onboarding" 경로 → OnboardingScreen 표시
        composable(AppRoute.ONBOARDING) {
            OnboardingScreen(
                onStartClick = {
                    // 시작하기 클릭 시 메인으로 이동, 온보딩은 백스택에서 제거
                    navController.navigate(AppRoute.main(firstEntry = true)) {
                        popUpTo(AppRoute.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // "main?firstEntry=..." 경로 → 하단 탭이 있는 MainScreen
        composable(
            route = AppRoute.MAIN,
            arguments = listOf(
                navArgument("firstEntry") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val firstEntry = backStackEntry.arguments?.getBoolean("firstEntry") ?: false

            MainScreen(
                points = points,
                messageCount = messageCount,
                addPoints = ::addPoints,
                spendPoints = ::spendPoints,
                incrementMessageCount = ::incrementMessageCount
            )
        }
    }
}