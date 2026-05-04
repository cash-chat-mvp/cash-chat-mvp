package com.nomadclub.cashchat.core.network

import com.google.gson.Gson
import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.data.model.AuthResponse
import com.nomadclub.cashchat.data.model.TokenRefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

// Confluence 문서: 동시 갱신 요청 중 첫 번째만 성공하므로 Mutex로 직렬화
@Volatile
private var isRefreshing = false

class TokenAuthenticator(
    private val tokenDataStore: TokenDataStore,
    private val baseUrl: String
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 이미 갱신 중이거나 갱신 요청 자체가 401이면 포기
        if (response.request.url.encodedPath.contains("auth/refresh") ||
            response.request.url.encodedPath.contains("auth/guest")
        ) return null

        return synchronized(this) {
            val role = tokenDataStore.getRoleBlocking()
            when {
                role == "GUEST" -> refreshGuestToken(response.request)
                role == "MEMBER" || role == "ADMIN" -> refreshMemberToken(response.request)
                else -> null
            }
        }
    }

    private fun refreshGuestToken(originalRequest: Request): Request? {
        val deviceToken = tokenDataStore.getDeviceTokenBlocking() ?: return null
        return runBlocking {
            try {
                val client = OkHttpClient()
                val req = Request.Builder()
                    .url("${baseUrl}api/auth/guest?deviceToken=$deviceToken")
                    .post(ByteArray(0).toRequestBody())
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@runBlocking null
                    val authResponse = Gson().fromJson(body, AuthResponse::class.java)
                    tokenDataStore.saveAuthResponse(authResponse)
                    originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${authResponse.accessToken}")
                        .build()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun refreshMemberToken(originalRequest: Request): Request? {
        val refreshToken = tokenDataStore.getRefreshTokenBlocking() ?: return null
        return runBlocking {
            try {
                val client = OkHttpClient()
                val json = Gson().toJson(TokenRefreshRequest(refreshToken))
                val body = json.toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("${baseUrl}api/auth/refresh")
                    .post(body)
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val respBody = resp.body?.string() ?: return@runBlocking null
                    val authResponse = Gson().fromJson(respBody, AuthResponse::class.java)
                    tokenDataStore.saveAuthResponse(authResponse)
                    originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${authResponse.accessToken}")
                        .build()
                } else {
                    // Refresh Token 만료 → 저장된 토큰 삭제 → null 반환 시 AuthRepository가 재로그인 유도
                    tokenDataStore.clearTokens()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
