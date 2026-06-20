package com.nomadclub.cashchat.feature.shop

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.points.PointsRepository
import com.nomadclub.cashchat.shared.shop.InventoryDto
import com.nomadclub.cashchat.shared.shop.ShopApi
import com.nomadclub.cashchat.shared.shop.ShopCatalogDto
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** BE ShopItemCategory와 1:1 (phase1은 ENHANCE만 활성) */
private val categories = listOf(
    "ENHANCE" to "강화",
    "COSMETIC" to "꾸미기",
    "VOUCHER" to "상품권",
)

private val itemEmojis = mapOf(
    "ENHANCE" to "🧿",
    "COSMETIC" to "🎀",
    "VOUCHER" to "🎁",
)

@Composable
fun ShopScreen(
    shopApi: ShopApi = koinInject(),
    pointsRepository: PointsRepository = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf("ENHANCE") }
    var catalog by remember { mutableStateOf<ShopCatalogDto?>(null) }
    var inventory by remember { mutableStateOf<InventoryDto?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var purchaseTarget by remember { mutableStateOf<ShopCatalogDto.Item?>(null) }
    // 구매 멱등키 — 구매 확인을 열 때 1회 생성하고 성공 전까지 재시도에 같은 키를 재사용해
    // 서버가 처리 후 응답만 실패한 경우의 중복 구매/코인 차감을 방지한다.
    var purchaseIdempotencyKey by remember { mutableStateOf<String?>(null) }
    var purchasing by remember { mutableStateOf(false) }
    // 로드 실패 후 "다시 시도" 시 LaunchedEffect를 재실행하기 위한 트리거
    var reloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedCategory, reloadTrigger) {
        loadFailed = false
        catalog = null
        runCatching { shopApi.getItems(selectedCategory) }
            .onSuccess { catalog = it }
            .onFailure { e ->
                if (e is CancellationException) throw e
                loadFailed = true
            }
        runCatching { shopApi.getInventory() }
            .onSuccess { inventory = it }
            .onFailure { e -> if (e is CancellationException) throw e }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "포인트 상점",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.first }) { (code, label) ->
                val selected = code == selectedCategory
                Surface(
                    modifier = Modifier.clickable { selectedCategory = code },
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(99.dp),
                ) {
                    Text(
                        label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // 보유 아이템 요약
        inventory?.takeIf { it.items.isNotEmpty() }?.let { inv ->
            Text(
                "보유: " + inv.items.joinToString { "${it.itemCode} ×${it.qty}" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        // 가변 상태(catalog)를 불변 지역값으로 캡처한다.
        // LazyColumn items{} 빌더는 snapshot item provider로 지연 재평가되는데,
        // 카테고리 전환 시 catalog가 null로 바뀌는 순간 catalog!! 가 NPE를 일으켰다.
        val currentCatalog = catalog
        when {
            loadFailed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("상점을 불러오지 못했어요")
                    TextButton(onClick = { reloadTrigger++ }) { Text("다시 시도") }
                }
            }
            currentCatalog == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !currentCatalog.phase1Active -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🔒 준비 중인 카테고리예요")
            }
            currentCatalog.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("판매 중인 아이템이 없어요")
            }
            else -> {
                // recomposition마다 재정렬되지 않도록 정렬 결과를 캐싱한다.
                val sortedItems = remember(currentCatalog.items) {
                    currentCatalog.items.sortedBy { it.displayOrder }
                }
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                items(sortedItems, key = { it.itemCode }) { item ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(itemEmojis[selectedCategory] ?: "🎁", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    item.effectSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = {
                                    purchaseTarget = item
                                    purchaseIdempotencyKey = UUID.randomUUID().toString()
                                },
                                enabled = !purchasing,
                            ) {
                                Text("🪙 %,d".format(item.priceCoin))
                            }
                        }
                    }
                }
                }
            }
        }
    }

    purchaseTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!purchasing) purchaseTarget = null },
            title = { Text("구매 확인") },
            text = { Text("${item.name}을(를) 🪙%,d에 구매할까요?".format(item.priceCoin)) },
            confirmButton = {
                TextButton(
                    enabled = !purchasing,
                    onClick = {
                        purchasing = true
                        scope.launch {
                            // 성공/명확한 거절이면 닫고 키 폐기, 일시적 네트워크 오류면 열어둔 채 같은 키로 재시도.
                            var finished = false
                            try {
                                val key = purchaseIdempotencyKey
                                    ?: UUID.randomUUID().toString().also { purchaseIdempotencyKey = it }
                                val result = shopApi.purchase(item.itemCode, 1, key)
                                finished = true
                                inventory = InventoryDto(result.inventory.map { InventoryDto.Item(it.itemCode, it.qty) })
                                // 구매 후 서버 잔액을 다른 화면(혜택존/마이페이지)과 동일 소스로 동기화
                                pointsRepository.applyDelta(result.coinBalance - pointsRepository.balance.value)
                                Toast.makeText(context, "구매 완료 · 잔액 🪙%,d".format(result.coinBalance), Toast.LENGTH_SHORT).show()
                            } catch (e: ApiException) {
                                finished = true // 서버가 명확히 거절 — 같은 키 재시도 의미 없음
                                val message = if (e.code == "INSUFFICIENT_COIN") "코인이 부족해요" else e.message
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, "구매에 실패했어요 · 다시 시도해주세요", Toast.LENGTH_SHORT).show()
                            } finally {
                                purchasing = false
                                if (finished) {
                                    purchaseTarget = null
                                    purchaseIdempotencyKey = null
                                }
                            }
                        }
                    },
                ) { Text("구매") }
            },
            dismissButton = {
                TextButton(enabled = !purchasing, onClick = { purchaseTarget = null }) { Text("취소") }
            },
        )
    }
}
