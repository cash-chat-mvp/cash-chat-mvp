package com.nomadclub.cashchat.feature.rewards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class MissionUiState(
    val id: String,
    val title: String,
    val description: String,
    val points: Int,
    val icon: ImageVector,
    val maxProgress: Int? = null
)

@Composable
fun RewardsScreen(
    points: Int,
    messageCount: Int,
    addPoints: (Int) -> Unit
) {
    val missions = remember {
        listOf(
            MissionUiState("1", "출석 체크", "오늘 하루 출석하고 포인트 받기", 10, Icons.Default.CalendarMonth),
            MissionUiState("2", "채팅 미션", "오늘 AI와 10번 대화하기", 50, Icons.AutoMirrored.Filled.Chat, maxProgress = 10),
            MissionUiState("3", "동영상 시청", "광고 보고 룰렛 돌리기", 100, Icons.Default.PlayArrow)
        )
    }
    var claimedMissionIds by remember { mutableStateOf(setOf<String>()) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val targetPoints = 4500
    val progressPercent = (points.toFloat() / targetPoints).coerceIn(0f, 1f)
    val remainingPoints = (targetPoints - points).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    Text(
                        text = "리워드 미션",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Progress Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF5C6BFA), Color(0xFF4A5AE8))
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "커피 교환까지",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "${String.format("%,d", points)} / ${String.format("%,d", targetPoints)} P",
                                        color = Color.White.copy(alpha = 0.9f),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { progressPercent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(99.dp)),
                                    trackColor = Color.White.copy(alpha = 0.3f),
                                    color = Color.White,
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "${String.format("%,d", remainingPoints)}P 더 모으면 스타벅스 아메리카노!",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Missions List
        items(missions, key = { it.id }) { mission ->
            val isClaimed = claimedMissionIds.contains(mission.id)
            val progress = if (mission.maxProgress != null) messageCount.coerceAtMost(mission.maxProgress) else null
            val canClaim = !isClaimed && (mission.maxProgress == null || (progress ?: 0) >= mission.maxProgress)

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 50 }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF4F6F8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = mission.icon,
                                contentDescription = mission.title,
                                tint = Color(0xFF5C6BFA),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mission.title,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF111827)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mission.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280),
                                lineHeight = 18.sp
                            )
                            
                            if (mission.maxProgress != null && progress != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                LinearProgressIndicator(
                                    progress = { progress.toFloat() / mission.maxProgress.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(99.dp)),
                                    trackColor = Color(0xFFF1F5F9),
                                    color = Color(0xFF5C6BFA),
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "진행도 $progress/${mission.maxProgress}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "+${mission.points}P",
                                    color = Color(0xFFFF6B00),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                                
                                if (isClaimed) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "완료",
                                            color = Color(0xFF94A3B8),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            addPoints(mission.points)
                                            claimedMissionIds = claimedMissionIds + mission.id
                                        },
                                        enabled = canClaim,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (canClaim) Color(0xFFFF6B00) else Color(0xFFF1F5F9),
                                            contentColor = if (canClaim) Color.White else Color(0xFF94A3B8)
                                        ),
                                        modifier = Modifier.height(40.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                                    ) {
                                        Text(
                                            text = if (canClaim) "받기" else "미달성",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            // Lucky Roulette Card
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, delayMillis = 200)) + slideInVertically(tween(600, delayMillis = 200)) { 50 }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF6B00), Color(0xFFFF8C3A))
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "행운의 룰렛",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        "최대 1,000P 획득 기회!",
                                        color = Color.White.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFFFF6B00)
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                            ) {
                                Text(
                                    "광고 보고 룰렛 돌리기",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
