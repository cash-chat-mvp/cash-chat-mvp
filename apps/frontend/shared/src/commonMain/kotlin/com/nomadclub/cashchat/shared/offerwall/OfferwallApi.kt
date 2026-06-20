package com.nomadclub.cashchat.shared.offerwall

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

@Serializable
data class UserTokenDto(val token: String)

/**
 * TNK 오퍼월 사용자 토큰 발급 API.
 * 사용자당 안정적인 불투명 토큰(get-or-create)을 받아 TNK SDK setUserName 에 사용한다.
 */
class OfferwallApi(private val client: HttpClient, private val baseUrl: String) {
    // iOS 에서 호출하는 suspend 는 @Throws 가 없으면 예외 발생 시 앱이 크래시한다.
    @Throws(Exception::class)
    suspend fun issueUserToken(): UserTokenDto =
        client.post("$baseUrl/api/offerwall/tnk/user-token").body()
}
