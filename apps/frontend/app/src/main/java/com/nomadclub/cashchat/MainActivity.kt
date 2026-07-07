package com.nomadclub.cashchat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.nomadclub.cashchat.config.AppGateState
import com.nomadclub.cashchat.config.RemoteConfigManager
import com.nomadclub.cashchat.feature.auth.AuthState
import com.nomadclub.cashchat.feature.auth.AuthViewModel
import com.nomadclub.cashchat.feature.auth.LoginScreen
import com.nomadclub.cashchat.feature.gate.AppGateScreen
import com.nomadclub.cashchat.feature.main.MainScreen
import com.nomadclub.cashchat.feature.onboarding.OnboardingScreen
import com.nomadclub.cashchat.feature.settings.SettingsViewModel
import com.nomadclub.cashchat.shared.points.PointsRepository
import com.nomadclub.cashchat.ui.theme.CashChatTheme
import com.nomadclub.cashchat.ui.theme.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 앱 내 화면 경로(route)를 정의하는 객체.
 * Jetpack Compose Navigation에서 "onboarding", "main" 같은 문자열로 화면을 구분합니다.
 */
private object AppRoute {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val MAIN = "main?firstEntry={firstEntry}"

    /** 메인 화면으로 이동할 때 쿼리 파라미터 firstEntry를 붙인 경로 문자열을 반환 */
    fun main(firstEntry: Boolean = false): String = "main?firstEntry=$firstEntry"
}

/**
 * 앱의 진입점(Activity).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.nomadclub.cashchat.flavor.FlavorModules.onMainActivityCreated(this)

        setContent {
            CashChatApp()
        }
    }
}

/**
 * 앱 전체 네비게이션과 공유 상태를 담당하는 Composable.
 */
@Composable
private fun CashChatApp() {
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    CashChatTheme(darkTheme = isDark) {
        CashChatAppContent()
    }
}

@Composable
private fun CashChatAppContent() {
    // 긴급 게이트(점검 모드 / 강제 업데이트)는 모든 화면보다 우선한다.
    val remoteConfigManager = koinInject<RemoteConfigManager>()
    val gateState by remoteConfigManager.gateState.collectAsStateWithLifecycle()
    if (gateState !is AppGateState.None) {
        val context = LocalContext.current
        AppGateScreen(state = gateState, onUpdate = { openPlayStore(context) })
        return
    }

    // 앱 시작 시 게스트 세션 자동 초기화 (CC-154, CC-155)
    val authViewModel: AuthViewModel = koinViewModel()
    val authState by authViewModel.authState.collectAsState()

    // 세션 초기화 중에는 로딩 화면 표시
    if (authState is AuthState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // 인증 오류 시 친화적 에러 화면 표시
    if (authState is AuthState.Error) {
        val errorMessage = (authState as AuthState.Error).message

        // 개발 빌드: 상세 에러를 Logcat에 기록
        if (BuildConfig.DEBUG) {
            Log.e("CashChatAuth", "세션 초기화 실패: $errorMessage")
        }

        AuthErrorScreen(onRetry = { authViewModel.retry() })
        return
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 포인트 잔액은 혜택존/상점과 동일한 공유 PointsRepository 를 단일 소스로 사용한다.
    // (이전엔 마이페이지만 별도 로컬 카운터를 써서 다른 화면과 잔액이 어긋나는 문제가 있었다.)
    val pointsRepository = koinInject<PointsRepository>()
    val balance by pointsRepository.balance.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        runCatching { pointsRepository.refresh() }
            .onFailure { Log.e("CashChatPoints", "포인트 잔액 동기화 실패", it) }
    }
    val points = balance.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    var messageCount by rememberSaveable { mutableIntStateOf(0) }

    fun addPoints(value: Int) {
        if (value <= 0) return
        pointsRepository.applyDelta(value.toLong())
    }

    fun spendPoints(value: Int): Boolean {
        if (value <= 0) return false
        return if (balance >= value) {
            pointsRepository.applyDelta(-value.toLong())
            true
        } else false
    }

    fun incrementMessageCount() {
        messageCount += 1
        addPoints(10)
    }

    // 이미 MEMBER 세션이 있으면 온보딩 생략 → 바로 메인으로
    val startDestination = when {
        authState is AuthState.Authenticated &&
            (authState as AuthState.Authenticated).role == "MEMBER" -> AppRoute.main()
        else -> AppRoute.ONBOARDING
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 온보딩 화면
            composable(AppRoute.ONBOARDING) {
                OnboardingScreen(
                    onLoginSuccess = {
                        navController.navigate(AppRoute.main(firstEntry = true)) {
                            popUpTo(AppRoute.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Google 로그인 화면 (게스트 → 멤버 전환)
            composable(AppRoute.LOGIN) {
                LoginScreen(
                    onGoogleSignInSuccess = { serverAuthCode ->
                        authViewModel.loginWithGoogle(
                            serverAuthCode = serverAuthCode,
                            onError = { errorMsg ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("로그인 실패: $errorMsg")
                                }
                            }
                        )
                        // 로그인 시도 후 백스택 복귀 (authState가 갱신되면 UI 자동 반영)
                        navController.popBackStack()
                    }
                )
            }

            // 메인 화면 (하단 탭)
            composable(
                route = AppRoute.MAIN,
                arguments = listOf(
                    navArgument("firstEntry") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) {
                MainScreen(
                    points = points,
                    messageCount = messageCount,
                    addPoints = ::addPoints,
                    spendPoints = ::spendPoints,
                    incrementMessageCount = ::incrementMessageCount,
                    onNavigateToLogin = {
                        navController.navigate(AppRoute.LOGIN)
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(AppRoute.ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

/**
 * 인증 실패 시 표시되는 에러 화면.
 *
 * - 사용자에게 친화적인 메시지와 재시도 버튼을 제공합니다.
 * - 상세 에러(서버 메시지 등)는 화면에 노출하지 않습니다.
 *   개발 중 확인은 Logcat의 "CashChatAuth" 태그를 이용하세요.
 */
@Composable
private fun AuthErrorScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "연결할 수 없어요",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "네트워크 상태를 확인하고\n다시 시도해주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onRetry) {
                Text("다시 시도")
            }
        }
    }
}

/** Play 스토어의 앱 상세로 이동(미설치 시 웹 폴백). 강제 업데이트 게이트에서 사용. */
private fun openPlayStore(context: Context) {
    val packageName = context.packageName
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { e ->
        if (e is ActivityNotFoundException) {
            // 웹 폴백도 브라우저 핸들러 부재/제한 환경에서 예외가 날 수 있으므로 감싸 크래시를 막는다.
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { fallbackError ->
                Log.e("CashChatGate", "Play 스토어 웹 폴백 실행 실패", fallbackError)
            }
        }
    }
}
