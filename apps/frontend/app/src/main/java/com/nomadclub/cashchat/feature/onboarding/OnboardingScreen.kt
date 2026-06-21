package com.nomadclub.cashchat.feature.onboarding

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.nomadclub.cashchat.BuildConfig
import org.koin.compose.koinInject
import com.nomadclub.cashchat.shared.invite.InviteStore
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

@Composable
fun OnboardingScreen(
    onLoginSuccess: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val context = LocalContext.current
    val inviteStore = koinInject<InviteStore>()
    var referral by remember { mutableStateOf("") }
    var referralDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        rotation.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    // 로그인 성공 시 메인으로 이동
    LaunchedEffect(uiState) {
        if (uiState is OnboardingViewModel.UiState.Success) {
            onLoginSuccess()
        }
        if (uiState is OnboardingViewModel.UiState.Error) {
            snackbarHostState.showSnackbar((uiState as OnboardingViewModel.UiState.Error).message)
            viewModel.clearError()
        }
    }

    // Google Sign-In: serverAuthCode를 받아 BE로 전달
    val googleSignInClient = remember {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestServerAuthCode(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build()
        )
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("CashChatAuth", "🔁 Google Sign-In result | resultCode=${result.resultCode} (OK=${Activity.RESULT_OK})")
        // resultCode 상관없이 task 파싱 → DEVELOPER_ERROR 시 RESULT_CANCELED로 오는 경우가 있음
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val authCode = account.serverAuthCode
            Log.d("CashChatAuth", "✅ 계정 선택 완료 | serverAuthCode=${if (authCode != null) "OK" else "NULL"}")
            if (authCode != null) {
                viewModel.loginWithGoogle(authCode)
            } else {
                if (BuildConfig.DEBUG) {
                    Log.e("CashChatAuth", "❌ serverAuthCode=null — GOOGLE_WEB_CLIENT_ID 타입 확인 필요")
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Google 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.")
                }
            }
        } catch (e: ApiException) {
            if (BuildConfig.DEBUG) {
                Log.e("CashChatAuth", "❌ Google Sign-In ApiException | statusCode=${e.statusCode}")
            }
            // 12501 = 사용자 취소, 그 외 = 오류
            val message = if (e.statusCode == 12501) "Google 로그인을 취소했습니다."
                          else "Google 로그인 오류가 발생했습니다. 다시 시도해주세요."
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val isLoading = uiState is OnboardingViewModel.UiState.Loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF5C6BFA), Color(0xFF4A5AE8))
                )
            )
            .padding(24.dp)
    ) {
        // 중앙 로고 + 텍스트
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.scaleIn(initialScale = 0.5f) + fadeIn(tween(500))
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(100.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier
                            .offset(x = 10.dp, y = (-10).dp)
                            .size(48.dp)
                            .rotate(rotation.value)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(tween(500, delayMillis = 200)) { 50 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "AI Chat+",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "대화하고 포인트 받자!",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 하단 버튼 영역
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 400)) + slideInVertically(tween(500, delayMillis = 400)) { 50 }
            ) {
                Button(
                    onClick = {
                        // Google Sign-In 시작 (이전 로그인 세션 해제 후 항상 계정 선택 화면 표시)
                        googleSignInClient.signOut().addOnCompleteListener {
                            googleLauncher.launch(googleSignInClient.signInIntent)
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF5C6BFA)
                    )
                ) {
                    Text("Google로 로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 500)) + slideInVertically(tween(500, delayMillis = 500)) { 50 }
            ) {
                Button(
                    onClick = { viewModel.loginAsGuest() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    )
                ) {
                    Text("게스트로 시작하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!referralDone) {
                OutlinedTextField(
                    value = referral, onValueChange = { referral = it.uppercase() },
                    singleLine = true, label = { Text("추천 코드 (선택)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        val code = referral
                        coroutineScope.launch {
                            val result = runCatching { inviteStore.redeem(code) }.getOrNull()
                            val msg = when {
                                result == null -> "잠시 후 다시 시도해주세요"
                                result.success -> { referralDone = true; "⚡${result.awardedEnergy} 에너지 적용됐어요!" }
                                else -> result.message ?: "코드를 확인해주세요"
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    },
                    enabled = referral.isNotBlank(),
                ) { Text("추천 코드 적용", color = Color.White) }
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 600))
            ) {
                Text(
                    text = "가입하면 즉시 500P 지급!",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 로딩 인디케이터
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
