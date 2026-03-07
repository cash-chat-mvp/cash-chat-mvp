package com.nomadclub.cashchat.feature.onboarding

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 앱 최초 실행 시 보여주는 온보딩(소개) 화면.
 * - onStartClick: "시작하기" 버튼을 누르면 메인 화면으로 넘어가기 위해 호출되는 콜백
 */
@Composable
fun OnboardingScreen(onStartClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }

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
        // Center Content (Logo + Text)
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
                    
                    // Rotating Coin
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

        // Bottom Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 400)) + slideInVertically(tween(500, delayMillis = 400)) { 50 }
            ) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF5C6BFA)
                    )
                ) {
                    Text("카카오로 3초 만에 시작하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 500)) + slideInVertically(tween(500, delayMillis = 500)) { 50 }
            ) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    )
                ) {
                    Text("Apple로 로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
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
    }
}