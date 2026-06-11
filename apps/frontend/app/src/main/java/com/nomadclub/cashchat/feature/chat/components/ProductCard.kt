package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.chat.model.ProductDto

@Composable
fun ProductCardList(item: ChatItem.ProductCards) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item.products.forEach { product ->
            OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(product.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("₩%,d".format(product.price), style = MaterialTheme.typography.bodyMedium)
                        product.rating?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "★$it (${product.reviewCount ?: 0})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(
                        onClick = { uriHandler.openUri(product.trackingUrl) },
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("쿠팡에서 보기 →") }
                    Text(
                        "ⓘ 이 포스팅은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductCardPreview() {
    ProductCardList(
        ChatItem.ProductCards(
            "p1",
            listOf(
                ProductDto("삼성 갤럭시 버즈3 Pro", 149000, 4.7, 32000, null, "https://link.coupang.com/x"),
            ),
        ),
    )
}
