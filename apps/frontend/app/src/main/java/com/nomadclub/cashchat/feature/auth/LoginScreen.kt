package com.nomadclub.cashchat.feature.auth

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.nomadclub.cashchat.BuildConfig

private const val TAG = "CashChatAuth"

/**
 * Google Sign-In을 사용하려면 Google Cloud Console에서 OAuth 2.0 클라이언트 ID가 필요합니다.
 *
 * 설정 방법:
 * 1. https://console.cloud.google.com → APIs & Services → Credentials
 * 2. OAuth 2.0 Client ID 생성 (Web application 타입) → Client ID를 복사
 * 3. local.properties에 추가: GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
 * 4. app/build.gradle.kts에서 BuildConfig 필드로 주입 (이미 준비됨)
 *
 * serverAuthCode가 필요한 이유:
 *   - Android SDK에서 받은 idToken은 BE에서 직접 사용 불가
 *   - serverAuthCode를 BE로 전달 → BE가 Google과 직접 교환 → 사용자 정보 획득
 *   - Swagger: GET /api/auth/callback/google?code={serverAuthCode}&deviceToken={uuid}
 */

@Composable
fun LoginFormContent(
    onGoogleSignIn: (serverAuthCode: String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Google Sign-In 런처 — Activity 결과를 Compose에서 처리
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "🔁 Google Sign-In result received | resultCode=${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val serverAuthCode = account?.serverAuthCode
            Log.d(TAG, "✅ Google 계정 선택 완료 | serverAuthCode=${if (serverAuthCode != null) "OK" else "NULL"}")
            if (serverAuthCode != null) {
                onGoogleSignIn(serverAuthCode)
            } else {
                // serverAuthCode가 null이면 WebClientId가 잘못된 것
                Log.e(TAG, "❌ serverAuthCode가 null입니다. local.properties의 GOOGLE_WEB_CLIENT_ID가 Web application 타입인지 확인하세요.")
            }
        } catch (e: ApiException) {
            // statusCode: 10 = DEVELOPER_ERROR (잘못된 설정), 12501 = 취소, 12502 = 진행 중
            Log.e(TAG, "❌ Google Sign-In ApiException | statusCode=${e.statusCode}, message=${e.message}")
        }
    }

    // 코인 아이콘을 계속 돌리기 위한 무한 애니메이션
    val infiniteTransition = rememberInfiniteTransition(label = "coin_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF5C6BFA), Color(0xFF4A5AE8))
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .height(96.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier
                            .offset(x = 20.dp, y = (-16).dp)
                            .height(48.dp)
                            .rotate(rotation)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "AI Chat+",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "대화하고 포인트 받자!",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Google 로그인 버튼
            Button(
                onClick = {
                    val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                    Log.d(TAG, "▶ Google Sign-In 시작 | webClientId=${webClientId.take(20)}...")
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestServerAuthCode(webClientId)
                        .requestEmail()
                        .build()
                    val signInClient = GoogleSignIn.getClient(context, gso)
                    // 이전 계정 로그아웃 후 항상 계정 선택 화면 표시
                    signInClient.signOut().addOnCompleteListener {
                        Log.d(TAG, "▶ 이전 세션 signOut 완료, 계정 선택 화면 시작")
                        googleSignInLauncher.launch(signInClient.signInIntent)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF5C6BFA)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "G  Google로 로그인",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "가입하면 즉시 500P 지급!",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/**
 * 로그인 화면 진입점.
 * onGoogleSignInSuccess: serverAuthCode를 받아 상위(NavHost)에서 BE API 호출
 */
@Composable
fun LoginScreen(
    onGoogleSignInSuccess: (serverAuthCode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(false) }

    LoginFormContent(
        onGoogleSignIn = { serverAuthCode ->
            isLoading = true
            try {
                onGoogleSignInSuccess(serverAuthCode)
            } finally {
                // 방어적 리셋: 향후 네비게이션 로직 변경으로 popBackStack이 생략될 경우에도
                // 로딩 인디케이터가 영구적으로 표시되지 않도록 보장
                isLoading = false
            }
        },
        isLoading = isLoading,
        modifier = modifier
    )
}
