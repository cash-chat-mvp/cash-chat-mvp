package com.nomadclub.cashchat.feature.auth

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 로그인 폼 UI만 담당하는 Composable (재사용/테스트 편의를 위해 화면과 분리).
 * - onSubmit: 카카오/Apple 로그인 버튼 클릭 시 호출되는 콜백
 */
@Composable
fun LoginFormContent(
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 코인 아이콘을 계속 돌리기 위한 무한 애니메이션 (2초마다 0~360도)
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

    // 세로 그라데이션 배경 + 전체 화면 채우기
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
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .height(96.dp)
                    )

                    androidx.compose.material3.Icon(
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
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF5C6BFA)
                )
            ) {
                Text(
                    text = "카카오로 3초 만에 시작하기",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Apple로 로그인",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
 * 로그인 화면. ViewModel에서 상태를 가져와 LoginFormContent에 전달합니다.
 * - viewModel(): Compose에서 ViewModel 인스턴스를 얻는 방법 (화면마다 하나씩 유지됨)
 * - collectAsState(): Flow 값을 Composable이 구독해서 상태로 쓰게 합니다 (값 바뀌면 다시 그려짐)
 */
@Composable
fun LoginScreen(modifier: Modifier = Modifier, viewModel: LoginViewModel = viewModel()) {
    val email by viewModel.emailState.collectAsState()

    LoginFormContent(
        onSubmit = { viewModel.login() },
        modifier = modifier
    )
}
