package com.nomadclub.cashchat.shared.shop

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class ShopCatalogDto(val category: String, val phase1Active: Boolean, val items: List<Item>) {
    @Serializable
    data class Item(
        val itemCode: String,
        val name: String,
        val priceCoin: Long,
        val effectSummary: String,
        val displayOrder: Int,
    )
}

@Serializable
data class PurchaseResultDto(
    val purchaseOrderId: Long,
    val status: String,
    val coinBalance: Long,
    val inventory: List<Item>,
) {
    @Serializable
    data class Item(val itemCode: String, val qty: Int)
}

@Serializable
data class InventoryDto(val items: List<Item>) {
    @Serializable
    data class Item(val itemCode: String, val qty: Int)
}

@Serializable
private data class PurchaseRequest(val itemCode: String, val qty: Int, val idempotencyKey: String)

class ShopApi(private val client: HttpClient, private val baseUrl: String) {
    /** category: BE ShopItemCategory enum 이름 (ENHANCE/COSMETIC/VOUCHER) — 잘못된 값은 400 */
    @Throws(Exception::class)
    suspend fun getItems(category: String): ShopCatalogDto =
        client.get("$baseUrl/api/shop/items") { parameter("category", category) }.body()

    /** idempotencyKey: UUID 형식 필수(서버 검증). 버튼 1탭 = 새 UUID, 재시도는 같은 키. */
    @Throws(Exception::class)
    suspend fun purchase(itemCode: String, qty: Int, idempotencyKey: String): PurchaseResultDto =
        client.post("$baseUrl/api/shop/purchase") {
            contentType(ContentType.Application.Json)
            setBody(PurchaseRequest(itemCode, qty, idempotencyKey))
        }.body()

    @Throws(Exception::class)
    suspend fun getInventory(): InventoryDto = client.get("$baseUrl/api/inventory/me").body()
}
