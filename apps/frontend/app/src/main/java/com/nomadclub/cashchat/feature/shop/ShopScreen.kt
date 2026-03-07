package com.nomadclub.cashchat.feature.shop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class ShopItem(
    val id: String,
    val name: String,
    val brand: String,
    val points: Int,
    val emoji: String,
    val category: Category
)

private enum class Category(val label: String, val icon: ImageVector) {
    ALL("전체", Icons.Default.ShoppingBag),
    CAFE("카페", Icons.Default.Coffee),
    CVS("편의점", Icons.Default.Store),
    FOOD("외식", Icons.Default.CardGiftcard),
    VOUCHER("상품권", Icons.Default.CreditCard)
}

@Composable
fun ShopScreen(
    points: Int,
    spendPoints: (Int) -> Boolean
) {
    val items = remember {
        listOf(
            ShopItem("1", "아메리카노 Tall", "스타벅스", 4500, "☕", Category.CAFE),
            ShopItem("2", "카페라떼 Grande", "스타벅스", 5500, "☕", Category.CAFE),
            ShopItem("3", "1+1 교환권", "CU", 3000, "🏪", Category.CVS),
            ShopItem("4", "해피콘 3,000원권", "배스킨라빈스", 3000, "🍦", Category.CAFE),
            ShopItem("5", "치킨 할인권 5,000원", "BBQ", 5000, "🍗", Category.FOOD),
            ShopItem("6", "문화상품권 5,000원", "문화상품권", 5000, "🎁", Category.VOUCHER)
        )
    }

    var selectedCategory by remember { mutableStateOf(Category.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var showNotEnoughDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<ShopItem?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val filteredItems = items.filter {
        (selectedCategory == Category.ALL || it.category == selectedCategory) &&
        (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.brand.contains(searchQuery, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6F8))) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "포인트 상점",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF111827)
                    )
                    Surface(
                        color = Color(0xFF5C6BFA),
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Text(
                            text = "🪙 ${String.format("%,d", points)} P",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                
                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    placeholder = { Text("상품을 검색하세요", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF1F5F9),
                        unfocusedContainerColor = Color(0xFFF1F5F9),
                        disabledContainerColor = Color(0xFFF1F5F9),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true
                )
            }
        }

        // Categories
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 0.dp
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Category.values(), key = { it.name }) { category ->
                    val selected = category == selectedCategory
                    Surface(
                        modifier = Modifier.clickable { selectedCategory = category },
                        color = if (selected) Color(0xFF5C6BFA) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                category.icon, 
                                contentDescription = null, 
                                modifier = Modifier.size(16.dp),
                                tint = if (selected) Color.White else Color(0xFF4B5563)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                category.label,
                                color = if (selected) Color.White else Color(0xFF4B5563),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Products Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            gridItems(filteredItems, key = { it.id }) { item ->
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.9f)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.2f)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.emoji, fontSize = 48.sp)
                            }
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(item.brand, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    item.name, 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1F2937),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.height(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "🪙 ${String.format("%,d", item.points)} P", 
                                    color = Color(0xFFFF6B00), 
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        selectedItem = item
                                        if (points >= item.points) {
                                            showPurchaseDialog = true
                                        } else {
                                            showNotEnoughDialog = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BFA)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("구매하기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Purchase Dialog
    if (showPurchaseDialog && selectedItem != null) {
        val item = selectedItem!!
        Dialog(
            onDismissRequest = { showPurchaseDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showPurchaseDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("구매 확인", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color(0xFF111827))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${item.brand} ${item.name}을(를) 구매하시겠습니까?", 
                            color = Color(0xFF4B5563),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("현재 포인트", color = Color(0xFF64748B), fontSize = 14.sp)
                                    Text("${String.format("%,d", points)} P", fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("사용 포인트", color = Color(0xFF64748B), fontSize = 14.sp)
                                    Text("-${String.format("%,d", item.points)} P", fontWeight = FontWeight.Bold, color = Color(0xFFFF6B00))
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("남은 포인트", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                    Text("${String.format("%,d", points - item.points)} P", fontWeight = FontWeight.Black, color = Color(0xFF5C6BFA))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showPurchaseDialog = false },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("취소", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = {
                                    if (spendPoints(item.points)) {
                                        showPurchaseDialog = false
                                        selectedItem = null
                                    } else {
                                        showPurchaseDialog = false
                                        showNotEnoughDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BFA)),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("구매하기", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    // Not Enough Points Dialog
    if (showNotEnoughDialog) {
        Dialog(
            onDismissRequest = { showNotEnoughDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showNotEnoughDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("😢", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("포인트가 부족해요!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color(0xFF111827))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "10초 광고를 보고 포인트를 더 모으시겠어요?", 
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showNotEnoughDialog = false },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF475569)),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("닫기", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { showNotEnoughDialog = false },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("광고 보기", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}
