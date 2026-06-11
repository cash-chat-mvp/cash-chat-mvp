package com.nomadclub.cashchat.shared.chat.model

/** SSE `event: product` 페이로드의 상품 1건 (P2-2). */
@kotlinx.serialization.Serializable
data class ProductDto(
    val title: String,
    val price: Long,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val imageUrl: String? = null,
    val trackingUrl: String,
)

/** 채팅 화면에 그려지는 항목. 쿠팡 카드·Ad Gate 등은 추후 타입 추가로 확장(스펙 §1.1). */
sealed interface ChatItem {
    val id: String

    enum class SendStatus { PENDING, CONFIRMED, BLOCKED }

    data class UserMessage(
        override val id: String,
        val text: String,
        val status: SendStatus,
    ) : ChatItem

    data class AssistantMessage(
        override val id: String,
        val text: String,
        val isStreaming: Boolean,
        val isError: Boolean = false,
        val gated: Boolean = false,
    ) : ChatItem

    data class ProductCards(override val id: String, val products: List<ProductDto>) : ChatItem
}
